// @vitest-environment jsdom
import { mount, flushPromises } from "@vue/test-utils";
import { createMemoryHistory, createRouter, onBeforeRouteLeave } from "vue-router";
import { defineComponent, h } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App.vue";
import { useSession } from "./app/session";
import { setToken } from "./shared/api";
import { createPrivateNavigationState } from "./shared/navigation/privateNavigationState";
import { useUnsavedChanges } from "./shared/composables/useUnsavedChanges";
import { resolveRouteAccess } from "./app/router";

vi.mock("./shared/ui/ToastHost.vue", () => ({ default: { template: "<aside />" } }));

beforeEach(() => {
  const session = useSession();
  session.state.ready = true;
  session.state.setup.initialized = true;
  session.state.user = { id: 1, studentNo: "synthetic-admin", name: "合成管理员", role: "ADMIN" };
});

afterEach(() => {
  useSession().expireSession();
  vi.unstubAllGlobals();
  localStorage.clear();
  sessionStorage.clear();
});

describe("public and authenticated route boundary", () => {
  it.each([true, false])("blocks setup inference after startup failure and retries the original target (initialized=%s)", async (initialized) => {
    const session = useSession();
    session.state.ready = false;
    session.state.user = null;
    const response = (data: unknown) => new Response(JSON.stringify(data), { headers: { "content-type": "application/json" } });
    const access = { mode: "LOCAL", kioskAvailable: true, allowedRemoteRoles: [] };
    const fetchMock = vi.fn().mockResolvedValueOnce(response(access)).mockRejectedValueOnce(new TypeError("synthetic private connection error"));
    vi.stubGlobal("fetch", fetchMock);
    const loadSetup = vi.fn().mockResolvedValue({ template: '<form>初始化本机</form>' });
    const history = createMemoryHistory();
    history.replace("/login?next=/admin/stats&from=2026-10-02");
    const router = createRouter({ history, routes: [
      { path: "/login", name: "login", component: { template: "<form>登录入口</form>" } },
      { path: "/setup", name: "setup", component: loadSetup },
    ] });
    router.beforeEach(async to => {
      if (!session.state.ready) session.state.startupTarget = to.fullPath;
      await session.bootstrap();
      return session.state.ready ? resolveRouteAccess(to, session.state) : false;
    });
    const wrapper = mount(App, { attachTo: document.body, global: { plugins: [router] } });
    try {
      await flushPromises();
      expect(wrapper.text()).toContain("暂时无法连接系统");
      expect(wrapper.text()).toContain("入口状态待确认");
      expect(wrapper.text()).not.toContain("初始化本机");
      expect(wrapper.text()).not.toContain("synthetic private");
      expect(loadSetup).not.toHaveBeenCalled();
      fetchMock.mockResolvedValueOnce(response(access)).mockRejectedValueOnce(new TypeError("still offline"));
      await wrapper.get('button').trigger('click');
      await flushPromises();
      expect(document.activeElement).toBe(wrapper.get('button').element);
      fetchMock.mockResolvedValueOnce(response(access)).mockResolvedValueOnce(response({ initialized }));
      await wrapper.get('button').trigger('click');
      await flushPromises();
      expect(wrapper.text()).toContain(initialized ? "登录入口" : "初始化本机");
      expect(wrapper.text()).not.toContain("重试连接");
      if (initialized) expect(router.currentRoute.value.query).toEqual({ next: "/admin/stats", from: "2026-10-02" });
    } finally { wrapper.unmount(); }
  });

  it("settles a pending unsaved leave when the expired workspace is unmounted", async () => {
    let guard!: ReturnType<typeof useUnsavedChanges>;
    const workspace = defineComponent({ setup() {
      guard = useUnsavedChanges(() => true);
      onBeforeRouteLeave(() => new Promise<boolean>(resolve => guard.request(() => resolve(true), () => resolve(false))));
      return () => h("main", "合成未保存表单");
    } });
    const router = createRouter({ history: createMemoryHistory(), routes: [
      { path: "/admin", meta: { auth: true }, component: workspace },
      { path: "/login", component: { template: "<form>登录</form>" } },
    ] });
    await router.push("/admin");
    const wrapper = mount(App, { global: { plugins: [router] } });
    let settled = false;
    const navigation = router.replace("/login").then(() => { settled = true; });
    try {
      await flushPromises();
      expect(guard.confirmOpen.value).toBe(true);
      useSession().expireSession();
      await flushPromises();
      expect(wrapper.text()).not.toContain("合成未保存表单");
      expect(wrapper.find(".auth-form").exists()).toBe(true);
      expect(settled).toBe(true);
    } finally {
      guard.cancel();
      await navigation;
      wrapper.unmount();
    }
  });

  it("removes private content after offline logout even if login navigation fails, then permits a fresh login", async () => {
    const missingLogin = vi.fn(() => Promise.reject(new Error("synthetic missing chunk")));
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: "/admin", meta: { auth: true }, component: { template: '<main><p>合成私有记录</p><button>修改记录</button></main>' } },
        { path: "/admin/today", name: "today", meta: { auth: true }, component: { template: "<main>新的工作区</main>" } },
        { path: "/login", name: "login", component: missingLogin },
      ],
    });
    router.onError(() => {});
    await router.push("/admin");
    const wrapper = mount(App, { global: { plugins: [router] } });
    const memory = createPrivateNavigationState<string>();
    const previous = memory.scope();
    previous.set("visit", "合成关键词");
    setToken("synthetic-old-token");
    const fetchMock = vi.fn().mockRejectedValue(new TypeError("offline"));
    vi.stubGlobal("fetch", fetchMock);
    try {
      expect(wrapper.text()).toContain("合成私有记录");
      await useSession().logout();
      await flushPromises();
      expect(wrapper.text()).not.toContain("合成私有记录");
      expect(wrapper.find(".auth-form").exists()).toBe(true);
      expect(previous.get("visit")).toBeUndefined();
      await expect(router.replace({ name: "login" })).rejects.toThrow("synthetic missing chunk");
      await flushPromises();
      expect(wrapper.find(".auth-form").exists()).toBe(true);
      expect(wrapper.find('button').text()).not.toBe("修改记录");
      expect(router.currentRoute.value.path).toBe("/admin");

      fetchMock.mockResolvedValue(new Response(JSON.stringify({ id: 2, studentNo: "fresh-admin", name: "新合成管理员", role: "ADMIN", token: "synthetic-new-token" }), { headers: { "content-type": "application/json" } }));
      await wrapper.get('#login-account').setValue("fresh-admin");
      await wrapper.get('#login-password').setValue("synthetic-password");
      await wrapper.get('.auth-form').trigger('submit');
      await flushPromises();
      expect(router.currentRoute.value.name).toBe("today");
      expect(wrapper.text()).toContain("新的工作区");
      expect(wrapper.find('.auth-form').exists()).toBe(false);
      expect(wrapper.text()).not.toContain("合成私有记录");
    } finally {
      wrapper.unmount();
    }
  });

  it("hides the protected page on expiry even when a navigation guard cancels the redirect", async () => {
    const router = createRouter({ history: createMemoryHistory(), routes: [
      { path: "/admin", meta: { auth: true }, component: { template: "<main>合成私有记录</main>" } },
      { path: "/login", component: { template: "<form>登录</form>" } },
    ] });
    await router.push("/admin");
    const wrapper = mount(App, { global: { plugins: [router] } });
    router.beforeEach(() => false);
    try {
      useSession().expireSession();
      await router.replace("/login");
      await flushPromises();
      expect(router.currentRoute.value.path).toBe("/admin");
      expect(wrapper.text()).not.toContain("合成私有记录");
      expect(wrapper.find(".auth-form").exists()).toBe(true);
    } finally {
      wrapper.unmount();
    }
  });

  it("renders the workspace after setup/login and can return to a public page", async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: "/setup", component: { template: "<form>初始化</form>" } },
        { path: "/admin", meta: { auth: true }, component: { template: "<main>工作区</main>" } },
        { path: "/login", component: { template: "<form>登录</form>" } },
      ],
    });
    await router.push("/setup");
    await router.isReady();
    const wrapper = mount(App, { global: { plugins: [router], stubs: { transition: false } } });
    try {
      await router.push("/admin");
      await flushPromises();
      expect(wrapper.find("main").text()).toBe("工作区");
      await router.push("/login");
      await flushPromises();
      expect(wrapper.find("form").text()).toBe("登录");
      await router.push("/admin");
      await flushPromises();
      expect(wrapper.findAll("main")).toHaveLength(1);
      expect(wrapper.find("form").exists()).toBe(false);
    } finally {
      wrapper.unmount();
    }
  });
});

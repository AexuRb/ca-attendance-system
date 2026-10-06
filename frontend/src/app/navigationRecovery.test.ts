// @vitest-environment jsdom
import { flushPromises, mount } from "@vue/test-utils";
import { defineComponent, h, ref } from "vue";
import { createMemoryHistory, createRouter, onBeforeRouteLeave } from "vue-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "../App.vue";
import { useSession } from "./session";
import { resolveRouteAccess } from "./router";
import { createNavigationRecovery, navigationRecoveryKey } from "./navigationRecovery";
import { useUnsavedChanges } from "../shared/composables/useUnsavedChanges";

vi.mock("../shared/ui/ToastHost.vue", () => ({ default: { template: "<aside />" } }));
const cleanups: (() => void)[] = [];
beforeEach(() => {
  const session = useSession();
  session.state.ready = true;
  session.state.setup.initialized = true;
  session.state.user = { id: 1, name: "合成管理员", studentNo: "synthetic", role: "ADMIN" };
});
afterEach(() => {
  cleanups.splice(0).forEach(cleanup => cleanup());
  useSession().expireSession();
  document.body.innerHTML = "";
});

function setup(loader = vi.fn().mockRejectedValue(new Error("synthetic private error"))) {
  const workspace = defineComponent({ setup() {
    const input = ref("合成未保存输入");
    return () => h("input", { value: input.value, onInput: (event: Event) => { input.value = (event.target as HTMLInputElement).value; } });
  } });
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: "/source", meta: { auth: true }, component: workspace },
    { path: "/target", meta: { auth: true, roles: ["ADMIN"] }, component: loader },
    { path: "/other", component: { template: "<main>其他页</main>" } },
    { path: "/login", name: "login", component: { template: "<form>登录</form>" } },
    { path: "/profile", name: "profile", component: { template: "<main>个人资料</main>" } },
  ] });
  router.beforeEach(to => resolveRouteAccess(to, useSession().state));
  const reloadDocument = vi.fn();
  const recovery = createNavigationRecovery(router, reloadDocument);
  cleanups.push(recovery.dispose);
  return { router, recovery, reloadDocument, loader };
}
async function mountSource(context: ReturnType<typeof setup>) {
  await context.router.push("/source");
  const wrapper = mount(App, { attachTo: document.body, global: {
    plugins: [context.router], provide: { [navigationRecoveryKey as symbol]: context.recovery },
  } });
  cleanups.push(() => wrapper.unmount());
  return wrapper;
}
const button = (name: string) => Array.from(document.querySelectorAll<HTMLButtonElement>('button')).find(item => item.textContent?.trim() === name)!;

describe("navigation failure recovery", () => {
  it("retains the workspace input and query, retries the exact intent, and succeeds without reloading", async () => {
    const context = setup();
    const wrapper = await mountSource(context);
    await expect(context.router.push("/target?from=2026-10-02&keyword=合成条件&page=2")).rejects.toThrow();
    await flushPromises();
    expect(wrapper.find('input').element.value).toBe("合成未保存输入");
    expect(context.router.currentRoute.value.path).toBe("/source");
    expect(document.body.textContent).toContain("页面未能打开");
    expect(document.body.textContent).not.toContain("synthetic private error");
    expect(document.body.textContent).not.toContain("合成条件");
    context.loader.mockResolvedValue({ template: "<main>目标页</main>" });
    button("重试打开").click();
    await flushPromises();
    expect(context.router.currentRoute.value.query).toEqual({ from: "2026-10-02", keyword: "合成条件", page: "2" });
    expect(wrapper.text()).toContain("目标页");
    expect(context.recovery.failure.value).toBeNull();
    expect(context.reloadDocument).not.toHaveBeenCalled();
  });

  it("repeated failures have one dialog and reload requires confirmation, omitting private searches", async () => {
    const context = setup();
    await mountSource(context);
    await context.router.push("/target?keyword=secret&participantKeyword=private&intent=new&from=2026-10-02&page=2").catch(() => {});
    await flushPromises();
    button("重试打开").click();
    await flushPromises();
    expect(document.querySelectorAll('[role=dialog]')).toHaveLength(1);
    expect(context.reloadDocument).not.toHaveBeenCalled();
    button("重新加载应用").click();
    await flushPromises();
    expect(document.body.textContent).toContain("未保存的输入和内存中的搜索条件");
    expect(document.activeElement).toBe(button("取消"));
    button("取消").click();
    await flushPromises();
    expect(context.reloadDocument).not.toHaveBeenCalled();
    expect(document.activeElement).toBe(button("重试打开"));
    button("重新加载应用").click();
    await flushPromises();
    button("确认重新加载").click();
    expect(context.reloadDocument).toHaveBeenCalledExactlyOnceWith("/target?from=2026-10-02&page=2");
  });

  it("offers recovery when the first route fails before any workspace mounts", async () => {
    const context = setup();
    const wrapper = mount(App, { attachTo: document.body, global: { plugins: [context.router], provide: { [navigationRecoveryKey as symbol]: context.recovery } } });
    cleanups.push(() => wrapper.unmount());
    await context.router.push("/target").catch(() => {});
    await flushPromises();
    expect(document.body.textContent).toContain("页面未能打开");
    expect(document.body.textContent).toContain("页面资源暂时无法加载");
    expect(button("重新加载应用")).toBeDefined();
  });

  it("dismisses old intent on expiry and a later navigation still runs permissions", async () => {
    const context = setup();
    await mountSource(context);
    await context.router.push("/target").catch(() => {});
    useSession().expireSession();
    expect(context.recovery.failure.value).toBeNull();
    await context.recovery.retry();
    context.recovery.reload();
    expect(context.reloadDocument).not.toHaveBeenCalled();
    expect(context.loader).toHaveBeenCalledTimes(1);
    useSession().state.user = { id: 2, name: "合成成员", studentNo: "member", role: "MEMBER" };
    await context.router.push("/target");
    expect(context.router.currentRoute.value.name).toBe("profile");
    expect(context.loader).toHaveBeenCalledTimes(1);
  });

  it("ignores a late failure after a newer page succeeds or the session ends", async () => {
    let reject!: (reason: Error) => void;
    const loader = vi.fn(() => new Promise((_resolve, rejectPromise) => { reject = rejectPromise; }));
    const context = setup(loader);
    await context.router.push("/source");
    const pending = context.router.push("/target").catch(() => {});
    await flushPromises();
    await context.router.push("/other");
    reject(new Error("late private error"));
    await pending;
    expect(context.recovery.failure.value).toBeNull();
    expect(context.router.currentRoute.value.path).toBe("/other");
  });

  it("closes recovery before retry so an unsaved leave cancellation is never hidden", async () => {
    const context = setup();
    let guard!: ReturnType<typeof useUnsavedChanges>;
    let dirty = false;
    context.router.addRoute({ path: "/dirty", meta: { auth: true }, component: defineComponent({ setup() {
      guard = useUnsavedChanges(() => dirty);
      onBeforeRouteLeave(() => new Promise<boolean>(resolve => guard.request(() => resolve(true), () => resolve(false))));
      return () => h("main", "合成设置");
    } }) });
    await context.router.push("/dirty");
    const wrapper = mount(App, { attachTo: document.body, global: { plugins: [context.router], provide: { [navigationRecoveryKey as symbol]: context.recovery } } });
    cleanups.push(() => wrapper.unmount());
    await context.router.push("/target").catch(() => {});
    dirty = true;
    const retry = context.recovery.retry();
    await flushPromises();
    expect(context.recovery.failure.value).toBeNull();
    expect(guard.confirmOpen.value).toBe(true);
    guard.cancel();
    await retry;
    expect(context.router.currentRoute.value.path).toBe("/dirty");
    expect(context.recovery.pending.value).toBe(false);
  });

  it("deduplicates retry clicks and ignores a pending failure after expiry", async () => {
    const context = setup();
    await mountSource(context);
    await context.router.push("/target").catch(() => {});
    let reject!: (error: Error) => void;
    context.loader.mockImplementation(() => new Promise((_resolve, rejectPromise) => { reject = rejectPromise; }));
    const firstRetry = context.recovery.retry();
    await context.recovery.retry();
    await flushPromises();
    expect(context.loader).toHaveBeenCalledTimes(2);
    useSession().expireSession();
    reject(new Error("old session error"));
    await firstRetry;
    expect(context.recovery.failure.value).toBeNull();
    expect(context.recovery.pending.value).toBe(false);
  });
});

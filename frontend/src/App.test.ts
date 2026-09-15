// @vitest-environment jsdom
import { mount, flushPromises } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { describe, expect, it, vi } from "vitest";
import App from "./App.vue";

vi.mock("./shared/ui/ToastHost.vue", () => ({ default: { template: "<aside />" } }));

describe("public and authenticated route boundary", () => {
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

// @vitest-environment jsdom
import { afterEach, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
vi.mock("./session", () => ({ useSession: () => ({
  bootstrap: async () => {},
  state: { ready: true, setup: { initialized: true }, access: { mode: "LOCAL", kioskAvailable: true }, user: null },
}) }));
vi.mock("../shared/composables/useToast", () => ({ notify: vi.fn() }));
import { router } from "./router";
import { notify } from "../shared/composables/useToast";
import { createNavigationRecovery, navigationRecoveryKey } from "./navigationRecovery";
import NavigationRecoveryDialog from "../shared/ui/NavigationRecoveryDialog.vue";
const remove: (() => void)[] = [];
afterEach(() => { remove.splice(0).reverse().forEach(fn => fn()); document.body.innerHTML = ""; vi.clearAllMocks(); });
it("reports failed loading without changing the current route or exposing diagnostics", async () => {
  const recovery = createNavigationRecovery(router);
  remove.push(() => recovery.dispose());
  const wrapper = mount(NavigationRecoveryDialog, { attachTo: document.body, global: {
    provide: { [navigationRecoveryKey as symbol]: recovery },
  } });
  remove.push(() => wrapper.unmount());
  remove.push(router.addRoute({ path: "/test-stable", component: { template: "<main />" } }));
  remove.push(router.addRoute({ path: "/test-failed", component: () => Promise.reject(new Error("private diagnostic details")) }));
  await router.push("/test-stable");
  await expect(router.push("/test-failed")).rejects.toThrow("private diagnostic details");
  await flushPromises();
  expect(router.currentRoute.value.path).toBe("/test-stable");
  expect(document.body.textContent).toContain("页面未能打开");
  expect(document.body.textContent).toContain("当前页面与输入已保留。");
  expect(document.body.textContent).not.toContain("private diagnostic details");
  expect(document.querySelectorAll('[role="dialog"]')).toHaveLength(1);
  expect(notify).not.toHaveBeenCalled();
});

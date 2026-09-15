// @vitest-environment jsdom
import { afterEach, expect, it, vi } from "vitest";
vi.mock("./session", () => ({ useSession: () => ({
  bootstrap: async () => {},
  state: { setup: { initialized: true }, access: { mode: "LOCAL", kioskAvailable: true }, user: null },
}) }));
vi.mock("../shared/composables/useToast", () => ({ notify: vi.fn() }));
import { router } from "./router";
import { notify } from "../shared/composables/useToast";
const remove: (() => void)[] = [];
afterEach(() => { remove.splice(0).forEach(fn => fn()); vi.clearAllMocks(); });
it("reports failed loading without changing the current route or exposing diagnostics", async () => {
  remove.push(router.addRoute({ path: "/test-stable", component: { template: "<main />" } }));
  remove.push(router.addRoute({ path: "/test-failed", component: () => Promise.reject(new Error("private diagnostic details")) }));
  await router.push("/test-stable");
  await expect(router.push("/test-failed")).rejects.toThrow("private diagnostic details");
  expect(router.currentRoute.value.path).toBe("/test-stable");
  expect(notify).toHaveBeenCalledWith("页面未能打开，请重新选择页面；若仍失败，请重新加载应用。", "danger");
  expect(notify).toHaveBeenCalledTimes(1);
});

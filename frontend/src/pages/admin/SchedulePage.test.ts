// @vitest-environment jsdom
import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import SchedulePage from "./SchedulePage.vue";

const apiGet = vi.fn();

vi.mock("vue-router", () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}));

vi.mock("../../shared/api", () => ({
  get: (...args: unknown[]) => apiGet(...args),
  post: vi.fn(),
  put: vi.fn(),
  del: vi.fn(),
  downloadBlob: vi.fn(),
}));

afterEach(() => {
  apiGet.mockReset();
  document.body.innerHTML = "";
});

describe("SchedulePage request states", () => {
  it("shows a retryable load error instead of the configured-empty board", async () => {
    apiGet.mockRejectedValue(new Error("排班数据加载失败"));
    const wrapper = mount(SchedulePage, {
      global: { stubs: { Teleport: true } },
    });
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain("排班数据加载失败");
    expect(wrapper.text()).not.toContain("请先在系统设置中添加值班时间段");

    apiGet.mockResolvedValue([]);
    await wrapper.get('[data-action="retry-schedule"]').trigger("click");
    await flushPromises();
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    wrapper.unmount();
  });
});

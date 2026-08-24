// @vitest-environment jsdom
import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import TodayPage from "./TodayPage.vue";

const apiGet = vi.fn();

vi.mock("../../shared/api", () => ({
  get: (...args: unknown[]) => apiGet(...args),
}));
vi.mock("../../app/session", () => ({
  useSession: () => ({ user: { value: { role: "ADMIN" } } }),
}));

afterEach(() => {
  apiGet.mockReset();
  vi.useRealTimers();
});

describe("TodayPage request states", () => {
  it("shows a retryable error instead of presenting failed data as empty", async () => {
    apiGet.mockRejectedValueOnce(new Error("今日数据加载失败"));
    apiGet.mockResolvedValueOnce({ slots: [] });
    apiGet.mockResolvedValueOnce([]);
    const wrapper = mount(TodayPage, {
      global: {
        stubs: {
          RouterLink: { template: "<a><slot /></a>" },
        },
      },
    });
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain("今日数据加载失败");
    expect(wrapper.text()).not.toContain("今日暂无排班");

    apiGet.mockImplementation((url: string) => {
      if (url.startsWith("/api/stats/dashboard")) {
        return Promise.resolve({ todayRecordCount: 0, todayValidHours: 0 });
      }
      if (url === "/api/public/schedules/today") return Promise.resolve({ slots: [] });
      return Promise.resolve([]);
    });
    await wrapper.get('[data-action="retry-today"]').trigger("click");
    await flushPromises();

    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    wrapper.unmount();
  });
});

// @vitest-environment jsdom
import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import StatsPage from "./StatsPage.vue";

const apiGet = vi.fn();

vi.mock("../../shared/api", () => ({
  get: (...args: unknown[]) => apiGet(...args),
  downloadBlob: vi.fn(),
}));

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((resolvePromise) => (resolve = resolvePromise));
  return { promise, resolve };
}

const row = (name: string) => ({
  userId: name,
  studentNo: name,
  name,
  grade: "2026级",
  role: "MEMBER",
  attendanceHours: 1,
  trainingHours: 0,
  totalHours: 1,
  attendanceCount: 1,
  trainingCount: 0,
});

afterEach(() => apiGet.mockReset());

describe("StatsPage request states", () => {
  it("keeps the latest preset result when an older response arrives late", async () => {
    const annual = deferred<ReturnType<typeof row>[]>();
    const weeklySummary = deferred<ReturnType<typeof row>[]>();
    const weeklyDetail = deferred<{
      days: never[];
      users: ReturnType<typeof row>[];
      cells: Record<string, never>;
    }>();
    apiGet.mockImplementation((url: string) => {
      if (url.includes("weekly-detail")) return weeklyDetail.promise;
      const summaryCalls = apiGet.mock.calls.filter(([path]) => String(path).includes("/summary?"));
      return summaryCalls.length === 1 ? annual.promise : weeklySummary.promise;
    });
    const wrapper = mount(StatsPage);
    await flushPromises();

    await wrapper.get(".segmented button").trigger("click");
    weeklySummary.resolve([row("新筛选成员")]);
    weeklyDetail.resolve({ days: [], users: [row("新筛选成员")], cells: {} });
    await flushPromises();
    annual.resolve([row("旧筛选成员")]);
    await flushPromises();

    expect(wrapper.text()).not.toContain("旧筛选成员");
    expect(wrapper.text()).toContain("新筛选成员");
    wrapper.unmount();
  });

  it("rejects an inverted custom date range before requesting data", async () => {
    apiGet.mockResolvedValue([]);
    const wrapper = mount(StatsPage);
    await flushPromises();
    apiGet.mockClear();

    const dates = wrapper.findAll('input[type="date"]');
    await dates[0].setValue("2026-08-22");
    await dates[1].setValue("2026-08-21");
    await wrapper.get("form").trigger("submit");

    expect(wrapper.get('[role="alert"]').text()).toContain(
      "开始日期不能晚于结束日期",
    );
    expect(apiGet).not.toHaveBeenCalled();
    wrapper.unmount();
  });
});

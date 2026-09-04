// @vitest-environment jsdom
import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import StatsPage from "./StatsPage.vue";

const apiGet = vi.fn();

vi.mock("vue-router", () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}));

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

afterEach(() => {
  apiGet.mockReset();
  vi.useRealTimers();
});

describe("StatsPage request states", () => {
  it("loads the current week by default", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 7, 26, 12));
    apiGet.mockImplementation((url: string) =>
      Promise.resolve(
        url.includes("weekly-detail")
          ? { days: [], users: [], cells: {} }
          : [],
      ),
    );

    const wrapper = mount(StatsPage);
    await flushPromises();

    const presets = wrapper.findAll(".segmented button");
    expect(presets[0].classes()).toContain("active");
    expect(presets[2].classes()).not.toContain("active");
    expect(apiGet).toHaveBeenCalledWith(
      "/api/stats/summary?from=2026-08-24&to=2026-08-26",
      expect.any(Object),
    );
    expect(apiGet).toHaveBeenCalledWith(
      "/api/stats/weekly-detail?from=2026-08-24&to=2026-08-26",
      expect.any(Object),
    );
    wrapper.unmount();
  });

  it("keeps the latest preset result when an older response arrives late", async () => {
    const initialSummary = deferred<ReturnType<typeof row>[]>();
    const initialDetail = deferred<{
      days: never[];
      users: ReturnType<typeof row>[];
      cells: Record<string, never>;
    }>();
    const monthlySummary = deferred<ReturnType<typeof row>[]>();
    apiGet.mockImplementation((url: string) => {
      if (url.includes("weekly-detail")) return initialDetail.promise;
      const summaryCalls = apiGet.mock.calls.filter(([path]) => String(path).includes("/summary?"));
      return summaryCalls.length === 1 ? initialSummary.promise : monthlySummary.promise;
    });
    const wrapper = mount(StatsPage);
    await flushPromises();

    await wrapper.findAll(".segmented button")[1].trigger("click");
    monthlySummary.resolve([row("新筛选成员")]);
    await flushPromises();
    initialSummary.resolve([row("旧筛选成员")]);
    initialDetail.resolve({ days: [], users: [row("旧筛选成员")], cells: {} });
    await flushPromises();

    expect(wrapper.text()).not.toContain("旧筛选成员");
    expect(wrapper.text()).toContain("新筛选成员");
    wrapper.unmount();
  });

  it("rejects an inverted custom date range before requesting data", async () => {
    apiGet.mockImplementation((url: string) =>
      Promise.resolve(
        url.includes("weekly-detail")
          ? { days: [], users: [], cells: {} }
          : [],
      ),
    );
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

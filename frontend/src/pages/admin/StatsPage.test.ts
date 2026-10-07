// @vitest-environment jsdom
import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import StatsPage from "./StatsPage.vue";

const apiGet = vi.fn();
const navigation = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }));

vi.mock("vue-router", () => ({
  RouterLink: { template: "<a><slot /></a>" },
  useRoute: () => ({ query: {} }),
  useRouter: () => navigation,
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
  navigation.push.mockReset();
  navigation.replace.mockReset();
  vi.useRealTimers();
});

describe("StatsPage request states", () => {
  it("filters and sorts loaded results without changing the full-range metrics or fetching", async () => {
    const members = [{ ...row('甲'), userId: 1, studentNo: '9900000001', trainingHours: 5, attendanceHours: 1, totalHours: 6 },
      { ...row('乙'), userId: 2, studentNo: '9900000002', trainingHours: 1, attendanceHours: 9, totalHours: 10 }];
    apiGet.mockImplementation((url: string) => Promise.resolve(url.includes('weekly-detail') ? { days: [], users: members, cells: {} } : members));
    const wrapper = mount(StatsPage);
    await flushPromises();
    await wrapper.findAll('.segmented button')[1].trigger('click');
    await flushPromises();
    const calls = apiGet.mock.calls.length;
    await wrapper.get('.stats-result-tools select').setValue('training');
    expect(wrapper.findAll('.stats-ranking-table tbody tr')[0].text()).toContain('甲');
    await wrapper.get('.stats-result-tools input').setValue('000002');
    expect(wrapper.findAll('.stats-ranking-table tbody tr')).toHaveLength(1);
    expect(wrapper.get('.stats-result-tools').text()).toContain('全范围 2 人 · 筛出 1 人');
    expect(wrapper.get('.workspace-metric-hours').text()).toContain('16');
    expect(apiGet.mock.calls.length).toBe(calls);
    wrapper.unmount();
  });

  it("retains successful statistics on failure and retries the captured range without committing draft dates", async () => {
    apiGet.mockImplementation((url: string) => Promise.resolve(url.includes('weekly-detail')
      ? { days: [], users: [row('原结果')], cells: {} } : [row('原结果')]));
    const wrapper = mount(StatsPage);
    await flushPromises();
    const before = wrapper.get('.stats-results-context').text();
    apiGet.mockRejectedValueOnce(new Error('本次查询失败'));
    await wrapper.get('input[name="statsFrom"]').setValue('2026-01-01');
    await wrapper.get('input[name="statsTo"]').setValue('2026-02-01');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(navigation.push).not.toHaveBeenCalled();
    expect(wrapper.get('.stats-results-context').text()).toContain(before);
    expect(wrapper.get('.stats-results-context').text()).toContain('统计未成功');
    expect(wrapper.get('.stats-results-context').text()).not.toContain('日期已修改');
    expect(wrapper.text()).toContain('原结果');
    expect(wrapper.find('.weekly-stats-table').exists()).toBe(true);
    expect(wrapper.get('.stats-detail-trigger').attributes('disabled')).toBeDefined();
    await wrapper.get('input[name="statsFrom"]').setValue('2026-03-01');
    await wrapper.get('[data-action="retry-stats"]').trigger('click');
    await flushPromises();
    expect(apiGet.mock.calls.at(-1)?.[0]).toBe('/api/stats/summary?from=2026-01-01&to=2026-02-01');
    expect(navigation.push).toHaveBeenLastCalledWith({ query: { from: '2026-01-01', to: '2026-02-01', preset: 'custom' } });
    expect(wrapper.get('.stats-results-context').text()).toContain('2026-01-01 — 2026-02-01');
    expect(wrapper.find('.stats-ranking-table').exists()).toBe(true);
    wrapper.unmount();
  });

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

  it("shows a retry state instead of zero metrics or an empty table when loading fails", async () => {
    apiGet.mockRejectedValue(new Error("统计数据加载失败"));
    const wrapper = mount(StatsPage);
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain("统计数据加载失败");
    expect(wrapper.get(".stats-metrics").text()).toContain("—");
    expect(wrapper.get(".stats-results").text()).toContain("统计结果暂不可用");
    expect(wrapper.find(".weekly-stats-table").exists()).toBe(false);
    expect(wrapper.get('button[title="请先完成统计，再导出当前日期范围"]').attributes("disabled")).toBeDefined();
    wrapper.unmount();
  });

  it("requires applying changed dates before exporting", async () => {
    apiGet.mockImplementation((url: string) => Promise.resolve(
      url.includes("weekly-detail")
        ? { days: [], users: [row("示例成员")], cells: {} }
        : [row("示例成员")],
    ));
    const wrapper = mount(StatsPage);
    await flushPromises();
    const exportButton = wrapper.get('button[title="导出当前统计结果"]');
    expect(exportButton.attributes("disabled")).toBeUndefined();

    await wrapper.get('input[name="statsFrom"]').setValue("2026-01-01");
    expect(wrapper.get('button[title="请先完成统计，再导出当前日期范围"]').attributes("disabled")).toBeDefined();
    expect(wrapper.get(".stats-results-context").text()).toContain("日期已修改，点击“统计”应用");
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    expect(wrapper.get('button[title="导出当前统计结果"]').attributes("disabled")).toBeUndefined();
    expect(wrapper.get(".stats-results-context").text()).toContain("2026-01-01");
    expect(wrapper.get(".stats-results-context").text()).not.toContain("日期已修改");
    wrapper.unmount();
  });

  it("opens a member detail from the monthly ranking and loads the applied range", async () => {
    apiGet.mockImplementation((url: string) => Promise.resolve(
      url.includes("detail")
        ? { training: [], trainingVisible: true, days: [{ dutyDate: "2026-08-25", weekday: 2, weekdayName: "周二" }, { dutyDate: "2026-09-01", weekday: 2, weekdayName: "周二" }], users: [], cells: { "2026-08-25": { "1": 1 }, "2026-09-01": { "1": 2 } } }
        : [{ ...row("示例成员"), userId: 1, dutyCount: 1 }],
    ));
    const wrapper = mount(StatsPage);
    await flushPromises();
    await wrapper.findAll(".segmented button")[1].trigger("click");
    await flushPromises();
    await wrapper.get(".stats-ranking-table .stats-detail-trigger").trigger("click");
    await flushPromises();

    expect(document.body.textContent).toContain("示例成员的统计详情");
    expect(document.body.textContent).toContain("2026-08-25");
    expect(document.body.textContent).toContain("2026年8月");
    expect(document.body.textContent).toContain("2026年9月");
    expect(apiGet.mock.calls.some(([url]) => String(url).includes("/member-detail?from="))).toBe(true);
    wrapper.unmount();
  });
});

vi.mock("../../shared/composables/useServiceHealth", () => ({ useServiceHealth: () => ({ online: true }) }));

// @vitest-environment jsdom
import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import TodayPage from "./TodayPage.vue";

const apiGet = vi.hoisted(() => vi.fn());
const session = vi.hoisted(() => ({ role: "ADMIN", name: "管理员示例", mode: "LOCAL" }));
vi.mock("../../shared/api", () => ({ get: (...args: unknown[]) => apiGet(...args) }));
vi.mock("vue-router", () => ({
  useRouter: () => ({}),
  RouterLink: { props: ["to"], template: "<a :data-route='to.name'><slot /></a>" },
}));
vi.mock("../../app/session", () => ({
  useSession: () => ({
    user: { value: { role: session.role, name: session.name } },
    state: { access: { mode: session.mode, kioskAvailable: session.mode === "LOCAL" } },
  }),
}));

function mockToday(pending: number, repairs: number, someonePresent = true) {
  apiGet.mockImplementation(async (url: string) => {
    if (url.startsWith("/api/stats/dashboard")) return { todayPendingCount: pending, ongoingRepairCount: repairs };
    if (url === "/api/skill-assessments/overview") return { date: "2026-11-21", items: someonePresent ? [
      { id: 1, name: "示例成员 A", disassemblyAssessment: "PASSED", osReinstallAssessment: "NOT_PASSED" },
    ] : [] };
    return {};
  });
}

afterEach(() => {
  apiGet.mockReset();
  session.role = "ADMIN";
  session.mode = "LOCAL";
  vi.useRealTimers();
});

describe("TodayPage display", () => {
  it.each([[0, 0], [3, 4]])("renders greeting and actual task counts (%s pending, %s repairs)", async (pending, repairs) => {
    mockToday(pending, repairs);
    const wrapper = mount(TodayPage);
    await flushPromises();
    expect(wrapper.get(".today-entry-message").text()).toBe("欢迎回来，管理员示例。");
    expect(wrapper.get('[data-task="reviews"] .today-task-count').text()).toBe(String(pending));
    expect(wrapper.get('[data-task="repairs"] .today-task-count').text()).toBe(String(repairs));
    const tasks = wrapper.get(".today-task-list");
    expect(tasks.findAll(".today-task-label").map(label => label.text())).toEqual(["待审核", "进行中维修"]);
    expect(tasks.get('[data-task="reviews"] .today-task-count').classes(pending ? "needs-review" : "is-zero")).toBe(true);
    expect(apiGet).not.toHaveBeenCalledWith("/api/public/schedules/today", expect.anything());
    expect(wrapper.get(".today-roster-table tbody tr").text()).toMatch(/示例成员 A\s*通过\s*未通过/);
    expect(wrapper.get(".today-shortcuts").findAll("a").map(link => link.text())).toEqual([
      "签到审核", "今日值班记录", "本周统计", "新建维修", "备份与恢复",
    ]);
    expect(wrapper.find(".overview-workspace :is(button,input,textarea,[role=combobox],[role=log])").exists()).toBe(false);
    wrapper.unmount();
  });

  it("shows only the repair metric for remote ministers", async () => {
    session.role = "MINISTER";
    session.mode = "REMOTE_ADMIN";
    mockToday(3, 4);
    const wrapper = mount(TodayPage);
    await flushPromises();
    expect(wrapper.find('[data-task="reviews"]').exists()).toBe(false);
    expect(wrapper.get(".today-task-list").findAll("li")).toHaveLength(1);
    expect(wrapper.find(".today-roster").exists()).toBe(false);
    expect(wrapper.get(".today-shortcuts").findAll("a").map(link => link.text())).toEqual(["今日值班记录", "本周统计"]);
    expect(apiGet).not.toHaveBeenCalledWith("/api/skill-assessments/overview", expect.anything());
    expect(wrapper.get('[data-task="repairs"] .today-task-count').text()).toBe("4");
    wrapper.unmount();
  });

  it("updates the present roster after the next 30-second refresh", async () => {
    vi.useFakeTimers({ toFake: ["setInterval", "clearInterval"] });
    mockToday(0, 0);
    const wrapper = mount(TodayPage);
    try {
      await flushPromises();
      expect(wrapper.get(".today-roster-heading").text()).toMatch(/当前在场\s*1 人/);
      mockToday(0, 0, false);
      await vi.advanceTimersByTimeAsync(30_000);
      await flushPromises();
      expect(wrapper.get(".today-roster-heading").text()).toMatch(/当前在场\s*0 人/);
      expect(wrapper.get(".today-roster-state").text()).toBe("当前暂无在场人员");
    } finally {
      wrapper.unmount();
    }
  });

  it("loads the new date after focus and keeps failed totals out of the display until retry succeeds", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 8, 29, 23, 59));
    mockToday(2, 3);
    const wrapper = mount(TodayPage);
    await flushPromises();
    expect(wrapper.get('[data-task="reviews"] .today-task-count').text()).toBe("2");
    vi.setSystemTime(new Date(2026, 8, 30, 0, 1));
    apiGet.mockImplementation(async (url: string) => {
      if (url.startsWith("/api/stats/dashboard")) throw new Error("演示刷新失败");
      return { items: [] };
    });
    window.dispatchEvent(new Event("focus"));
    await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain("暂时没能读取");
    expect(wrapper.find(".today-task-count").exists()).toBe(false);
    expect(apiGet).toHaveBeenCalledWith("/api/stats/dashboard?date=2026-09-30", expect.anything());
    mockToday(0, 4);
    await wrapper.get('[data-action="retry-today"]').trigger("click");
    await flushPromises();
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect(wrapper.get('[data-task="reviews"] .today-task-count').text()).toBe("0");
    expect(wrapper.get('[data-task="repairs"] .today-task-count').text()).toBe("4");
    wrapper.unmount();
  });
});

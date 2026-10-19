// @vitest-environment jsdom
import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import TodayPage from "./TodayPage.vue";

const mocks = vi.hoisted(() => ({
  apiGet: vi.fn(),
  routerPush: vi.fn(),
  notify: vi.fn(),
}));
const sessionMock = vi.hoisted(() => ({ role: "ADMIN", name: "林知远" }));

vi.mock("../../shared/api", () => ({
  get: (...args: unknown[]) => mocks.apiGet(...args),
}));
vi.mock("vue-router", () => ({
  useRouter: () => ({ push: mocks.routerPush }),
  RouterLink: { template: "<a><slot /></a>" },
}));
vi.mock("../../shared/composables/useToast", () => ({ notify: mocks.notify }));
vi.mock("../../app/session", () => ({
  useSession: () => ({ user: { value: { role: sessionMock.role, name: sessionMock.name } }, state: { access: { kioskAvailable: true } }, logout: vi.fn() }),
}));

afterEach(() => {
  Object.values(mocks).forEach((mock) => mock.mockReset());
  sessionMock.role = "ADMIN";
  sessionMock.name = "林知远";
  vi.useRealTimers();
});

describe("TodayPage request states", () => {
  it("shows a plain greeting until the first message is sent and restores it on re-entry", async () => {
    sessionMock.name = "示例成员甲";
    mocks.apiGet.mockImplementation((url: string) => Promise.resolve(url.includes("schedules") ? { slots: [] } : { todayPendingCount: 0, ongoingRepairCount: 0 }));
    const wrapper = mount(TodayPage);
    await flushPromises();
    expect(wrapper.get(".today-entry-message").element.tagName).toBe("P");
    expect(wrapper.get(".today-entry-message").classes()).not.toContain("today-system-message");
    expect(wrapper.find('[role="log"]').exists()).toBe(false);

    const input = wrapper.get('textarea[aria-label="查找后台功能或输入命令"]');
    await input.setValue("/ 打开 成员名册");
    expect(wrapper.find(".today-entry-message").exists()).toBe(true);
    await input.trigger("keydown", { key: "Enter" });
    await flushPromises();
    expect(wrapper.find(".today-entry-message").exists()).toBe(false);
    expect(wrapper.get('[role="log"]').findAll(".today-conversation-turn")).toHaveLength(1);
    expect(mocks.routerPush).not.toHaveBeenCalled();
    wrapper.unmount();

    const reopened = mount(TodayPage);
    await flushPromises();
    expect(reopened.get(".today-entry-message").text()).toBe("欢迎回来，示例成员甲。");
    reopened.unmount();
  });

  it("keeps sidebar shortcuts on the guarded conversation path", async () => {
    mocks.apiGet.mockImplementation((url: string) => Promise.resolve(url.includes('schedules') ? { slots: [] } : {todayPendingCount:0,ongoingRepairCount:0}));
    const wrapper = mount(TodayPage);
    await flushPromises();
    const button = wrapper.findAll('.today-context-rail .today-command-shortcuts button').find(item => item.text().includes('新建维修'))!;
    await button.trigger('click');
    await button.trigger('click');
    await flushPromises();
    expect(wrapper.findAll('.today-conversation-turn')).toHaveLength(1);
    expect(wrapper.find('.today-entry-message').exists()).toBe(false);
    expect(wrapper.get('.today-user-message').text()).toBe('新建维修');
    expect(mocks.routerPush).not.toHaveBeenCalled();
    expect(mocks.apiGet.mock.calls.every(([url]) => /stats\/dashboard|schedules\/today/.test(url))).toBe(true);
    wrapper.unmount();
  });

  it("keeps the greeting and a role-allowed schedule reminder separate", async () => {
    mocks.apiGet.mockImplementation((url: string) => Promise.resolve(url.includes('schedules') ? {slots:[{key:'a',title:'14:00',assignees:[]}]} : {todayPendingCount:0,ongoingRepairCount:0}));
    mocks.routerPush.mockResolvedValue(undefined);
    const wrapper = mount(TodayPage);
    await flushPromises();
    expect(wrapper.get('.today-entry-message').text()).toBe('欢迎回来，林知远。');
    expect(wrapper.get('.today-schedule-link').text()).toContain('1 个空缺时段');
    await wrapper.get('.today-schedule-link').trigger('click');
    await flushPromises();
    expect(mocks.routerPush).toHaveBeenCalledWith({name:'schedules'});
    wrapper.unmount();
  });

  it.each(["ADMIN", "PRESIDENT", "MINISTER"])("shows the backup shortcut only to leaders and navigates after its reply (%s)", async role => {
    sessionMock.role = role;
    mocks.apiGet.mockImplementation((url: string) => Promise.resolve(url.includes("schedules") ? { slots: [] } : { todayPendingCount: 0, ongoingRepairCount: 0 }));
    mocks.routerPush.mockResolvedValue(undefined);
    const wrapper = mount(TodayPage);
    await flushPromises();
    const shortcuts = wrapper.findAll(".today-context-rail .today-command-shortcuts button");
    expect(shortcuts.some(button => button.text() === "排班管理")).toBe(false);
    const backup = shortcuts.find(button => button.text() === "备份与恢复");
    expect(Boolean(backup)).toBe(role !== "MINISTER");
    if (backup) {
      await backup.trigger("click");
      await flushPromises();
      expect(wrapper.get(".today-user-message").text()).toBe("备份与恢复");
      expect(mocks.routerPush).not.toHaveBeenCalled();
      expect(mocks.apiGet.mock.calls.every(([url]) => /stats\/dashboard|schedules\/today/.test(url))).toBe(true);
      await wrapper.get(".today-reply-action").trigger("click");
      await flushPromises();
      expect(mocks.routerPush).toHaveBeenCalledWith({ name: "data", query: { tab: "backups" } });
    }
    wrapper.unmount();
  });
  it.each([
    [0, 4], [3, 0], [0, 0], [3, 4],
  ])("keeps welcome separate from actionable task entries even at zero (%s pending, %s repairs)", async (pending, repairs) => {
    mocks.apiGet.mockImplementation((url: string) => Promise.resolve(
      url.includes("schedules") ? { slots: [] } : { todayPendingCount: pending, ongoingRepairCount: repairs },
    ));
    const wrapper = mount(TodayPage);
    await flushPromises();
    expect(wrapper.get(".today-entry-message").text()).toBe("欢迎回来，林知远。");
    expect(wrapper.find(".today-entry-message button").exists()).toBe(false);
    expect(wrapper.find(".command-composer-zone .today-command-shortcuts").exists()).toBe(false);
    const pendingEntry = wrapper.get('[data-task="reviews"]');
    const repairEntry = wrapper.get('[data-task="repairs"]');
    expect(pendingEntry.get('.today-task-count').text()).toBe(String(pending));
    expect(repairEntry.get('.today-task-count').text()).toBe(String(repairs));
    expect(pendingEntry.attributes('disabled')).toBeUndefined();
    expect(repairEntry.attributes('disabled')).toBeUndefined();
    if (!pending) expect(pendingEntry.text()).toContain('暂无待审核');
    if (!repairs) expect(repairEntry.text()).toContain('暂无进行中维修');
    mocks.routerPush.mockResolvedValue(undefined);
    await pendingEntry.trigger('click');
    await flushPromises();
    const date = new Date();
    const today = [date.getFullYear(), String(date.getMonth()+1).padStart(2,'0'), String(date.getDate()).padStart(2,'0')].join('-');
    expect(mocks.routerPush).toHaveBeenCalledWith({name:'attendance',query:{status:'PENDING',from:today,to:today}});
    await repairEntry.trigger('click');
    await flushPromises();
    expect(mocks.routerPush).toHaveBeenCalledWith({name:'repairs',query:{status:'REPAIRING'}});
    expect(wrapper.findAll(".today-context-rail .today-command-shortcuts button")).toHaveLength(5);
    wrapper.unmount();
  });

  it("hides stale totals after a failed refresh and preserves an explicit query date", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 8, 29, 23, 59));
    mocks.apiGet.mockImplementation((url: string) => Promise.resolve(
      url.includes("schedules") ? { slots: [] } : { todayPendingCount: 2, ongoingRepairCount: 0, total: 2 },
    ));
    mocks.routerPush.mockResolvedValue(undefined);
    const wrapper = mount(TodayPage);
    await flushPromises();
    expect(wrapper.get('[data-task="reviews"] .today-task-count').text()).toBe("2");

    vi.setSystemTime(new Date(2026, 8, 30, 0, 1));
    mocks.apiGet.mockRejectedValueOnce(new Error("演示刷新失败"));
    window.dispatchEvent(new Event("focus"));
    await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain("暂时没能读取");
    expect(wrapper.find(".today-task-count").exists()).toBe(false);
    expect(wrapper.get('[data-task="reviews"]').attributes("disabled")).toBeDefined();
    expect(wrapper.get(".today-entry-message").text()).toBe("欢迎回来，林知远。");
    // Failed refresh hides stale counts; a command using the prior explicit date stays explicit.
    const input = wrapper.get('textarea[aria-label="查找后台功能或输入命令"]');
    await input.setValue("/ 查看 值班记录 待审核 2026-09-29");
    await input.trigger("keydown", { key: "Enter" });
    await flushPromises();
    expect(mocks.routerPush).not.toHaveBeenCalled();
    await wrapper.findAll(".today-reply-action").at(-1)!.trigger("click");
    await flushPromises();
    expect(mocks.routerPush).toHaveBeenCalledWith({
      name: "attendance", query: { status: "PENDING", from: "2026-09-29", to: "2026-09-29" },
    });
    wrapper.unmount();
  });

  it("shows a retryable error instead of presenting failed data as empty", async () => {
    mocks.apiGet.mockRejectedValueOnce(new Error("今日数据加载失败"));
    mocks.apiGet.mockResolvedValueOnce({ slots: [] });
    const wrapper = mount(TodayPage, {
      global: {
        stubs: {
          RouterLink: { template: "<a><slot /></a>" },
        },
      },
    });
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain("暂时没能读取");
    expect(wrapper.text()).not.toContain("今日暂无排班");

    mocks.apiGet.mockImplementation((url: string) => {
      if (url.startsWith("/api/stats/dashboard")) {
        return Promise.resolve({ todayRecordCount: 0, todayValidHours: 0, todayPendingCount: 0, ongoingRepairCount: 0 });
      }
      if (url === "/api/public/schedules/today") return Promise.resolve({ slots: [] });
      return Promise.resolve([]);
    });
    await wrapper.get('[data-action="retry-today"]').trigger("click");
    await flushPromises();

    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it("executes a role-allowed command with its parsed date range", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 7, 28, 12));
    mocks.apiGet.mockImplementation((url: string) =>
      Promise.resolve(url.includes("schedules") ? { slots: [] } : url.includes("/summary") ? [] : { todayPendingCount: 0, ongoingRepairCount: 0, total: 0 }),
    );
    mocks.routerPush.mockResolvedValue(undefined);
    const wrapper = mount(TodayPage);
    await flushPromises();

    const input = wrapper.get('textarea[aria-label="查找后台功能或输入命令"]');
    await input.setValue("/ 查看 值班记录 本周");
    await input.trigger("keydown", { key: "Enter" });
    await flushPromises();

    expect(mocks.routerPush).not.toHaveBeenCalled();
    await wrapper.findAll(".today-reply-action").at(-1)!.trigger("click");
    await flushPromises();
    expect(mocks.routerPush).toHaveBeenCalledWith({
      name: "attendance",
      query: { from: "2026-08-24", to: "2026-08-30" },
    });
    expect(mocks.notify).toHaveBeenCalledWith("已打开本周值班记录", "success");
    wrapper.unmount();
  });

  it("completes a command-tree node without executing it", async () => {
    mocks.apiGet.mockImplementation((url: string) =>
      Promise.resolve(url.includes("schedules") ? { slots: [] } : url.includes("/summary") ? [] : { todayPendingCount: 0, ongoingRepairCount: 0, total: 0 }),
    );
    const wrapper = mount(TodayPage);
    await flushPromises();

    const input = wrapper.get('textarea[aria-label="查找后台功能或输入命令"]');
    await input.setValue("/ 查看 ");
    const option = wrapper.findAll(".command-suggestion-option")
      .find((item) => item.text().includes("值班记录"));
    expect(option).toBeDefined();
    await option!.trigger("click");
    await flushPromises();

    expect((input.element as HTMLTextAreaElement).value).toBe("/ 查看 值班记录 ");
    expect(mocks.routerPush).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it("switches to the compact work state and explains extensible commands", async () => {
    mocks.apiGet.mockImplementation((url: string) =>
      Promise.resolve(url.includes("schedules") ? { slots: [] } : url.includes("/summary") ? [] : { todayPendingCount: 0, ongoingRepairCount: 0, total: 0 }),
    );
    const wrapper = mount(TodayPage);
    await flushPromises();
    const input = wrapper.get('textarea[aria-label="查找后台功能或输入命令"]');

    await input.setValue("/ 查看 维修事务 已完成 ");
    await flushPromises();

    expect(wrapper.get(".command-workspace").classes()).toContain("is-engaged");
    expect(wrapper.find(".daily-command").exists()).toBe(true);
    expect(wrapper.find(".command-welcome h1").exists()).toBe(false);
    expect(wrapper.get(".command-composer-mode").text())
      .toBe("可以执行，也可继续补充范围");
    expect(wrapper.findAll(".command-suggestion-option")).toHaveLength(7);
    expect(wrapper.text()).toContain("查看已完成的本月维修");

    await input.setValue("");
    await flushPromises();
    expect(wrapper.get(".command-workspace").classes()).not.toContain("is-engaged");
    expect(wrapper.get(".overview-page-heading h1").text()).toBe("今日概览");
    wrapper.unmount();
  });

  it("executes an extensible command without forcing an optional range", async () => {
    mocks.apiGet.mockImplementation((url: string) =>
      Promise.resolve(url.includes("schedules") ? { slots: [] } : url.includes("/summary") ? [] : { todayPendingCount: 0, ongoingRepairCount: 0, total: 0 }),
    );
    mocks.routerPush.mockResolvedValue(undefined);
    const wrapper = mount(TodayPage);
    await flushPromises();
    const input = wrapper.get('textarea[aria-label="查找后台功能或输入命令"]');

    await input.setValue("/ 查看 维修事务 已完成");
    expect(wrapper.get(".command-composer-send").attributes("aria-label")).toBe("发送操作");
    await input.trigger("keydown", { key: "Enter" });
    await flushPromises();

    expect(mocks.routerPush).not.toHaveBeenCalled();
    await wrapper.findAll(".today-reply-action").at(-1)!.trigger("click");
    await flushPromises();
    expect(mocks.routerPush).toHaveBeenCalledWith({
      name: "repairs",
      query: { status: "COMPLETED" },
    });
    wrapper.unmount();
  });

  it("shows the parser message for an invalid command", async () => {
    mocks.apiGet.mockImplementation((url: string) =>
      Promise.resolve(url.includes("schedules") ? { slots: [] } : url.includes("/summary") ? [] : { todayPendingCount: 0, ongoingRepairCount: 0, total: 0 }),
    );
    const wrapper = mount(TodayPage);
    await flushPromises();

    await wrapper.get('textarea[aria-label="查找后台功能或输入命令"]')
      .setValue("/ 不存在");

    expect(wrapper.get(".command-composer-mode").text()).toContain("无法识别动作");
    expect(wrapper.get(".command-composer-send").attributes("aria-label")).toBe("检查命令");
    wrapper.unmount();
  });

  it("uses slash as the global command shortcut without keeping Ctrl+K", async () => {
    mocks.apiGet.mockImplementation((url: string) =>
      Promise.resolve(url.includes("schedules") ? { slots: [] } : url.includes("/summary") ? [] : { todayPendingCount: 0, ongoingRepairCount: 0, total: 0 }),
    );
    const wrapper = mount(TodayPage, { attachTo: document.body });
    await flushPromises();
    const input = wrapper.get('textarea[aria-label="查找后台功能或输入命令"]');

    window.dispatchEvent(new KeyboardEvent("keydown", { key: "k", ctrlKey: true, bubbles: true }));
    expect(document.activeElement).not.toBe(input.element);

    window.dispatchEvent(new KeyboardEvent("keydown", { key: "/", bubbles: true }));
    await flushPromises();
    expect((input.element as HTMLTextAreaElement).value).toBe("/");
    expect(document.activeElement).toBe(input.element);
    wrapper.unmount();
  });

  it("provides reply buttons for keyword searches and form shortcuts without issuing business writes", async () => {
    vi.useFakeTimers();
    mocks.apiGet.mockImplementation((url: string) => Promise.resolve(url.includes("schedules") ? { slots: [] } : url.includes("/summary") ? [] : { todayPendingCount: 0, ongoingRepairCount: 0, total: 0 }));
    const wrapper = mount(TodayPage);
    await flushPromises();
    const input = wrapper.get("textarea");
    await input.setValue('/ 查找 值班记录 "演示成员"');
    await input.trigger("keydown", { key: "Enter" });
    await flushPromises();
    expect(mocks.routerPush).not.toHaveBeenCalled();
    await wrapper.findAll(".today-reply-action").at(-1)!.trigger("click");
    await flushPromises();
    expect(mocks.routerPush).toHaveBeenCalledWith({ name: "attendance", query: { keyword: "演示成员" } });
    mocks.routerPush.mockClear();
    vi.advanceTimersByTime(600);
    await wrapper.findAll(".today-command-shortcuts button").find(item => item.text().includes("新建维修"))!.trigger("click");
    await flushPromises();
    expect(wrapper.find(".today-command-history").exists()).toBe(false);
    expect(mocks.routerPush).not.toHaveBeenCalled();
    await wrapper.findAll(".today-reply-action").at(-1)!.trigger("click");
    await flushPromises();
    expect(mocks.routerPush).toHaveBeenCalledWith({ name: "repairs", query: { intent: "new", status: "REPAIRING" } });
    expect(mocks.apiGet.mock.calls.every(([url]) => /stats\/dashboard|schedules\/today/.test(url))).toBe(true);
    wrapper.unmount();
  });

  it("locks duplicate navigation and preserves the command for retry after failure", async () => {
    vi.useFakeTimers();
    mocks.apiGet.mockImplementation((url: string) => Promise.resolve(url.includes("schedules") ? { slots: [] } : url.includes("/summary") ? [] : { todayPendingCount: 0, ongoingRepairCount: 0, total: 0 }));
    let rejectNavigation!: (reason: Error) => void;
    mocks.routerPush.mockImplementation(() => new Promise((_resolve, reject) => { rejectNavigation = reject; }));
    const wrapper = mount(TodayPage);
    await flushPromises();
    await wrapper.get("textarea").setValue("/ 查看 数据统计 本周");
    await wrapper.get("textarea").trigger("keydown", { key: "Enter" });
    await flushPromises();
    await wrapper.get(".today-reply-action").trigger("click");
    await wrapper.get(".today-reply-action").trigger("click");
    expect(mocks.routerPush).toHaveBeenCalledTimes(1);
    rejectNavigation(new Error("synthetic navigation failure"));
    await flushPromises();
    expect(wrapper.get(".today-navigation-error").text()).toContain("页面没能打开，请再试一次");
    expect(wrapper.text()).not.toContain("synthetic navigation failure");
    expect(wrapper.get(".today-user-message").text()).toBe("/ 查看 数据统计 本周");
    vi.advanceTimersByTime(600);
    mocks.routerPush.mockResolvedValue(undefined);
    await flushPromises();
    await wrapper.get(".today-reply-action").trigger("click");
    await flushPromises();
    expect(mocks.routerPush).toHaveBeenCalledTimes(2);
    expect(wrapper.find(".today-navigation-error").exists()).toBe(false);
    wrapper.unmount();
  });

  it("does not submit during IME composition or prepare a dangerous command", async () => {
    mocks.apiGet.mockImplementation((url: string) => Promise.resolve(url.includes("schedules") ? { slots: [] } : url.includes("/summary") ? [] : { todayPendingCount: 0, ongoingRepairCount: 0, total: 0 }));
    const wrapper = mount(TodayPage);
    await flushPromises();
    const input = wrapper.get("textarea");
    await input.setValue("/ 查看 数据统计 本周");
    await input.trigger("compositionstart");
    await input.trigger("keydown", { key: "Enter", isComposing: true });
    expect(mocks.routerPush).not.toHaveBeenCalled();
    await input.trigger("compositionend");
    await input.setValue("/ 删除 成员名册");
    await input.trigger("keydown", { key: "Enter" });
    await flushPromises();
    expect(wrapper.get(".today-command-error").text()).toContain("不直接执行该操作");
    expect(wrapper.find(".today-reply-action").exists()).toBe(false);
    expect(mocks.routerPush).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it("keeps keyboard selection independent of mouse hover and completes the selected option", async () => {
    mocks.apiGet.mockImplementation((url: string) => Promise.resolve(url.includes("schedules") ? { slots: [] } : url.includes("/summary") ? [] : { todayPendingCount: 0, ongoingRepairCount: 0, total: 0 }));
    const wrapper = mount(TodayPage);
    await flushPromises();
    const input = wrapper.get("textarea");
    await input.setValue("/ 查看 ");
    await input.trigger("keydown", { key: "ArrowDown" });
    const options = wrapper.findAll(".command-suggestion-option");
    const selected = options[1]!.get("strong").text();
    await options.at(-1)!.trigger("mouseenter");
    expect(options[1]!.attributes("aria-selected")).toBe("true");
    expect(options.every(option => option.attributes("tabindex") === "-1")).toBe(true);
    await input.trigger("keydown", { key: "Tab" });
    await flushPromises();
    expect((input.element as HTMLTextAreaElement).value).toContain(selected);
    expect(mocks.routerPush).not.toHaveBeenCalled();
    await input.setValue("/ 查找 值班记");
    await input.trigger("keydown", { key: "ArrowRight" });
    await flushPromises();
    expect((input.element as HTMLTextAreaElement).value).toBe("/ 查找 值班记录 ");
    expect(mocks.routerPush).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it("does not overwrite a newer draft when an earlier navigation fails", async () => {
    mocks.apiGet.mockImplementation((url: string) => Promise.resolve(url.includes("schedules") ? { slots: [] } : url.includes("/summary") ? [] : { todayPendingCount: 0, ongoingRepairCount: 0, total: 0 }));
    let rejectNavigation!: (reason: Error) => void;
    mocks.routerPush.mockImplementation(() => new Promise((_resolve, reject) => { rejectNavigation = reject; }));
    const wrapper = mount(TodayPage);
    await flushPromises();
    const input = wrapper.get("textarea");
    await input.setValue("/ 查看 数据统计 本周");
    await input.trigger("keydown", { key: "Enter" });
    await flushPromises();
    await wrapper.get(".today-reply-action").trigger("click");
    await input.setValue("/ 查找 值班记录 演示姓名");
    rejectNavigation(new Error("synthetic failure"));
    await flushPromises();
    expect((input.element as HTMLTextAreaElement).value).toBe("/ 查找 值班记录 演示姓名");
    expect(wrapper.find(".today-command-error").exists()).toBe(false);
    wrapper.unmount();
  });

  it("summarizes actual statistics without navigating or changing a newer draft", async () => {
    let finish!: (value: unknown) => void;
    mocks.apiGet.mockImplementation((url: string) => url.includes('/summary') ? new Promise(resolve => { finish = resolve; }) : Promise.resolve(url.includes('schedules') ? { slots: [] } : { todayPendingCount: 0, ongoingRepairCount: 0 }));
    const wrapper = mount(TodayPage);
    await flushPromises();
    const input = wrapper.get('textarea');
    await input.setValue('/ 查看 数据统计 本周');
    await input.trigger('keydown', { key: 'Enter' });
    await input.setValue('/ 新建 维修事务');
    finish([{ attendanceCount: 6, trainingCount: 2, totalHours: 8.5 }, { attendanceCount: 3, trainingCount: 1, totalHours: 4 }]);
    await flushPromises();
    expect(wrapper.get('.today-conversation-turn').text()).toContain('值班 9 次，培训参与 3 次，合计有效时长 12.5 小时');
    expect((input.element as HTMLTextAreaElement).value).toBe('/ 新建 维修事务');
    expect(mocks.routerPush).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it("keeps query failures distinct from zero results and retries the same scope", async () => {
    let attempts = 0;
    mocks.apiGet.mockImplementation((url: string) => {
      if (url.startsWith('/api/repairs?')) return ++attempts === 1 ? Promise.reject(new Error('internal query error')) : Promise.resolve({ total: 0 });
      return Promise.resolve(url.includes('schedules') ? { slots: [] } : { todayPendingCount: 0, ongoingRepairCount: 0 });
    });
    const wrapper = mount(TodayPage);
    await flushPromises();
    await wrapper.get('textarea').setValue('/ 查看 维修事务 已完成 本月');
    await wrapper.get('textarea').trigger('keydown', { key: 'Enter' });
    await flushPromises();
    expect(wrapper.get('.today-conversation-turn').text()).toContain('查询没能完成');
    expect(wrapper.get('.today-conversation-turn').text()).not.toContain('暂无匹配');
    expect(wrapper.text()).not.toContain('internal query error');
    await wrapper.findAll('.today-conversation-turn button').find(b => b.text().includes('重新查询'))!.trigger('click');
    await flushPromises();
    expect(wrapper.get('.today-query-summary').text()).toBe('已完成的维修共 0 项。');
    const urls = mocks.apiGet.mock.calls.filter(([url]) => url.startsWith('/api/repairs?')).map(([url]) => url);
    expect(urls[0]).toBe(urls[1]);
    expect(mocks.routerPush).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('shows a shortcut name while preserving the parsed command and navigation target', async () => {
    mocks.apiGet.mockImplementation((url: string) => Promise.resolve(url.includes('schedules') ? { slots: [] } : { todayPendingCount: 0, ongoingRepairCount: 0 }));
    mocks.routerPush.mockResolvedValue(undefined);
    const wrapper = mount(TodayPage);
    await flushPromises();
    await wrapper.findAll('.today-command-shortcuts button').find(button => button.text().includes('新建维修'))!.trigger('click');
    await flushPromises();
    expect(wrapper.get('.today-user-message').text()).toBe('新建维修');
    expect(wrapper.get('.today-conversation-turn').text()).not.toContain('/ 新建 维修事务');
    expect(mocks.routerPush).not.toHaveBeenCalled();
    await wrapper.get('.today-reply-action').trigger('click');
    await flushPromises();
    // The display name must preserve both the original form intent and default repair status.
    expect(mocks.routerPush).toHaveBeenCalledWith({ name: 'repairs', query: { intent: 'new', status: 'REPAIRING' } });
    wrapper.unmount();
  });
});

vi.mock("../../shared/composables/useServiceHealth", () => ({ useServiceHealth: () => ({ online: true }) }));


describe("TodayPage data center commands", () => {
  it("holds an export command until its reply button is clicked and carries the parsed range", async () => {
    mocks.apiGet.mockImplementation((url:string)=>Promise.resolve(url.includes("schedules")?{slots:[]}:{todayPendingCount:0,ongoingRepairCount:0}));
    mocks.routerPush.mockResolvedValue(undefined);
    const wrapper=mount(TodayPage); await flushPromises();
    const input=wrapper.get('textarea[aria-label="查找后台功能或输入命令"]');
    await input.setValue("/ 导出 维修事务 已取消 2026-08-01..2026-08-28"); await input.trigger("keydown",{key:"Enter"}); await flushPromises();
    expect(mocks.routerPush).not.toHaveBeenCalled();
    expect(mocks.apiGet.mock.calls.every(([url])=>/stats\/dashboard|schedules\/today/.test(url))).toBe(true);
    await wrapper.get(".today-reply-action").trigger("click"); await flushPromises();
    expect(mocks.routerPush).toHaveBeenCalledWith({name:"data",query:{tab:"export",source:"repairs",status:"CANCELED",from:"2026-08-01",to:"2026-08-28"}});
    wrapper.unmount();
  });
});

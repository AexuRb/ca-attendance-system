// @vitest-environment jsdom
import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import TodayPage from "./TodayPage.vue";

const mocks = vi.hoisted(() => ({
  apiGet: vi.fn(),
  routerPush: vi.fn(),
  notify: vi.fn(),
}));

vi.mock("../../shared/api", () => ({
  get: (...args: unknown[]) => mocks.apiGet(...args),
}));
vi.mock("vue-router", () => ({
  useRouter: () => ({ push: mocks.routerPush }),
  RouterLink: { template: "<a><slot /></a>" },
}));
vi.mock("../../shared/composables/useToast", () => ({ notify: mocks.notify }));
vi.mock("../../app/session", () => ({
  useSession: () => ({ user: { value: { role: "ADMIN" } } }),
}));

afterEach(() => {
  Object.values(mocks).forEach((mock) => mock.mockReset());
  vi.useRealTimers();
});

describe("TodayPage request states", () => {
  it("shows a retryable error instead of presenting failed data as empty", async () => {
    mocks.apiGet.mockRejectedValueOnce(new Error("今日数据加载失败"));
    mocks.apiGet.mockResolvedValueOnce({ slots: [] });
    mocks.apiGet.mockResolvedValueOnce([]);
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

    mocks.apiGet.mockImplementation((url: string) => {
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

  it("executes a role-allowed command with its parsed date range", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 7, 28, 12));
    mocks.apiGet.mockImplementation((url: string) =>
      Promise.resolve(url.includes("schedules") ? { slots: [] } : {}),
    );
    mocks.routerPush.mockResolvedValue(undefined);
    const wrapper = mount(TodayPage);
    await flushPromises();

    const input = wrapper.get('textarea[aria-label="查找后台功能或输入命令"]');
    await input.setValue("/ 查看 值班记录 本周");
    await input.trigger("keydown", { key: "Enter" });
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
      Promise.resolve(url.includes("schedules") ? { slots: [] } : {}),
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
      Promise.resolve(url.includes("schedules") ? { slots: [] } : {}),
    );
    const wrapper = mount(TodayPage);
    await flushPromises();
    const input = wrapper.get('textarea[aria-label="查找后台功能或输入命令"]');

    await input.setValue("/ 查看 维修事务 已完成 ");
    await flushPromises();

    expect(wrapper.get(".command-workspace").classes()).toContain("is-engaged");
    expect(wrapper.get(".command-welcome").classes()).toContain("is-compact");
    expect(wrapper.find(".command-welcome h1").exists()).toBe(false);
    expect(wrapper.get(".command-composer-mode").text())
      .toBe("可以执行，也可继续补充范围");
    expect(wrapper.findAll(".command-suggestion-option")).toHaveLength(7);
    expect(wrapper.text()).toContain("查看已完成的本月维修");

    await input.setValue("");
    await flushPromises();
    expect(wrapper.get(".command-workspace").classes()).not.toContain("is-engaged");
    expect(wrapper.get(".command-welcome h1").text()).toBe("今天要处理什么？");
    wrapper.unmount();
  });

  it("executes an extensible command without forcing an optional range", async () => {
    mocks.apiGet.mockImplementation((url: string) =>
      Promise.resolve(url.includes("schedules") ? { slots: [] } : {}),
    );
    mocks.routerPush.mockResolvedValue(undefined);
    const wrapper = mount(TodayPage);
    await flushPromises();
    const input = wrapper.get('textarea[aria-label="查找后台功能或输入命令"]');

    await input.setValue("/ 查看 维修事务 已完成");
    expect(wrapper.get(".command-composer-send").attributes("aria-label")).toBe("执行命令");
    await input.trigger("keydown", { key: "Enter" });
    await flushPromises();

    expect(mocks.routerPush).toHaveBeenCalledWith({
      name: "repairs",
      query: { status: "COMPLETED" },
    });
    wrapper.unmount();
  });

  it("shows the parser message for an invalid command", async () => {
    mocks.apiGet.mockImplementation((url: string) =>
      Promise.resolve(url.includes("schedules") ? { slots: [] } : {}),
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
      Promise.resolve(url.includes("schedules") ? { slots: [] } : {}),
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
});

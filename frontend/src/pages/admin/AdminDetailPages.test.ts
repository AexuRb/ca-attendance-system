// @vitest-environment jsdom
import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import TrainingPage from "./TrainingPage.vue";
import SettingsPage from "./SettingsPage.vue";

const apiGet = vi.fn();
const apiPut = vi.fn();
const routerReplace = vi.fn();

vi.mock("../../shared/api", () => ({
  api: (...args: unknown[]) => apiGet(...args),
  get: (...args: unknown[]) => apiGet(...args),
  post: vi.fn(),
  put: (...args: unknown[]) => apiPut(...args),
  del: vi.fn(),
  downloadBlob: vi.fn(),
}));

vi.mock("vue-router", () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ replace: routerReplace }),
  onBeforeRouteLeave: vi.fn(),
}));

vi.mock("../../app/session", () => ({
  useSession: () => ({ user: { value: { role: "ADMIN" } } }),
}));

afterEach(() => {
  apiGet.mockReset();
  apiPut.mockReset();
  routerReplace.mockReset();
  document.body.innerHTML = "";
});

describe("TrainingPage details", () => {
  it("labels icon actions and prepares participant rows for narrow screens", async () => {
    apiGet.mockImplementation((url: string) => {
      if (url.startsWith("/api/trainings/page?")) {
        return Promise.resolve({
          items: [{
            id: 1,
            title: "Windows 系统维护、数据备份与常见硬件故障排查实务培训",
            trainingDate: "2026-08-11",
            startTime: "09:30",
            endTime: "11:45",
            location: "大学生活动中心计算机协会办公室",
            speaker: "陈禹杭",
            participantCount: 1,
            totalDurationHours: 2.25,
          }],
          total: 1,
          page: 1,
          pageSize: 20,
          hasMore: false,
        });
      }
      if (url.startsWith("/api/trainings/1/participants/page?")) {
        return Promise.resolve({
          items: [{
            id: 2,
            sessionId: 1,
            name: "测试成员",
            studentNo: "99010811102736",
            durationHours: 2.25,
            remark: "主讲人，负责现场演示与问题答疑。",
          }],
          total: 1,
          page: 1,
          pageSize: 20,
          hasMore: false,
        });
      }
      return Promise.resolve([]);
    });

    const wrapper = mount(TrainingPage);
    await flushPromises();

    expect(wrapper.find(".training-month-shell").exists()).toBe(true);
    expect(wrapper.findAll(".training-ribbon-event")).toHaveLength(1);
    expect(wrapper.get('button[data-action="add-participant"]').text()).toContain(
      "新增记录",
    );
    expect(wrapper.get('button[data-action="import-participants"]').text()).toContain(
      "导入",
    );

    const more = wrapper.get('button[aria-haspopup="menu"]');
    expect(more.attributes("aria-label")).toContain("更多操作");
    expect(more.get("svg").attributes("aria-hidden")).toBe("true");
    expect(wrapper.get(".training-overview-edit").text()).toContain("编辑培训");
    await more.trigger("click");
    await flushPromises();
    expect(document.body.textContent).toContain("导出名单");
    expect(document.body.textContent).toContain("归档培训");

    const participantRows = wrapper.findAll(".training-participant-row");
    expect(participantRows).toHaveLength(1);
    expect(participantRows[0].text()).toContain("测试成员");
    expect(
      participantRows[0].get('button[aria-label^="编辑"]').attributes("title"),
    ).toBe("编辑参与记录");
    expect(
      participantRows[0].get('button[aria-label^="删除"]').attributes("title"),
    ).toBe("删除参与记录");
    expect(wrapper.text()).not.toContain("出勤");
    expect(wrapper.text()).not.toContain("来源");
  });
});

describe("SettingsPage details", () => {
  it("announces validation errors and labels period icon actions", async () => {
    apiGet.mockImplementation((url: string) => {
      if (url === "/api/settings/weekdays") return Promise.resolve([]);
      if (url === "/api/settings/attendance-policy") {
        return Promise.resolve({
          requireDutyDay: false,
          requireDutyPeriod: false,
        });
      }
      if (url === "/api/settings/duty-periods") {
        return Promise.resolve([
          { startTime: "14:00", endTime: "16:00", enabled: true },
          { startTime: "15:00", endTime: "17:00", enabled: true },
        ]);
      }
      return Promise.resolve([]);
    });

    const wrapper = mount(SettingsPage);
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain("重叠");
    for (const label of ["上移", "下移", "删除时段"]) {
      const button = wrapper.get(`button[title="${label}"]`);
      expect(button.attributes("aria-label")).toBe(label);
      expect(button.get("svg").attributes("aria-hidden")).toBe("true");
    }
  });

  it("prevents duplicate saves for the same settings section", async () => {
    apiGet.mockImplementation((url: string) => {
      if (url === "/api/settings/weekdays") return Promise.resolve([]);
      if (url === "/api/settings/attendance-policy") {
        return Promise.resolve({ requireDutyDay: false, requireDutyPeriod: false });
      }
      return Promise.resolve([]);
    });
    let resolveSave!: (value: unknown) => void;
    apiPut.mockReturnValue(new Promise((resolve) => (resolveSave = resolve)));
    const wrapper = mount(SettingsPage, {
      global: { stubs: { Teleport: true } },
    });
    await flushPromises();

    const saveWeekdays = wrapper.findAll(".setting-section")[0].get(".button.primary");
    await saveWeekdays.trigger("click");
    await saveWeekdays.trigger("click");
    expect(apiPut).toHaveBeenCalledTimes(1);
    expect(saveWeekdays.attributes("disabled")).toBeDefined();

    resolveSave({});
    await flushPromises();
    wrapper.unmount();
  });

  it("saves weekdays selected from the calendar leaves", async () => {
    apiGet.mockImplementation((url: string) => {
      if (url === "/api/settings/weekdays") {
        return Promise.resolve([
          { weekday: 1, weekday_name: "星期一", enabled: true },
          { weekday: 2, weekday_name: "星期二", enabled: false },
        ]);
      }
      if (url === "/api/settings/attendance-policy") {
        return Promise.resolve({ requireDutyDay: false, requireDutyPeriod: false });
      }
      return Promise.resolve([]);
    });
    apiPut.mockResolvedValue({});
    const wrapper = mount(SettingsPage, {
      global: { stubs: { Teleport: true } },
    });
    await flushPromises();

    await wrapper.get('[data-weekday="2"]').trigger("click");
    expect(wrapper.get('[data-weekday="2"]').attributes("aria-pressed")).toBe(
      "true",
    );
    await wrapper
      .findAll(".setting-section")[0]
      .get(".button.primary")
      .trigger("click");
    await flushPromises();

    expect(apiPut).toHaveBeenCalledWith("/api/settings/weekdays", {
      enabledWeekdays: [1, 2],
    });
  });
});

import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import TrainingPage from "./TrainingPage.vue";
import SettingsPage from "./SettingsPage.vue";

const apiGet = vi.fn();

vi.mock("../../shared/api", () => ({
  get: (...args: unknown[]) => apiGet(...args),
  post: vi.fn(),
  put: vi.fn(),
  del: vi.fn(),
  downloadBlob: vi.fn(),
}));

vi.mock("../../app/session", () => ({
  useSession: () => ({ user: { value: { role: "ADMIN" } } }),
}));

afterEach(() => {
  apiGet.mockReset();
  document.body.innerHTML = "";
});

describe("TrainingPage details", () => {
  it("labels icon actions and prepares participant rows for narrow screens", async () => {
    apiGet.mockImplementation((url: string) => {
      if (url.startsWith("/api/trainings?")) {
        return Promise.resolve([
          {
            id: 1,
            title: "Windows 系统维护、数据备份与常见硬件故障排查实务培训",
            trainingDate: "2026-08-11",
            startTime: "09:30",
            endTime: "11:45",
            location: "大学生活动中心计算机协会办公室",
            speaker: "陈禹杭",
            participantCount: 1,
            totalDurationHours: 2.25,
          },
        ]);
      }
      if (url === "/api/trainings/1/participants") {
        return Promise.resolve([
          {
            id: 2,
            name: "测试成员",
            studentNo: "99010811102736",
            durationHours: 2.25,
            remark: "主讲人，负责现场演示与问题答疑。",
          },
        ]);
      }
      return Promise.resolve([]);
    });

    const wrapper = mount(TrainingPage);
    await flushPromises();

    const actions = wrapper.findAll(".detail-heading .icon-button");
    expect(actions.map((button) => button.attributes("aria-label"))).toEqual([
      "导出名单",
      "导入名单",
      "编辑培训",
      "归档培训",
    ]);
    expect(
      actions.every(
        (button) => button.get("svg").attributes("aria-hidden") === "true",
      ),
    ).toBe(true);

    const participantCells = wrapper.findAll(
      ".training-participant-table tbody td",
    );
    expect(participantCells.map((cell) => cell.attributes("data-label"))).toEqual(
      ["参与人", "计入时长", "备注", "操作"],
    );
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
});

import { describe, expect, it } from "vitest";
import {
  validateParticipantForm,
  validateTrainingSessionForm,
} from "./trainingForms";

describe("training form validation", () => {
  it("returns field-specific session errors", () => {
    expect(
      validateTrainingSessionForm({
        id: null,
        title: "",
        trainingDate: "",
        startTime: "16:00",
        endTime: "14:00",
        location: "",
        speaker: "",
        description: "",
      }),
    ).toEqual({
      title: "请填写培训标题",
      trainingDate: "请选择培训日期",
      endTime: "结束时间不能早于开始时间",
    });
  });

  it("rejects invalid participant identity and duration", () => {
    expect(
      validateParticipantForm({
        id: null,
        studentNo: "abc",
        name: "",
        durationHours: -1,
        remark: "",
      }),
    ).toEqual({
      studentNo: "学号应为 6 至 32 位数字",
      name: "请填写姓名",
      durationHours: "时长不能小于 0",
    });
  });

  it("rejects a session title longer than one hundred characters", () => {
    expect(
      validateTrainingSessionForm({
        id: null,
        title: "培".repeat(101),
        trainingDate: "2026-08-21",
        startTime: "14:00",
        endTime: "16:00",
        location: "",
        speaker: "",
        description: "",
      }),
    ).toEqual({ title: "培训标题不能超过 100 个字符" });
  });
});

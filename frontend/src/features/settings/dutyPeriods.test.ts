import { describe, expect, it } from "vitest";
import { moveDutyPeriod, validateDutyPeriods } from "./dutyPeriods";

describe("duty periods", () => {
  it("moves periods without sorting by clock time", () => {
    const periods = [
      { startTime: "14:00", endTime: "16:00", enabled: true },
      { startTime: "16:00", endTime: "18:00", enabled: true },
    ];

    expect(moveDutyPeriod(periods, 1, -1)).toEqual([
      periods[1],
      periods[0],
    ]);
  });

  it("only treats enabled overlapping ranges as conflicts", () => {
    expect(
      validateDutyPeriods([
        { startTime: "14:00", endTime: "16:00", enabled: true },
        { startTime: "15:00", endTime: "17:00", enabled: false },
      ]),
    ).toBe("");
    expect(
      validateDutyPeriods([
        { startTime: "14:00", endTime: "16:00", enabled: true },
        { startTime: "15:00", endTime: "17:00", enabled: true },
      ]),
    ).toContain("不能重叠");
  });

  it("matches the backend limit of twelve periods", () => {
    const periods = Array.from({ length: 13 }, (_, index) => ({
      startTime: `${String(index).padStart(2, "0")}:00`,
      endTime: `${String(index).padStart(2, "0")}:30`,
      enabled: false,
    }));

    expect(validateDutyPeriods(periods)).toBe("值班时间段最多设置 12 个");
  });

  it("rejects duplicate periods even when one is disabled", () => {
    expect(
      validateDutyPeriods([
        { startTime: "14:00", endTime: "16:00", enabled: true },
        { startTime: "14:00", endTime: "16:00", enabled: false },
      ]),
    ).toBe("值班时间段不能重复");
  });
});

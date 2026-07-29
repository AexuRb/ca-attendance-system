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
});

import { describe, expect, it } from "vitest";
import { normalizeDutyWeekdays } from "./dutyWeekdays";

describe("duty weekday settings", () => {
  it("normalizes SQLite integer flags before binding checkboxes", () => {
    expect(
      normalizeDutyWeekdays([
        { weekday: 1, weekday_name: "星期一", enabled: 0 },
        { weekday: 2, weekday_name: "星期二", enabled: 1 },
      ]),
    ).toEqual([
      { weekday: 1, weekday_name: "星期一", enabled: false },
      { weekday: 2, weekday_name: "星期二", enabled: true },
    ]);
  });
});

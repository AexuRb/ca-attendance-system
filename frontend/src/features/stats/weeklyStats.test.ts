import { describe, expect, it } from "vitest";
import { weeklyCellHours, type WeeklyStatsDetail } from "./weeklyStats";

describe("weekly statistics matrix", () => {
  const detail: WeeklyStatsDetail = {
    days: [
      {
        dutyDate: "2026-07-27",
        weekday: 1,
        weekdayName: "星期一",
      },
    ],
    users: [
      {
        userId: 7,
        studentNo: "1001",
        name: "测试成员",
        grade: "2025级",
        role: "MEMBER",
        attendanceHours: 2,
        trainingHours: 1.5,
        totalHours: 3.5,
      },
    ],
    cells: {
      "2026-07-27": {
        "7": 2,
      },
    },
  };

  it("reads a member's daily duty hours by date and id", () => {
    expect(weeklyCellHours(detail, "2026-07-27", 7)).toBe(2);
  });

  it("returns zero for an empty day cell", () => {
    expect(weeklyCellHours(detail, "2026-07-28", 7)).toBe(0);
  });
});

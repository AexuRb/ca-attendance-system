import { describe, expect, it } from "vitest";
import { effectiveDutyCount } from "./statsSummary";

describe("statistics summary", () => {
  it("uses the backend duty count that includes training participation", () => {
    expect(
      effectiveDutyCount({
        studentNo: "1001",
        name: "测试成员",
        role: "MEMBER",
        attendanceCount: 3,
        trainingCount: 2,
        dutyCount: 5,
      }),
    ).toBe(5);
  });
});

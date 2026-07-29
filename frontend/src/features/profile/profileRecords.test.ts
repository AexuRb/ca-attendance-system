import { describe, expect, it } from "vitest";
import {
  attendanceStatusMeta,
  trainingStatusMeta,
  totalAttendanceHours,
} from "./profileRecords";

describe("profile record presentation", () => {
  it("keeps each attendance state distinct", () => {
    expect(attendanceStatusMeta("VALID")).toEqual({
      label: "有效",
      tone: "success",
    });
    expect(attendanceStatusMeta("PENDING").label).toBe("待审核");
    expect(attendanceStatusMeta("INVALID").label).toBe("无效");
    expect(attendanceStatusMeta("INCOMPLETE").label).toBe("未完成");
  });

  it("shows the exact training attendance state", () => {
    expect(trainingStatusMeta("PRESENT").label).toBe("出席");
    expect(trainingStatusMeta("LEAVE").label).toBe("请假");
    expect(trainingStatusMeta("ABSENT").label).toBe("缺席");
  });

  it("only sums valid attendance hours", () => {
    expect(
      totalAttendanceHours([
        { effectiveStatus: "VALID", validHours: 2 },
        { effectiveStatus: "PENDING", validHours: 3 },
        { effectiveStatus: "VALID", validHours: 1 },
      ]),
    ).toBe(3);
  });
});

import { describe, expect, it } from "vitest";
import {
  canManageAttendancePolicy,
  normalizeAttendancePolicy,
} from "./attendancePolicy";

describe("attendance eligibility policy", () => {
  it("only lets administrators change eligibility rules", () => {
    expect(canManageAttendancePolicy("MEMBER")).toBe(false);
    expect(canManageAttendancePolicy("MINISTER")).toBe(false);
    expect(canManageAttendancePolicy("PRESIDENT")).toBe(false);
    expect(canManageAttendancePolicy("ADMIN")).toBe(true);
  });

  it("uses relaxed defaults when settings are absent", () => {
    expect(normalizeAttendancePolicy(undefined)).toEqual({
      requireDutyDay: false,
      requireDutyPeriod: false,
    });
  });
});

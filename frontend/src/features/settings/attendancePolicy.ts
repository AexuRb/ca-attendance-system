import type { Role } from "../../shared/types";

export interface AttendancePolicy {
  requireDutyDay: boolean;
  requireDutyPeriod: boolean;
}

export function normalizeAttendancePolicy(
  value?: Partial<AttendancePolicy>,
): AttendancePolicy {
  return {
    requireDutyDay: value?.requireDutyDay === true,
    requireDutyPeriod: value?.requireDutyPeriod === true,
  };
}

export function canManageAttendancePolicy(role?: Role | string): boolean {
  return role === "ADMIN";
}

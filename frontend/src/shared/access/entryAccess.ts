import type { AccessContext, Role } from "../types";

export type EntryMode = AccessContext["mode"];

export function isRemoteMinister(role?: Role | string, mode?: EntryMode): boolean {
  return role === "MINISTER" && mode === "REMOTE_ADMIN";
}

export function canReviewAttendance(role?: Role, mode?: EntryMode): boolean {
  return Boolean(role && ["MINISTER", "PRESIDENT", "ADMIN"].includes(role))
    && !isRemoteMinister(role, mode);
}

export function canConductSkillAssessments(role?: Role, mode?: EntryMode): boolean {
  return role === "MINISTER" && mode === "LOCAL";
}

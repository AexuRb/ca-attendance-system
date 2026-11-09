import type { AccessContext, Role } from "../types";

export type EntryMode = AccessContext["mode"];

export function isRemoteMinister(role?: Role | string, mode?: EntryMode): boolean {
  return role === "MINISTER" && mode === "REMOTE_ADMIN";
}

export function canReviewAttendance(role?: Role, mode?: EntryMode): boolean {
  return Boolean(role && ["MINISTER", "PRESIDENT", "ADMIN"].includes(role))
    && !isRemoteMinister(role, mode);
}

export function commandAllowedAtEntry(command: string, role: Role, mode?: EntryMode): boolean {
  if (!isRemoteMinister(role, mode)) return true;
  const content = command.replace(/^\s*\/\s*/, "").trim();
  return !/^(打开|查看|查找)\s+签到审核(?:\s|$)/.test(content)
    && !/^(新建|创建|导入|设置)(?:\s|$)/.test(content);
}

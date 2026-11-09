import type { Role } from "../../shared/types";
import { isRemoteMinister, type EntryMode } from "../../shared/access/entryAccess";

export function canManageRepairs(role?: Role | string, mode?: EntryMode): boolean {
  return (role === "MINISTER" || role === "PRESIDENT" || role === "ADMIN")
    && !isRemoteMinister(role, mode);
}

export function canExportRepairs(role?: Role | string): boolean {
  return role === "PRESIDENT" || role === "ADMIN";
}

export function canDeleteRepairs(role?: Role | string): boolean {
  return role === "PRESIDENT" || role === "ADMIN";
}

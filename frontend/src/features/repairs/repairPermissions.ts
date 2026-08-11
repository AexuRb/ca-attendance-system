import type { Role } from "../../shared/types";

export function canManageRepairs(role?: Role | string): boolean {
  return role === "MINISTER" || role === "PRESIDENT" || role === "ADMIN";
}

export function canExportRepairs(role?: Role | string): boolean {
  return role === "PRESIDENT" || role === "ADMIN";
}

export function canDeleteRepairs(role?: Role | string): boolean {
  return role === "PRESIDENT" || role === "ADMIN";
}

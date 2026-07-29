import type { Role } from "../../shared/types";

export function canExportRepairs(role?: Role | string): boolean {
  return role === "PRESIDENT" || role === "ADMIN";
}

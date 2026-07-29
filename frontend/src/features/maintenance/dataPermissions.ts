import type { Role } from "../../shared/types";

export function canRestoreBackup(role?: Role | string): boolean {
  return role === "ADMIN";
}

export function canViewRepairRecycleBin(role?: Role | string): boolean {
  return role === "ADMIN";
}

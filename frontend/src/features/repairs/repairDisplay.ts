import type { RepairCase } from "./repairTypes";

const DAY_MS = 24 * 60 * 60 * 1000;

export function maskRepairPhone(value?: string): string {
  const normalized = value?.trim() || "";
  if (!normalized) return "未填写";
  return `**** **** ${normalized.slice(-4)}`;
}

export function repairAgeDays(
  repair: Pick<RepairCase, "status" | "receivedAt" | "completedAt">,
  now = new Date(),
): number {
  const started = parseLocalDate(repair.receivedAt);
  const ended =
    repair.status === "COMPLETED" && repair.completedAt
      ? parseLocalDate(repair.completedAt)
      : now;
  if (!started || !ended) return 0;
  return Math.max(0, Math.floor((daySerial(ended) - daySerial(started)) / DAY_MS));
}

export function repairAgeLabel(
  repair: Pick<RepairCase, "status" | "receivedAt" | "completedAt">,
  now = new Date(),
): string {
  const days = repairAgeDays(repair, now);
  if (repair.status === "COMPLETED") return `处理历时 ${days} 天`;
  if (repair.status === "CANCELED") return `流程历时 ${days} 天`;
  return days ? `已受理 ${days} 天` : "今日受理";
}

export function isLongRunningRepair(
  repair: Pick<RepairCase, "status" | "receivedAt" | "completedAt">,
  now = new Date(),
): boolean {
  return repair.status === "REPAIRING" && repairAgeDays(repair, now) >= 7;
}

function parseLocalDate(value?: string): Date | null {
  if (!value) return null;
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

function daySerial(value: Date): number {
  return Date.UTC(value.getFullYear(), value.getMonth(), value.getDate());
}

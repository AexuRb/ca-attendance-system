import type { RepairStatus } from "../../../features/repairs/repairTypes";

export function statusLabel(status: RepairStatus): string {
  return { REPAIRING: "进行中", COMPLETED: "已完成", CANCELED: "已取消" }[status];
}

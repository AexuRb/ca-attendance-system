import { defaultCommands } from "../../../features/command-center/commandCatalog";
import type { Role } from "../../../shared/types";
import type { TodayDashboardData, TodayQuickAction } from "./types";

export function buildTodayQuickActions(
  dashboard: TodayDashboardData | null,
  missingScheduleCount: number,
  canSchedule: boolean,
  role: Role,
  date = "今天",
): TodayQuickAction[] {
  const items: TodayQuickAction[] = [];
  const pending = dashboard?.todayPendingCount || 0;
  const open = dashboard?.todayOpenCount || 0;
  const repairs = dashboard?.ongoingRepairCount || 0;
  const dateLabel = date === "今天" ? "今日" : date;
  const canManage = ["MINISTER", "PRESIDENT", "ADMIN"].includes(role);

  if (canManage && pending) items.push({
    id: "reviews",
    command: "/ 打开 签到审核",
    label: "处理待审核记录",
    detail: `${dateLabel} ${pending} 条提醒 · 完整队列`,
    tone: "amber",
  });
  if (canManage && open) items.push({
    id: "attendance-open",
    command: "/ 查看 值班记录 未签退 " + date,
    label: `查看 ${open} 条未签退记录`,
    detail: `${dateLabel} · 值班记录`,
    tone: "red",
  });
  if (canSchedule && ["PRESIDENT", "ADMIN"].includes(role) && missingScheduleCount) items.push({
    id: "schedules",
    command: "/ 打开 排班管理",
    label: `补充 ${missingScheduleCount} 个排班时段`,
    detail: "固定周表",
    tone: "red",
  });
  if (canManage && repairs) items.push({
    id: "repairs",
    command: "/ 查看 维修事务 进行中",
    label: `查看 ${repairs} 项维修事务`,
    detail: "进行中",
    tone: "blue",
  });

  for (const command of defaultCommands(role)) {
    if (items.length >= 3) break;
    if (items.some((item) => item.id === command.id)) continue;
    items.push({
      id: command.id,
      command: command.command,
      label: shortCommandLabel(command.command),
      detail: command.description,
      tone: command.execution === "confirm" ? "amber" : "blue",
    });
  }
  return items;
}

function shortCommandLabel(command: string): string {
  return command
    .replace(/^\/\s*/, "")
    .replace(/^(打开|查看)\s+/, "")
    .replace(/\s+(今天|本周)$/, "");
}

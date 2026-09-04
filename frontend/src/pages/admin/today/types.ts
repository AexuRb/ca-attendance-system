export interface TodayDashboardData {
  todayRecordCount?: number;
  todayValidHours?: number;
  todayPendingCount?: number;
  todayOpenCount?: number;
  ongoingRepairCount?: number;
}

export interface TodayScheduleAssignee {
  studentNo: string;
  name: string;
}

export interface TodayScheduleSlot {
  key: string;
  title: string;
  startTime?: string;
  endTime?: string;
  assignees: TodayScheduleAssignee[];
}

export interface TodayScheduleData {
  weekdayName?: string;
  slots?: TodayScheduleSlot[];
}

export type TodayQuickActionTone = "blue" | "amber" | "red" | "green";

export interface TodayQuickAction {
  id: string;
  command: string;
  label: string;
  detail: string;
  tone: TodayQuickActionTone;
}

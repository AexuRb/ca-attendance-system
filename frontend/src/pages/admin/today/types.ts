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

export interface TodayAttendanceRecord {
  id: number;
  studentNo?: string;
  name: string;
  checkInTime?: string;
  checkOutTime?: string;
  durationMinutes?: number;
  effectiveStatus: string;
}

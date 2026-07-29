export interface WeeklyStatsDay {
  dutyDate: string;
  weekday: number;
  weekdayName: string;
}

export interface WeeklyStatsUser {
  userId: number;
  studentNo: string;
  name: string;
  grade?: string;
  role: string;
  attendanceHours: number;
  trainingHours: number;
  totalHours: number;
}

export interface WeeklyStatsDetail {
  days: WeeklyStatsDay[];
  users: WeeklyStatsUser[];
  cells: Record<string, Record<string, number>>;
}

export function weeklyCellHours(
  detail: WeeklyStatsDetail,
  dutyDate: string,
  userId: number,
): number {
  return Number(detail.cells[dutyDate]?.[String(userId)] || 0);
}

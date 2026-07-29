export interface StatsSummaryRow {
  userId?: number;
  studentNo: string;
  name: string;
  role: string;
  grade?: string;
  attendanceHours?: number;
  dutyHours?: number;
  trainingHours?: number;
  totalHours?: number;
  attendanceCount?: number;
  trainingCount?: number;
  dutyCount?: number;
}

export function effectiveDutyCount(row: StatsSummaryRow): number {
  const count = Number(row.dutyCount ?? 0);
  return Number.isFinite(count) ? count : 0;
}

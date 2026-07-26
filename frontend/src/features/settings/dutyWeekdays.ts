export interface DutyWeekdaySetting {
  weekday: number;
  weekday_name: string;
  enabled: boolean;
}

interface DutyWeekdayApiRow {
  weekday: number | string;
  weekday_name?: string;
  weekdayName?: string;
  enabled: boolean | number | string;
}

export function normalizeDutyWeekdays(
  rows: DutyWeekdayApiRow[],
): DutyWeekdaySetting[] {
  return rows.map((row) => ({
    weekday: Number(row.weekday),
    weekday_name:
      row.weekday_name || row.weekdayName || `星期${Number(row.weekday)}`,
    enabled:
      row.enabled === true ||
      row.enabled === 1 ||
      row.enabled === "1" ||
      row.enabled === "true",
  }));
}

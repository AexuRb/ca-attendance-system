export interface DutyPeriod {
  sortOrder?: number;
  startTime: string;
  endTime: string;
  enabled: boolean;
}

export function moveDutyPeriod(
  periods: DutyPeriod[],
  index: number,
  direction: -1 | 1,
): DutyPeriod[] {
  const target = index + direction;
  if (target < 0 || target >= periods.length) return periods;
  const next = [...periods];
  [next[index], next[target]] = [next[target], next[index]];
  return next;
}

export function validateDutyPeriods(periods: DutyPeriod[]): string {
  if (!periods.length) return "至少保留一个值班时间段";
  for (const [index, item] of periods.entries()) {
    if (!item.startTime || !item.endTime) return `第 ${index + 1} 个时段不完整`;
    if (item.startTime >= item.endTime) {
      return `第 ${index + 1} 个时段的结束时间必须晚于开始时间`;
    }
  }
  const enabled = periods
    .filter((item) => item.enabled)
    .sort((left, right) => left.startTime.localeCompare(right.startTime));
  for (let index = 1; index < enabled.length; index++) {
    if (enabled[index].startTime < enabled[index - 1].endTime) {
      return "启用中的值班时间段不能重叠";
    }
  }
  return "";
}

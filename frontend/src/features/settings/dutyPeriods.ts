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
  const currentPeriod = next[index];
  const targetPeriod = next[target];
  if (!currentPeriod || !targetPeriod) return periods;
  next[index] = targetPeriod;
  next[target] = currentPeriod;
  return next;
}

export function validateDutyPeriods(periods: DutyPeriod[]): string {
  if (!periods.length) return "至少保留一个值班时间段";
  if (periods.length > 12) return "值班时间段最多设置 12 个";
  const keys = new Set<string>();
  for (const [index, item] of periods.entries()) {
    if (!item.startTime || !item.endTime) return `第 ${index + 1} 个时段不完整`;
    if (item.startTime >= item.endTime) {
      return `第 ${index + 1} 个时段的结束时间必须晚于开始时间`;
    }
    const key = `${item.startTime}-${item.endTime}`;
    if (keys.has(key)) return "值班时间段不能重复";
    keys.add(key);
  }
  const enabled = periods
    .filter((item) => item.enabled)
    .sort((left, right) => left.startTime.localeCompare(right.startTime));
  for (let index = 1; index < enabled.length; index++) {
    const current = enabled[index];
    const previous = enabled[index - 1];
    if (current && previous && current.startTime < previous.endTime) {
      return "启用中的值班时间段不能重叠";
    }
  }
  return "";
}

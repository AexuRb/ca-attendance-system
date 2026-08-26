export interface TrainingMonthRange {
  from: string;
  to: string;
}

export function currentTrainingMonth(now = new Date()): TrainingMonthRange {
  return rangeForMonth(now.getFullYear(), now.getMonth());
}

export function shiftTrainingMonth(
  value: string,
  step: number,
): TrainingMonthRange {
  const parsed = parseDate(value);
  const base = parsed || new Date();
  return rangeForMonth(base.getFullYear(), base.getMonth() + step);
}

export function trainingRangeLabel(from: string, to: string) {
  const start = parseDate(from);
  const end = parseDate(to);
  if (!start || !end) return "培训场次";
  if (
    start.getFullYear() === end.getFullYear() &&
    start.getMonth() === end.getMonth()
  ) {
    return `${start.getFullYear()}年${start.getMonth() + 1}月`;
  }
  return `${readableDate(from)} 至 ${readableDate(to)}`;
}

function rangeForMonth(year: number, zeroBasedMonth: number): TrainingMonthRange {
  const first = new Date(year, zeroBasedMonth, 1);
  const last = new Date(year, zeroBasedMonth + 1, 0);
  return { from: localDate(first), to: localDate(last) };
}

function parseDate(value: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return null;
  const [year = 0, month = 0, day = 0] = value.split("-").map(Number);
  const parsed = new Date(year, month - 1, day);
  return parsed.getFullYear() === year &&
    parsed.getMonth() === month - 1 &&
    parsed.getDate() === day
    ? parsed
    : null;
}

function localDate(value: Date) {
  return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, "0")}-${String(value.getDate()).padStart(2, "0")}`;
}

function readableDate(value: string) {
  const [, month = "", day = ""] = value.split("-");
  return `${Number(month)}月${Number(day)}日`;
}

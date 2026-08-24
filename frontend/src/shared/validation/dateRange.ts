export function dateRangeError(from?: string, to?: string) {
  if (from && to && from > to) return "开始日期不能晚于结束日期";
  return "";
}

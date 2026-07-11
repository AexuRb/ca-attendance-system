export function normalizeDutyPeriods(items) {
  return (items || [])
    .map((item, index) => ({
      sortOrder: Number(item.sortOrder ?? index),
      startTime: shortTime(item.startTime),
      endTime: shortTime(item.endTime)
    }))
    .filter(item => item.startTime && item.endTime)
    .sort((a, b) => timeToMinutes(a.startTime) - timeToMinutes(b.startTime) || timeToMinutes(a.endTime) - timeToMinutes(b.endTime))
}

export function parseDutyPeriodDrafts(drafts) {
  const periods = []
  for (const [index, draft] of (drafts || []).entries()) {
    const startTime = shortTime(draft.startTime)
    const endTime = shortTime(draft.endTime)
    if (!startTime && !endTime) continue
    if (!startTime || !endTime) return { periods: null, error: `第 ${index + 1} 个时间段未填写完整` }
    const start = timeToMinutes(startTime)
    const end = timeToMinutes(endTime)
    if (start == null || end == null) return { periods: null, error: `第 ${index + 1} 个时间段格式不正确` }
    if (end <= start) return { periods: null, error: `第 ${index + 1} 个时间段结束时间必须晚于开始时间` }
    periods.push({ startTime, endTime })
  }
  if (periods.length === 0) return { periods: null, error: '请至少填写一个值班时间段' }
  return { periods, error: '' }
}

export function nextPeriodEnd(startTime) {
  const start = timeToMinutes(startTime)
  if (start == null) return ''
  const end = Math.min(start + 120, 23 * 60 + 59)
  return `${String(Math.floor(end / 60)).padStart(2, '0')}:${String(end % 60).padStart(2, '0')}`
}

export function timeToMinutes(value) {
  if (!value) return null
  const [hour, minute] = String(value).split(':').map(part => Number(part))
  if (!Number.isFinite(hour) || !Number.isFinite(minute)) return null
  return hour * 60 + minute
}

export function shortTime(value) {
  return value ? String(value).slice(0, 5) : ''
}

export function resolveStatsRange(preset, now = new Date()) {
  const to = new Date(now)
  const from = new Date(now)

  if (preset === 'week') {
    const weekday = now.getDay() || 7
    from.setDate(now.getDate() - weekday + 1)
  } else if (preset === 'month') {
    from.setDate(1)
  } else if (preset === 'schoolYear') {
    const startYear = now.getMonth() >= 8 ? now.getFullYear() : now.getFullYear() - 1
    from.setFullYear(startYear, 8, 1)
  }

  return { from: formatLocalDate(from), to: formatLocalDate(to) }
}

export function formatLocalDate(value) {
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

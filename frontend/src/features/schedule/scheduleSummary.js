import { normalizeDutyPeriods, shortTime, timeToMinutes } from './dutyPeriods.js'

export function buildPeriodSummary(slots, configuredPeriods, now = new Date()) {
  const periods = normalizeDutyPeriods(configuredPeriods)
  const groups = slotsByAssignedPeriod(slots, periods)
  return periods.map(period => {
    const key = periodKey(period)
    const people = assigneePeopleForSlots(groups.get(key) || [])
    return {
      key,
      startTime: shortTime(period.startTime),
      endTime: shortTime(period.endTime),
      timeText: periodTime(period),
      count: people.length,
      people,
      active: isCurrentPeriod(period, now),
      missing: people.length === 0
    }
  })
}

export function buildWeekScheduleSummary({
  weekSchedule,
  weekdays,
  periods,
  todayWeekday,
  now = new Date()
}) {
  return (weekdays || []).map(day => {
    const weekday = Number(day.weekday)
    const daySlots = (weekSchedule || []).filter(slot => Number(slot.weekday) === weekday)
    const dayPeriods = buildPeriodSummary(daySlots, periods, now)
    return {
      weekday,
      name: day.name,
      count: dayPeriods.filter(period => period.count > 0).length,
      isToday: weekday === Number(todayWeekday)
    }
  })
}

export function shortWeekdayName(value) {
  const text = String(value || '')
  return text.startsWith('星期') ? text.replace('星期', '周') : text || '周?'
}

function slotsByAssignedPeriod(slots, periods) {
  const groups = new Map(periods.map(period => [periodKey(period), []]))
  for (const slot of slots || []) {
    const key = assignedPeriodKey(slot, periods)
    if (key && groups.has(key)) groups.get(key).push(slot)
  }
  return groups
}

function assigneePeopleForSlots(slots) {
  const people = new Map()
  for (const slot of slots || []) {
    const assignees = Array.isArray(slot.assignees) && slot.assignees.length ? slot.assignees : [slot]
    for (const person of assignees) {
      if (!person?.name) continue
      const key = person.studentNo || person.name
      if (!people.has(key)) {
        people.set(key, { key, name: person.name })
      }
    }
  }
  return Array.from(people.values())
}

function assignedPeriodKey(slot, periods) {
  for (const period of periods) {
    const key = periodKey(period)
    if (periodKey(slot) === key) return key
  }
  return null
}

function isCurrentPeriod(period, now) {
  const start = timeToMinutes(period.startTime)
  const end = timeToMinutes(period.endTime)
  if (start == null || end == null) return false
  const current = now.getHours() * 60 + now.getMinutes()
  return current >= start && current < end
}

function periodTime(period) {
  const start = shortTime(period.startTime)
  const end = shortTime(period.endTime)
  return start && end ? `${start}-${end}` : start || end || '全天'
}

function periodKey(period) {
  return `${shortTime(period.startTime)}-${shortTime(period.endTime)}`
}

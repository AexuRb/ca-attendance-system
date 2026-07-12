import { describe, expect, it } from 'vitest'
import { buildPeriodSummary, buildWeekScheduleSummary, shortWeekdayName } from './scheduleSummary.js'

const periods = [
  { startTime: '14:00', endTime: '16:00' },
  { startTime: '16:00', endTime: '18:00' }
]

describe('schedule summary', () => {
  it('groups assignees only into configured periods and marks empty periods', () => {
    const summary = buildPeriodSummary([
      { startTime: '14:00', endTime: '16:00', studentNo: '1', name: '部长甲' },
      { startTime: '14:00', endTime: '16:00', studentNo: '2', name: '部长乙' },
      { startTime: '15:00', endTime: '17:00', studentNo: '3', name: '未配置时段' }
    ], periods, new Date('2026-07-12T14:30:00'))

    expect(summary).toMatchObject([
      { key: '14:00-16:00', count: 2, active: true, missing: false },
      { key: '16:00-18:00', count: 0, active: false, missing: true }
    ])
    expect(summary[0].people.map(person => person.name)).toEqual(['部长甲', '部长乙'])
  })

  it('builds the compact week counts from the same configured periods', () => {
    expect(buildWeekScheduleSummary({
      weekSchedule: [{ weekday: 1, startTime: '14:00', endTime: '16:00', studentNo: '1', name: '部长甲' }],
      weekdays: [{ weekday: 1, name: '周一' }, { weekday: 2, name: '周二' }],
      periods,
      todayWeekday: 2,
      now: new Date('2026-07-12T12:00:00')
    })).toEqual([
      { weekday: 1, name: '周一', count: 1, isToday: false },
      { weekday: 2, name: '周二', count: 0, isToday: true }
    ])
    expect(shortWeekdayName('星期三')).toBe('周三')
  })
})

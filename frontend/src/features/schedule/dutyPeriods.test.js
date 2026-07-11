import { describe, expect, it } from 'vitest'
import { nextPeriodEnd, normalizeDutyPeriods, parseDutyPeriodDrafts } from './dutyPeriods.js'

describe('duty period helpers', () => {
  it('normalizes and sorts saved periods', () => {
    expect(normalizeDutyPeriods([
      { id: 2, startTime: '16:00:00', endTime: '18:00:00' },
      { id: 1, startTime: '14:00', endTime: '16:00' }
    ])).toEqual([
      { sortOrder: 1, startTime: '14:00', endTime: '16:00' },
      { sortOrder: 0, startTime: '16:00', endTime: '18:00' }
    ])
  })

  it('suggests a two-hour end without crossing midnight', () => {
    expect(nextPeriodEnd('14:00')).toBe('16:00')
    expect(nextPeriodEnd('23:00')).toBe('23:59')
  })

  it('validates editable periods with actionable errors', () => {
    expect(parseDutyPeriodDrafts([{ startTime: '14:00', endTime: '16:00' }])).toEqual({
      periods: [{ startTime: '14:00', endTime: '16:00' }],
      error: ''
    })
    expect(parseDutyPeriodDrafts([{ startTime: '16:00', endTime: '14:00' }]).error).toBe('第 1 个时间段结束时间必须晚于开始时间')
    expect(parseDutyPeriodDrafts([]).error).toBe('请至少填写一个值班时间段')
  })
})

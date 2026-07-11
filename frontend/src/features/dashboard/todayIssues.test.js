import { describe, expect, it } from 'vitest'
import { buildTodayIssues } from './todayIssues.js'

describe('today issue summary', () => {
  it('keeps only work that needs attention', () => {
    expect(buildTodayIssues({
      pendingCount: 2,
      openCount: 0,
      missingScheduleCount: 1,
      ongoingRepairCount: 3
    })).toMatchObject([
      { id: 'pending', count: 2, unit: '条', tab: 'reviews' },
      { id: 'schedule', count: 1, unit: '个时段', tab: 'schedules' },
      { id: 'repairs', count: 3, unit: '件', tab: 'repairs' }
    ])
  })

  it('returns an empty list when the day has no outstanding work', () => {
    expect(buildTodayIssues()).toEqual([])
    expect(buildTodayIssues({ pendingCount: -1, openCount: 'invalid' })).toEqual([])
  })
})

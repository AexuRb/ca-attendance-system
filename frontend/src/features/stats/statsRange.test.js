import { describe, expect, it } from 'vitest'
import { resolveStatsRange } from './statsRange.js'

describe('resolveStatsRange', () => {
  it('starts a week on Monday', () => {
    expect(resolveStatsRange('week', new Date(2026, 6, 12))).toEqual({
      from: '2026-07-06',
      to: '2026-07-12'
    })
  })

  it('starts a month on its first day', () => {
    expect(resolveStatsRange('month', new Date(2026, 6, 12))).toEqual({
      from: '2026-07-01',
      to: '2026-07-12'
    })
  })

  it('uses September 1 as the school-year boundary', () => {
    expect(resolveStatsRange('schoolYear', new Date(2026, 0, 15))).toEqual({
      from: '2025-09-01',
      to: '2026-01-15'
    })
    expect(resolveStatsRange('schoolYear', new Date(2026, 8, 1))).toEqual({
      from: '2026-09-01',
      to: '2026-09-01'
    })
  })
})

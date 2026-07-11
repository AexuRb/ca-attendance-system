import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  createKioskRequestId,
  createKioskResetTimer,
  maskStudentNumber
} from './kioskFlow.js'

describe('kiosk flow helpers', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('shows only the last four digits of a student number', () => {
    expect(maskStudentNumber('1004231224')).toBe('1224')
    expect(maskStudentNumber('123')).toBe('123')
    expect(maskStudentNumber('')).toBe('----')
  })

  it('creates request ids suitable for idempotent submission', () => {
    expect(createKioskRequestId(() => 'fixed-uuid-value')).toBe('fixed-uuid-value')
  })

  it('resets the success state after four seconds and reports the countdown', () => {
    vi.useFakeTimers()
    const ticks = []
    const onReset = vi.fn()
    const timer = createKioskResetTimer({ onTick: value => ticks.push(value), onReset })

    timer.start()
    vi.advanceTimersByTime(3_000)
    expect(ticks).toEqual([4, 3, 2, 1])
    expect(onReset).not.toHaveBeenCalled()

    vi.advanceTimersByTime(1_000)
    expect(onReset).toHaveBeenCalledOnce()
  })

  it('can cancel an automatic reset', () => {
    vi.useFakeTimers()
    const onReset = vi.fn()
    const timer = createKioskResetTimer({ onTick: () => {}, onReset })

    timer.start()
    timer.cancel()
    vi.advanceTimersByTime(5_000)

    expect(onReset).not.toHaveBeenCalled()
  })
})

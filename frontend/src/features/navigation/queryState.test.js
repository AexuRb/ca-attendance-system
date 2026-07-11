import { describe, expect, it } from 'vitest'
import {
  compactQuery,
  queryDate,
  queryOneOf,
  queryPositiveInt,
  queryText
} from './queryState.js'

describe('route query state helpers', () => {
  it('normalizes text and ignores duplicate query values', () => {
    expect(queryText({ q: ['  chen  ', 'ignored'] }, 'q')).toBe('chen')
  })

  it('rejects invalid enum, date, and pagination values', () => {
    expect(queryOneOf({ role: 'ROOT' }, 'role', ['MEMBER', 'ADMIN'])).toBe('')
    expect(queryDate({ from: 'yesterday' }, 'from', '2026-01-01')).toBe('2026-01-01')
    expect(queryPositiveInt({ page: '-4' }, 'page', 1)).toBe(1)
    expect(queryPositiveInt({ size: '30' }, 'size', 20, [10, 20, 50])).toBe(20)
  })

  it('removes empty values before writing a URL', () => {
    expect(compactQuery({ q: '', page: 2, from: '2026-01-01', role: null })).toEqual({
      page: 2,
      from: '2026-01-01'
    })
  })
})

import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from './api.js'

describe('api network errors', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('marks a failed connection as retriable without losing the original cause', async () => {
    const cause = new TypeError('Failed to fetch')
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(cause))

    await expect(api('/api/health')).rejects.toMatchObject({
      name: 'ApiNetworkError',
      isNetworkError: true,
      cause
    })
  })
})

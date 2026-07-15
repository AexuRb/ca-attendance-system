import { describe, expect, it } from 'vitest'
import { inferredAccessContext, normalizeAccessContext, routeForAccessMode } from './accessMode.js'

describe('remote access mode', () => {
  it('keeps the default remote port restricted when access discovery fails', () => {
    expect(inferredAccessContext({ hostname: '127.0.0.1', port: '8081' })).toEqual({
      mode: 'REMOTE_ADMIN',
      kioskAvailable: false,
      allowedRemoteRoles: ['PRESIDENT', 'ADMIN']
    })
  })

  it('redirects remote kiosk routes to login without affecting local kiosk access', () => {
    const remote = normalizeAccessContext({ mode: 'REMOTE_ADMIN', kioskAvailable: false })
    const local = normalizeAccessContext({ mode: 'LOCAL', kioskAvailable: true })

    expect(routeForAccessMode(remote, 'kiosk', false)).toBe('/login')
    expect(routeForAccessMode(local, 'kiosk', false)).toBe('')
  })

  it('keeps an authenticated remote manager in the backend', () => {
    const remote = normalizeAccessContext({ mode: 'REMOTE_ADMIN', kioskAvailable: false })

    expect(routeForAccessMode(remote, 'kiosk', true)).toBe('/admin/today')
  })
})

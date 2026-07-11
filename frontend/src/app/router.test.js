import { describe, expect, it } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import {
  adminModuleLocation,
  createAppRouter,
  tabFromRoute
} from './router.js'

describe('application router', () => {
  it('opens the public kiosk for the root URL', async () => {
    const router = createAppRouter(createMemoryHistory())

    await router.push('/')
    await router.isReady()

    expect(router.currentRoute.value.fullPath).toBe('/kiosk')
  })

  it('keeps module filters and pagination in the URL', async () => {
    const router = createAppRouter(createMemoryHistory())

    await router.push('/admin/members?keyword=chen&page=2')
    await router.isReady()

    expect(tabFromRoute(router.currentRoute.value)).toBe('members')
    expect(router.currentRoute.value.query).toEqual({ keyword: 'chen', page: '2' })
  })

  it('maps the overview tab to a readable today URL', () => {
    expect(adminModuleLocation('overview')).toEqual({
      name: 'admin-module',
      params: { module: 'today' },
      query: {}
    })
  })

  it('redirects unknown admin modules to today', async () => {
    const router = createAppRouter(createMemoryHistory())

    await router.push('/admin/not-a-module')
    await router.isReady()

    expect(router.currentRoute.value.path).toBe('/admin/today')
  })
})

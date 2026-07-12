import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api, put } from '../api.js'
import ProfilePanel from './ProfilePanel.vue'

vi.mock('../api.js', () => ({
  api: vi.fn(),
  put: vi.fn()
}))

function testRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/admin/:module', name: 'admin-module', component: { template: '<div />' } }]
  })
}

describe('ProfilePanel', () => {
  beforeEach(() => {
    api.mockImplementation(async path => {
      if (path === '/api/auth/me') return { phone: '13800000000', major: '计算机学院', grade: '2025级', qq: '12345' }
      if (path.startsWith('/api/attendance/me')) return []
      if (path.startsWith('/api/trainings/me/hours')) return { trainingHours: 0, trainingCount: 0 }
      throw new Error(`unexpected path: ${path}`)
    })
    put.mockResolvedValue({})
  })

  it('owns profile data loading and keeps the same card order for every role', async () => {
    const router = testRouter()
    await router.push('/admin/profile')
    await router.isReady()
    const wrapper = mount(ProfilePanel, { global: { plugins: [router] } })
    await flushPromises()

    expect(api).toHaveBeenCalledWith('/api/auth/me')
    expect(wrapper.get('#profilePhone').element.value).toBe('13800000000')
    const cards = wrapper.get('.profile-grid').element.children
    expect(cards[0].classList.contains('profile-card')).toBe(true)
    expect(cards[1].classList.contains('records-card')).toBe(true)
  })
})

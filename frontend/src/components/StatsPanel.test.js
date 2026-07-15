import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'
import { describe, expect, it, vi } from 'vitest'
import { api } from '../api.js'
import { createAppRouter } from '../app/router.js'
import StatsPanel from './StatsPanel.vue'

vi.mock('../api.js', () => ({ api: vi.fn() }))

describe('StatsPanel export permissions', () => {
  it('shows the standard statistics export to ministers', async () => {
    api.mockResolvedValue([])
    const router = createAppRouter(createMemoryHistory())
    await router.push('/admin/stats')
    await router.isReady()

    const wrapper = mount(StatsPanel, {
      props: { currentUser: { role: 'MINISTER' } },
      global: { plugins: [router] }
    })
    await flushPromises()

    expect(wrapper.get('[data-action="export-stats"]').text()).toContain('导出')
  })
})

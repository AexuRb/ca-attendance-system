import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api.js'
import DataCenterPanel from './DataCenterPanel.vue'

vi.mock('../api.js', () => ({
  api: vi.fn(),
  post: vi.fn(),
  del: vi.fn()
}))

describe('DataCenterPanel', () => {
  beforeEach(() => {
    api.mockImplementation(async path => {
      if (path === '/api/exports/options') return { sources: [] }
      if (path === '/api/maintenance/summary') {
        return {
          datasets: [{ key: 'members', label: '成员', total: 12, detail: '当前成员总数' }],
          backups: { count: 1, totalSize: 128 }
        }
      }
      if (path === '/api/maintenance/backups') {
        return [{ filename: 'backup_test.zip', createdAt: '2026-07-12T09:00:00', size: 128 }]
      }
      throw new Error(`unexpected path: ${path}`)
    })
  })

  it('owns maintenance summary and backup loading', async () => {
    const wrapper = mount(DataCenterPanel, {
      props: { currentUser: { role: 'ADMIN' } }
    })
    await flushPromises()

    expect(api).toHaveBeenCalledWith('/api/maintenance/summary')
    expect(api).toHaveBeenCalledWith('/api/maintenance/backups')
    expect(wrapper.text()).toContain('当前成员总数')
    expect(wrapper.text()).toContain('1')
  })
})

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api.js'
import RepairPanel from './RepairPanel.vue'

vi.mock('../api.js', () => ({
  api: vi.fn(),
  del: vi.fn(),
  post: vi.fn(),
  put: vi.fn()
}))

const repair = {
  id: 7,
  caseNo: 'JXWX-2026-007',
  agreementType: 'PERSONAL_DEVICE',
  ownerName: '测试送修人',
  ownerPhone: '13800000000',
  deviceType: '笔记本电脑',
  deviceBrand: 'Test',
  deviceModel: 'Notebook',
  status: 'REPAIRING',
  receivedAt: '2026-07-12T10:00:00',
  faultDescription: '无法启动'
}

describe('RepairPanel agreement preview', () => {
  beforeEach(() => {
    api.mockImplementation(async path => {
      if (path.startsWith('/api/repairs?')) return [repair]
      if (path === '/api/repairs/7/agreement') {
        return new Blob([
          '<!doctype html><html><head><title>维修协议预览</title></head><body><main class="paper">协议正文</main><button class="print" onclick="window.print()">打印</button></body></html>'
        ], { type: 'text/html' })
      }
      throw new Error(`unexpected path: ${path}`)
    })
  })

  it('opens the agreement inside the application without creating a browser window', async () => {
    const openSpy = vi.spyOn(window, 'open')
    const wrapper = mount(RepairPanel, {
      props: { currentUser: { role: 'MINISTER', name: '测试部长' } },
      global: { stubs: { Teleport: true } }
    })
    await flushPromises()

    await wrapper.get('[data-action="preview-agreement"]').trigger('click')
    await flushPromises()

    expect(api).toHaveBeenCalledWith('/api/repairs/7/agreement')
    expect(wrapper.get('[role="dialog"]').text()).toContain('JXWX-2026-007')
    expect(wrapper.get('iframe').attributes('srcdoc')).toContain('协议正文')
    expect(wrapper.get('iframe').attributes('srcdoc')).not.toContain('class="print"')
    expect(openSpy).not.toHaveBeenCalled()
    openSpy.mockRestore()
  })
})

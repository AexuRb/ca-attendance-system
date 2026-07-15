import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api, del, put } from '../api.js'
import { createAppRouter } from '../app/router.js'
import { requestTextInput } from '../shared/confirm.js'
import AttendanceRecordsPanel from './AttendanceRecordsPanel.vue'

vi.mock('../api.js', () => ({
  api: vi.fn(),
  del: vi.fn(),
  post: vi.fn(),
  put: vi.fn()
}))

vi.mock('../shared/confirm.js', () => ({
  requestConfirmation: vi.fn(),
  requestTextInput: vi.fn()
}))

describe('AttendanceRecordsPanel minister permissions', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.mockResolvedValue([
      attendanceRecord(1, 'MEMBER', new Date()),
      attendanceRecord(2, 'MINISTER', new Date()),
      attendanceRecord(3, 'PRESIDENT', new Date()),
      attendanceRecord(4, 'MEMBER', addDays(new Date(), -7))
    ])
  })

  it('allows ministers to edit and delete only current-week member and minister records', async () => {
    const wrapper = await mountPanel({ role: 'MINISTER', studentNo: 'minister', name: '测试部长' })

    expect(wrapper.text()).not.toContain('新增记录')
    expect(wrapper.findAll('[data-action="edit-record"]')).toHaveLength(2)
    expect(wrapper.findAll('[data-action="delete-record"]')).toHaveLength(2)

    await wrapper.find('[data-action="edit-record"]').trigger('click')
    await wrapper.get('#recordEditReason').setValue('修正签到时间')
    await wrapper.get('.record-edit-form').trigger('submit')
    await flushPromises()

    expect(put).toHaveBeenCalledWith('/api/attendance/1/manual', expect.objectContaining({
      reason: '修正签到时间'
    }))

    requestTextInput.mockResolvedValue('重复签到')
    await wrapper.find('[data-action="delete-record"]').trigger('click')
    await flushPromises()

    expect(del).toHaveBeenCalledWith('/api/attendance/1?reason=%E9%87%8D%E5%A4%8D%E7%AD%BE%E5%88%B0')
    expect(wrapper.find('.record-edit-panel').exists()).toBe(false)
  })
})

async function mountPanel(currentUser) {
  const router = createAppRouter(createMemoryHistory())
  await router.push('/admin/records')
  await router.isReady()
  const wrapper = mount(AttendanceRecordsPanel, {
    props: { currentUser },
    global: { plugins: [router] }
  })
  await flushPromises()
  return wrapper
}

function attendanceRecord(id, userRole, date) {
  const dutyDate = formatDate(date)
  return {
    id,
    userId: id,
    userRole,
    studentNo: `2026000${id}`,
    name: `测试成员${id}`,
    dutyDate,
    checkInTime: `${dutyDate}T14:00:00`,
    checkOutTime: `${dutyDate}T16:00:00`,
    checkInStatus: 'AUTO_APPROVED',
    checkOutStatus: 'AUTO_APPROVED',
    effectiveStatus: 'VALID',
    validHours: 2,
    source: 'PUBLIC'
  }
}

function addDays(date, amount) {
  const result = new Date(date)
  result.setDate(result.getDate() + amount)
  return result
}

function formatDate(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

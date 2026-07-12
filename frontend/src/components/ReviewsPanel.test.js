import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api, post } from '../api.js'
import ReviewsPanel from './ReviewsPanel.vue'

vi.mock('../api.js', () => ({
  api: vi.fn(),
  post: vi.fn()
}))

describe('ReviewsPanel', () => {
  beforeEach(() => {
    api.mockResolvedValue([{
      id: 7,
      name: '测试成员',
      studentNo: '1000000001',
      dutyDate: '2026-07-12',
      checkInStatus: 'PENDING',
      checkOutStatus: 'NOT_SUBMITTED'
    }])
    post.mockResolvedValue({})
  })

  it('owns pending-record loading and single review actions', async () => {
    const wrapper = mount(ReviewsPanel)
    await flushPromises()

    await wrapper.get('[data-action="refresh-reviews"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-action="approve-check-in"]').trigger('click')
    await flushPromises()

    expect(api).toHaveBeenCalledWith('/api/attendance/reviews/pending')
    expect(post).toHaveBeenCalledWith('/api/attendance/7/review', {
      part: 'CHECK_IN',
      action: 'APPROVE',
      reason: '审核通过'
    })
    expect(wrapper.emitted('updated')).toHaveLength(1)
  })
})

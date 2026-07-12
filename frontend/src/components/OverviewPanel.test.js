import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import OverviewPanel from './OverviewPanel.vue'

describe('OverviewPanel', () => {
  it('renders a role-aware clear detail without mentioning unavailable scheduling work', () => {
    const wrapper = mount(OverviewPanel, {
      props: {
        todayIssues: [],
        todayPeriodSummary: [],
        todayRecords: [],
        emptyBoardText: '今日暂无部长排班',
        clearDetail: '审核、签退与维修状态均正常',
        canManageSchedules: false,
        canOpenRecords: false
      }
    })

    expect(wrapper.text()).toContain('审核、签退与维修状态均正常')
    expect(wrapper.text()).not.toContain('排班、审核、签退与维修状态均正常')
  })

  it('emits navigation only for actionable work', async () => {
    const wrapper = mount(OverviewPanel, {
      props: {
        todayIssues: [{
          id: 'repairs',
          title: '维修进行中',
          detail: '维修事务仍在处理中',
          count: 2,
          unit: '件',
          tab: 'repairs',
          tone: 'teal',
          actionable: true
        }],
        todayPeriodSummary: [],
        todayRecords: [],
        emptyBoardText: '今日暂无部长排班',
        clearDetail: '审核、签退与维修状态均正常',
        canManageSchedules: false,
        canOpenRecords: false
      }
    })

    await wrapper.get('.today-issue-item').trigger('click')
    expect(wrapper.emitted('select-tab')).toEqual([['repairs']])
  })
})

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import KioskView from './KioskView.vue'

function kioskProps() {
  return {
    healthOk: true,
    dateText: '7月12日 星期日',
    timeText: '09:30:00',
    currentPeriodText: '当前时段 09:00-10:00',
    todayPeriodSummary: [{
      key: '09:00-10:00',
      startTime: '09:00',
      endTime: '10:00',
      timeText: '09:00-10:00',
      count: 1,
      people: [{ key: '1', name: '测试部长' }],
      active: true,
      missing: false
    }],
    emptyBoardText: '今日暂无部长排班',
    weekSummary: [{ weekday: 7, name: '周日', count: 1, isToday: true }]
  }
}

describe('KioskView', () => {
  it('renders shared schedule data and emits shell navigation actions', async () => {
    const wrapper = mount(KioskView, { props: kioskProps() })

    expect(wrapper.text()).toContain('测试部长')
    expect(wrapper.text()).toContain('09:00-10:00')
    await wrapper.get('.kiosk-admin-button').trigger('click')
    await wrapper.get('[aria-label="刷新排班"]').trigger('click')
    expect(wrapper.emitted('open-dashboard')).toHaveLength(1)
    expect(wrapper.emitted('refresh-schedules')).toHaveLength(1)
  })
})

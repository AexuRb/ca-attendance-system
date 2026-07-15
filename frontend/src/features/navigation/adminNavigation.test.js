import { describe, expect, it } from 'vitest'
import { tabsForRole } from './adminNavigation.js'

describe('admin navigation by role', () => {
  it('keeps sensitive modules hidden while allowing ministers to manage records and export statistics', () => {
    expect(tabsForRole('MEMBER').map(tab => tab.id)).toEqual(['profile'])
    const ministerTabs = tabsForRole('MINISTER').map(tab => tab.id)
    expect(ministerTabs).toContain('records')
    expect(ministerTabs).toContain('stats')
    expect(ministerTabs).not.toContain('members')
    expect(ministerTabs).not.toContain('trainings')
    expect(ministerTabs).not.toContain('schedules')
    expect(ministerTabs).not.toContain('data')
    expect(tabsForRole('PRESIDENT').map(tab => tab.id)).toContain('schedules')
  })
})

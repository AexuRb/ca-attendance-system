import { describe, expect, it } from 'vitest'
import { tabsForRole } from './adminNavigation.js'

describe('admin navigation by role', () => {
  it('keeps members on profile and excludes scheduling from ministers', () => {
    expect(tabsForRole('MEMBER').map(tab => tab.id)).toEqual(['profile'])
    expect(tabsForRole('MINISTER').map(tab => tab.id)).not.toContain('schedules')
    expect(tabsForRole('PRESIDENT').map(tab => tab.id)).toContain('schedules')
  })
})

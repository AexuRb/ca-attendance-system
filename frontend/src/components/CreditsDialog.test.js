import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import CreditsDialog from './CreditsDialog.vue'

describe('CreditsDialog', () => {
  it('renders the configured contributor and closes from Escape', async () => {
    const wrapper = mount(CreditsDialog, {
      props: { open: true },
      global: { stubs: { Teleport: true } }
    })

    expect(wrapper.get('[role="dialog"]').attributes('aria-modal')).toBe('true')
    expect(wrapper.text()).toContain('2025 会长')
    expect(wrapper.text()).toContain('陈禹杭')
    expect(wrapper.text()).not.toContain('AexuRb')
    expect(wrapper.text()).not.toContain('Codex')

    await wrapper.get('[role="dialog"]').trigger('keydown', { key: 'Escape' })
    expect(wrapper.emitted('close')).toHaveLength(1)
  })
})

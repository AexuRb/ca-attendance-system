import { nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import ActionConfirmDialog from './ActionConfirmDialog.vue'
import {
  requestConfirmation,
  requestTextInput,
  resetConfirmation
} from './confirm.js'

describe('ActionConfirmDialog', () => {
  beforeEach(resetConfirmation)

  it('renders an accessible modal and confirms the action', async () => {
    const wrapper = mount(ActionConfirmDialog, { attachTo: document.body })
    const result = requestConfirmation({
      title: '删除成员',
      message: '该成员将被永久删除。',
      confirmLabel: '删除'
    })
    await nextTick()

    const dialog = wrapper.get('[role="dialog"]')
    expect(dialog.attributes('aria-modal')).toBe('true')
    expect(dialog.text()).toContain('该成员将被永久删除。')

    await wrapper.get('[data-action="confirm"]').trigger('click')
    await expect(result).resolves.toBe(true)
    wrapper.unmount()
  })

  it('keeps confirmation disabled until the required phrase matches', async () => {
    const wrapper = mount(ActionConfirmDialog)
    const result = requestConfirmation({
      title: '清空日志',
      requiredText: '清空日志'
    })
    await nextTick()

    const confirmButton = wrapper.get('[data-action="confirm"]')
    expect(confirmButton.attributes()).toHaveProperty('disabled')

    await wrapper.get('input').setValue('清空日志')
    expect(confirmButton.attributes()).not.toHaveProperty('disabled')

    await confirmButton.trigger('click')
    await expect(result).resolves.toBe(true)
  })

  it('cancels from the Escape key', async () => {
    const wrapper = mount(ActionConfirmDialog)
    const result = requestConfirmation({ title: '恢复数据' })
    await nextTick()

    await wrapper.get('[role="dialog"]').trigger('keydown', { key: 'Escape' })

    await expect(result).resolves.toBe(false)
  })

  it('collects required text input for a prompt action', async () => {
    const wrapper = mount(ActionConfirmDialog)
    const result = requestTextInput({
      title: '驳回签到',
      inputLabel: '驳回原因',
      confirmLabel: '确认驳回'
    })
    await nextTick()

    const input = wrapper.get('input')
    expect(input.attributes('aria-required')).toBe('true')
    await input.setValue('时间填写错误')
    await wrapper.get('[data-action="confirm"]').trigger('click')

    await expect(result).resolves.toBe('时间填写错误')
  })
})

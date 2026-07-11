import { beforeEach, describe, expect, it } from 'vitest'
import {
  acceptConfirmation,
  cancelConfirmation,
  confirmationState,
  requestConfirmation,
  requestTextInput,
  resetConfirmation
} from './confirm.js'

describe('confirmation service', () => {
  beforeEach(resetConfirmation)

  it('resolves false when the dialog is cancelled', async () => {
    const result = requestConfirmation({
      title: '删除记录',
      message: '删除后无法恢复。'
    })

    expect(confirmationState.open).toBe(true)
    expect(confirmationState.title).toBe('删除记录')

    cancelConfirmation()

    await expect(result).resolves.toBe(false)
    expect(confirmationState.open).toBe(false)
  })

  it('requires an exact confirmation phrase for dangerous actions', async () => {
    const result = requestConfirmation({
      title: '清空日志',
      message: '全部日志将被清除。',
      requiredText: '清空日志'
    })

    expect(acceptConfirmation('清空')).toBe(false)
    expect(confirmationState.open).toBe(true)
    expect(acceptConfirmation('清空日志')).toBe(true)

    await expect(result).resolves.toBe(true)
  })

  it('cancels an older request before opening a newer one', async () => {
    const first = requestConfirmation({ title: '第一项' })
    const second = requestConfirmation({ title: '第二项' })

    await expect(first).resolves.toBe(false)
    expect(confirmationState.title).toBe('第二项')

    cancelConfirmation()
    await expect(second).resolves.toBe(false)
  })

  it('returns trimmed text from an input request', async () => {
    const result = requestTextInput({
      title: '驳回签到',
      inputLabel: '驳回原因'
    })

    expect(acceptConfirmation('   ')).toBe(false)
    expect(acceptConfirmation('  学号填写错误  ')).toBe(true)

    await expect(result).resolves.toBe('学号填写错误')
  })
})

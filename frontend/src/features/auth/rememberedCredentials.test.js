import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  REMEMBERED_ACCOUNT_KEY,
  hasSecureCredentialStorage,
  loadRememberedLogin,
  persistRememberedLogin
} from './rememberedCredentials.js'

describe('remembered login credentials', () => {
  beforeEach(() => {
    window.localStorage.clear()
    delete window.desktopAPI
  })

  it('uses the desktop encrypted credential bridge when available', async () => {
    const loadRememberedCredentials = vi.fn().mockResolvedValue({
      account: 'test-admin',
      password: 'desktop-secret'
    })
    const saveRememberedCredentials = vi.fn().mockResolvedValue({ saved: true })
    const clearRememberedCredentials = vi.fn().mockResolvedValue({ cleared: true })
    window.desktopAPI = {
      isDesktop: true,
      loadRememberedCredentials,
      saveRememberedCredentials,
      clearRememberedCredentials
    }

    expect(hasSecureCredentialStorage()).toBe(true)
    await expect(loadRememberedLogin()).resolves.toEqual({
      account: 'test-admin',
      password: 'desktop-secret',
      remembersPassword: true,
      mode: 'secure'
    })

    await expect(persistRememberedLogin({
      account: 'test-admin',
      password: 'new-secret',
      remember: true
    })).resolves.toEqual({ mode: 'secure', savedPassword: true })
    expect(saveRememberedCredentials).toHaveBeenCalledWith({
      account: 'test-admin',
      password: 'new-secret'
    })
    expect(window.localStorage.getItem(REMEMBERED_ACCOUNT_KEY)).toBeNull()

    await expect(persistRememberedLogin({
      account: 'test-admin',
      password: 'new-secret',
      remember: false
    })).resolves.toEqual({ mode: 'cleared', savedPassword: false })
    expect(clearRememberedCredentials).toHaveBeenCalledOnce()
  })

  it('remembers only the account in a normal browser', async () => {
    expect(hasSecureCredentialStorage()).toBe(false)

    await expect(persistRememberedLogin({
      account: 'test-admin',
      password: 'must-not-be-stored',
      remember: true
    })).resolves.toEqual({ mode: 'account-only', savedPassword: false })

    expect(window.localStorage.getItem(REMEMBERED_ACCOUNT_KEY)).toBe('test-admin')
    expect(JSON.stringify(window.localStorage)).not.toContain('must-not-be-stored')
    await expect(loadRememberedLogin()).resolves.toEqual({
      account: 'test-admin',
      password: '',
      remembersPassword: false,
      mode: 'account-only'
    })
  })

  it('does not fall back to plaintext password storage when desktop encryption fails', async () => {
    window.desktopAPI = {
      isDesktop: true,
      loadRememberedCredentials: vi.fn().mockResolvedValue(null),
      saveRememberedCredentials: vi.fn().mockRejectedValue(new Error('unavailable')),
      clearRememberedCredentials: vi.fn().mockResolvedValue({ cleared: true })
    }

    await expect(persistRememberedLogin({
      account: 'test-admin',
      password: 'must-stay-private',
      remember: true
    })).resolves.toEqual({ mode: 'failed', savedPassword: false })
    expect(JSON.stringify(window.localStorage)).not.toContain('must-stay-private')
  })
})

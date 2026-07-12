import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AuthView from './AuthView.vue'

function loginProps() {
  return {
    setupRequired: false,
    healthOk: true,
    loginError: false,
    loginVerified: false,
    busy: false,
    setupForm: { account: '', name: '', password: '', confirmPassword: '' },
    loginForm: { studentNo: 'test-admin', password: 'test-password' },
    setupFormReady: false,
    rememberStorageHint: '由 Windows 加密保存在本机',
    passwordForm: { oldPassword: '', newPassword: '', confirmPassword: '' },
    requiredPasswordError: '',
    requiresPasswordChange: false,
    showLoginPassword: false,
    showSetupPassword: false,
    rememberLogin: true
  }
}

describe('AuthView', () => {
  it('renders the remember-password control and emits login', async () => {
    const wrapper = mount(AuthView, { props: loginProps() })

    expect(wrapper.text()).toContain('记住密码')
    expect(wrapper.text()).toContain('由 Windows 加密保存在本机')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('login')).toHaveLength(1)
  })

  it('uses the required password form when the account must change password', async () => {
    const wrapper = mount(AuthView, {
      props: {
        ...loginProps(),
        requiresPasswordChange: true
      }
    })

    expect(wrapper.text()).toContain('设置你的新密码')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('change-required-password')).toHaveLength(1)
  })
})

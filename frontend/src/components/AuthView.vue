<template>
  <div v-if="!requiresPasswordChange" class="login-page">
    <section class="login-access-panel" :class="{ error: loginError, verified: loginVerified, 'setup-mode': setupRequired }">
      <aside class="login-identity-panel">
        <div class="login-identity-logo">
          <img :src="brandLogoUrl" alt="计协会徽" />
        </div>
        <div class="login-identity-copy">
          <p class="eyebrow">Computer Association</p>
          <h1>{{ setupRequired ? '系统首次初始化' : '本地离线后台' }}</h1>
          <span>{{ setupRequired ? '建立本机数据库与首位管理员' : '值班、成员与协会事务管理' }}</span>
        </div>
        <div class="login-identity-status" :class="{ online: healthOk }">
          <i aria-hidden="true"></i>
          <div>
            <span>本机服务</span>
            <strong>{{ healthOk ? '连接正常' : '暂未连接' }}</strong>
          </div>
        </div>
        <span class="login-identity-scan" aria-hidden="true"></span>
      </aside>

      <section class="login-form-panel">
        <Transition name="login-state" mode="out-in">
          <div v-if="loginVerified" key="verified" class="login-verified-state">
            <span><CheckCircle2 :size="48" /></span>
            <p>Access Granted</p>
            <h2>身份验证通过</h2>
            <small>正在进入后台</small>
            <i aria-hidden="true"></i>
          </div>

          <form v-else-if="setupRequired" key="setup" class="setup-form" :aria-busy="busy" @submit.prevent="$emit('initialize')">
            <div class="login-form-heading">
              <p class="eyebrow">First Run Setup</p>
              <h2>创建管理员</h2>
              <span>数据将保存在本机应用根目录</span>
            </div>

            <label for="setupAccount">管理员账号</label>
            <div class="login-field">
              <UserRound :size="19" aria-hidden="true" />
              <input
                id="setupAccount"
                v-model.trim="setupForm.account"
                autocomplete="username"
                maxlength="32"
                placeholder="4-32 位字母、数字、下划线或短横线"
              />
              <span class="login-field-scan" aria-hidden="true"></span>
            </div>

            <label for="setupName">管理员姓名</label>
            <div class="login-field">
              <BadgeCheck :size="19" aria-hidden="true" />
              <input
                id="setupName"
                v-model.trim="setupForm.name"
                autocomplete="name"
                maxlength="64"
                placeholder="输入管理员姓名"
              />
              <span class="login-field-scan" aria-hidden="true"></span>
            </div>

            <label for="setupPassword">设置密码</label>
            <div class="login-field login-password-field">
              <KeyRound :size="19" aria-hidden="true" />
              <input
                id="setupPassword"
                v-model="setupForm.password"
                :type="showSetupPassword ? 'text' : 'password'"
                autocomplete="new-password"
                maxlength="64"
                placeholder="至少 6 位"
              />
              <button
                type="button"
                :title="showSetupPassword ? '隐藏密码' : '显示密码'"
                :aria-label="showSetupPassword ? '隐藏密码' : '显示密码'"
                @click="showSetupPassword = !showSetupPassword"
              >
                <EyeOff v-if="showSetupPassword" :size="18" />
                <Eye v-else :size="18" />
              </button>
              <span class="login-field-scan" aria-hidden="true"></span>
            </div>

            <label for="setupPasswordConfirm">确认密码</label>
            <div class="login-field">
              <ShieldCheck :size="19" aria-hidden="true" />
              <input
                id="setupPasswordConfirm"
                v-model="setupForm.confirmPassword"
                :type="showSetupPassword ? 'text' : 'password'"
                autocomplete="new-password"
                maxlength="64"
                placeholder="再次输入密码"
              />
              <span class="login-field-scan" aria-hidden="true"></span>
            </div>

            <button class="login-submit-button" type="submit" :disabled="busy || !setupFormReady">
              <ShieldCheck :size="19" />
              <span>{{ busy ? '正在初始化' : '完成初始化' }}</span>
              <i v-if="busy" aria-hidden="true"></i>
            </button>
          </form>

          <form v-else key="form" :aria-busy="busy" @submit.prevent="$emit('login')">
            <div class="login-form-heading">
              <p class="eyebrow">Identity Verification</p>
              <h2>后台身份验证</h2>
              <span>使用协会后台账号登录</span>
            </div>

            <label for="loginStudentNo">账号 / 学号</label>
            <div class="login-field">
              <UserRound :size="19" aria-hidden="true" />
              <input
                id="loginStudentNo"
                v-model.trim="loginForm.studentNo"
                autocomplete="username"
                placeholder="输入后台账号或学号"
              />
              <span class="login-field-scan" aria-hidden="true"></span>
            </div>

            <label for="loginPassword">密码</label>
            <div class="login-field login-password-field">
              <KeyRound :size="19" aria-hidden="true" />
              <input
                id="loginPassword"
                v-model="loginForm.password"
                :type="showLoginPassword ? 'text' : 'password'"
                autocomplete="current-password"
                placeholder="输入密码"
              />
              <button
                type="button"
                :title="showLoginPassword ? '隐藏密码' : '显示密码'"
                :aria-label="showLoginPassword ? '隐藏密码' : '显示密码'"
                @click="showLoginPassword = !showLoginPassword"
              >
                <EyeOff v-if="showLoginPassword" :size="18" />
                <Eye v-else :size="18" />
              </button>
              <span class="login-field-scan" aria-hidden="true"></span>
            </div>

            <label class="login-remember-row">
              <input v-model="rememberLogin" type="checkbox" />
              <span>记住密码</span>
              <small>{{ rememberStorageHint }}</small>
            </label>

            <button class="login-submit-button" type="submit" :disabled="busy || !loginForm.studentNo || !loginForm.password">
              <LogIn :size="19" />
              <span>{{ busy ? '正在验证' : '登录后台' }}</span>
              <i v-if="busy" aria-hidden="true"></i>
            </button>
          </form>
        </Transition>
      </section>
    </section>
  </div>

  <div v-else class="required-password-page">
    <section class="required-password-panel" aria-labelledby="requiredPasswordTitle">
      <header>
        <span><KeyRound :size="23" /></span>
        <div>
          <p>首次登录</p>
          <h1 id="requiredPasswordTitle">设置你的新密码</h1>
          <small>当前密码为初始或重置密码，修改后才能进入后台。</small>
        </div>
      </header>
      <form @submit.prevent="$emit('change-required-password')">
        <label for="requiredOldPassword">当前密码</label>
        <input
          id="requiredOldPassword"
          v-model="passwordForm.oldPassword"
          name="currentPassword"
          type="password"
          autocomplete="current-password"
          required
        />
        <label for="requiredNewPassword">新密码</label>
        <input
          id="requiredNewPassword"
          v-model="passwordForm.newPassword"
          name="newPassword"
          type="password"
          autocomplete="new-password"
          minlength="6"
          maxlength="64"
          required
        />
        <label for="requiredConfirmPassword">确认新密码</label>
        <input
          id="requiredConfirmPassword"
          v-model="passwordForm.confirmPassword"
          name="confirmPassword"
          type="password"
          autocomplete="new-password"
          minlength="6"
          maxlength="64"
          required
        />
        <p v-if="requiredPasswordError" class="required-password-error" role="alert">{{ requiredPasswordError }}</p>
        <button class="primary-action" type="submit" :disabled="busy">
          <Save :size="17" />{{ busy ? '正在修改' : '修改密码并重新登录' }}
        </button>
      </form>
    </section>
  </div>
</template>

<script setup>
import {
  BadgeCheck,
  CheckCircle2,
  Eye,
  EyeOff,
  KeyRound,
  LogIn,
  Save,
  ShieldCheck,
  UserRound
} from '@lucide/vue'

defineProps({
  setupRequired: { type: Boolean, default: false },
  healthOk: { type: Boolean, default: false },
  loginError: { type: Boolean, default: false },
  loginVerified: { type: Boolean, default: false },
  busy: { type: Boolean, default: false },
  setupForm: { type: Object, required: true },
  loginForm: { type: Object, required: true },
  setupFormReady: { type: Boolean, default: false },
  rememberStorageHint: { type: String, default: '' },
  passwordForm: { type: Object, required: true },
  requiredPasswordError: { type: String, default: '' },
  requiresPasswordChange: { type: Boolean, default: false }
})

defineEmits(['login', 'initialize', 'change-required-password'])

const showLoginPassword = defineModel('showLoginPassword', { type: Boolean, default: false })
const showSetupPassword = defineModel('showSetupPassword', { type: Boolean, default: false })
const rememberLogin = defineModel('rememberLogin', { type: Boolean, default: false })
const brandLogoUrl = '/brand/ca-logo-white.png'
</script>

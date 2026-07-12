<template>
  <div
    class="app-shell"
    :class="{
      'admin-fullscreen': view === 'dashboard' && currentUser,
      'login-fullscreen': view === 'dashboard' && !currentUser,
      'kiosk-fullscreen': view === 'kiosk'
    }"
  >
    <aside class="side-panel">
      <div class="brand-block">
        <div class="brand-mark">
          <img src="/brand/ca-logo-white.png" alt="计协会徽" />
        </div>
        <div>
          <p class="eyebrow">Computer Association</p>
          <h1>值班签到台</h1>
          <p class="brand-code">#include &lt;the.world&gt;</p>
        </div>
      </div>

      <nav class="rail-nav" aria-label="主导航">
        <button :class="{ active: view === 'kiosk' }" @click="returnToKiosk">
          <ScanLine :size="18" />
          <span>签到台</span>
        </button>
        <button :class="{ active: view === 'dashboard' }" @click="openDashboard">
          <LayoutDashboard :size="18" />
          <span>后台</span>
        </button>
      </nav>

      <div class="operator-strip">
        <div class="signal-dot" :class="{ online: healthOk }"></div>
        <span>{{ healthOk ? '服务在线' : '服务未连接' }}</span>
      </div>
    </aside>

    <main class="main-surface">
      <KioskView
        v-if="view === 'kiosk'"
        :health-ok="healthOk"
        :date-text="kioskDateText"
        :time-text="kioskTimeText"
        :current-period-text="kioskCurrentPeriodText"
        :today-period-summary="todayPeriodSummary"
        :empty-board-text="kioskEmptyBoardText"
        :week-summary="kioskWeekSummary"
        @open-dashboard="openDashboard"
        @refresh-schedules="loadPublicSchedules"
        @health-change="healthOk = $event"
      />

      <section
        v-else
        class="dashboard"
        :class="[
          { 'login-dashboard': !currentUser, 'logged-in-dashboard': currentUser },
          dashboardRoleClass,
          currentUser ? `active-module-${activeTab}` : ''
        ]"
      >
        <header class="dashboard-header" :class="{ 'admin-topbar': currentUser, 'password-change-topbar': currentUser?.mustChangePassword }">
          <template v-if="currentUser">
            <div class="admin-brand-lockup">
              <span class="admin-brand-symbol">
                <img src="/brand/ca-logo-white.png" alt="计协会徽" />
              </span>
              <span class="admin-brand-copy">
                <strong>计算机协会</strong>
                <small>本地离线管理后台</small>
              </span>
            </div>

            <nav v-if="!currentUser.mustChangePassword" class="admin-primary-nav" aria-label="后台一级导航">
              <button
                v-for="group in adminNavGroups"
                :key="group.id"
                type="button"
                :class="{ active: activeAdminGroup?.id === group.id }"
                :aria-current="activeAdminGroup?.id === group.id ? 'page' : undefined"
                @click="selectAdminGroup(group)"
              >
                <component :is="group.icon" :size="18" />
                <span>{{ group.label }}</span>
              </button>
            </nav>
            <div v-else class="required-password-top-state">
              <KeyRound :size="17" />首次登录需要修改密码
            </div>

            <div class="user-chip">
              <span class="admin-user-avatar"><UserRound :size="17" /></span>
              <span>{{ currentUser.name }} · {{ roleLabel(currentUser.role) }}</span>
              <button class="ghost-button" type="button" title="退出后台" @click="logout">
                <Power :size="15" />
                <span>退出</span>
              </button>
            </div>
          </template>
          <template v-else>
            <div class="login-topbar-brand">
              <span><img src="/brand/ca-logo-white.png" alt="计协会徽" /></span>
              <div>
                <strong>计算机协会</strong>
                <small>本地离线管理后台</small>
              </div>
            </div>
            <div class="login-topbar-clock">
              <span>{{ kioskDateText }}</span>
              <strong>{{ kioskTimeText }}</strong>
            </div>
            <div class="login-topbar-tools">
              <span class="login-health" :class="{ online: healthOk }">
                <i aria-hidden="true"></i>{{ healthOk ? '服务正常' : '服务未连接' }}
              </span>
              <button type="button" title="返回签到台" aria-label="返回签到台" @click="returnToKiosk">
                <ScanLine :size="19" />
              </button>
            </div>
          </template>
        </header>

        <AuthView
          v-if="!currentUser || currentUser.mustChangePassword"
          v-model:show-login-password="showLoginPassword"
          v-model:show-setup-password="showSetupPassword"
          v-model:remember-login="rememberLogin"
          :setup-required="setupRequired"
          :health-ok="healthOk"
          :login-error="loginError"
          :login-verified="loginVerified"
          :busy="busy"
          :setup-form="setupForm"
          :login-form="loginForm"
          :setup-form-ready="setupFormReady"
          :remember-storage-hint="rememberStorageHint"
          :password-form="passwordForm"
          :required-password-error="requiredPasswordError"
          :requires-password-change="Boolean(currentUser?.mustChangePassword)"
          @login="login"
          @initialize="initializeSystem"
          @change-required-password="changeRequiredPassword"
        />

        <div v-else class="workspace admin-ledger-workspace admin-workbench-workspace">
          <div class="admin-ledger-shell admin-workbench-shell">
            <div class="admin-subnav">
              <div class="admin-subnav-date">
                <CalendarDays :size="17" />
                <span>{{ todayText }} · {{ weekdayText }}</span>
              </div>
              <nav aria-label="当前模块页面">
                <button
                  v-for="tab in activeAdminGroupTabs"
                  :key="tab.id"
                  :class="{ active: activeTab === tab.id }"
                  :aria-current="activeTab === tab.id ? 'page' : undefined"
                  type="button"
                  @click="selectTab(tab.id)"
                >
                  <component :is="tab.icon" :size="16" />
                  <span>{{ tab.label }}</span>
                  <small v-if="adminTabBadge(tab.id)">{{ adminTabBadge(tab.id) }}</small>
                </button>
              </nav>
              <div class="admin-service-state" :class="{ online: healthOk }">
                <span aria-hidden="true"></span>
                {{ healthOk ? '本机服务正常' : '本机服务未连接' }}
              </div>
            </div>

            <div class="admin-ledger-main admin-workbench-main">
              <div class="admin-contextbar">
                <div class="admin-context-heading">
                  <span class="admin-context-icon"><component :is="activeTabInfo.icon" :size="21" /></span>
                  <div>
                    <h1>{{ activeTabInfo.label }}</h1>
                    <span>{{ activeTabDescription }}</span>
                  </div>
                </div>
              </div>

              <div :key="activeTab" class="admin-tab-stage">
          <OverviewPanel
            v-if="activeTab === 'overview'"
            :today-issues="todayIssues"
            :today-period-summary="todayPeriodSummary"
            :today-records="todayRecords"
            :empty-board-text="kioskEmptyBoardText"
            :clear-detail="todayClearDetail"
            :can-manage-schedules="canOpenAdminTab('schedules')"
            :can-open-records="canOpenAdminTab('records')"
            @select-tab="selectTab"
          />

          <ReviewsPanel
            v-if="activeTab === 'reviews'"
            @notify="notify($event.message, $event.type)"
            @updated="loadOverview"
          />

          <AttendanceRecordsPanel
            v-if="activeTab === 'records'"
            :current-user="currentUser"
            @notify="notify($event.message, $event.type)"
            @dirty-change="setFormDirty('manual-record', $event)"
          />

          <MembersPanel
            v-if="activeTab === 'members'"
            :current-user="currentUser"
            @notify="notify($event.message, $event.type)"
            @dirty-change="setFormDirty('members', $event)"
          />

          <StatsPanel
            v-if="activeTab === 'stats'"
            :current-user="currentUser"
            @notify="notify($event.message, $event.type)"
          />

          <TrainingPanel
            v-if="activeTab === 'trainings'"
            :current-user="currentUser"
            @notify="notify($event.message, $event.type)"
            @dirty-change="setFormDirty('training', $event)"
          />

          <SchedulePanel
            v-if="activeTab === 'schedules'"
            :current-user="currentUser"
            @notify="notify($event.message, $event.type)"
            @dirty-change="setFormDirty('schedule', $event)"
          />

          <RepairPanel
            v-if="activeTab === 'repairs'"
            :current-user="currentUser"
            @notify="notify($event.message, $event.type)"
            @dirty-change="setFormDirty('repair', $event)"
          />

          <DataCenterPanel
            v-if="activeTab === 'data'"
            :current-user="currentUser"
            @notify="notify($event.message, $event.type)"
            @session-invalidated="handleRestoredSession"
          />

          <SettingsPanel
            v-if="activeTab === 'settings'"
            @notify="notify($event.message, $event.type)"
            @dirty-change="setFormDirty('settings', $event)"
            @updated="loadPublicSchedules"
          />

          <LogsPanel v-if="activeTab === 'logs'" :current-user="currentUser" @notify="notify($event.message, $event.type)" />

          <ProfilePanel
            v-if="activeTab === 'profile'"
            @password-change="changePassword"
            @notify="notify($event.message, $event.type)"
            @dirty-change="handleProfileDirtyChange"
          />
              </div>
            </div>
          </div>
        </div>
      </section>

      <Transition name="toast-pop">
        <div v-if="toast.message" class="toast" :class="toast.type" role="status" aria-live="polite">{{ toast.message }}</div>
      </Transition>
      <ActionConfirmDialog />
    </main>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  CalendarDays,
  KeyRound,
  LayoutDashboard,
  Power,
  ScanLine,
  UserRound,
  X
} from '@lucide/vue'
import { api, getToken, post, setToken } from './api.js'
import { adminModuleLocation, tabFromRoute } from './app/router.js'
import {
  adminNavBlueprint,
  adminTabDescriptions,
  adminTabs,
  roleLabel,
  tabsForRole
} from './features/navigation/adminNavigation.js'
import AttendanceRecordsPanel from './components/AttendanceRecordsPanel.vue'
import AuthView from './components/AuthView.vue'
import DataCenterPanel from './components/DataCenterPanel.vue'
import KioskView from './components/KioskView.vue'
import LogsPanel from './components/LogsPanel.vue'
import MembersPanel from './components/MembersPanel.vue'
import OverviewPanel from './components/OverviewPanel.vue'
import ProfilePanel from './components/ProfilePanel.vue'
import RepairPanel from './components/RepairPanel.vue'
import ReviewsPanel from './components/ReviewsPanel.vue'
import SchedulePanel from './components/SchedulePanel.vue'
import SettingsPanel from './components/SettingsPanel.vue'
import StatsPanel from './components/StatsPanel.vue'
import TrainingPanel from './components/TrainingPanel.vue'
import ActionConfirmDialog from './shared/ActionConfirmDialog.vue'
import { requestConfirmation } from './shared/confirm.js'
import { buildTodayIssues } from './features/dashboard/todayIssues.js'
import {
  hasSecureCredentialStorage,
  loadRememberedLogin,
  persistRememberedLogin
} from './features/auth/rememberedCredentials.js'
import { normalizeDutyPeriods } from './features/schedule/dutyPeriods.js'
import {
  buildPeriodSummary,
  buildWeekScheduleSummary,
  shortWeekdayName
} from './features/schedule/scheduleSummary.js'

const route = useRoute()
const appRouter = useRouter()

const view = ref('kiosk')
const activeTab = ref('overview')
const pendingAdminTab = ref(null)
const healthOk = ref(false)
const busy = ref(false)
const liveNow = ref(new Date())
const currentUser = ref(null)
const todayRecords = ref([])
const todaySchedule = ref([])
const weekSchedule = ref([])
const dutyPeriods = ref([])
const publicDutyWeekdays = ref([])
const today = new Date()
const todayValue = formatLocalDate(today)
const loginForm = reactive({ studentNo: '', password: '' })
const setupForm = reactive({ account: '', name: '', password: '', confirmPassword: '' })
const setupRequired = ref(false)
const showLoginPassword = ref(false)
const showSetupPassword = ref(false)
const rememberLogin = ref(true)
const loginVerified = ref(false)
const loginError = ref(false)
const requiredPasswordError = ref('')
const dirtyForms = ref(new Set())
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const overview = reactive({
  pendingCount: 0,
  totalHours: 0,
  totalCount: 0,
  dutyDays: [],
  dashboard: {
    todayRecordCount: 0,
    todayOpenCount: 0,
    todayPendingCount: 0,
    ongoingRepairCount: 0,
    todayValidHours: 0,
    weekValidHours: 0,
    yearValidHours: 0,
    yearValidCount: 0
  }
})
const toast = reactive({ message: '', type: 'info' })
let loginErrorTimer = null

const availableTabs = computed(() => tabsForRole(currentUser.value?.role))
const activeTabInfo = computed(() => availableTabs.value.find(tab => tab.id === activeTab.value) || availableTabs.value[0] || adminTabs[0])
const activeTabDescription = computed(() => adminTabDescriptions[activeTabInfo.value?.id] || '管理当前模块的数据和操作。')
const adminNavGroups = computed(() => {
  const tabMap = new Map(availableTabs.value.map(tab => [tab.id, tab]))
  return adminNavBlueprint
    .map(group => ({
      ...group,
      tabs: group.tabs.map(id => tabMap.get(id)).filter(Boolean)
    }))
    .filter(group => group.tabs.length > 0)
})
const activeAdminGroup = computed(() => (
  adminNavGroups.value.find(group => group.tabs.some(tab => tab.id === activeTab.value)) || adminNavGroups.value[0]
))
const activeAdminGroupTabs = computed(() => activeAdminGroup.value?.tabs || [])
const rememberStorageHint = computed(() => (
  hasSecureCredentialStorage() ? '由 Windows 加密保存在本机' : '浏览器模式仅记住账号'
))
const setupFormReady = computed(() => (
  /^[A-Za-z0-9_-]{4,32}$/.test(setupForm.account) &&
  setupForm.name.length > 0 &&
  setupForm.password.length >= 6 &&
  setupForm.password === setupForm.confirmPassword
))
const todayText = computed(() => today.toLocaleDateString('zh-CN', { month: 'long', day: 'numeric' }))
const weekdayText = computed(() => today.toLocaleDateString('zh-CN', { weekday: 'long' }))
const kioskDateText = computed(() => liveNow.value.toLocaleDateString('zh-CN', {
  month: 'long',
  day: 'numeric',
  weekday: 'long'
}))
const kioskTimeText = computed(() => liveNow.value.toLocaleTimeString('zh-CN', {
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false
}))
const allWeekdayOptions = [
  { weekday: 1, name: '周一', fullName: '星期一' },
  { weekday: 2, name: '周二', fullName: '星期二' },
  { weekday: 3, name: '周三', fullName: '星期三' },
  { weekday: 4, name: '周四', fullName: '星期四' },
  { weekday: 5, name: '周五', fullName: '星期五' },
  { weekday: 6, name: '周六', fullName: '星期六' },
  { weekday: 7, name: '周日', fullName: '星期日' }
]
const kioskEnabledWeekdays = computed(() => {
  if (!publicDutyWeekdays.value.length) return allWeekdayOptions
  return publicDutyWeekdays.value
    .map(row => {
      const weekday = Number(row.weekday)
      const fallback = allWeekdayOptions.find(day => day.weekday === weekday)
      return {
        weekday,
        name: shortWeekdayName(row.weekday_name || row.weekdayName || fallback?.fullName),
        fullName: row.weekday_name || row.weekdayName || fallback?.fullName || `星期${weekday}`,
        enabled: row.enabled === true || row.enabled === 1 || row.enabled === '1' || row.enabled === 'true'
      }
    })
    .filter(day => day.weekday >= 1 && day.weekday <= 7 && day.enabled)
})
const todayWeekdayValue = today.getDay() || 7
const hasDutyPeriodSettings = computed(() => normalizeDutyPeriods(dutyPeriods.value).length > 0)
const todayIsConfiguredDutyDay = computed(() => kioskEnabledWeekdays.value.some(day => day.weekday === todayWeekdayValue))
const todayPeriodSummary = computed(() => {
  liveNow.value
  return todayIsConfiguredDutyDay.value
    ? buildPeriodSummary(todaySchedule.value, dutyPeriods.value, liveNow.value)
    : []
})
const missingScheduleCount = computed(() => {
  if (!todayIsConfiguredDutyDay.value) return 0
  if (!hasDutyPeriodSettings.value) return 1
  return todayPeriodSummary.value.filter(period => period.missing).length
})
const todayIssues = computed(() => buildTodayIssues({
  pendingCount: overview.pendingCount,
  openCount: overview.dashboard.todayOpenCount,
  missingScheduleCount: missingScheduleCount.value,
  ongoingRepairCount: overview.dashboard.ongoingRepairCount
}, {
  includeSchedule: canOpenAdminTab('schedules')
}).map(issue => {
  const needsPeriodSettings = issue.id === 'schedule' && !hasDutyPeriodSettings.value
  const tab = needsPeriodSettings ? 'settings' : issue.tab
  return {
    ...issue,
    tab,
    title: needsPeriodSettings ? '值班时段未设置' : issue.title,
    detail: needsPeriodSettings ? '请先设置今日可用的值班时段' : issue.detail,
    actionable: canOpenAdminTab(tab)
  }
}))
const todayClearDetail = computed(() => (
  canOpenAdminTab('schedules')
    ? '排班、审核、签退与维修状态均正常'
    : '审核、签退与维修状态均正常'
))
const kioskCurrentPeriodText = computed(() => {
  const activePeriod = todayPeriodSummary.value.find(period => period.active)
  if (activePeriod) return `当前时段 ${activePeriod.timeText}`
  if (!todayIsConfiguredDutyDay.value) return '今日非值班日'
  return '当前无值班时段'
})
const kioskEmptyBoardText = computed(() => {
  if (!todayIsConfiguredDutyDay.value) return '今日非值班日'
  if (!hasDutyPeriodSettings.value) return '请先在后台设置值班时间段'
  return '今日暂无部长排班'
})
const kioskWeekSummary = computed(() => {
  return buildWeekScheduleSummary({
    weekSchedule: weekSchedule.value,
    weekdays: kioskEnabledWeekdays.value,
    periods: dutyPeriods.value,
    todayWeekday: todayWeekdayValue,
    now: liveNow.value
  })
})
const dashboardRoleClass = computed(() => currentUser.value ? `role-${String(currentUser.value.role).toLowerCase()}` : '')

onMounted(async () => {
  removeUnsavedRouteGuard = appRouter.beforeEach(confirmUnsavedRouteChange)
  window.addEventListener('beforeunload', warnBeforeUnload)
  await loadRememberedLoginCredentials()
  kioskClockTimer = window.setInterval(() => {
    liveNow.value = new Date()
  }, 1000)
  await checkHealth()
  if (healthOk.value) await checkSetupStatus()
  if (!setupRequired.value) await loadPublicSchedules()
  await restoreSession()
  if (setupRequired.value) {
    await appRouter.replace('/login')
  } else if (currentUser.value?.mustChangePassword && route.name !== 'kiosk') {
    await appRouter.replace('/password-change')
  } else if (route.name === 'admin-module' && !currentUser.value) {
    pendingAdminTab.value = tabFromRoute(route)
    await appRouter.replace('/login')
  } else if (route.name === 'login' && currentUser.value) {
    await selectTab(availableTabs.value[0]?.id || 'profile', { replace: true })
  } else {
    await applyRouteLocation()
  }
  overviewRefreshTimer = window.setInterval(refreshVisibleOverview, 30_000)
  kioskHealthTimer = window.setInterval(checkHealth, 5_000)
  document.addEventListener('visibilitychange', refreshVisibleOverview)
  window.addEventListener('focus', refreshVisibleOverview)
})

let kioskClockTimer = null
let kioskHealthTimer = null
let overviewRefreshTimer = null
let appliedRouteKey = ''
let removeUnsavedRouteGuard = null

watch(() => route.fullPath, () => {
  void applyRouteLocation()
})

onBeforeUnmount(() => {
  if (kioskClockTimer) window.clearInterval(kioskClockTimer)
  if (kioskHealthTimer) window.clearInterval(kioskHealthTimer)
  if (overviewRefreshTimer) window.clearInterval(overviewRefreshTimer)
  if (loginErrorTimer) window.clearTimeout(loginErrorTimer)
  removeUnsavedRouteGuard?.()
  window.removeEventListener('beforeunload', warnBeforeUnload)
  document.removeEventListener('visibilitychange', refreshVisibleOverview)
  window.removeEventListener('focus', refreshVisibleOverview)
})

function refreshVisibleOverview() {
  if (
    document.visibilityState === 'visible' &&
    currentUser.value &&
    activeTab.value === 'overview' &&
    !busy.value
  ) {
    void loadOverview()
  }
}

async function checkHealth() {
  try {
    await api('/api/health')
    healthOk.value = true
  } catch {
    healthOk.value = false
  }
}

async function checkSetupStatus() {
  try {
    const status = await api('/api/setup/status')
    setupRequired.value = !status.initialized
    if (setupRequired.value) view.value = 'dashboard'
  } catch {
    setupRequired.value = false
  }
}

async function restoreSession() {
  if (!getToken()) return
  try {
    currentUser.value = await api('/api/auth/me')
  } catch {
    setToken('')
    currentUser.value = null
  }
}

async function loadPublicSchedules() {
  await run(async () => {
    const [todayItems, weekItems, periods, dutyWeekdays] = await Promise.all([
      api('/api/public/schedules/today'),
      api('/api/public/schedules/week'),
      api('/api/public/duty-periods'),
      api('/api/public/duty-weekdays')
    ])
    todaySchedule.value = todayItems
    weekSchedule.value = weekItems
    dutyPeriods.value = normalizeDutyPeriods(periods)
    publicDutyWeekdays.value = dutyWeekdays
  }, false)
}

async function openDashboard() {
  if (!currentUser.value) {
    await loadRememberedLoginCredentials()
    await appRouter.push('/login')
    return
  }
  if (currentUser.value.mustChangePassword) {
    void appRouter.push('/password-change')
    return
  }
  void selectTab(activeTab.value || availableTabs.value[0]?.id || 'profile')
}

function returnToKiosk() {
  loginVerified.value = false
  loginError.value = false
  showLoginPassword.value = false
  loginForm.password = ''
  void appRouter.push('/kiosk')
}

async function login() {
  busy.value = true
  loginError.value = false
  try {
    const submittedPassword = loginForm.password
    const res = await post('/api/auth/login', loginForm)
    setToken(res.token)
    const rememberedResult = res.mustChangePassword && rememberLogin.value
      ? { mode: 'deferred', savedPassword: false }
      : await persistRememberedLogin({
          account: loginForm.studentNo,
          password: submittedPassword,
          remember: rememberLogin.value
        })
    loginVerified.value = true
    await new Promise(resolve => window.setTimeout(resolve, 720))
    currentUser.value = res
    clearPasswordForm()
    loginForm.password = ''
    showLoginPassword.value = false
    loginVerified.value = false
    if (res.mustChangePassword) {
      requiredPasswordError.value = ''
      notify('请先修改初始密码', 'info')
      appliedRouteKey = ''
      await appRouter.replace('/password-change')
      return
    }
    notify(
      rememberedResult.mode === 'failed' ? '已登录，但系统未能保存密码' : '已登录后台',
      rememberedResult.mode === 'failed' ? 'warn' : 'success'
    )
    const requestedTab = pendingAdminTab.value
    pendingAdminTab.value = null
    const targetTab = availableTabs.value.some(tab => tab.id === requestedTab)
      ? requestedTab
      : availableTabs.value[0]?.id || 'profile'
    appliedRouteKey = ''
    await selectTab(targetTab, { replace: true })
  } catch (error) {
    loginVerified.value = false
    triggerLoginError()
    notify(error.message, 'error')
  } finally {
    busy.value = false
  }
}

async function initializeSystem() {
  if (!setupFormReady.value) {
    if (setupForm.password !== setupForm.confirmPassword) return notify('两次输入的密码不一致', 'warn')
    return notify('请完整填写管理员信息', 'warn')
  }
  busy.value = true
  loginError.value = false
  try {
    const res = await post('/api/setup/initialize', {
      account: setupForm.account,
      name: setupForm.name,
      password: setupForm.password
    })
    setToken(res.token)
    loginVerified.value = true
    await new Promise(resolve => window.setTimeout(resolve, 720))
    currentUser.value = res
    setupRequired.value = false
    loginVerified.value = false
    showSetupPassword.value = false
    setupForm.password = ''
    setupForm.confirmPassword = ''
    loginForm.studentNo = res.studentNo
    notify('系统初始化完成', 'success')
    await loadPublicSchedules()
    appliedRouteKey = ''
    await selectTab(availableTabs.value[0]?.id || 'overview', { replace: true })
  } catch (error) {
    loginVerified.value = false
    triggerLoginError()
    notify(error.message, 'error')
  } finally {
    busy.value = false
  }
}

async function logout() {
  if (!await confirmDiscardUnsaved()) return
  setToken('')
  currentUser.value = null
  loginForm.password = ''
  showLoginPassword.value = false
  loginVerified.value = false
  clearPasswordForm()
  notify('已退出', 'info')
  await appRouter.replace('/login')
  await loadRememberedLoginCredentials()
}

async function handleRestoredSession(message) {
  setToken('')
  currentUser.value = null
  clearAllDirtyForms()
  activeTab.value = 'overview'
  appliedRouteKey = ''
  await appRouter.replace('/login')
  await loadRememberedLoginCredentials()
  notify(message, 'success')
}

async function loadRememberedLoginCredentials() {
  const remembered = await loadRememberedLogin()
  if (remembered.account) {
    loginForm.studentNo = remembered.account
    rememberLogin.value = true
  }
  loginForm.password = remembered.password
  if (remembered.remembersPassword) rememberLogin.value = true
}

function triggerLoginError() {
  loginError.value = false
  window.requestAnimationFrame(() => {
    loginError.value = true
    window.clearTimeout(loginErrorTimer)
    loginErrorTimer = window.setTimeout(() => {
      loginError.value = false
    }, 520)
  })
}

async function selectAdminGroup(group) {
  if (!group?.tabs?.length || group.tabs.some(tab => tab.id === activeTab.value)) return
  await selectTab(group.tabs[0].id)
}

function adminTabBadge(tabId) {
  if (tabId === 'reviews' && overview.pendingCount > 0) return overview.pendingCount
  if (tabId === 'overview') {
    return todayIssues.value.reduce((sum, issue) => sum + issue.count, 0) || ''
  }
  return ''
}

function canOpenAdminTab(tab) {
  return availableTabs.value.some(item => item.id === tab)
}

async function selectTab(tab, options = {}) {
  if (currentUser.value?.mustChangePassword) {
    await appRouter.replace('/password-change')
    return
  }
  const safeTab = availableTabs.value.some(item => item.id === tab)
    ? tab
    : availableTabs.value[0]?.id || 'profile'
  const location = adminModuleLocation(safeTab, options.query || {})
  const sameTab = route.name === 'admin-module' && tabFromRoute(route) === safeTab
  if (!sameTab || Object.keys(options.query || {}).length) {
    await appRouter[options.replace ? 'replace' : 'push'](location)
  }
  await applyRouteLocation()
}

async function applyRouteLocation() {
  if (route.name === 'kiosk') {
    view.value = 'kiosk'
    return
  }

  view.value = 'dashboard'
  if (route.name === 'login') return
  if (route.name === 'password-change') {
    if (!currentUser.value) await appRouter.replace('/login')
    else if (!currentUser.value.mustChangePassword) {
      await selectTab(availableTabs.value[0]?.id || 'profile', { replace: true })
    }
    return
  }

  const tab = tabFromRoute(route)
  if (!tab) return
  if (!currentUser.value) {
    pendingAdminTab.value = tab
    return
  }
  if (currentUser.value.mustChangePassword) {
    await appRouter.replace('/password-change')
    return
  }
  if (!availableTabs.value.some(item => item.id === tab)) {
    await appRouter.replace(adminModuleLocation(availableTabs.value[0]?.id || 'profile'))
    return
  }

  const routeKey = `${route.fullPath}|${currentUser.value.id || currentUser.value.studentNo}`
  if (appliedRouteKey === routeKey) return
  appliedRouteKey = routeKey
  await loadTab(tab)
}

async function loadTab(tab) {
  activeTab.value = tab
  if (tab === 'overview') await loadOverview()
}

async function loadOverview() {
  await run(async () => {
    const [pending, summary, dutyDays, dashboard, records, todayItems, weekItems, periods, publicWeekdays] = await Promise.all([
      api('/api/attendance/reviews/pending'),
      api(`/api/stats/summary?from=${today.getFullYear()}-01-01&to=${todayValue}`),
      api('/api/settings/weekdays'),
      api(`/api/stats/dashboard?date=${todayValue}`),
      api(`/api/attendance?from=${todayValue}&to=${todayValue}`),
      api('/api/public/schedules/today'),
      api('/api/public/schedules/week'),
      api('/api/public/duty-periods'),
      api('/api/public/duty-weekdays')
    ])
    overview.pendingCount = pending.length
    overview.totalHours = summary.reduce((sum, row) => sum + Number(row.totalHours || 0), 0)
    overview.totalCount = summary.reduce((sum, row) => sum + Number(row.dutyCount || 0), 0)
    overview.dutyDays = dutyDays.filter(day => day.enabled).map(day => day.weekday_name)
    Object.assign(overview.dashboard, dashboard)
    todayRecords.value = records
      .slice()
      .sort((a, b) => String(b.checkInTime || '').localeCompare(String(a.checkInTime || '')))
    todaySchedule.value = todayItems
    weekSchedule.value = weekItems
    dutyPeriods.value = normalizeDutyPeriods(periods)
    publicDutyWeekdays.value = publicWeekdays
  }, false)
}

async function changePassword(credentials) {
  await submitPasswordChange(false, credentials)
}

async function changeRequiredPassword() {
  await submitPasswordChange(true)
}

async function submitPasswordChange(requiredChange, credentials = passwordForm) {
  const validationError = passwordChangeValidationError(credentials)
  if (validationError) {
    if (requiredChange) requiredPasswordError.value = validationError
    notify(validationError, 'warn')
    return
  }

  busy.value = true
  requiredPasswordError.value = ''
  try {
    const account = currentUser.value?.studentNo || loginForm.studentNo
    const newPassword = credentials.newPassword
    await post('/api/auth/change-password', {
      oldPassword: credentials.oldPassword,
      newPassword
    })
    const rememberedResult = await persistRememberedLogin({
      account,
      password: newPassword,
      remember: rememberLogin.value
    })
    setToken('')
    currentUser.value = null
    clearAllDirtyForms()
    clearPasswordForm()
    loginForm.studentNo = account
    loginForm.password = rememberedResult.savedPassword ? newPassword : ''
    appliedRouteKey = ''
    await appRouter.replace('/login')
    notify(
      rememberedResult.mode === 'failed'
        ? '密码已修改，但系统未能保存新密码'
        : '密码已修改，请使用新密码重新登录',
      rememberedResult.mode === 'failed' ? 'warn' : 'success'
    )
  } catch (error) {
    if (requiredChange) requiredPasswordError.value = error.message
    notify(error.message, 'error')
  } finally {
    busy.value = false
  }
}

function passwordChangeValidationError(credentials) {
  if (!credentials.oldPassword || !credentials.newPassword || !credentials.confirmPassword) return '请填写完整密码信息'
  if (credentials.newPassword.length < 6) return '新密码至少 6 位'
  if (credentials.newPassword !== credentials.confirmPassword) return '两次新密码不一致'
  if (credentials.oldPassword === credentials.newPassword) return '新密码不能与当前密码相同'
  return ''
}

function clearPasswordForm() {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
}

function setFormDirty(source, dirty = true) {
  const next = new Set(dirtyForms.value)
  if (dirty) next.add(source)
  else next.delete(source)
  dirtyForms.value = next
}

function handleProfileDirtyChange(event) {
  if (!event?.form) return
  setFormDirty(`profile:${event.form}`, Boolean(event.dirty))
}

function clearAllDirtyForms() {
  dirtyForms.value = new Set()
  clearPasswordForm()
}

async function confirmDiscardUnsaved() {
  if (dirtyForms.value.size === 0) return true
  const confirmed = await requestConfirmation({
    title: '放弃未保存的修改？',
    message: '当前页面还有未保存的内容，离开后这些修改会丢失。',
    confirmLabel: '放弃修改',
    tone: 'danger'
  })
  if (confirmed) clearAllDirtyForms()
  return confirmed
}

async function confirmUnsavedRouteChange(to, from) {
  if (to.path === from.path) return true
  return confirmDiscardUnsaved()
}

function warnBeforeUnload(event) {
  if (dirtyForms.value.size === 0) return
  event.preventDefault()
  event.returnValue = ''
}

async function run(fn, showError = true) {
  busy.value = true
  try {
    await fn()
  } catch (error) {
    if (showError) notify(error.message, 'error')
  } finally {
    busy.value = false
  }
}

function notify(message, type = 'info') {
  toast.message = message
  toast.type = type
  window.clearTimeout(notify.timer)
  notify.timer = window.setTimeout(() => {
    toast.message = ''
  }, 2800)
}

function formatLocalDate(value) {
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
</script>

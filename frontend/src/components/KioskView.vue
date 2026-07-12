<template>
  <section class="kiosk-portal">
    <header class="kiosk-portal-header">
      <div class="kiosk-portal-brand">
        <span><img :src="brandLogoUrl" alt="计协会徽" /></span>
        <div>
          <strong>计算机协会</strong>
          <small>值班签到台</small>
        </div>
      </div>
      <div class="kiosk-live-clock" aria-label="当前时间">
        <span>{{ dateText }}</span>
        <strong>{{ timeText }}</strong>
      </div>
      <div class="kiosk-portal-tools">
        <span class="kiosk-health" :class="{ online: healthOk }">
          <i aria-hidden="true"></i>{{ healthOk ? '服务正常' : pendingAction ? '服务重连中' : '服务未连接' }}
        </span>
        <button class="kiosk-admin-button" type="button" title="进入后台" aria-label="进入后台" @click="$emit('open-dashboard')">
          <LayoutDashboard :size="19" />
        </button>
      </div>
    </header>

    <main class="kiosk-portal-main">
      <section class="kiosk-checkin-stage" :class="{ success: attendanceSuccess }">
        <div class="kiosk-stage-heading">
          <div>
            <p class="eyebrow">Duty Checkpoint</p>
            <h1>{{ attendanceSuccess ? `${attendanceSuccess.actionLabel}成功` : '签到 / 签退' }}</h1>
          </div>
          <span v-if="!attendanceSuccess">{{ currentPeriodText }}</span>
        </div>

        <Transition name="kiosk-state" mode="out-in" @after-enter="handleStateEntered">
          <div v-if="attendanceSuccess" key="success" class="kiosk-success-state">
            <span class="kiosk-success-ring"><CheckCircle2 :size="48" /></span>
            <div>
              <p>{{ attendanceSuccess.actionLabel }}完成</p>
              <strong>{{ attendanceSuccess.name }}</strong>
              <span>{{ attendanceSuccess.message }}</span>
              <small class="kiosk-reset-countdown">{{ resetSeconds }} 秒后自动清除</small>
            </div>
            <button class="kiosk-next-button" type="button" @click="resetKiosk">
              <ScanLine :size="19" />下一位
            </button>
            <span class="kiosk-reset-progress" aria-hidden="true"></span>
          </div>

          <div v-else key="lookup" class="kiosk-lookup-stage">
            <form class="lookup-form kiosk-lookup-form" @submit.prevent="lookupMember()">
              <label for="studentNo">学号或姓名</label>
              <div class="input-row">
                <ScanLine class="kiosk-input-icon" :size="22" aria-hidden="true" />
                <input
                  id="studentNo"
                  ref="inputRef"
                  v-model.trim="memberQuery"
                  name="memberQuery"
                  autocomplete="off"
                  spellcheck="false"
                  placeholder="输入学号或姓名…"
                  :aria-describedby="inlineError ? 'kioskInlineError' : undefined"
                />
                <button type="submit" class="kiosk-search-button" :disabled="busy">
                  <Search :size="20" />
                  <span>查询</span>
                </button>
                <span v-if="busy" class="kiosk-search-scan" aria-hidden="true"></span>
              </div>
            </form>

            <div v-if="pendingAction || inlineError" class="kiosk-inline-notice" :class="{ offline: pendingAction }" role="status" aria-live="polite">
              <WifiOff v-if="pendingAction" :size="20" aria-hidden="true" />
              <AlertTriangle v-else :size="20" aria-hidden="true" />
              <div>
                <strong>{{ pendingAction ? '本机服务连接中断' : '暂时无法完成查询' }}</strong>
                <span id="kioskInlineError">{{ pendingAction ? pendingAction.message : inlineError }}</span>
              </div>
              <button v-if="pendingAction" type="button" :disabled="busy" @click="retryPendingAction">
                <RefreshCw :size="16" aria-hidden="true" />立即重试
              </button>
            </div>

            <div v-if="lookupCandidates.length" class="candidate-zone kiosk-choice-box">
              <div class="kiosk-result-heading">
                <div>
                  <p class="eyebrow">同名成员</p>
                  <h2>选择自己的学号</h2>
                </div>
                <span>{{ lookupCandidates.length }} 人</span>
              </div>
              <div class="candidate-list">
                <button
                  v-for="candidate in lookupCandidates"
                  :key="candidate.studentNo"
                  type="button"
                  class="candidate-card"
                  :disabled="busy"
                  @click="selectLookupCandidate(candidate)"
                >
                  <strong>{{ candidate.name }}</strong>
                  <span class="mono">学号尾号 {{ maskStudentNumber(candidate.studentNo) }}</span>
                </button>
              </div>
            </div>

            <div v-if="lookupResult && !lookupCandidates.length" class="confirm-zone kiosk-confirm-box">
              <div class="member-confirm" :class="{ blocked: !lookupResult.exists }">
                <div>
                  <p class="eyebrow">查询结果</p>
                  <h2>{{ lookupResult.name || '未找到成员' }}</h2>
                  <p>{{ lookupResult.message }}</p>
                  <div v-if="!lookupResult.exists" class="kiosk-lookup-help">
                    <span>请检查学号或姓名是否输入正确。</span>
                    <span>仍无法查询时，请联系管理员确认账号是否停用。</span>
                  </div>
                </div>
                <div class="status-pills">
                  <span v-if="lookupResult.exists">{{ lookupResult.action === 'CHECK_OUT' ? '本次签退' : '本次签到' }}</span>
                  <span>{{ lookupResult.dutyDay ? '今日值班日' : '非值班时段' }}</span>
                </div>
              </div>
              <button class="kiosk-confirm-button" type="button" :disabled="!lookupResult.exists || busy" @click="submitAttendance()">
                <CheckCircle2 :size="20" />
                <span>确认{{ lookupResult.action === 'CHECK_OUT' ? '签退' : '签到' }}</span>
              </button>
            </div>
          </div>
        </Transition>
      </section>

      <section class="kiosk-duty-track">
        <header class="kiosk-track-head">
          <div>
            <p class="eyebrow">Today Schedule</p>
            <h2>今日部长排班</h2>
          </div>
          <button type="button" title="刷新排班" aria-label="刷新排班" @click="$emit('refresh-schedules')">
            <RefreshCw :size="17" />
          </button>
        </header>

        <div v-if="todayPeriodSummary.length" class="kiosk-track-grid">
          <article
            v-for="period in todayPeriodSummary"
            :key="period.key"
            class="kiosk-track-period"
            :class="{ active: period.active, missing: period.missing }"
            :style="{ '--period-progress': `${dutyPeriodProgress(period)}%` }"
          >
            <div class="kiosk-track-time">
              <strong>{{ period.timeText }}</strong>
              <span>{{ period.count }} 人</span>
            </div>
            <div class="kiosk-track-members" :class="{ empty: !period.people.length }">
              <span v-for="person in period.people" :key="person.key">{{ person.name }}</span>
              <em v-if="!period.people.length">待安排部长</em>
            </div>
            <span v-if="period.active" class="kiosk-period-progress" aria-hidden="true"></span>
          </article>
        </div>
        <div v-else class="kiosk-track-empty">
          <CalendarDays :size="22" />
          <strong>{{ emptyBoardText }}</strong>
        </div>
      </section>

      <section class="kiosk-week-board" aria-label="本周值班概览">
        <div v-for="day in weekSummary" :key="day.weekday" :class="{ today: day.isToday }">
          <strong>{{ day.name }}</strong>
          <span>{{ day.count ? `${day.count} 个时段` : '暂无排班' }}</span>
          <i v-if="day.isToday">今日</i>
        </div>
      </section>
    </main>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  AlertTriangle,
  CalendarDays,
  CheckCircle2,
  LayoutDashboard,
  RefreshCw,
  ScanLine,
  Search,
  WifiOff
} from '@lucide/vue'
import { api, post } from '../api.js'
import {
  createKioskRequestId,
  createKioskResetTimer,
  maskStudentNumber
} from '../features/kiosk/kioskFlow.js'
import { timeToMinutes } from '../features/schedule/dutyPeriods.js'

const props = defineProps({
  healthOk: { type: Boolean, default: false },
  dateText: { type: String, default: '' },
  timeText: { type: String, default: '' },
  currentPeriodText: { type: String, default: '' },
  todayPeriodSummary: { type: Array, default: () => [] },
  emptyBoardText: { type: String, default: '' },
  weekSummary: { type: Array, default: () => [] }
})

const emit = defineEmits(['open-dashboard', 'refresh-schedules', 'health-change'])
const brandLogoUrl = '/brand/ca-logo-white.png'
const busy = ref(false)
const memberQuery = ref('')
const inputRef = ref(null)
const inlineError = ref('')
const pendingAction = ref(null)
const resetSeconds = ref(4)
const lookupResult = ref(null)
const attendanceSuccess = ref(null)
const lookupCandidates = computed(() => lookupResult.value?.matches || [])

const resetTimer = createKioskResetTimer({
  onTick: seconds => {
    resetSeconds.value = seconds
  },
  onReset: resetKiosk
})

onMounted(focusInput)
onBeforeUnmount(resetTimer.cancel)

watch(() => props.healthOk, (healthy, wasHealthy) => {
  if (healthy && !wasHealthy && pendingAction.value && !busy.value) {
    void retryPendingAction()
  }
})

async function lookupMember(retryAction = null) {
  const query = retryAction?.type === 'lookup' ? retryAction.query : memberQuery.value.trim()
  if (!query) {
    inlineError.value = '请输入学号或姓名后再查询。'
    await focusInput()
    return
  }

  attendanceSuccess.value = null
  resetTimer.cancel()
  inlineError.value = ''
  busy.value = true
  try {
    lookupResult.value = await api(`/api/public/attendance/lookup?query=${encodeURIComponent(query)}`)
    memberQuery.value = query
    emit('health-change', true)
    if (pendingAction.value?.type === 'lookup') pendingAction.value = null
  } catch (error) {
    handleFailure(error, {
      type: 'lookup',
      query,
      message: '输入内容已保留，连接恢复后会自动重新查询。'
    })
    await focusInput()
  } finally {
    busy.value = false
  }
}

async function selectLookupCandidate(candidate) {
  memberQuery.value = candidate.studentNo
  inlineError.value = ''
  await lookupMember()
}

async function submitAttendance(retryAction = null) {
  const pending = retryAction?.type === 'submit' ? retryAction : null
  const result = pending?.lookupResult || lookupResult.value
  const studentNo = pending?.studentNo || lookupResult.value?.studentNo || memberQuery.value
  if (!studentNo) {
    inlineError.value = '请先查询并确认成员。'
    await focusInput()
    return
  }

  const requestId = pending?.requestId || createKioskRequestId()
  inlineError.value = ''
  busy.value = true
  try {
    const response = await post('/api/public/attendance/submit', { studentNo, requestId })
    emit('health-change', true)
    pendingAction.value = null
    attendanceSuccess.value = {
      name: response.name || result?.name || studentNo,
      actionLabel: response.action === 'CHECK_OUT' ? '签退' : '签到',
      message: response.message
    }
    lookupResult.value = null
    memberQuery.value = ''
    resetTimer.start()
  } catch (error) {
    handleFailure(error, {
      type: 'submit',
      studentNo,
      requestId,
      lookupResult: result ? { ...result } : null,
      message: '确认信息已保留，连接恢复后会使用同一提交编号安全重试。'
    })
  } finally {
    busy.value = false
  }
}

function resetKiosk() {
  resetTimer.cancel()
  attendanceSuccess.value = null
  lookupResult.value = null
  memberQuery.value = ''
  inlineError.value = ''
  pendingAction.value = null
  resetSeconds.value = 4
  void focusInput()
}

function handleFailure(error, retryAction) {
  if (error?.isNetworkError) {
    emit('health-change', false)
    inlineError.value = ''
    pendingAction.value = retryAction
    return
  }
  pendingAction.value = null
  inlineError.value = `${error?.message || '请求失败'} 请重新查询；问题仍存在时请联系管理员。`
}

async function retryPendingAction() {
  const pending = pendingAction.value
  if (!pending || busy.value) return
  if (pending.type === 'submit') await submitAttendance(pending)
  else await lookupMember(pending)
}

async function focusInput() {
  await nextTick()
  if (!attendanceSuccess.value) inputRef.value?.focus()
}

function handleStateEntered() {
  if (!attendanceSuccess.value) void focusInput()
}

function dutyPeriodProgress(period) {
  if (!period?.active) return 0
  const start = timeToMinutes(period.startTime)
  const end = timeToMinutes(period.endTime)
  const now = new Date()
  const current = now.getHours() * 60 + now.getMinutes()
  if (start === null || end === null || end <= start) return 0
  return Math.min(100, Math.max(0, ((current - start) / (end - start)) * 100))
}
</script>

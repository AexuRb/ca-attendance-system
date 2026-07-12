<template>
  <section class="work-section tab-profile">
    <div class="section-head"><h3>个人中心</h3></div>
    <div class="profile-grid">
      <div class="profile-card">
        <div class="subsection-head">
          <h4>个人资料</h4>
        </div>
        <form class="profile-form" @input="$emit('dirty-change', { form: 'profile', dirty: true })" @change="$emit('dirty-change', { form: 'profile', dirty: true })" @submit.prevent="saveProfile">
          <label for="profilePhone"><span>手机号</span><input id="profilePhone" v-model.trim="profile.phone" name="phone" inputmode="tel" autocomplete="tel" /></label>
          <label for="profileMajor"><span>学院</span><input id="profileMajor" v-model.trim="profile.major" name="major" autocomplete="organization" /></label>
          <label for="profileGrade">
            <span>年级</span>
            <select id="profileGrade" v-model="profile.grade" name="grade">
              <option value="">未填写</option>
              <option v-for="grade in gradeOptions" :key="grade" :value="grade">{{ grade }}</option>
            </select>
          </label>
          <label for="profileQq"><span>QQ</span><input id="profileQq" v-model.trim="profile.qq" name="qq" inputmode="numeric" autocomplete="off" /></label>
          <button class="primary-action" type="submit" :disabled="busy"><Save :size="18" /><span>保存资料</span></button>
        </form>

        <details class="password-disclosure">
          <summary>修改密码</summary>
          <form class="profile-form password-form" @input="$emit('dirty-change', { form: 'password', dirty: true })" @submit.prevent="requestPasswordChange">
            <label for="profileOldPassword"><span>原密码</span><input id="profileOldPassword" v-model="passwordForm.oldPassword" name="currentPassword" type="password" autocomplete="current-password" /></label>
            <label for="profileNewPassword"><span>新密码</span><input id="profileNewPassword" v-model="passwordForm.newPassword" name="newPassword" type="password" autocomplete="new-password" minlength="6" /></label>
            <label for="profileConfirmPassword"><span>确认新密码</span><input id="profileConfirmPassword" v-model="passwordForm.confirmPassword" name="confirmPassword" type="password" autocomplete="new-password" minlength="6" /></label>
            <button class="primary-action" type="submit" :disabled="busy"><Save :size="18" /><span>修改密码</span></button>
          </form>
        </details>
      </div>

      <div class="records-card">
        <div class="subsection-head">
          <h4>我的值班记录</h4>
          <button class="ghost-button" type="button" :disabled="busy" @click="loadMyRecords"><RefreshCw :size="16" />刷新</button>
        </div>
        <div class="filters">
          <label class="filter-field" for="myRecordsFrom"><span>开始日期</span><input id="myRecordsFrom" v-model="myRecordRange.from" name="from" type="date" /></label>
          <label class="filter-field" for="myRecordsTo"><span>结束日期</span><input id="myRecordsTo" v-model="myRecordRange.to" name="to" type="date" /></label>
          <button class="ghost-button" type="button" :disabled="busy" @click="loadMyRecords">查询</button>
        </div>
        <div class="mini-summary">
          <div><span>记录数</span><strong>{{ myRecordCount }}</strong></div>
          <div><span>有效时长</span><strong>{{ formatHours(myRecordHours) }} h</strong></div>
        </div>
        <div class="table-wrap compact-table">
          <table>
            <thead>
              <tr><th>日期</th><th>签到</th><th>签退</th><th>审核</th><th>状态</th><th>有效时长</th></tr>
            </thead>
            <tbody>
              <tr v-for="item in myRecords" :key="item.id">
                <td>{{ item.dutyDate }}</td>
                <td>{{ timeText(item.checkInTime) }}</td>
                <td>{{ timeText(item.checkOutTime) }}</td>
                <td>{{ statusText(item.checkInStatus) }} / {{ statusText(item.checkOutStatus) }}</td>
                <td><span class="status-badge" :class="item.effectiveStatus?.toLowerCase()">{{ effectiveStatusText(item.effectiveStatus) }}</span></td>
                <td>{{ formatHours(item.validHours) }} h</td>
              </tr>
              <tr v-if="myRecords.length === 0"><td colspan="6" class="empty">暂无值班记录</td></tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { RefreshCw, Save } from '@lucide/vue'
import { api, put } from '../api.js'
import { adminModuleLocation, tabFromRoute } from '../app/router.js'
import { compactQuery, queryDate } from '../features/navigation/queryState.js'

const emit = defineEmits(['notify', 'password-change', 'dirty-change'])
const route = useRoute()
const router = useRouter()
const today = new Date()
const todayValue = formatLocalDate(today)
const yearStart = `${today.getFullYear()}-01-01`
const pendingOperations = ref(0)
const busy = computed(() => pendingOperations.value > 0)
const profile = reactive({ phone: '', major: '', grade: '', qq: '' })
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const myRecordRange = reactive({ from: yearStart, to: todayValue })
const myRecords = ref([])
const myTrainingHours = ref(0)
const myTrainingCount = ref(0)
const gradeOptions = Array.from({ length: 2057 - 2007 + 1 }, (_, index) => `${2007 + index}级`)
const myRecordHours = computed(() => (
  myRecords.value.reduce((sum, row) => sum + Number(row.validHours || 0), 0) + Number(myTrainingHours.value || 0)
))
const myRecordCount = computed(() => myRecords.value.length + Number(myTrainingCount.value || 0))
let appliedRangeKey = ''

onMounted(loadProfile)

watch(() => route.fullPath, () => {
  if (tabFromRoute(route) !== 'profile') return
  const from = queryDate(route.query, 'from', yearStart)
  const to = queryDate(route.query, 'to', todayValue)
  const rangeKey = `${from}|${to}`
  if (rangeKey === appliedRangeKey) return
  appliedRangeKey = rangeKey
  myRecordRange.from = from
  myRecordRange.to = to
  void loadMyRecords({ syncQuery: false })
}, { immediate: true })

async function loadProfile() {
  await run(async () => {
    const me = await api('/api/auth/me')
    profile.phone = me.phone || ''
    profile.major = me.major || ''
    profile.grade = me.grade || ''
    profile.qq = me.qq || ''
  }, false)
}

async function saveProfile() {
  await run(async () => {
    await put('/api/me/profile', profile)
    emit('dirty-change', { form: 'profile', dirty: false })
    emit('notify', { message: '资料已保存', type: 'success' })
  })
}

async function loadMyRecords(options = {}) {
  await run(async () => {
    const [records, training] = await Promise.all([
      api(`/api/attendance/me?from=${myRecordRange.from}&to=${myRecordRange.to}`),
      api(`/api/trainings/me/hours?from=${myRecordRange.from}&to=${myRecordRange.to}`)
    ])
    myRecords.value = records
    myTrainingHours.value = Number(training.trainingHours || 0)
    myTrainingCount.value = Number(training.trainingCount || 0)
    if (options.syncQuery !== false && tabFromRoute(route) === 'profile') {
      appliedRangeKey = `${myRecordRange.from}|${myRecordRange.to}`
      const location = adminModuleLocation('profile', compactQuery({
        from: myRecordRange.from,
        to: myRecordRange.to
      }))
      if (router.resolve(location).fullPath !== route.fullPath) await router.replace(location)
    }
  }, false)
}

function requestPasswordChange() {
  emit('password-change', { ...passwordForm })
}

async function run(action, showError = true) {
  pendingOperations.value += 1
  try {
    await action()
  } catch (error) {
    if (showError) emit('notify', { message: error.message, type: 'error' })
  } finally {
    pendingOperations.value = Math.max(0, pendingOperations.value - 1)
  }
}

function statusText(status) {
  return {
    NOT_SUBMITTED: '未提交',
    PENDING: '待审核',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    AUTO_APPROVED: '自动通过'
  }[status] || status
}

function effectiveStatusText(status) {
  return {
    PENDING: '待处理',
    VALID: '有效',
    INVALID: '无效',
    INCOMPLETE: '未签退'
  }[status] || status
}

function timeText(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

function formatHours(value) {
  const number = Number(value || 0)
  return Number.isInteger(number) ? String(number) : number.toFixed(2).replace(/0+$/, '').replace(/\.$/, '')
}

function formatLocalDate(value) {
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
</script>

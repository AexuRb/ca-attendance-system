<template>
  <section class="work-section tab-records">
    <div class="section-head">
      <h3>签到记录</h3>
      <div class="section-actions">
        <button v-if="canManageRecords" class="ghost-button" :class="{ active: showCreateForm }" @click="toggleCreateForm">
          <ClipboardCheck :size="16" />新增记录
        </button>
        <button class="ghost-button" :disabled="busy" @click="loadRecords"><RefreshCw :size="16" />刷新</button>
      </div>
    </div>

    <div v-if="showCreateForm" class="inline-form-block record-action-panel">
      <div class="subsection-head">
        <h4>新增签到记录</h4>
        <span>会长和管理员可补录，新增后自动计算有效时长</span>
      </div>
      <form class="record-create-form" novalidate @input="setDirty(true)" @change="setDirty(true)" @submit.prevent="createRecord">
        <label for="manualStudentNo"><span>成员学号</span><input id="manualStudentNo" v-model.trim="form.studentNo" name="studentNo" inputmode="numeric" autocomplete="off" :aria-invalid="Boolean(errors.studentNo)" required /><small v-if="errors.studentNo" class="field-error">{{ errors.studentNo }}</small></label>
        <label for="manualCheckIn"><span>签到时间</span><input id="manualCheckIn" v-model="form.checkInTime" name="checkInTime" type="datetime-local" :aria-invalid="Boolean(errors.checkInTime)" required /><small v-if="errors.checkInTime" class="field-error">{{ errors.checkInTime }}</small></label>
        <label for="manualCheckOut"><span>签退时间</span><input id="manualCheckOut" v-model="form.checkOutTime" name="checkOutTime" type="datetime-local" :aria-invalid="Boolean(errors.checkOutTime)" /><small v-if="errors.checkOutTime" class="field-error">{{ errors.checkOutTime }}</small></label>
        <label for="manualReason"><span>补录原因</span><input id="manualReason" v-model.trim="form.reason" name="reason" autocomplete="off" :aria-invalid="Boolean(errors.reason)" required /><small v-if="errors.reason" class="field-error">{{ errors.reason }}</small></label>
        <button class="primary-action" type="submit" :disabled="busy"><ClipboardCheck :size="18" /><span>添加记录</span></button>
        <button class="ghost-button" type="button" @click="cancelCreateForm">取消</button>
      </form>
    </div>

    <div class="filters record-filters">
      <label class="filter-field" for="recordKeyword"><span>关键词</span><input id="recordKeyword" v-model.trim="filters.keyword" name="keyword" autocomplete="off" placeholder="学号或姓名" @keyup.enter="loadRecords" /></label>
      <label class="filter-field" for="recordStatus"><span>状态</span><select id="recordStatus" v-model="filters.status" name="status">
        <option value="">全部状态</option>
        <option v-for="item in effectiveStatusOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
      </select></label>
      <label class="filter-field" for="recordFrom"><span>开始日期</span><input id="recordFrom" v-model="filters.from" name="from" type="date" /></label>
      <label class="filter-field" for="recordTo"><span>结束日期</span><input id="recordTo" v-model="filters.to" name="to" type="date" /></label>
      <button class="ghost-button" :disabled="busy" @click="loadRecords">查询</button>
    </div>

    <div class="record-summary-strip">
      <div><span>记录数</span><strong>{{ records.length }}</strong></div>
      <div><span>有效时长</span><strong>{{ formatHours(recordHours) }} h</strong></div>
      <div><span>待审核项</span><strong>{{ pendingCount }}</strong></div>
    </div>

    <div class="table-wrap records-table-wrap">
      <table class="records-table">
        <thead>
          <tr>
            <th class="record-action-column">操作</th><th>日期</th><th>姓名</th><th>学号</th><th>签到</th><th>签退</th>
            <th>签到审核</th><th>签退审核</th><th>状态</th><th>有效时长</th><th>来源</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in records" :key="item.id">
            <td class="actions record-action-column">
              <button v-if="canManageRecords" class="danger" @click="deleteRecord(item)"><Trash2 :size="14" />删除</button>
              <span v-else class="muted-cell">-</span>
            </td>
            <td>{{ item.dutyDate }}</td><td>{{ item.name }}</td><td class="mono">{{ item.studentNo }}</td>
            <td>{{ timeText(item.checkInTime) }}</td><td>{{ timeText(item.checkOutTime) }}</td>
            <td>{{ statusText(item.checkInStatus) }}</td><td>{{ statusText(item.checkOutStatus) }}</td>
            <td><span class="status-badge" :class="item.effectiveStatus?.toLowerCase()">{{ effectiveStatusText(item.effectiveStatus) }}</span></td>
            <td>{{ item.validHours }} h</td><td>{{ sourceText(item.source) }}</td>
          </tr>
          <tr v-if="records.length === 0"><td colspan="11" class="empty">暂无签到记录</td></tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ClipboardCheck, RefreshCw, Trash2 } from '@lucide/vue'
import { api, del, post } from '../api.js'
import { compactQuery, queryDate, queryOneOf, queryText } from '../features/navigation/queryState.js'
import { requestConfirmation } from '../shared/confirm.js'

const props = defineProps({ currentUser: { type: Object, required: true } })
const emit = defineEmits(['notify', 'dirty-change'])
const route = useRoute()
const router = useRouter()

const today = new Date()
const todayValue = formatLocalDate(today)
const yearStart = `${today.getFullYear()}-01-01`
const effectiveStatusOptions = [
  { value: 'VALID', label: '有效' },
  { value: 'PENDING', label: '待审核' },
  { value: 'INCOMPLETE', label: '未签退' },
  { value: 'INVALID', label: '无效' }
]

const busy = ref(false)
const records = ref([])
const showCreateForm = ref(false)
const dirty = ref(false)
const lastAppliedRoute = ref('')
const filters = reactive({ keyword: '', status: '', from: yearStart, to: todayValue })
const form = reactive(emptyForm())
const errors = reactive({ studentNo: '', checkInTime: '', checkOutTime: '', reason: '' })

const canManageRecords = computed(() => ['PRESIDENT', 'ADMIN'].includes(props.currentUser.role))
const recordHours = computed(() => records.value.reduce((sum, row) => sum + Number(row.validHours || 0), 0))
const pendingCount = computed(() => records.value.filter(row => row.checkInStatus === 'PENDING' || row.checkOutStatus === 'PENDING').length)

onMounted(async () => {
  hydrateRouteQuery()
  await loadRecords(false)
})

watch(() => route.fullPath, async fullPath => {
  if (!route.path.endsWith('/records') || fullPath === lastAppliedRoute.value) return
  hydrateRouteQuery()
  await loadRecords(false)
})

onBeforeUnmount(() => emit('dirty-change', false))

function hydrateRouteQuery() {
  filters.keyword = queryText(route.query, 'q')
  filters.status = queryOneOf(route.query, 'status', ['', ...effectiveStatusOptions.map(item => item.value)])
  filters.from = queryDate(route.query, 'from', yearStart)
  filters.to = queryDate(route.query, 'to', todayValue)
}

async function syncRouteQuery() {
  const location = { path: route.path, query: compactQuery({ q: filters.keyword, status: filters.status, from: filters.from, to: filters.to }) }
  const resolved = router.resolve(location)
  if (resolved.fullPath === route.fullPath) return
  lastAppliedRoute.value = resolved.fullPath
  await router.replace(location)
}

async function loadRecords(syncRoute = true) {
  await run(async () => {
    const params = new URLSearchParams({ from: filters.from || yearStart, to: filters.to || todayValue })
    if (filters.keyword) params.set('studentNo', filters.keyword)
    if (filters.status) params.set('status', filters.status)
    records.value = await api(`/api/attendance?${params}`)
    if (syncRoute) await syncRouteQuery()
  }, false)
}

async function createRecord() {
  if (!canManageRecords.value) return notify('只有会长或管理员可以添加签到记录', 'warn')
  if (!await validateForm()) return
  await run(async () => {
    await post('/api/attendance/manual', {
      studentNo: form.studentNo,
      checkInTime: form.checkInTime,
      checkOutTime: form.checkOutTime || null,
      reason: form.reason
    })
    cancelCreateForm()
    notify('签到记录已添加', 'success')
    await loadRecords()
  })
}

function toggleCreateForm() {
  if (showCreateForm.value) cancelCreateForm()
  else showCreateForm.value = true
}

function cancelCreateForm() {
  Object.assign(form, emptyForm())
  Object.keys(errors).forEach(key => { errors[key] = '' })
  showCreateForm.value = false
  setDirty(false)
}

async function validateForm() {
  errors.studentNo = form.studentNo ? '' : '请填写成员学号'
  errors.checkInTime = form.checkInTime ? '' : '请选择签到时间'
  errors.reason = form.reason ? '' : '请填写补录原因'
  errors.checkOutTime = form.checkOutTime && form.checkOutTime <= form.checkInTime ? '签退时间必须晚于签到时间' : ''
  const id = errors.studentNo ? 'manualStudentNo' : errors.checkInTime ? 'manualCheckIn' : errors.checkOutTime ? 'manualCheckOut' : errors.reason ? 'manualReason' : ''
  if (!id) return true
  await nextTick()
  document.getElementById(id)?.focus()
  notify(errors.studentNo || errors.checkInTime || errors.checkOutTime || errors.reason, 'warn')
  return false
}

async function deleteRecord(item) {
  if (!canManageRecords.value) return notify('只有会长或管理员可以删除签到记录', 'warn')
  const timeLabel = item.checkInTime ? timeText(item.checkInTime) : item.dutyDate
  const confirmed = await requestConfirmation({
    title: '删除确认',
    message: `确认删除 ${item.name}（${item.studentNo}）在 ${timeLabel} 的签到记录？删除后无法恢复。`,
    confirmLabel: '删除',
    requiredText: '删除'
  })
  if (!confirmed) return
  await run(async () => {
    await del(`/api/attendance/${item.id}`)
    notify('签到记录已删除，删除前已自动备份', 'success')
    await loadRecords()
  })
}

function setDirty(value) {
  if (dirty.value === value) return
  dirty.value = value
  emit('dirty-change', value)
}

async function run(action, showError = true) {
  busy.value = true
  try { await action() } catch (error) { if (showError) notify(error.message, 'error') } finally { busy.value = false }
}

function notify(message, type = 'info') { emit('notify', { message, type }) }
function emptyForm() { return { studentNo: '', checkInTime: '', checkOutTime: '', reason: '' } }
function formatLocalDate(value) { return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}` }
function timeText(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '-' }
function formatHours(value) { const number = Number(value || 0); return Number.isInteger(number) ? String(number) : number.toFixed(2).replace(/0+$/, '').replace(/\.$/, '') }
function statusText(status) { return { NOT_SUBMITTED: '未提交', PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回', AUTO_APPROVED: '自动通过' }[status] || status }
function effectiveStatusText(status) { return { VALID: '有效', PENDING: '待审核', INCOMPLETE: '未签退', INVALID: '无效' }[status] || status }
function sourceText(source) { return { PUBLIC: '公开提交', ADMIN_MANUAL: '后台手动' }[source] || source || '-' }
</script>

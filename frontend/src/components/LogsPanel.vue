<template>
  <section class="work-section tab-logs">
    <div class="section-head">
      <h3>操作日志</h3>
      <div class="section-actions">
        <button class="ghost-button" :disabled="busy" @click="exportLogs"><Download :size="16" />导出日志</button>
        <button class="ghost-button danger-button" :disabled="busy" @click="clearLogs"><Trash2 :size="16" />清空日志</button>
        <button class="ghost-button" :disabled="busy" @click="loadLogs(page)"><RefreshCw :size="16" />刷新</button>
      </div>
    </div>

    <div class="filters log-filters">
      <label class="filter-field log-search-field" for="logKeyword"><span>关键词</span><input id="logKeyword" class="log-search" v-model.trim="filters.keyword" name="keyword" autocomplete="off" placeholder="操作人、学号或原因" @keyup.enter="loadLogs(1)" /></label>
      <label class="filter-field" for="logAction"><span>操作类型</span><select id="logAction" class="log-action-select" v-model="filters.actionType" name="actionType" @change="loadLogs(1)">
        <option value="">全部操作</option><option v-for="item in actionOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
      </select></label>
      <label class="filter-field" for="logFrom"><span>开始日期</span><input id="logFrom" v-model="filters.from" name="from" type="date" /></label>
      <label class="filter-field" for="logTo"><span>结束日期</span><input id="logTo" v-model="filters.to" name="to" type="date" /></label>
      <button class="ghost-button" :disabled="busy" @click="loadLogs(1)">查询</button>
    </div>

    <div class="table-wrap">
      <table class="log-table">
        <thead><tr><th>时间</th><th>操作人</th><th>操作</th><th>对象</th><th>原因</th><th>详情</th></tr></thead>
        <tbody>
          <tr v-for="item in logs" :key="item.id">
            <td class="mono">{{ timeText(item.createdAt) }}</td><td>{{ operatorText(item) }}</td><td>{{ actionLabel(item.actionType) }}</td>
            <td class="mono">{{ targetText(item) }}</td><td class="log-reason">{{ item.reason || '-' }}</td>
            <td class="actions"><button @click="selectedLog = selectedLog?.id === item.id ? null : item">详情</button></td>
          </tr>
          <tr v-if="logs.length === 0"><td colspan="6" class="empty">暂无操作日志</td></tr>
        </tbody>
      </table>
    </div>

    <div class="pagination-bar">
      <button class="ghost-button" :disabled="page <= 1 || busy" @click="changePage(-1)">上一页</button>
      <span>第 {{ page }} / {{ totalPages }} 页，共 {{ total }} 条</span>
      <button class="ghost-button" :disabled="page >= totalPages || busy" @click="changePage(1)">下一页</button>
    </div>

    <div v-if="selectedLog" class="log-detail-panel">
      <div class="subsection-head"><h4>{{ actionLabel(selectedLog.actionType) }} · {{ timeText(selectedLog.createdAt) }}</h4><button class="ghost-button" @click="selectedLog = null">收起</button></div>
      <div class="log-detail-grid">
        <div><h5>修改前</h5><pre>{{ prettyLogData(selectedLog.beforeData) }}</pre></div>
        <div><h5>修改后</h5><pre>{{ prettyLogData(selectedLog.afterData) }}</pre></div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Download, RefreshCw, Trash2 } from '@lucide/vue'
import { api, del } from '../api.js'
import { compactQuery, queryDate, queryPositiveInt, queryText } from '../features/navigation/queryState.js'
import { requestConfirmation } from '../shared/confirm.js'

const props = defineProps({ currentUser: { type: Object, required: true } })
const emit = defineEmits(['notify'])
const route = useRoute()
const router = useRouter()
const today = new Date()
const todayValue = formatLocalDate(today)
const yearStart = `${today.getFullYear()}-01-01`
const pageSize = 20

const busy = ref(false)
const logs = ref([])
const total = ref(0)
const page = ref(1)
const selectedLog = ref(null)
const lastAppliedRoute = ref('')
const filters = reactive({ keyword: '', actionType: '', from: yearStart, to: todayValue })
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

const actionOptions = [
  ['INITIALIZE_SYSTEM', '初始化系统'], ['CREATE_USER', '新增成员'], ['IMPORT_USERS', '批量导入成员'], ['UPDATE_USER', '修改成员信息'],
  ['RESET_PASSWORD', '重置密码'], ['DELETE_USER', '删除成员'], ['BULK_UPDATE_USER_STATUS', '批量启停账号'], ['REVIEW_ATTENDANCE', '审核签到记录'],
  ['MANUAL_CREATE_ATTENDANCE', '新增签到记录'], ['DELETE_ATTENDANCE_RECORD', '删除签到记录'], ['CREATE_TRAINING', '新增培训'], ['UPDATE_TRAINING', '修改培训'],
  ['ARCHIVE_TRAINING', '归档培训'], ['CREATE_TRAINING_PARTICIPANT', '新增培训参与记录'], ['UPDATE_TRAINING_PARTICIPANT', '修改培训参与记录'],
  ['DELETE_TRAINING_PARTICIPANT', '删除培训参与记录'], ['IMPORT_TRAINING_PARTICIPANTS', '导入培训名单'], ['CREATE_DUTY_SCHEDULE', '新增排班'],
  ['UPDATE_DUTY_SCHEDULE', '修改排班'], ['ARCHIVE_DUTY_SCHEDULE', '归档排班'], ['IMPORT_DUTY_SCHEDULES', '批量导入排班'],
  ['UPDATE_DUTY_PERIODS', '调整值班时间段'], ['CREATE_REPAIR_CASE', '新增维修事务'], ['UPDATE_REPAIR_CASE', '修改维修事务'],
  ['DELETE_REPAIR_CASE', '删除维修事务'], ['RESTORE_REPAIR_CASE', '恢复维修事务'], ['PURGE_REPAIR_CASE', '永久删除维修事务'],
  ['UPDATE_DUTY_WEEKDAYS', '调整值班星期'], ['MANUAL_UPDATE_ATTENDANCE', '手动修改记录'], ['EXPORT_CUSTOM_DATA', '自定义导出数据'],
  ['RESTORE_BACKUP', '恢复备份']
].map(([value, label]) => ({ value, label }))

onMounted(async () => { hydrateRouteQuery(); await loadLogs(page.value, false) })
watch(() => route.fullPath, async fullPath => {
  if (!route.path.endsWith('/logs') || fullPath === lastAppliedRoute.value) return
  hydrateRouteQuery()
  await loadLogs(page.value, false)
})

function hydrateRouteQuery() {
  filters.keyword = queryText(route.query, 'q')
  filters.actionType = queryText(route.query, 'action')
  filters.from = queryDate(route.query, 'from', yearStart)
  filters.to = queryDate(route.query, 'to', todayValue)
  page.value = queryPositiveInt(route.query, 'page', 1)
}

async function syncRouteQuery() {
  const location = { path: route.path, query: compactQuery({ q: filters.keyword, action: filters.actionType, from: filters.from, to: filters.to, page: page.value }) }
  const resolved = router.resolve(location)
  if (resolved.fullPath === route.fullPath) return
  lastAppliedRoute.value = resolved.fullPath
  await router.replace(location)
}

async function loadLogs(targetPage = page.value, syncRoute = true) {
  await run(async () => {
    const params = new URLSearchParams({ page: String(targetPage), pageSize: String(pageSize) })
    if (filters.keyword) params.set('keyword', filters.keyword)
    if (filters.actionType) params.set('actionType', filters.actionType)
    if (filters.from) params.set('from', filters.from)
    if (filters.to) params.set('to', filters.to)
    const result = await api(`/api/logs?${params}`)
    logs.value = result.items
    total.value = result.total
    page.value = result.page
    selectedLog.value = null
    if (syncRoute) await syncRouteQuery()
  }, false)
}

async function exportLogs() {
  await run(async () => {
    const params = new URLSearchParams()
    if (filters.keyword) params.set('keyword', filters.keyword)
    if (filters.actionType) params.set('actionType', filters.actionType)
    if (filters.from) params.set('from', filters.from)
    if (filters.to) params.set('to', filters.to)
    downloadBlob(await api(`/api/logs/export?${params}`), `操作日志_${filters.from || '开始'}_${filters.to || '结束'}.xlsx`)
  })
}

async function clearLogs() {
  if (props.currentUser.role !== 'ADMIN') return notify('只有管理员可以清空操作日志', 'warn')
  const confirmed = await requestConfirmation({ title: '清空日志确认', message: '确认清空全部操作日志？建议先导出日志留档。该操作不可恢复。', confirmLabel: '清空日志', requiredText: '清空日志' })
  if (!confirmed) return
  await run(async () => {
    const result = await del('/api/logs')
    logs.value = []
    total.value = 0
    page.value = 1
    selectedLog.value = null
    const backup = result?.safetyBackup ? `，清空前备份：${result.safetyBackup.filename}` : ''
    notify(`已清空 ${result?.deleted || 0} 条日志${backup}`, 'success')
  })
}

async function changePage(delta) {
  const next = Math.min(Math.max(1, page.value + delta), totalPages.value)
  if (next !== page.value) await loadLogs(next)
}

async function run(action, showError = true) { busy.value = true; try { await action() } catch (error) { if (showError) notify(error.message, 'error') } finally { busy.value = false } }
function notify(message, type = 'info') { emit('notify', { message, type }) }
function actionLabel(action) { return actionOptions.find(item => item.value === action)?.label || action }
function operatorText(item) { return !item.operatorName && !item.operatorStudentNo ? '-' : item.operatorStudentNo ? `${item.operatorName || '-'}（${item.operatorStudentNo}）` : item.operatorName }
function targetText(item) { return item.targetId ? `${item.targetType}#${item.targetId}` : (item.targetType || '-') }
function prettyLogData(value) { if (!value) return '-'; try { return JSON.stringify(JSON.parse(value), null, 2) } catch { return String(value) } }
function timeText(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '-' }
function formatLocalDate(value) { return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}` }
function downloadBlob(blob, filename) { const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = filename; link.click(); URL.revokeObjectURL(url) }
</script>

<template>
  <section class="work-section tab-schedules">
    <div class="section-head">
      <div>
        <h3>排班表</h3>
        <span>维护公开签到页显示的每周值班安排</span>
      </div>
      <div class="section-actions">
        <button v-if="canManageSchedules" class="ghost-button" :disabled="busy" @click="downloadImportTemplate">
          <Download :size="16" />模板
        </button>
        <button v-if="canManageSchedules" class="ghost-button" :disabled="busy" @click="openImportPanel">
          <Upload :size="16" />批量导入
        </button>
        <button
          v-if="canManageSchedules"
          class="ghost-button"
          :disabled="!dutyPeriodOptions.length"
          :title="dutyPeriodOptions.length ? '新增排班' : '请先在值班设置中保存值班时间段'"
          @click="startCreate"
        >
          <Plus :size="16" />新排班
        </button>
        <button class="ghost-button" @click="loadSchedules"><RefreshCw :size="16" />刷新</button>
      </div>
    </div>

    <div v-if="canManageSchedules && showImport" class="inline-form-block schedule-import-panel">
      <div class="subsection-head">
        <div>
          <h4><FileSpreadsheet :size="17" />批量导入排班</h4>
          <span>先预览校验，再覆盖文件中出现的星期和时段</span>
        </div>
        <button class="ghost-button" type="button" :disabled="busy" @click="cancelImport">关闭</button>
      </div>

      <div class="schedule-import-controls">
        <label class="schedule-import-file">
          <span>Excel 文件</span>
          <input ref="importInput" type="file" accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" @change="selectImportFile" />
        </label>
        <button class="primary-action" type="button" :disabled="busy || !importFile" @click="previewImport">
          <FileSearch :size="16" />校验预览
        </button>
      </div>

      <div v-if="importPreview" class="schedule-import-result" :class="{ invalid: !importPreview.valid }">
        <div class="schedule-import-summary">
          <div><span>分组</span><strong>{{ importPreview.groupCount }}</strong></div>
          <div><span>人员</span><strong>{{ importPreview.memberCount }}</strong></div>
          <div><span>校验</span><strong>{{ importPreview.valid ? '通过' : `${importPreview.issues.length} 处错误` }}</strong></div>
        </div>

        <div v-if="importPreview.issues.length" class="schedule-import-issues" role="alert">
          <div v-for="(issue, index) in importPreview.issues" :key="`${issue.row}-${issue.field}-${index}`">
            <AlertTriangle :size="15" />
            <span>{{ issue.row ? `第 ${issue.row} 行` : '文件' }}：{{ issue.message }}</span>
          </div>
        </div>

        <div v-if="importPreview.groups.length" class="schedule-import-groups">
          <article v-for="group in importPreview.groups" :key="`${group.weekday}-${group.startTime}-${group.endTime}`">
            <header>
              <strong>{{ group.weekdayName }} · {{ group.startTime }}-{{ group.endTime }}</strong>
              <span>{{ group.members.length }} 人</span>
            </header>
            <div>
              <span v-for="member in group.members" :key="member.studentNo">{{ member.name }} <small>{{ member.studentNo }}</small></span>
            </div>
          </article>
        </div>

        <div class="schedule-import-actions">
          <button class="ghost-button" type="button" :disabled="busy" @click="cancelImport">取消</button>
          <button class="primary-action" type="button" :disabled="busy || !importPreview.valid" @click="confirmImport">
            <Upload :size="16" />确认导入
          </button>
        </div>
      </div>
    </div>

    <div v-if="canManageSchedules && showForm" class="inline-form-block schedule-form-panel">
      <div class="subsection-head">
        <h4><CalendarDays :size="17" />{{ editingId ? '编辑排班' : '新增排班' }}</h4>
        <span>成员每行一个，可写“学号 姓名”或只写姓名；学号存在时自动使用成员表姓名</span>
      </div>
      <form class="schedule-form-grid" @submit.prevent="saveSchedule">
        <select v-model.number="form.weekday">
          <option v-for="day in formWeekdayOptions" :key="day.value" :value="day.value">{{ day.label }}</option>
        </select>
        <select v-model="form.periodKey" @change="applySelectedPeriod">
          <option disabled value="">{{ dutyPeriodOptions.length ? '选择值班时段' : '请先设置值班时段' }}</option>
          <option v-for="period in dutyPeriodOptions" :key="period.key" :value="period.key">{{ period.timeText }}</option>
        </select>
        <input v-model.trim="form.title" placeholder="标题，例如 日常值班" />
        <input v-model.trim="form.location" placeholder="地点，可选" />
        <label class="schedule-enabled-toggle">
          <input v-model="form.enabled" type="checkbox" />
          <span>显示在签到页</span>
        </label>
        <input class="schedule-note-input" v-model.trim="form.note" placeholder="备注，可选" />
        <textarea v-model="form.assigneeText" rows="5" placeholder="每行一个值班成员&#10;2025000001 张三&#10;李四"></textarea>
        <div class="schedule-form-actions">
          <button class="primary-action" type="submit" :disabled="busy || !form.title || !form.periodKey || !dutyPeriodOptions.length">
            <Save :size="16" />保存排班
          </button>
          <button class="ghost-button" type="button" @click="cancelForm">取消</button>
        </div>
      </form>
    </div>

    <div class="schedule-week-grid">
      <section v-for="day in displayWeekdayOptions" :key="day.value" class="schedule-day-column" :class="{ disabled: !day.enabled }">
        <div class="schedule-day-head">
          <strong>{{ day.label }}</strong>
          <span>{{ day.enabled ? `${activeGroupCount(day.value)} 个时间段` : `未启用 · ${activeGroupCount(day.value)} 个时间段` }}</span>
        </div>
        <section v-for="group in scheduleGroupsByWeekday(day.value)" :key="group.key" class="schedule-period-group">
          <div class="schedule-period-head">
            <strong>{{ group.timeText }}</strong>
            <span>{{ group.peopleCount }} 人</span>
          </div>
          <article v-for="slot in group.slots" :key="`${group.key}-${slot.id}`" class="schedule-slot-card" :class="{ muted: !slot.enabled }">
            <div class="schedule-slot-top">
              <div>
                <h4>{{ slot.title }}</h4>
              </div>
              <span class="schedule-state">{{ slot.enabled ? '显示' : '隐藏' }}</span>
            </div>
            <p>{{ [slot.location, slot.note].filter(Boolean).join(' · ') || '未填写地点/备注' }}</p>
            <div class="schedule-assignees">
              <span v-for="person in slot.assignees" :key="person.id">{{ person.name }}</span>
              <span v-if="slot.assignees.length === 0">未安排人员</span>
            </div>
            <div v-if="canManageSchedules" class="schedule-card-actions">
              <button class="ghost-button" @click="startEdit(slot)">编辑</button>
              <button class="ghost-button danger-button" @click="archiveSchedule(slot)"><Trash2 :size="15" />归档</button>
            </div>
          </article>
          <div v-if="group.slots.length === 0" class="empty schedule-empty">暂无排班</div>
        </section>
        <div v-if="schedulesByWeekday(day.value).length === 0 && !dutyPeriodOptions.length" class="empty schedule-empty">暂无排班</div>
        <section v-if="unclassifiedSchedules(day.value).length" class="schedule-period-group warning">
          <div class="schedule-period-head">
            <strong>未匹配时间段</strong>
            <span>{{ unclassifiedPeopleCount(day.value) }} 人</span>
          </div>
          <article v-for="slot in unclassifiedSchedules(day.value)" :key="`unclassified-${slot.id}`" class="schedule-slot-card" :class="{ muted: !slot.enabled }">
            <div class="schedule-slot-top">
              <div>
                <span class="schedule-time">{{ slotTime(slot) }}</span>
                <h4>{{ slot.title }}</h4>
              </div>
              <span class="schedule-state">{{ slot.enabled ? '显示' : '隐藏' }}</span>
            </div>
            <p>{{ [slot.location, slot.note].filter(Boolean).join(' · ') || '未填写地点/备注' }}</p>
            <div class="schedule-assignees">
              <span v-for="person in slot.assignees" :key="person.id">{{ person.name }}</span>
              <span v-if="slot.assignees.length === 0">未安排人员</span>
            </div>
            <div v-if="canManageSchedules" class="schedule-card-actions">
              <button class="ghost-button" @click="startEdit(slot)">编辑</button>
              <button class="ghost-button danger-button" @click="archiveSchedule(slot)"><Trash2 :size="15" />归档</button>
            </div>
          </article>
        </section>
      </section>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { AlertTriangle, CalendarDays, Download, FileSearch, FileSpreadsheet, Plus, RefreshCw, Save, Trash2, Upload } from '@lucide/vue'
import { api, del, post, put } from '../api.js'

const props = defineProps({
  currentUser: { type: Object, default: null }
})
const emit = defineEmits(['notify'])

const schedules = ref([])
const dutyPeriods = ref([])
const dutyWeekdays = ref([])
const busy = ref(false)
const showForm = ref(false)
const showImport = ref(false)
const editingId = ref(null)
const importInput = ref(null)
const importFile = ref(null)
const importPreview = ref(null)
const form = reactive(emptyForm())

const weekdayOptions = [
  { value: 1, label: '星期一', enabled: true },
  { value: 2, label: '星期二', enabled: true },
  { value: 3, label: '星期三', enabled: true },
  { value: 4, label: '星期四', enabled: true },
  { value: 5, label: '星期五', enabled: true },
  { value: 6, label: '星期六', enabled: true },
  { value: 7, label: '星期日', enabled: true }
]

const canManageSchedules = computed(() => ['PRESIDENT', 'ADMIN'].includes(props.currentUser?.role))
const dutyPeriodOptions = computed(() => normalizeDutyPeriods(dutyPeriods.value).map(period => ({
  ...period,
  key: periodKey(period),
  timeText: periodTime(period)
})))
const normalizedWeekdayOptions = computed(() => {
  if (!dutyWeekdays.value.length) return weekdayOptions
  return dutyWeekdays.value
    .map(row => {
      const value = Number(row.weekday)
      const fallback = weekdayOptions.find(day => day.value === value)
      return {
        value,
        label: row.weekday_name || row.weekdayName || fallback?.label || `星期${value}`,
        enabled: row.enabled === true || row.enabled === 1 || row.enabled === '1' || row.enabled === 'true'
      }
    })
    .filter(day => day.value >= 1 && day.value <= 7)
})
const enabledWeekdayOptions = computed(() => normalizedWeekdayOptions.value.filter(day => day.enabled))
const displayWeekdayOptions = computed(() => {
  const scheduledWeekdays = new Set(schedules.value.map(item => Number(item.weekday)))
  return normalizedWeekdayOptions.value.filter(day => day.enabled || scheduledWeekdays.has(day.value))
})
const formWeekdayOptions = computed(() => enabledWeekdayOptions.value.length ? enabledWeekdayOptions.value : normalizedWeekdayOptions.value)

onMounted(loadScheduleData)

async function loadScheduleData() {
  await run(async () => {
    const [items, periods, weekdays] = await Promise.all([
      api('/api/schedules'),
      api('/api/settings/duty-periods'),
      api('/api/settings/weekdays')
    ])
    schedules.value = items
    dutyPeriods.value = periods
    dutyWeekdays.value = weekdays
  }, false)
}

async function loadSchedules() {
  await run(async () => {
    const [items, periods, weekdays] = await Promise.all([
      api('/api/schedules'),
      api('/api/settings/duty-periods'),
      api('/api/settings/weekdays')
    ])
    schedules.value = items
    dutyPeriods.value = periods
    dutyWeekdays.value = weekdays
  }, false)
}

function schedulesByWeekday(weekday) {
  return schedules.value.filter(item => item.weekday === weekday)
}

function scheduleGroupsByWeekday(weekday) {
  const daySlots = schedulesByWeekday(weekday)
  const groupedSlots = slotsByAssignedPeriod(daySlots, dutyPeriodOptions.value)
  return dutyPeriodOptions.value.map(period => {
    const slots = groupedSlots.get(period.key) || []
    return {
      ...period,
      slots,
      peopleCount: assigneeCount(slots)
    }
  })
}

function activeGroupCount(weekday) {
  return scheduleGroupsByWeekday(weekday).filter(group => group.slots.length > 0).length
}

function unclassifiedSchedules(weekday) {
  return schedulesByWeekday(weekday).filter(slot => !assignedPeriodKey(slot, dutyPeriodOptions.value))
}

function unclassifiedPeopleCount(weekday) {
  return assigneeCount(unclassifiedSchedules(weekday))
}

function startCreate() {
  if (!dutyPeriodOptions.value.length) {
    notify('请先在值班设置中保存值班时间段', 'warn')
    return
  }
  editingId.value = null
  showImport.value = false
  Object.assign(form, emptyForm())
  form.weekday = formWeekdayOptions.value[0]?.value || 1
  form.periodKey = dutyPeriodOptions.value[0]?.key || ''
  applySelectedPeriod()
  showForm.value = true
}

function startEdit(slot) {
  showImport.value = false
  editingId.value = slot.id
  Object.assign(form, {
    weekday: slot.weekday,
    startTime: slot.startTime || '',
    endTime: slot.endTime || '',
    title: slot.title,
    location: slot.location || '',
    note: slot.note || '',
    enabled: slot.enabled,
    periodKey: periodKeyForSlot(slot),
    assigneeText: slot.assignees.map(person => [person.studentNo, person.name].filter(Boolean).join(' ')).join('\n')
  })
  showForm.value = true
}

function cancelForm() {
  editingId.value = null
  Object.assign(form, emptyForm())
  showForm.value = false
}

function openImportPanel() {
  showForm.value = false
  editingId.value = null
  showImport.value = true
}

function selectImportFile(event) {
  importFile.value = event.target.files?.[0] || null
  importPreview.value = null
}

function cancelImport() {
  showImport.value = false
  importFile.value = null
  importPreview.value = null
  if (importInput.value) importInput.value.value = ''
}

async function downloadImportTemplate() {
  await run(async () => {
    const blob = await api('/api/schedules/import-template')
    downloadBlob(blob, '部长排班导入模板.xlsx')
    notify('排班导入模板已下载', 'success')
  })
}

async function previewImport() {
  if (!importFile.value) return notify('请选择排班 Excel 文件', 'warn')
  await run(async () => {
    const formData = new FormData()
    formData.append('file', importFile.value)
    importPreview.value = await api('/api/schedules/import/preview', { method: 'POST', body: formData })
    if (importPreview.value.valid) notify('文件校验通过，可以确认导入', 'success')
    else notify(`发现 ${importPreview.value.issues.length} 处错误，未写入排班`, 'warn')
  })
}

async function confirmImport() {
  if (!importFile.value || !importPreview.value?.valid) return
  await run(async () => {
    const formData = new FormData()
    formData.append('file', importFile.value)
    const result = await api('/api/schedules/import', { method: 'POST', body: formData })
    notify(`已导入 ${result.replacedGroups} 个时段、${result.assignedMembers} 人`, 'success')
    cancelImport()
    await loadScheduleData()
  })
}

async function saveSchedule() {
  if (!applySelectedPeriod()) {
    notify('请选择已设置的值班时间段', 'warn')
    return
  }
  await run(async () => {
    const payload = {
      weekday: form.weekday,
      startTime: form.startTime || null,
      endTime: form.endTime || null,
      title: form.title,
      location: form.location,
      note: form.note,
      enabled: form.enabled,
      assignees: parseAssignees(form.assigneeText)
    }
    if (editingId.value) await put(`/api/schedules/${editingId.value}`, payload)
    else await post('/api/schedules', payload)
    notify('排班已保存', 'success')
    cancelForm()
    await loadScheduleData()
  })
}

async function archiveSchedule(slot) {
  if (!window.confirm(`确认归档“${slot.weekdayName} ${slot.title}”这段排班？`)) return
  await run(async () => {
    await del(`/api/schedules/${slot.id}`)
    notify('排班已归档', 'success')
    await loadScheduleData()
  })
}

function parseAssignees(text) {
  return String(text || '')
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(Boolean)
    .map(line => {
      const parts = line.split(/\s+/)
      if (/^\d{1,32}$/.test(parts[0])) {
        return { studentNo: parts[0], name: parts.slice(1).join(' ') }
      }
      return { studentNo: '', name: line }
    })
}

function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  URL.revokeObjectURL(url)
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
  emit('notify', { message, type })
}

function slotTime(slot) {
  const start = shortTime(slot.startTime)
  const end = shortTime(slot.endTime)
  if (start && end) return `${start}-${end}`
  return start || end || '全天'
}

function shortTime(value) {
  return value ? String(value).slice(0, 5) : ''
}

function periodTime(period) {
  const start = shortTime(period.startTime)
  const end = shortTime(period.endTime)
  return start && end ? `${start}-${end}` : start || end || '全天'
}

function periodKeyForSlot(slot) {
  const start = shortTime(slot.startTime)
  const end = shortTime(slot.endTime)
  const key = `${start}-${end}`
  return dutyPeriodOptions.value.some(period => period.key === key) ? key : ''
}

function applySelectedPeriod() {
  const period = dutyPeriodOptions.value.find(item => item.key === form.periodKey)
  if (!period) {
    form.startTime = ''
    form.endTime = ''
    return false
  }
  form.periodKey = period.key
  form.startTime = period.startTime
  form.endTime = period.endTime
  return true
}

function slotsByAssignedPeriod(slots, periods) {
  const groups = new Map(periods.map(period => [period.key || periodKey(period), []]))
  for (const slot of slots || []) {
    const key = assignedPeriodKey(slot, periods)
    if (key && groups.has(key)) {
      groups.get(key).push(slot)
    }
  }
  return groups
}

function assignedPeriodKey(slot, periods) {
  for (const period of periods) {
    const key = period.key || periodKey(period)
    if (periodKey(slot) === key) {
      return key
    }
  }
  return null
}

function assigneeCount(slots) {
  const assignees = new Set()
  for (const slot of slots) {
    for (const person of slot.assignees || []) {
      if (!person?.name) continue
      assignees.add(person.studentNo || person.name)
    }
  }
  return assignees.size
}

function normalizeDutyPeriods(items) {
  return (items || [])
    .map((item, index) => ({
      sortOrder: Number(item.sortOrder ?? index),
      startTime: shortTime(item.startTime),
      endTime: shortTime(item.endTime)
    }))
    .filter(item => item.startTime && item.endTime)
    .sort((a, b) => timeToMinutes(a.startTime) - timeToMinutes(b.startTime) || timeToMinutes(a.endTime) - timeToMinutes(b.endTime))
}

function periodKey(period) {
  return `${shortTime(period.startTime)}-${shortTime(period.endTime)}`
}

function timeToMinutes(value) {
  if (!value) return null
  const [hour, minute] = String(value).split(':').map(item => Number(item))
  if (!Number.isFinite(hour) || !Number.isFinite(minute)) return null
  return hour * 60 + minute
}

function emptyForm() {
  return {
    weekday: 1,
    periodKey: '',
    startTime: '',
    endTime: '',
    title: '日常值班',
    location: '',
    note: '',
    enabled: true,
    assigneeText: ''
  }
}
</script>

<template>
  <section class="work-section tab-trainings">
    <div class="section-head">
      <div>
        <h3>培训管理</h3>
        <span>记录协会培训场次、参与名单和培训时长</span>
      </div>
      <div class="section-actions">
        <button v-if="canManageTrainings" class="ghost-button" @click="exportSummary"><Download :size="16" />导出统计</button>
        <button v-if="canManageTrainings" class="ghost-button" @click="startCreateSession"><Plus :size="16" />新培训</button>
        <button class="ghost-button" @click="loadSessions"><RefreshCw :size="16" />刷新</button>
      </div>
    </div>

    <div class="filters training-filters">
      <input v-model.trim="filters.keyword" placeholder="搜索标题、地点、主讲人" @keyup.enter="loadSessions" />
      <input v-model="filters.from" type="date" />
      <input v-model="filters.to" type="date" />
      <button class="ghost-button" @click="loadSessions">查询</button>
    </div>

    <div v-if="canManageTrainings && showSessionForm" class="inline-form-block training-session-form">
      <div class="subsection-head">
        <h4><GraduationCap :size="17" />{{ editingSessionId ? '编辑培训' : '新增培训' }}</h4>
        <span>培训创建后，可在右侧导入或手动维护参与名单</span>
      </div>
      <form class="training-form-grid" @submit.prevent="saveSession">
        <input v-model.trim="sessionForm.title" placeholder="培训标题" />
        <input v-model="sessionForm.trainingDate" type="date" />
        <input v-model="sessionForm.startTime" type="time" />
        <input v-model="sessionForm.endTime" type="time" />
        <input v-model.trim="sessionForm.location" placeholder="地点" />
        <input v-model.trim="sessionForm.speaker" placeholder="主讲人" />
        <input class="training-description-input" v-model.trim="sessionForm.description" placeholder="说明，可选" />
        <div class="training-form-actions">
          <button class="primary-action" type="submit" :disabled="busy || !sessionForm.title || !sessionForm.trainingDate">
            <Save :size="16" />保存培训
          </button>
          <button class="ghost-button" type="button" @click="cancelSessionForm">取消</button>
        </div>
      </form>
    </div>

    <div class="training-summary-strip">
      <div><span>培训场次</span><strong>{{ sessions.length }}</strong></div>
      <div><span>参与记录</span><strong>{{ totalParticipants }}</strong></div>
      <div><span>培训时长</span><strong>{{ formatHours(totalTrainingHours) }} h</strong></div>
    </div>

    <div class="training-layout">
      <aside class="training-session-list">
        <button
          v-for="session in sessions"
          :key="session.id"
          class="training-session-card"
          :class="{ active: selectedSession?.id === session.id }"
          @click="selectSession(session)"
        >
          <span class="training-date">{{ session.trainingDate }}</span>
          <strong>{{ session.title }}</strong>
          <small>{{ [session.location, session.speaker].filter(Boolean).join(' · ') || '未填写地点/主讲人' }}</small>
          <div class="training-card-meta">
            <span>{{ session.participantCount }} 人</span>
            <span>{{ formatHours(session.totalDurationHours) }} h</span>
          </div>
        </button>
        <div v-if="sessions.length === 0" class="empty training-empty">暂无培训</div>
      </aside>

      <section class="training-participant-panel">
        <div v-if="!selectedSession" class="empty training-empty">请选择培训场次</div>
        <template v-else>
          <div class="participant-panel-head">
            <div>
              <p class="eyebrow">{{ selectedSession.trainingDate }}</p>
              <h4>{{ selectedSession.title }}</h4>
              <span>{{ [selectedSession.location, selectedSession.speaker].filter(Boolean).join(' · ') || '未填写地点/主讲人' }}</span>
            </div>
            <div class="section-actions">
              <button v-if="canManageTrainings" class="ghost-button" @click="downloadSession"><Download :size="16" />名单</button>
              <button v-if="canManageTrainings" class="ghost-button" @click="showImportPanel = !showImportPanel"><Upload :size="16" />导入</button>
              <button v-if="canManageTrainings" class="ghost-button" @click="startEditSession(selectedSession)">编辑</button>
              <button v-if="canManageTrainings" class="ghost-button danger-button" @click="archiveSession(selectedSession)">
                <Trash2 :size="16" />归档
              </button>
            </div>
          </div>

          <div class="training-stat-grid">
            <div><span>参与</span><strong>{{ selectedSession.participantCount }}</strong></div>
            <div><span>培训时长</span><strong>{{ formatHours(selectedSession.totalDurationHours) }} h</strong></div>
          </div>

          <div v-if="canManageTrainings && showImportPanel" class="inline-form-block training-import-panel">
            <div class="subsection-head">
              <h4><Upload :size="17" />导入参与名单</h4>
              <span>识别列：学号、姓名、时长、备注；时长未填时使用培训开始/结束时间</span>
            </div>
            <div class="training-import-row">
              <input class="file-input" type="file" accept=".xlsx,.xls" @change="pickImportFile" />
              <span>{{ importFile ? `${importFile.name} · ${bytesText(importFile.size)}` : '请选择 Excel 文件' }}</span>
              <button class="ghost-button" :disabled="busy" @click="downloadImportTemplate"><Download :size="16" />模板</button>
              <button class="primary-action" :disabled="busy || !importFile" @click="importParticipants"><Upload :size="16" />导入</button>
            </div>
            <p v-if="importResult" class="import-result">
              新增 {{ importResult.created }}，更新 {{ importResult.updated }}，跳过 {{ importResult.skipped }}
            </p>
            <ul v-if="importResult?.errors?.length" class="import-errors">
              <li v-for="item in importResult.errors" :key="item">{{ item }}</li>
            </ul>
          </div>

          <div v-if="canManageTrainings" class="training-participant-tools">
            <button class="ghost-button" @click="startCreateParticipant"><Plus :size="16" />新增参与记录</button>
          </div>

          <div v-if="canManageTrainings && showParticipantForm" class="inline-form-block participant-form-panel">
            <form class="participant-form-grid" @submit.prevent="saveParticipant">
              <input v-model.trim="participantForm.studentNo" placeholder="学号" />
              <input v-model.trim="participantForm.name" placeholder="姓名" />
              <input v-model.number="participantForm.durationHours" min="0" max="999.99" step="0.25" type="number" placeholder="时长" />
              <input v-model.trim="participantForm.remark" placeholder="备注，可选" />
              <button class="primary-action" type="submit" :disabled="busy || !participantForm.name">
                <Save :size="16" />保存
              </button>
              <button class="ghost-button" type="button" @click="cancelParticipantForm">取消</button>
            </form>
          </div>

          <div class="table-wrap training-participant-table-wrap">
            <table>
              <thead>
                <tr><th>学号</th><th>姓名</th><th>时长</th><th>备注</th><th v-if="canManageTrainings">操作</th></tr>
              </thead>
              <tbody>
                <tr v-for="item in participants" :key="item.id">
                  <td class="mono">{{ item.studentNo }}</td>
                  <td>{{ item.name }}</td>
                  <td>{{ formatHours(item.durationHours) }} h</td>
                  <td>{{ item.remark || '-' }}</td>
                  <td v-if="canManageTrainings" class="row-actions">
                    <button @click="startEditParticipant(item)">编辑</button>
                    <button class="danger" @click="deleteParticipant(item)">删除</button>
                  </td>
                </tr>
                <tr v-if="participants.length === 0"><td :colspan="canManageTrainings ? 5 : 4" class="empty">暂无参与记录</td></tr>
              </tbody>
            </table>
          </div>
        </template>
      </section>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Download, GraduationCap, Plus, RefreshCw, Save, Trash2, Upload } from '@lucide/vue'
import { api, del, post, put } from '../api.js'

const props = defineProps({
  currentUser: { type: Object, default: null }
})
const emit = defineEmits(['notify'])

const busy = ref(false)
const sessions = ref([])
const participants = ref([])
const selectedSession = ref(null)
const showSessionForm = ref(false)
const editingSessionId = ref(null)
const showImportPanel = ref(false)
const importFile = ref(null)
const importResult = ref(null)
const showParticipantForm = ref(false)
const editingParticipantId = ref(null)

const today = new Date()
const todayValue = formatLocalDate(today)
const filters = reactive({
  keyword: '',
  from: `${today.getFullYear()}-01-01`,
  to: todayValue
})
const sessionForm = reactive(emptySessionForm())
const participantForm = reactive(emptyParticipantForm())

const canManageTrainings = computed(() => ['PRESIDENT', 'ADMIN'].includes(props.currentUser?.role))
const totalParticipants = computed(() => sessions.value.reduce((sum, item) => sum + Number(item.participantCount || 0), 0))
const totalTrainingHours = computed(() => sessions.value.reduce((sum, item) => sum + Number(item.totalDurationHours || 0), 0))

onMounted(loadSessions)

async function loadSessions() {
  await run(async () => {
    const params = new URLSearchParams()
    if (filters.keyword) params.set('keyword', filters.keyword)
    if (filters.from) params.set('from', filters.from)
    if (filters.to) params.set('to', filters.to)
    sessions.value = await api(`/api/trainings?${params.toString()}`)
    const selected = sessions.value.find(item => item.id === selectedSession.value?.id) || sessions.value[0] || null
    selectedSession.value = selected
    if (selected) await loadParticipants(selected.id)
    else participants.value = []
  }, false)
}

async function selectSession(session) {
  selectedSession.value = session
  importResult.value = null
  cancelParticipantForm()
  await loadParticipants(session.id)
}

async function loadParticipants(sessionId) {
  await run(async () => {
    participants.value = await api(`/api/trainings/${sessionId}/participants`)
  }, false)
}

function startCreateSession() {
  editingSessionId.value = null
  Object.assign(sessionForm, emptySessionForm())
  showSessionForm.value = true
}

function startEditSession(session) {
  editingSessionId.value = session.id
  Object.assign(sessionForm, {
    title: session.title,
    trainingDate: session.trainingDate,
    startTime: session.startTime || '',
    endTime: session.endTime || '',
    location: session.location || '',
    speaker: session.speaker || '',
    description: session.description || ''
  })
  showSessionForm.value = true
}

function cancelSessionForm() {
  showSessionForm.value = false
  editingSessionId.value = null
  Object.assign(sessionForm, emptySessionForm())
}

async function saveSession() {
  await run(async () => {
    const payload = {
      ...sessionForm,
      startTime: sessionForm.startTime || null,
      endTime: sessionForm.endTime || null
    }
    if (editingSessionId.value) await put(`/api/trainings/${editingSessionId.value}`, payload)
    else await post('/api/trainings', payload)
    notify('培训已保存', 'success')
    cancelSessionForm()
    await loadSessions()
  })
}

async function archiveSession(session) {
  if (!window.confirm(`确认归档培训“${session.title}”？`)) return
  await run(async () => {
    await del(`/api/trainings/${session.id}`)
    notify('培训已归档', 'success')
    selectedSession.value = null
    await loadSessions()
  })
}

function pickImportFile(event) {
  importFile.value = event.target.files?.[0] || null
  importResult.value = null
}

async function importParticipants() {
  if (!selectedSession.value || !importFile.value) return
  await run(async () => {
    const formData = new FormData()
    formData.append('file', importFile.value)
    importResult.value = await api(`/api/trainings/${selectedSession.value.id}/participants/import`, {
      method: 'POST',
      body: formData
    })
    importFile.value = null
    notify('培训名单已导入', importResult.value.skipped ? 'warn' : 'success')
    await loadSessions()
  })
}

async function downloadImportTemplate() {
  if (!selectedSession.value) return
  await run(async () => {
    const blob = await api(`/api/trainings/${selectedSession.value.id}/participants/import-template`)
    downloadBlob(blob, `培训名单导入模板_${selectedSession.value.title}_${selectedSession.value.trainingDate}.xlsx`)
  })
}

function startCreateParticipant() {
  editingParticipantId.value = null
  Object.assign(participantForm, emptyParticipantForm())
  if (participants.value.length === 0 && selectedSession.value?.speaker) {
    participantForm.name = selectedSession.value.speaker
  }
  participantForm.durationHours = defaultSessionDuration(selectedSession.value)
  showParticipantForm.value = true
}

function startEditParticipant(item) {
  editingParticipantId.value = item.id
  Object.assign(participantForm, {
    studentNo: item.studentNo,
    name: item.name,
    durationHours: Number(item.durationHours || 0),
    remark: item.remark || ''
  })
  showParticipantForm.value = true
}

function cancelParticipantForm() {
  showParticipantForm.value = false
  editingParticipantId.value = null
  Object.assign(participantForm, emptyParticipantForm())
}

async function saveParticipant() {
  if (!selectedSession.value) return
  await run(async () => {
    if (editingParticipantId.value) {
      await put(`/api/trainings/${selectedSession.value.id}/participants/${editingParticipantId.value}`, participantForm)
    } else {
      await post(`/api/trainings/${selectedSession.value.id}/participants`, participantForm)
    }
    notify('参与记录已保存', 'success')
    cancelParticipantForm()
    await loadSessions()
  })
}

async function deleteParticipant(item) {
  if (!selectedSession.value) return
  if (!window.confirm(`确认删除 ${item.name} 的参与记录？`)) return
  await run(async () => {
    await del(`/api/trainings/${selectedSession.value.id}/participants/${item.id}`)
    notify('参与记录已删除', 'success')
    await loadSessions()
  })
}

async function downloadSession() {
  if (!selectedSession.value) return
  await run(async () => {
    const blob = await api(`/api/trainings/${selectedSession.value.id}/export`)
    downloadBlob(blob, `培训名单_${selectedSession.value.title}_${selectedSession.value.trainingDate}.xlsx`)
  })
}

async function exportSummary() {
  await run(async () => {
    const params = new URLSearchParams()
    if (filters.keyword) params.set('keyword', filters.keyword)
    if (filters.from) params.set('from', filters.from)
    if (filters.to) params.set('to', filters.to)
    const blob = await api(`/api/trainings/export?${params.toString()}`)
    downloadBlob(blob, `培训统计_${filters.from || '开始'}_${filters.to || '结束'}.xlsx`)
  })
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

function bytesText(value) {
  const size = Number(value || 0)
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

function formatLocalDate(value) {
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function emptySessionForm() {
  return {
    title: '',
    trainingDate: todayValue,
    startTime: '',
    endTime: '',
    location: '',
    speaker: '',
    description: ''
  }
}

function emptyParticipantForm() {
  return {
    studentNo: '',
    name: '',
    durationHours: defaultSessionDuration(selectedSession.value),
    remark: ''
  }
}

function defaultSessionDuration(session) {
  if (!session?.startTime || !session?.endTime) return 0
  const [startHour, startMinute] = String(session.startTime).split(':').map(Number)
  const [endHour, endMinute] = String(session.endTime).split(':').map(Number)
  if (!Number.isFinite(startHour) || !Number.isFinite(startMinute) || !Number.isFinite(endHour) || !Number.isFinite(endMinute)) return 0
  const minutes = endHour * 60 + endMinute - (startHour * 60 + startMinute)
  return minutes > 0 ? Number((minutes / 60).toFixed(2)) : 0
}

function formatHours(value) {
  const number = Number(value || 0)
  return Number.isInteger(number) ? String(number) : number.toFixed(2).replace(/0+$/, '').replace(/\.$/, '')
}
</script>

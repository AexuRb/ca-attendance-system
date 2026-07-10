<template>
  <section class="work-section tab-repairs">
    <div class="section-head">
      <div>
        <h3>维修事务</h3>
        <span>本机记录接修、处理和协议确认</span>
      </div>
      <div class="section-actions">
        <button v-if="canExportRepairs" class="ghost-button" :disabled="busy" @click="exportRepairs"><Download :size="16" />导出</button>
        <button class="ghost-button" :disabled="busy" @click="startCreate"><Plus :size="16" />新事务</button>
        <button class="ghost-button" :disabled="busy" @click="loadRepairs"><RefreshCw :size="16" />刷新</button>
      </div>
    </div>

    <div class="filters repair-filters">
      <input v-model.trim="filters.keyword" placeholder="搜索编号、送修人、设备或处理记录" @keyup.enter="loadRepairs" />
      <select v-model="filters.status" @change="loadRepairs">
        <option v-for="item in filterStatusOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
      </select>
      <input v-model="filters.from" type="date" />
      <input v-model="filters.to" type="date" />
      <button class="ghost-button" :disabled="busy" @click="loadRepairs">查询</button>
    </div>

    <div class="repair-summary-strip">
      <div><span>当前列表</span><strong>{{ repairs.length }}</strong></div>
      <div><span>进行中</span><strong>{{ openCount }}</strong></div>
      <div><span>已完成</span><strong>{{ completedCount }}</strong></div>
      <div><span>已取消</span><strong>{{ canceledCount }}</strong></div>
    </div>

    <div class="repair-layout" :class="{ 'form-open': showForm }">
      <aside class="repair-case-list">
        <button
          v-for="item in repairs"
          :key="item.id"
          class="repair-case-card"
          :class="{ active: selectedRepair?.id === item.id && !showForm }"
          @click="selectRepair(item)"
        >
          <div class="repair-card-line">
            <span class="mono">{{ item.caseNo }}</span>
            <span class="status-pill" :class="statusClass(item.status)">{{ statusText(item.status) }}</span>
          </div>
          <strong>{{ item.ownerName }} · {{ item.deviceType }}</strong>
          <small>{{ deviceText(item) }}</small>
          <div class="repair-card-meta">
            <span>{{ timeText(item.receivedAt) }}</span>
            <span>{{ item.handlerName || '未填处理人' }}</span>
          </div>
        </button>
        <div v-if="repairs.length === 0" class="empty repair-empty">暂无维修事务</div>
      </aside>

      <section class="repair-detail-panel">
        <div v-if="showForm" class="repair-form-board">
          <div class="participant-panel-head repair-panel-head">
            <div>
              <p class="eyebrow">{{ editingRepairId ? 'Edit Repair Case' : 'New Repair Case' }}</p>
              <h4>{{ editingRepairId ? '编辑维修事务' : '新增维修事务' }}</h4>
              <span>{{ editingRepairId ? form.caseNo : '保存后自动生成事务编号' }}</span>
            </div>
            <button class="ghost-button" type="button" @click="cancelForm">取消</button>
          </div>

          <form class="repair-form" @submit.prevent="saveRepair">
            <div class="repair-form-section">
              <h5>送修信息</h5>
              <div class="repair-form-grid">
                <label>
                  <span>协议类型</span>
                  <select v-model="form.agreementType">
                    <option v-for="item in agreementOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
                  </select>
                </label>
                <label>
                  <span>送修人</span>
                  <input v-model.trim="form.ownerName" placeholder="姓名" />
                </label>
                <label>
                  <span>联系方式</span>
                  <input v-model.trim="form.ownerPhone" placeholder="手机号或其他联系方式" />
                </label>
              </div>
            </div>

            <div class="repair-form-section">
              <h5>设备信息</h5>
              <div class="repair-form-grid">
                <label>
                  <span>设备类型</span>
                  <input v-model.trim="form.deviceType" placeholder="笔记本、台式机、打印机等" />
                </label>
                <label>
                  <span>品牌</span>
                  <input v-model.trim="form.deviceBrand" placeholder="品牌，可选" />
                </label>
                <label>
                  <span>型号</span>
                  <input v-model.trim="form.deviceModel" placeholder="型号，可选" />
                </label>
                <label class="wide">
                  <span>随附物品</span>
                  <input v-model.trim="form.accessories" placeholder="电源、鼠标、硬盘等" />
                </label>
              </div>
            </div>

            <div class="repair-form-section">
              <h5>处理过程</h5>
              <div class="repair-form-grid">
                <label>
                  <span>状态</span>
                  <select v-model="form.status">
                    <option v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
                  </select>
                </label>
                <label>
                  <span>接收时间</span>
                  <input v-model="form.receivedAt" type="datetime-local" />
                </label>
                <label>
                  <span>完成时间</span>
                  <input v-model="form.completedAt" type="datetime-local" :disabled="form.status !== 'COMPLETED'" />
                </label>
                <label>
                  <span>处理人</span>
                  <input v-model.trim="form.handlerName" placeholder="默认当前登录人" />
                </label>
                <label class="wide">
                  <span>故障描述</span>
                  <textarea v-model.trim="form.faultDescription" rows="3" placeholder="送修时描述的现象、报错或处理诉求"></textarea>
                </label>
                <label class="wide">
                  <span>处理记录</span>
                  <textarea v-model.trim="form.serviceDescription" rows="3" placeholder="检测结果、采取操作、后续说明"></textarea>
                </label>
                <label class="wide">
                  <span>备注</span>
                  <textarea v-model.trim="form.remark" rows="2" placeholder="其他补充"></textarea>
                </label>
              </div>
            </div>

            <div class="repair-confirm-grid">
              <label class="repair-check-row">
                <input v-model="form.dataBackupConfirmed" class="repair-checkbox" type="checkbox" />
                <span>已提醒送修人自行备份数据</span>
              </label>
              <label class="repair-check-row">
                <input v-model="form.riskAcknowledged" class="repair-checkbox" type="checkbox" />
                <span>已确认维修和拆装风险</span>
              </label>
              <label class="repair-check-row">
                <input v-model="form.privacyAcknowledged" class="repair-checkbox" type="checkbox" />
                <span>已确认隐私保护提示</span>
              </label>
            </div>

            <div class="repair-form-actions">
              <button class="primary-action" type="submit" :disabled="busy || !form.ownerName || !form.deviceType || !form.faultDescription">
                <Save :size="16" />保存事务
              </button>
              <button class="ghost-button" type="button" @click="cancelForm">取消</button>
            </div>
          </form>
        </div>

        <template v-else-if="selectedRepair">
          <div class="participant-panel-head repair-panel-head">
            <div>
              <p class="eyebrow">{{ agreementText(selectedRepair.agreementType) }}</p>
              <h4>{{ selectedRepair.ownerName }} · {{ selectedRepair.deviceType }}</h4>
              <span>{{ selectedRepair.caseNo }} · {{ timeText(selectedRepair.receivedAt) }}</span>
            </div>
            <div class="section-actions">
              <button class="ghost-button" :disabled="busy" @click="printAgreement(selectedRepair)"><Printer :size="16" />协议</button>
              <button class="ghost-button" :disabled="busy" @click="startEdit(selectedRepair)">编辑</button>
              <button class="ghost-button" :disabled="busy" @click="startCreate"><Plus :size="16" />新事务</button>
            </div>
          </div>

          <div class="repair-focus-line">
            <span class="status-pill" :class="statusClass(selectedRepair.status)">{{ statusText(selectedRepair.status) }}</span>
            <strong>{{ deviceText(selectedRepair) }}</strong>
            <small>{{ selectedRepair.handlerName || '未填写处理人' }}</small>
          </div>

          <div class="repair-detail-grid">
            <div><span>联系方式</span><strong>{{ selectedRepair.ownerPhone || '-' }}</strong></div>
            <div><span>随附物品</span><strong>{{ selectedRepair.accessories || '-' }}</strong></div>
            <div><span>完成时间</span><strong>{{ timeText(selectedRepair.completedAt) }}</strong></div>
          </div>

          <div class="repair-text-grid">
            <article>
              <h5><FileText :size="16" />故障描述</h5>
              <p>{{ selectedRepair.faultDescription }}</p>
            </article>
            <article>
              <h5><Wrench :size="16" />处理记录</h5>
              <p>{{ selectedRepair.serviceDescription || '暂无处理记录' }}</p>
            </article>
          </div>

          <div class="repair-confirm-strip">
            <span :class="{ on: selectedRepair.dataBackupConfirmed }">数据备份提醒</span>
            <span :class="{ on: selectedRepair.riskAcknowledged }">维修风险确认</span>
            <span :class="{ on: selectedRepair.privacyAcknowledged }">隐私提示确认</span>
          </div>

          <p v-if="selectedRepair.remark" class="repair-remark">{{ selectedRepair.remark }}</p>
        </template>

        <div v-else class="empty-state repair-empty-state">
          <Wrench :size="30" />
          <strong>还没有维修事务</strong>
          <span>新增一条事务后，可以打印协议、导出 Excel，并随完整备份一起归档。</span>
          <button class="primary-action" @click="startCreate"><Plus :size="16" />新事务</button>
        </div>
      </section>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Download, FileText, Plus, Printer, RefreshCw, Save, Wrench } from '@lucide/vue'
import { api, post, put } from '../api.js'

const props = defineProps({
  currentUser: { type: Object, default: null }
})
const emit = defineEmits(['notify'])

const today = new Date()
const todayValue = formatLocalDate(today)
const busy = ref(false)
const repairs = ref([])
const selectedRepair = ref(null)
const showForm = ref(false)
const editingRepairId = ref(null)
const filters = reactive({
  keyword: '',
  status: '',
  from: `${today.getFullYear()}-01-01`,
  to: todayValue
})
const form = reactive(emptyForm())

const agreementOptions = [
  { value: 'PERSONAL_DEVICE', label: '维修协议' },
  { value: 'PUBLIC_DEVICE', label: '免责协议' }
]
const statusOptions = [
  { value: 'REPAIRING', label: '进行中' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELED', label: '已取消' }
]
const filterStatusOptions = [
  { value: '', label: '进行中' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELED', label: '已取消' },
  { value: 'ALL', label: '全部状态' }
]

const canManageRepairs = computed(() => ['MINISTER', 'PRESIDENT', 'ADMIN'].includes(props.currentUser?.role))
const canExportRepairs = computed(() => ['PRESIDENT', 'ADMIN'].includes(props.currentUser?.role))
const openCount = computed(() => repairs.value.filter(item => normalizeStatus(item.status) === 'REPAIRING').length)
const completedCount = computed(() => repairs.value.filter(item => normalizeStatus(item.status) === 'COMPLETED').length)
const canceledCount = computed(() => repairs.value.filter(item => normalizeStatus(item.status) === 'CANCELED').length)

onMounted(loadRepairs)

async function loadRepairs() {
  await run(async () => {
    const params = new URLSearchParams()
    if (filters.keyword) params.set('keyword', filters.keyword)
    if (filters.status) params.set('status', filters.status)
    if (filters.from) params.set('from', filters.from)
    if (filters.to) params.set('to', filters.to)
    repairs.value = await api(`/api/repairs?${params.toString()}`)
    selectedRepair.value = repairs.value.find(item => item.id === selectedRepair.value?.id) || repairs.value[0] || null
  }, false)
}

function selectRepair(item) {
  selectedRepair.value = item
  showForm.value = false
  editingRepairId.value = null
}

function startCreate() {
  if (!canManageRepairs.value) return notify('没有维修事务管理权限', 'warn')
  editingRepairId.value = null
  Object.assign(form, emptyForm())
  showForm.value = true
}

function startEdit(item) {
  if (!canManageRepairs.value) return notify('没有维修事务管理权限', 'warn')
  editingRepairId.value = item.id
  Object.assign(form, {
    caseNo: item.caseNo,
    agreementType: item.agreementType,
    ownerName: item.ownerName || '',
    ownerPhone: item.ownerPhone || '',
    deviceType: item.deviceType || '',
    deviceBrand: item.deviceBrand || '',
    deviceModel: item.deviceModel || '',
    accessories: item.accessories || '',
    faultDescription: item.faultDescription || '',
    serviceDescription: item.serviceDescription || '',
    dataBackupConfirmed: Boolean(item.dataBackupConfirmed),
    riskAcknowledged: Boolean(item.riskAcknowledged),
    privacyAcknowledged: Boolean(item.privacyAcknowledged),
    status: normalizeStatus(item.status),
    receivedAt: toInputDateTime(item.receivedAt),
    completedAt: toInputDateTime(item.completedAt),
    handlerName: item.handlerName || '',
    remark: item.remark || ''
  })
  showForm.value = true
}

function cancelForm() {
  showForm.value = false
  editingRepairId.value = null
  Object.assign(form, emptyForm())
}

async function saveRepair() {
  await run(async () => {
    const payload = {
      agreementType: form.agreementType,
      ownerName: form.ownerName,
      ownerPhone: form.ownerPhone || null,
      deviceType: form.deviceType,
      deviceBrand: form.deviceBrand || null,
      deviceModel: form.deviceModel || null,
      accessories: form.accessories || null,
      faultDescription: form.faultDescription,
      serviceDescription: form.serviceDescription || null,
      dataBackupConfirmed: form.dataBackupConfirmed,
      riskAcknowledged: form.riskAcknowledged,
      privacyAcknowledged: form.privacyAcknowledged,
      status: form.status,
      receivedAt: form.receivedAt || null,
      completedAt: form.status === 'COMPLETED' ? form.completedAt || null : null,
      handlerName: form.handlerName || null,
      remark: form.remark || null
    }
    const saved = editingRepairId.value
      ? await put(`/api/repairs/${editingRepairId.value}`, payload)
      : await post('/api/repairs', payload)
    notify('维修事务已保存', 'success')
    showForm.value = false
    editingRepairId.value = null
    await loadRepairs()
    selectedRepair.value = repairs.value.find(item => item.id === saved.id) || saved
  })
}

async function exportRepairs() {
  if (!canExportRepairs.value) return notify('只有会长或管理员可以导出维修事务', 'warn')
  await run(async () => {
    const params = new URLSearchParams()
    if (filters.keyword) params.set('keyword', filters.keyword)
    if (filters.status) params.set('status', filters.status)
    if (filters.from) params.set('from', filters.from)
    if (filters.to) params.set('to', filters.to)
    const blob = await api(`/api/repairs/export?${params.toString()}`)
    downloadBlob(blob, `维修事务_${filters.from || '开始'}_${filters.to || '结束'}.xlsx`)
    notify('维修事务已导出', 'success')
  })
}

async function printAgreement(item) {
  await run(async () => {
    const blob = await api(`/api/repairs/${item.id}/agreement`)
    const url = URL.createObjectURL(blob)
    const opened = window.open(url, '_blank')
    if (!opened) {
      notify('浏览器阻止了协议窗口', 'warn')
    }
    window.setTimeout(() => URL.revokeObjectURL(url), 60000)
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

function statusText(status) {
  return statusOptions.find(item => item.value === normalizeStatus(status))?.label || status
}

function normalizeStatus(status) {
  if (['COMPLETED', 'CANCELED'].includes(status)) return status
  return 'REPAIRING'
}

function statusClass(status) {
  return `status-${normalizeStatus(status).toLowerCase()}`
}

function agreementText(type) {
  return agreementOptions.find(item => item.value === type)?.label || type
}

function deviceText(item) {
  return [item.deviceBrand, item.deviceModel]
    .filter(Boolean)
    .join(' / ') || '未填写设备详情'
}

function timeText(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

function toInputDateTime(value) {
  if (!value) return ''
  return String(value).slice(0, 16)
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

function formatLocalDateTime(value) {
  const date = formatLocalDate(value)
  const hour = String(value.getHours()).padStart(2, '0')
  const minute = String(value.getMinutes()).padStart(2, '0')
  return `${date}T${hour}:${minute}`
}

function emptyForm() {
  return {
    caseNo: '',
    agreementType: 'PERSONAL_DEVICE',
    ownerName: '',
    ownerPhone: '',
    deviceType: '',
    deviceBrand: '',
    deviceModel: '',
    accessories: '',
    faultDescription: '',
    serviceDescription: '',
    dataBackupConfirmed: false,
    riskAcknowledged: true,
    privacyAcknowledged: true,
    status: 'REPAIRING',
    receivedAt: formatLocalDateTime(new Date()),
    completedAt: '',
    handlerName: props.currentUser?.name || '',
    remark: ''
  }
}
</script>

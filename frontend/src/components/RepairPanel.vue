<template>
  <section class="work-section tab-repairs">
    <div class="section-head">
      <div>
        <h3>维修事务</h3>
        <span>{{ viewMode === 'recycle' ? '恢复误删事务，或在自动备份后永久删除' : '本机记录接修、处理和协议确认' }}</span>
      </div>
      <div class="section-actions">
        <button v-if="viewMode === 'active' && canExportRepairs" class="ghost-button" :disabled="busy" @click="exportRepairs"><Download :size="16" />导出</button>
        <button v-if="viewMode === 'active'" class="ghost-button" :disabled="busy" @click="startCreate"><Plus :size="16" />新事务</button>
        <button
          v-if="canManageRecycle"
          class="ghost-button repair-view-toggle"
          :class="{ active: viewMode === 'recycle' }"
          :disabled="busy"
          @click="viewMode === 'recycle' ? showActiveRepairs() : showRecycleBin()"
        >
          <ArrowLeft v-if="viewMode === 'recycle'" :size="16" />
          <Trash2 v-else :size="16" />
          {{ viewMode === 'recycle' ? '返回事务' : '回收站' }}
          <small v-if="viewMode === 'active' && recycledRepairs.length" class="repair-recycle-count">{{ recycledRepairs.length }}</small>
        </button>
        <button class="ghost-button" :disabled="busy" @click="refreshCurrentView"><RefreshCw :size="16" />刷新</button>
      </div>
    </div>

    <div v-if="viewMode === 'active'" class="filters repair-filters">
      <label class="filter-field" for="repairKeyword"><span>关键词</span><input id="repairKeyword" v-model.trim="filters.keyword" name="keyword" autocomplete="off" placeholder="编号、送修人、设备或处理记录" @keyup.enter="loadRepairs" /></label>
      <label class="filter-field" for="repairStatus"><span>状态</span><select id="repairStatus" v-model="filters.status" name="status" @change="loadRepairs">
        <option v-for="item in filterStatusOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
      </select></label>
      <label class="filter-field" for="repairFrom"><span>开始日期</span><input id="repairFrom" v-model="filters.from" name="from" type="date" /></label>
      <label class="filter-field" for="repairTo"><span>结束日期</span><input id="repairTo" v-model="filters.to" name="to" type="date" /></label>
      <button class="ghost-button" :disabled="busy" @click="loadRepairs">查询</button>
    </div>

    <div v-if="viewMode === 'active'" class="repair-summary-strip">
      <div><span>当前列表</span><strong>{{ repairs.length }}</strong></div>
      <div><span>进行中</span><strong>{{ openCount }}</strong></div>
      <div><span>已完成</span><strong>{{ completedCount }}</strong></div>
      <div><span>已取消</span><strong>{{ canceledCount }}</strong></div>
    </div>

    <div class="repair-layout" :class="{ 'form-open': showForm }">
      <aside class="repair-case-list">
        <button
          v-for="item in visibleRepairs"
          :key="item.id"
          class="repair-case-card"
          :class="{ active: selectedRepair?.id === item.id && !showForm, recycled: viewMode === 'recycle' }"
          @click="selectRepair(item)"
        >
          <div class="repair-card-line">
            <span class="mono">{{ item.caseNo }}</span>
            <span class="status-pill" :class="statusClass(item.status)">{{ statusText(item.status) }}</span>
          </div>
          <strong>{{ item.ownerName }} · {{ item.deviceType }}</strong>
          <small>{{ deviceText(item) }}</small>
          <div class="repair-card-meta">
            <span>{{ viewMode === 'recycle' ? `删除于 ${timeText(item.deletedAt)}` : timeText(item.receivedAt) }}</span>
            <span>{{ viewMode === 'recycle' ? item.deletedByName || '未知操作人' : item.handlerName || '未填处理人' }}</span>
          </div>
        </button>
        <div v-if="visibleRepairs.length === 0" class="empty repair-empty">{{ viewMode === 'recycle' ? '回收站为空' : '暂无维修事务' }}</div>
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

          <form class="repair-form" novalidate @input="setDirty(true)" @change="setDirty(true)" @submit.prevent="saveRepair">
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
                  <input id="repairOwnerName" v-model.trim="form.ownerName" name="ownerName" autocomplete="name" :aria-invalid="Boolean(formErrors.ownerName)" required />
                  <small v-if="formErrors.ownerName" class="field-error">{{ formErrors.ownerName }}</small>
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
                  <input id="repairDeviceType" v-model.trim="form.deviceType" name="deviceType" placeholder="笔记本、台式机、打印机等" :aria-invalid="Boolean(formErrors.deviceType)" required />
                  <small v-if="formErrors.deviceType" class="field-error">{{ formErrors.deviceType }}</small>
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
                  <textarea id="repairFaultDescription" v-model.trim="form.faultDescription" name="faultDescription" rows="3" placeholder="送修时描述的现象、报错或处理诉求" :aria-invalid="Boolean(formErrors.faultDescription)" required></textarea>
                  <small v-if="formErrors.faultDescription" class="field-error">{{ formErrors.faultDescription }}</small>
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
              <button class="primary-action" type="submit" :disabled="busy">
                <Save :size="16" />保存事务
              </button>
              <button class="ghost-button" type="button" @click="cancelForm">取消</button>
            </div>
          </form>
        </div>

        <template v-else-if="selectedRepair">
          <div class="participant-panel-head repair-panel-head">
            <div>
              <p class="eyebrow">{{ viewMode === 'recycle' ? 'Recycle Bin' : agreementText(selectedRepair.agreementType) }}</p>
              <h4>{{ selectedRepair.ownerName }} · {{ selectedRepair.deviceType }}</h4>
              <span>{{ selectedRepair.caseNo }} · {{ viewMode === 'recycle' ? `删除于 ${timeText(selectedRepair.deletedAt)}` : timeText(selectedRepair.receivedAt) }}</span>
            </div>
            <div v-if="viewMode === 'active'" class="section-actions">
              <button class="ghost-button" type="button" data-action="preview-agreement" :disabled="busy" @click="openAgreementPreview(selectedRepair)"><Printer :size="16" />预览协议</button>
              <button class="ghost-button" :disabled="busy" @click="startEdit(selectedRepair)">编辑</button>
              <button v-if="canDeleteRepairs" class="ghost-button danger-button" :disabled="busy" @click="deleteRepair(selectedRepair)"><Trash2 :size="16" />删除</button>
              <button class="ghost-button" :disabled="busy" @click="startCreate"><Plus :size="16" />新事务</button>
            </div>
            <div v-else class="section-actions">
              <button class="ghost-button" :disabled="busy" @click="restoreRepair(selectedRepair)"><RotateCcw :size="16" />恢复</button>
              <button class="ghost-button danger-button" :disabled="busy" @click="openPurgeDialog(selectedRepair)"><Trash2 :size="16" />永久删除</button>
            </div>
          </div>

          <div class="repair-focus-line">
            <span class="status-pill" :class="statusClass(selectedRepair.status)">{{ statusText(selectedRepair.status) }}</span>
            <strong>{{ deviceText(selectedRepair) }}</strong>
            <small>{{ viewMode === 'recycle' ? `由 ${selectedRepair.deletedByName || '未知操作人'} 删除` : selectedRepair.handlerName || '未填写处理人' }}</small>
          </div>

          <div class="repair-detail-grid">
            <div><span>联系方式</span><strong>{{ selectedRepair.ownerPhone || '-' }}</strong></div>
            <div><span>随附物品</span><strong>{{ selectedRepair.accessories || '-' }}</strong></div>
            <div><span>完成时间</span><strong>{{ timeText(selectedRepair.completedAt) }}</strong></div>
            <div v-if="viewMode === 'recycle'"><span>删除时间</span><strong>{{ timeText(selectedRepair.deletedAt) }}</strong></div>
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
          <strong>{{ viewMode === 'recycle' ? '回收站为空' : '还没有维修事务' }}</strong>
          <span v-if="viewMode === 'active'">新增一条事务后，可以预览并打印协议、导出 Excel，并随完整备份一起归档。</span>
          <button v-if="viewMode === 'active'" class="primary-action" @click="startCreate"><Plus :size="16" />新事务</button>
        </div>
      </section>
    </div>

    <div v-if="purgeTarget" class="repair-purge-backdrop" role="presentation" @click.self="closePurgeDialog">
      <section class="repair-purge-dialog" role="dialog" aria-modal="true" aria-labelledby="purgeRepairTitle">
        <header>
          <div>
            <p class="eyebrow">Permanent Delete</p>
            <h4 id="purgeRepairTitle">永久删除维修事务</h4>
          </div>
          <button class="ghost-button" type="button" title="关闭" aria-label="关闭" :disabled="busy" @click="closePurgeDialog"><X :size="18" /></button>
        </header>
        <p>系统会先自动生成完整备份。永久删除后，只能从备份文件恢复。</p>
        <label>
          <span>输入维修编号 <strong>{{ purgeTarget.caseNo }}</strong> 继续</span>
          <input v-model.trim="purgeConfirmation" autocomplete="off" :placeholder="purgeTarget.caseNo" @keyup.enter="purgeRepair" />
        </label>
        <div class="repair-purge-actions">
          <button class="ghost-button" type="button" :disabled="busy" @click="closePurgeDialog">取消</button>
          <button class="ghost-button danger-button" type="button" :disabled="busy || purgeConfirmation !== purgeTarget.caseNo" @click="purgeRepair">
            <Trash2 :size="16" />永久删除
          </button>
        </div>
      </section>
    </div>

    <AgreementPreviewDialog
      :open="agreementPreview.open"
      :title="agreementPreview.title"
      :case-no="agreementPreview.caseNo"
      :html="agreementPreview.html"
      :loading="agreementPreview.loading"
      :error="agreementPreview.error"
      @close="closeAgreementPreview"
      @retry="retryAgreementPreview"
      @print-error="notify($event, 'error')"
    />
  </section>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ArrowLeft, Download, FileText, Plus, Printer, RefreshCw, RotateCcw, Save, Trash2, Wrench, X } from '@lucide/vue'
import { api, del, post, put } from '../api.js'
import { agreementPreviewFromBlob } from '../features/repairs/agreementPreview.js'
import { requestConfirmation } from '../shared/confirm.js'
import AgreementPreviewDialog from './AgreementPreviewDialog.vue'

const props = defineProps({
  currentUser: { type: Object, default: null }
})
const emit = defineEmits(['notify', 'dirty-change'])

const today = new Date()
const todayValue = formatLocalDate(today)
const busy = ref(false)
const repairs = ref([])
const recycledRepairs = ref([])
const selectedRepair = ref(null)
const agreementPreview = reactive({
  open: false,
  loading: false,
  error: '',
  html: '',
  title: '维修协议预览',
  caseNo: '',
  item: null
})
const showForm = ref(false)
const editingRepairId = ref(null)
const viewMode = ref('active')
const purgeTarget = ref(null)
const purgeConfirmation = ref('')
const filters = reactive({
  keyword: '',
  status: '',
  from: `${today.getFullYear()}-01-01`,
  to: todayValue
})
const form = reactive(emptyForm())
const formErrors = reactive({ ownerName: '', deviceType: '', faultDescription: '' })
const formDirty = ref(false)

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
const canDeleteRepairs = computed(() => ['PRESIDENT', 'ADMIN'].includes(props.currentUser?.role))
const canManageRecycle = computed(() => props.currentUser?.role === 'ADMIN')
const visibleRepairs = computed(() => viewMode.value === 'recycle' ? recycledRepairs.value : repairs.value)
const openCount = computed(() => repairs.value.filter(item => normalizeStatus(item.status) === 'REPAIRING').length)
const completedCount = computed(() => repairs.value.filter(item => normalizeStatus(item.status) === 'COMPLETED').length)
const canceledCount = computed(() => repairs.value.filter(item => normalizeStatus(item.status) === 'CANCELED').length)

onMounted(async () => {
  await loadRepairs()
  if (canManageRecycle.value) await loadRecycleBin(false)
})

async function loadRepairs() {
  await run(async () => {
    const params = new URLSearchParams()
    if (filters.keyword) params.set('keyword', filters.keyword)
    if (filters.status) params.set('status', filters.status)
    if (filters.from) params.set('from', filters.from)
    if (filters.to) params.set('to', filters.to)
    repairs.value = await api(`/api/repairs?${params.toString()}`)
    if (viewMode.value === 'active') {
      selectedRepair.value = repairs.value.find(item => item.id === selectedRepair.value?.id) || repairs.value[0] || null
    }
  }, false)
}

async function loadRecycleBin(showError = true) {
  if (!canManageRecycle.value) return
  await run(async () => {
    recycledRepairs.value = await api('/api/repairs/recycle-bin')
    if (viewMode.value === 'recycle') {
      selectedRepair.value = recycledRepairs.value.find(item => item.id === selectedRepair.value?.id) || recycledRepairs.value[0] || null
    }
  }, showError)
}

async function showActiveRepairs() {
  if (!await confirmLocalChanges()) return
  viewMode.value = 'active'
  showForm.value = false
  editingRepairId.value = null
  selectedRepair.value = repairs.value[0] || null
}

async function showRecycleBin() {
  if (!canManageRecycle.value) return
  if (!await confirmLocalChanges()) return
  viewMode.value = 'recycle'
  showForm.value = false
  editingRepairId.value = null
  selectedRepair.value = recycledRepairs.value[0] || null
  await loadRecycleBin()
}

async function refreshCurrentView() {
  if (viewMode.value === 'recycle') await loadRecycleBin()
  else await loadRepairs()
}

async function selectRepair(item) {
  if (!await confirmLocalChanges()) return
  selectedRepair.value = item
  showForm.value = false
  editingRepairId.value = null
}

async function startCreate() {
  if (!canManageRepairs.value) return notify('没有维修事务管理权限', 'warn')
  if (!await confirmLocalChanges()) return
  viewMode.value = 'active'
  editingRepairId.value = null
  Object.assign(form, emptyForm())
  clearFormErrors()
  setDirty(false)
  showForm.value = true
}

async function startEdit(item) {
  if (!canManageRepairs.value) return notify('没有维修事务管理权限', 'warn')
  if (!await confirmLocalChanges()) return
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
  clearFormErrors()
  setDirty(false)
  showForm.value = true
}

function cancelForm() {
  showForm.value = false
  editingRepairId.value = null
  Object.assign(form, emptyForm())
  clearFormErrors()
  setDirty(false)
}

async function saveRepair() {
  if (!await validateRepairForm()) return
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
    setDirty(false)
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

async function deleteRepair(item) {
  if (!canDeleteRepairs.value) return notify('只有会长或管理员可以删除维修事务', 'warn')
  if (!await requestConfirmation({
    title: '移入维修回收站',
    message: `确认将维修事务 ${item.caseNo} 移入回收站？管理员可以稍后恢复。`,
    confirmLabel: '移入回收站'
  })) return
  await run(async () => {
    await del(`/api/repairs/${item.id}`)
    selectedRepair.value = null
    notify('维修事务已移入回收站', 'success')
    await loadRepairs()
    if (canManageRecycle.value) await loadRecycleBin(false)
  })
}

async function restoreRepair(item) {
  if (!canManageRecycle.value) return notify('只有管理员可以恢复维修事务', 'warn')
  if (!await requestConfirmation({
    title: '恢复维修事务',
    message: `确认恢复维修事务 ${item.caseNo}？`,
    confirmLabel: '恢复',
    tone: 'steady'
  })) return
  await run(async () => {
    await post(`/api/repairs/${item.id}/restore`)
    selectedRepair.value = null
    notify('维修事务已恢复', 'success')
    await loadRecycleBin(false)
    await loadRepairs()
  })
}

function openPurgeDialog(item) {
  if (!canManageRecycle.value) return notify('只有管理员可以永久删除维修事务', 'warn')
  purgeTarget.value = item
  purgeConfirmation.value = ''
}

function closePurgeDialog() {
  if (busy.value) return
  purgeTarget.value = null
  purgeConfirmation.value = ''
}

async function purgeRepair() {
  const target = purgeTarget.value
  if (!target || purgeConfirmation.value !== target.caseNo) return
  await run(async () => {
    const result = await post(`/api/repairs/${target.id}/purge`, { caseNo: purgeConfirmation.value })
    purgeTarget.value = null
    purgeConfirmation.value = ''
    selectedRepair.value = null
    await loadRecycleBin(false)
    notify(`已永久删除，安全备份：${result.safetyBackup.filename}`, 'success')
  })
}

async function openAgreementPreview(item) {
  if (!item?.id) return
  agreementPreview.open = true
  agreementPreview.loading = true
  agreementPreview.error = ''
  agreementPreview.html = ''
  agreementPreview.title = `${agreementText(item.agreementType)}预览`
  agreementPreview.caseNo = item.caseNo || ''
  agreementPreview.item = item
  try {
    const blob = await api(`/api/repairs/${item.id}/agreement`)
    const preview = await agreementPreviewFromBlob(blob)
    agreementPreview.html = preview.html
  } catch (error) {
    agreementPreview.error = error.message || '协议预览加载失败'
  } finally {
    agreementPreview.loading = false
  }
}

function closeAgreementPreview() {
  agreementPreview.open = false
}

function retryAgreementPreview() {
  if (agreementPreview.item) void openAgreementPreview(agreementPreview.item)
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

function setDirty(dirty) {
  formDirty.value = dirty
  emit('dirty-change', dirty)
}

async function confirmLocalChanges() {
  if (!formDirty.value) return true
  const confirmed = await requestConfirmation({
    title: '放弃未保存的维修修改？',
    message: '当前维修表单尚未保存，继续后这些内容会丢失。',
    confirmLabel: '放弃修改'
  })
  if (confirmed) setDirty(false)
  return confirmed
}

async function validateRepairForm() {
  formErrors.ownerName = form.ownerName ? '' : '请填写送修人'
  formErrors.deviceType = form.deviceType ? '' : '请填写设备类型'
  formErrors.faultDescription = form.faultDescription ? '' : '请填写故障描述'
  const firstInvalidId = formErrors.ownerName
    ? 'repairOwnerName'
    : formErrors.deviceType
      ? 'repairDeviceType'
      : formErrors.faultDescription
        ? 'repairFaultDescription'
        : ''
  if (!firstInvalidId) return true
  await nextTick()
  document.getElementById(firstInvalidId)?.focus()
  return false
}

function clearFormErrors() {
  formErrors.ownerName = ''
  formErrors.deviceType = ''
  formErrors.faultDescription = ''
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

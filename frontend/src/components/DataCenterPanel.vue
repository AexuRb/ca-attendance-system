<template>
  <section class="work-section tab-data tab-data-center">
    <div class="section-head">
      <div>
        <h3>数据中心</h3>
        <span>统一管理模板、导出文件、系统备份和数据恢复</span>
      </div>
      <div class="section-actions">
        <button class="ghost-button" :disabled="busy || localBusy" @click="loadDataCenter">
          <RefreshCw :size="16" />刷新数据
        </button>
      </div>
    </div>

    <div class="data-health-grid">
      <article v-for="item in metrics" :key="item.key" class="data-health-card" :class="`tone-${item.tone || 'steady'}`">
        <span>{{ item.label }}</span>
        <strong>{{ item.total }}</strong>
        <small>{{ item.detail }}</small>
      </article>
    </div>

    <div class="data-center-tabs" role="tablist" aria-label="数据中心功能">
      <button
        type="button"
        role="tab"
        :aria-selected="activeArea === 'export'"
        :class="{ active: activeArea === 'export' }"
        @click="activeArea = 'export'"
      >
        <FileSpreadsheet :size="17" />
        <span>自定义导出</span>
      </button>
      <button
        type="button"
        role="tab"
        :aria-selected="activeArea === 'templates'"
        :class="{ active: activeArea === 'templates' }"
        @click="activeArea = 'templates'"
      >
        <Download :size="17" />
        <span>导入模板</span>
      </button>
      <button
        type="button"
        role="tab"
        :aria-selected="activeArea === 'backup'"
        :class="{ active: activeArea === 'backup' }"
        @click="activeArea = 'backup'"
      >
        <Archive :size="17" />
        <span>备份与恢复</span>
        <small>{{ backups.length }}</small>
      </button>
    </div>

    <Transition name="data-pane" mode="out-in">
      <div v-if="activeArea === 'templates'" key="templates" class="data-center-pane">
        <section class="data-section-block" aria-labelledby="templateSectionTitle">
          <div class="subsection-head">
            <div>
              <h4 id="templateSectionTitle">导入模板</h4>
              <span>使用统一列名，导入前可减少格式错误</span>
            </div>
          </div>
          <div class="data-template-grid">
            <article class="data-tool-card">
              <div class="tool-card-head">
                <UsersRound :size="20" />
                <div>
                  <h4>成员导入模板</h4>
                  <span>用于成员页面的批量新增与资料更新</span>
                </div>
              </div>
              <a class="ghost-button" href="/templates/member-import-template.xlsx" download="成员批量导入模板.xlsx">
                <Download :size="16" />下载模板
              </a>
            </article>

            <article class="data-tool-card">
              <div class="tool-card-head">
                <GraduationCap :size="20" />
                <div>
                  <h4>培训导入模板</h4>
                  <span>用于培训场次的参与名单批量导入</span>
                </div>
              </div>
              <button class="ghost-button" :disabled="busy || localBusy" @click="downloadTrainingTemplate">
                <Download :size="16" />下载模板
              </button>
            </article>

            <article class="data-tool-card">
              <div class="tool-card-head">
                <CalendarDays :size="20" />
                <div>
                  <h4>排班导入模板</h4>
                  <span>按当前值班星期和时段生成可填写表格</span>
                </div>
              </div>
              <button class="ghost-button" :disabled="busy || localBusy" @click="downloadScheduleTemplate">
                <Download :size="16" />下载模板
              </button>
            </article>
          </div>
        </section>
      </div>

      <div v-else-if="activeArea === 'export'" key="export" class="data-center-pane">
        <section class="data-section-block" aria-labelledby="exportSectionTitle">
          <div class="subsection-head">
            <div>
              <h4 id="exportSectionTitle">自定义 Excel 导出</h4>
              <span>按步骤选择数据、检查内容，再生成最终文件</span>
            </div>
          </div>
          <div v-if="selectedExportSource" class="custom-export-wizard">
            <ol class="export-stepper" aria-label="自定义导出步骤">
              <li v-for="step in exportSteps" :key="step.id" :class="{ active: activeExportStep === step.id, complete: furthestExportStep > step.id && activeExportStep !== step.id }">
                <button
                  type="button"
                  :disabled="!canVisitExportStep(step.id)"
                  :aria-current="activeExportStep === step.id ? 'step' : undefined"
                  @click="goToExportStep(step.id)"
                >
                  <span><Check v-if="activeExportStep > step.id" :size="14" /><template v-else>{{ step.id }}</template></span>
                  <strong>{{ step.label }}</strong>
                </button>
              </li>
            </ol>

            <Transition name="export-step" mode="out-in">
              <section v-if="activeExportStep === 1" key="source" class="export-step-panel" aria-labelledby="exportStepSource">
                <header class="export-step-heading">
                  <span>01</span>
                  <div><h5 id="exportStepSource">选择数据源</h5><p>每次导出一种业务数据，后续字段会随数据源变化。</p></div>
                </header>
                <div class="export-source-grid">
                  <button
                    v-for="source in exportOptions"
                    :key="source.id"
                    type="button"
                    :class="{ active: selectedExportSourceId === source.id }"
                    :aria-pressed="selectedExportSourceId === source.id"
                    @click="selectExportSource(source.id)"
                  >
                    <span><component :is="exportSourceIcon(source.id)" :size="20" /></span>
                    <strong>{{ source.label }}</strong>
                    <small>{{ source.fields.length }} 个可选字段</small>
                    <Check v-if="selectedExportSourceId === source.id" :size="17" />
                  </button>
                </div>
              </section>

              <section v-else-if="activeExportStep === 2" key="filters" class="export-step-panel" aria-labelledby="exportStepFilters">
                <header class="export-step-heading">
                  <span>02</span>
                  <div><h5 id="exportStepFilters">设置筛选范围</h5><p>留空表示不限制该条件，日期范围包含开始日和结束日。</p></div>
                </header>
                <div v-if="selectedExportSource.filters.length" class="custom-export-filters export-filter-grid">
                  <label v-for="filter in selectedExportSource.filters" :key="filter.id" :for="`export-filter-${filter.id}`">
                    <span>{{ filter.label }}</span>
                    <select
                      v-if="filter.type === 'select'"
                      :id="`export-filter-${filter.id}`"
                      v-model="exportFilters[filter.id]"
                      :name="filter.id"
                      @change="invalidateExportPreview"
                    >
                      <option value="">全部</option>
                      <option v-for="option in filter.options" :key="option.value" :value="option.value">{{ option.label }}</option>
                    </select>
                    <input
                      v-else
                      :id="`export-filter-${filter.id}`"
                      v-model.trim="exportFilters[filter.id]"
                      :name="filter.id"
                      :type="filter.type"
                      :placeholder="filter.type === 'text' ? `输入${filter.label}` : ''"
                      @input="invalidateExportPreview"
                    />
                  </label>
                </div>
                <div v-else class="export-no-filters">
                  <Check :size="19" />
                  <div><strong>此数据源无需筛选</strong><span>将预览当前全部 {{ selectedExportSource.label }} 数据。</span></div>
                </div>
              </section>

              <section v-else-if="activeExportStep === 3" key="fields" class="export-step-panel" aria-labelledby="exportStepFields">
                <header class="export-step-heading export-fields-heading">
                  <span>03</span>
                  <div><h5 id="exportStepFields">选择字段与顺序</h5><p>Excel 列顺序与下方顺序一致，至少保留一列。</p></div>
                  <button class="ghost-button" type="button" @click="resetExportFields"><RotateCcw :size="15" />恢复默认</button>
                </header>
                <div class="custom-export-fields-head">
                  <strong>已选 {{ selectedExportFieldCount }} / {{ exportFields.length }} 列</strong>
                  <span>使用箭头调整列顺序</span>
                </div>
                <div class="custom-export-field-list export-field-grid">
                  <div v-for="(field, index) in exportFields" :key="field.id" class="custom-export-field-row" :class="{ selected: field.selected }">
                    <label :for="`export-field-${field.id}`">
                      <input :id="`export-field-${field.id}`" v-model="field.selected" type="checkbox" @change="invalidateExportPreview" />
                      <span>{{ field.label }}</span>
                    </label>
                    <div>
                      <button type="button" title="上移" :aria-label="`${field.label}上移`" :disabled="index === 0" @click="moveExportField(index, -1)"><ArrowUp :size="15" /></button>
                      <button type="button" title="下移" :aria-label="`${field.label}下移`" :disabled="index === exportFields.length - 1" @click="moveExportField(index, 1)"><ArrowDown :size="15" /></button>
                    </div>
                  </div>
                </div>
              </section>

              <section v-else-if="activeExportStep === 4" key="preview" class="export-step-panel export-preview-panel" aria-labelledby="exportStepPreview">
                <header class="export-step-heading export-preview-heading">
                  <span>04</span>
                  <div><h5 id="exportStepPreview">预览真实数据</h5><p>最多展示前 12 行，导出时仍会包含筛选后的全部数据。</p></div>
                  <button class="ghost-button" type="button" :disabled="previewLoading" @click="loadExportPreview"><RefreshCw :size="15" />重新预览</button>
                </header>

                <div v-if="previewLoading" class="export-preview-loading" role="status">
                  <RefreshCw :size="20" />正在读取预览数据
                </div>
                <div v-else-if="previewError" class="export-preview-error" role="alert">
                  <AlertTriangle :size="20" />
                  <div><strong>暂时无法生成预览</strong><span>{{ previewError }}</span></div>
                  <button class="ghost-button" type="button" @click="loadExportPreview">重试</button>
                </div>
                <template v-else-if="exportPreview">
                  <div class="export-preview-summary">
                    <span>{{ selectedExportSource.label }}</span>
                    <strong>{{ exportPreview.totalRows }} 行</strong>
                    <small>{{ selectedExportFieldCount }} 列</small>
                  </div>
                  <div class="table-wrap export-preview-table-wrap">
                    <table class="export-preview-table">
                      <thead><tr><th v-for="field in exportPreview.fields" :key="field.id">{{ field.label }}</th></tr></thead>
                      <tbody>
                        <tr v-for="(row, rowIndex) in exportPreview.rows" :key="rowIndex">
                          <td v-for="field in exportPreview.fields" :key="field.id" :title="previewValue(row[field.id])">{{ previewValue(row[field.id]) }}</td>
                        </tr>
                        <tr v-if="exportPreview.rows.length === 0"><td :colspan="exportPreview.fields.length" class="empty">当前筛选范围内没有数据</td></tr>
                      </tbody>
                    </table>
                  </div>
                  <p v-if="exportPreview.truncated" class="export-preview-note">这里只展示前 12 行，Excel 将包含全部 {{ exportPreview.totalRows }} 行。</p>
                </template>
                <div v-else class="export-preview-empty">
                  <FileSpreadsheet :size="24" /><strong>尚未生成预览</strong><button class="ghost-button" type="button" @click="loadExportPreview">开始预览</button>
                </div>
              </section>

              <section v-else key="export" class="export-step-panel export-finish-panel" aria-labelledby="exportStepFinish">
                <header class="export-step-heading">
                  <span>05</span>
                  <div><h5 id="exportStepFinish">确认并导出</h5><p>确认文件名后生成 Excel，文件会保存到浏览器下载位置。</p></div>
                </header>
                <div class="export-final-summary">
                  <div><span>数据源</span><strong>{{ selectedExportSource.label }}</strong></div>
                  <div><span>数据量</span><strong>{{ exportPreview?.totalRows || 0 }} 行</strong></div>
                  <div><span>字段</span><strong>{{ selectedExportFieldCount }} 列</strong></div>
                </div>
                <label class="custom-export-filename" for="customExportFilename">
                  <span>文件名</span>
                  <div>
                    <input id="customExportFilename" v-model.trim="exportFilename" name="filename" maxlength="80" />
                    <small>.xlsx</small>
                  </div>
                </label>
                <button class="primary-action export-final-button" type="button" :disabled="busy || localBusy || !exportPreview" @click="exportCustomExcel">
                  <FileSpreadsheet :size="18" />导出 {{ selectedExportSource.label }} Excel
                </button>
              </section>
            </Transition>

            <footer class="export-wizard-footer">
              <button v-if="activeExportStep > 1" class="ghost-button" type="button" @click="goToExportStep(activeExportStep - 1)">
                <ArrowLeft :size="16" />上一步
              </button>
              <span>第 {{ activeExportStep }} / {{ exportSteps.length }} 步</span>
              <button
                v-if="activeExportStep < exportSteps.length"
                class="primary-action"
                type="button"
                :disabled="!canAdvanceCurrentStep || previewLoading"
                @click="advanceExportStep"
              >
                {{ activeExportStep === 3 ? '生成预览' : activeExportStep === 4 ? '确认并继续' : '下一步' }}
                <ArrowRight :size="16" />
              </button>
            </footer>
          </div>
          <div v-else class="empty custom-export-empty">正在读取导出配置</div>
        </section>
      </div>

      <div v-else key="backup" class="data-center-pane data-backup-layout">
        <div class="data-archive-board">
          <section class="backup-overview">
            <div class="tool-card-head">
              <Archive :size="20" />
              <div>
                <h4>完整系统备份</h4>
                <span>{{ backupOverview.count || 0 }} 个文件，共 {{ bytesText(backupOverview.totalSize || 0) }}</span>
              </div>
            </div>
            <div class="latest-backup-box">
              <span>最近一次备份</span>
              <strong>{{ latestBackup?.filename || '暂无备份文件' }}</strong>
              <small>{{ latestBackup ? `${timeText(latestBackup.createdAt)} · ${bytesText(latestBackup.size)}` : '建议现在生成首个备份' }}</small>
            </div>
            <div class="data-button-row">
              <button class="primary-action" :disabled="busy || localBusy" @click="createBackup">
                <Save :size="16" />生成备份
              </button>
              <button class="ghost-button" :disabled="!latestBackup || busy || localBusy" @click="downloadBackup(latestBackup)">
                <Download :size="16" />下载最新
              </button>
            </div>
          </section>

          <section v-if="canRestoreBackups" class="restore-card">
            <div class="tool-card-head">
              <ShieldCheck :size="20" />
              <div>
                <h4>恢复备份</h4>
                <span>恢复前会自动保存当前数据，完成后需重新登录</span>
              </div>
            </div>
            <div class="restore-file-line">
              <strong>{{ restoreFile ? restoreFile.name : '未选择备份文件' }}</strong>
              <span>{{ restoreFile ? bytesText(restoreFile.size) : '请选择系统生成的 backup_*.zip' }}</span>
            </div>
            <div class="data-button-row">
              <input ref="restoreInput" type="file" accept=".zip,application/zip" hidden @change="selectRestoreFile" />
              <button class="ghost-button" type="button" @click="restoreInput?.click()">
                <Upload :size="16" />选择文件
              </button>
              <button class="ghost-button danger-button" type="button" :disabled="!restoreFile || busy || localBusy" @click="restoreBackup">
                <RefreshCw :size="16" />恢复数据
              </button>
            </div>
          </section>
        </div>

        <section class="recent-backup-panel">
          <div class="subsection-head">
            <div>
              <h4>备份文件</h4>
              <span>可下载到 U 盘，用于换届交接或整机迁移</span>
            </div>
            <strong>{{ backups.length }} 个</strong>
          </div>
          <div class="table-wrap compact-table">
            <table class="data-backup-table">
              <thead>
                <tr><th>文件名</th><th>生成时间</th><th>大小</th><th>操作</th></tr>
              </thead>
              <tbody>
                <tr v-for="item in backups" :key="item.filename">
                  <td class="mono">{{ item.filename }}</td>
                  <td>{{ timeText(item.createdAt) }}</td>
                  <td>{{ bytesText(item.size) }}</td>
                  <td class="actions">
                    <button @click="downloadBackup(item)"><Download :size="14" />下载</button>
                    <button v-if="canDeleteBackups" class="danger" @click="deleteBackup(item)"><Trash2 :size="14" />删除</button>
                  </td>
                </tr>
                <tr v-if="backups.length === 0"><td colspan="4" class="empty">暂无备份文件</td></tr>
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </Transition>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  AlertTriangle,
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  ArrowUp,
  Archive,
  CalendarDays,
  Check,
  ClipboardList,
  Download,
  FileSpreadsheet,
  GraduationCap,
  History,
  RefreshCw,
  RotateCcw,
  Save,
  ShieldCheck,
  Trash2,
  Upload,
  UsersRound,
  Wrench
} from '@lucide/vue'
import { api, del, post } from '../api.js'
import { requestConfirmation } from '../shared/confirm.js'
import {
  canAdvanceExportStep,
  exportPreviewValue,
  exportSteps,
  selectedExportFieldIds
} from '../features/export/exportWizard.js'

const props = defineProps({
  currentUser: { type: Object, required: true }
})

const emit = defineEmits([
  'notify',
  'session-invalidated'
])

const today = new Date()
const todayValue = formatLocalDate(today)
const activeArea = ref('export')
const restoreInput = ref(null)
const maintenanceOperations = ref(0)
const busy = computed(() => maintenanceOperations.value > 0)
const localBusy = ref(false)
const summary = ref(null)
const backups = ref([])
const restoreFile = ref(null)
const exportOptions = ref([])
const selectedExportSourceId = ref('')
const exportFields = ref([])
const exportFilters = reactive({})
const exportFilename = ref('')
const activeExportStep = ref(1)
const furthestExportStep = ref(1)
const exportPreview = ref(null)
const previewLoading = ref(false)
const previewError = ref('')

const metrics = computed(() => summary.value?.datasets || [])
const backupOverview = computed(() => summary.value?.backups || {})
const latestBackup = computed(() => backups.value[0] || null)
const canDeleteBackups = computed(() => props.currentUser?.role === 'ADMIN')
const canRestoreBackups = computed(() => props.currentUser?.role === 'ADMIN')
const selectedExportSource = computed(() => (
  exportOptions.value.find(source => source.id === selectedExportSourceId.value) || null
))
const selectedExportFieldCount = computed(() => exportFields.value.filter(field => field.selected).length)
const canAdvanceCurrentStep = computed(() => canAdvanceExportStep(activeExportStep.value, {
  sourceSelected: Boolean(selectedExportSource.value),
  selectedFieldCount: selectedExportFieldCount.value,
  previewReady: Boolean(exportPreview.value)
}))

onMounted(loadExportOptions)
onMounted(loadDataCenter)

watch(restoreFile, value => {
  if (!value && restoreInput.value) restoreInput.value.value = ''
})

async function loadDataCenter() {
  await runMaintenance(async () => {
    const [nextSummary, items] = await Promise.all([
      api('/api/maintenance/summary'),
      api('/api/maintenance/backups')
    ])
    summary.value = nextSummary
    backups.value = items
  }, false)
}

async function createBackup() {
  if (!['PRESIDENT', 'ADMIN'].includes(props.currentUser?.role)) {
    emit('notify', { message: '只有会长或管理员可以备份数据', type: 'warn' })
    return
  }
  await runMaintenance(async () => {
    await post('/api/maintenance/backups')
    emit('notify', { message: '备份已生成', type: 'success' })
    await loadDataCenter()
  })
}

async function downloadBackup(item) {
  if (!item?.filename) return
  await runMaintenance(async () => {
    const blob = await api(`/api/maintenance/backups/${encodeURIComponent(item.filename)}`)
    downloadBlob(blob, item.filename)
  })
}

async function deleteBackup(item) {
  if (!canDeleteBackups.value) {
    emit('notify', { message: '只有管理员可以删除备份', type: 'warn' })
    return
  }
  const confirmed = await requestConfirmation({
    title: '删除备份',
    message: `确认删除备份 ${item.filename}？删除后无法恢复。`,
    confirmLabel: '删除',
    requiredText: '删除',
    tone: 'danger'
  })
  if (!confirmed) return
  await runMaintenance(async () => {
    await del(`/api/maintenance/backups/${encodeURIComponent(item.filename)}`)
    emit('notify', { message: '备份已删除', type: 'success' })
    await loadDataCenter()
  })
}

function selectRestoreFile(event) {
  restoreFile.value = event.target.files?.[0] || null
}

async function restoreBackup() {
  if (!canRestoreBackups.value) {
    emit('notify', { message: '只有管理员可以恢复备份', type: 'warn' })
    return
  }
  if (!restoreFile.value) {
    emit('notify', { message: '请选择备份 zip 文件', type: 'warn' })
    return
  }
  const confirmed = await requestConfirmation({
    title: '恢复系统备份',
    message: '恢复会覆盖当前成员、签到记录、日志和值班星期。系统会先自动备份当前数据，恢复成功后需要重新登录。',
    confirmLabel: '恢复',
    requiredText: '恢复',
    tone: 'danger'
  })
  if (!confirmed) return

  const formData = new FormData()
  formData.append('file', restoreFile.value)
  await runMaintenance(async () => {
    const result = await api('/api/maintenance/backups/restore', { method: 'POST', body: formData })
    restoreFile.value = null
    backups.value = []
    emit('session-invalidated', `恢复完成，恢复前备份：${result.safetyBackup.filename}`)
  })
}

async function runMaintenance(action, showError = true) {
  maintenanceOperations.value += 1
  try {
    await action()
  } catch (error) {
    if (showError) emit('notify', { message: error.message, type: 'error' })
  } finally {
    maintenanceOperations.value = Math.max(0, maintenanceOperations.value - 1)
  }
}

async function downloadTrainingTemplate() {
  await exportFile('/api/trainings/import-template', '培训名单导入模板.xlsx', '培训导入模板已下载')
}

async function downloadScheduleTemplate() {
  await exportFile('/api/schedules/import-template', '部长排班导入模板.xlsx', '排班导入模板已下载')
}

async function loadExportOptions() {
  localBusy.value = true
  try {
    const result = await api('/api/exports/options')
    exportOptions.value = result.sources || []
    selectedExportSourceId.value = exportOptions.value[0]?.id || ''
    initializeExportSource()
  } catch (error) {
    emit('notify', { message: error.message, type: 'error' })
  } finally {
    localBusy.value = false
  }
}

function initializeExportSource() {
  const source = selectedExportSource.value
  exportFields.value = (source?.fields || []).map(field => ({ ...field, selected: Boolean(field.defaultSelected) }))
  Object.keys(exportFilters).forEach(key => delete exportFilters[key])
  for (const filter of source?.filters || []) exportFilters[filter.id] = filter.defaultValue || ''
  exportFilename.value = source ? `${source.label}_${todayValue}` : ''
  activeExportStep.value = 1
  furthestExportStep.value = 1
  invalidateExportPreview()
}

function selectExportSource(sourceId) {
  if (selectedExportSourceId.value === sourceId) return
  selectedExportSourceId.value = sourceId
  initializeExportSource()
}

function resetExportFields() {
  exportFields.value = (selectedExportSource.value?.fields || [])
    .map(field => ({ ...field, selected: Boolean(field.defaultSelected) }))
  invalidateExportPreview()
}

function moveExportField(index, offset) {
  const target = index + offset
  if (target < 0 || target >= exportFields.value.length) return
  const next = exportFields.value.slice()
  const [field] = next.splice(index, 1)
  next.splice(target, 0, field)
  exportFields.value = next
  invalidateExportPreview()
}

function invalidateExportPreview() {
  exportPreview.value = null
  previewError.value = ''
  furthestExportStep.value = Math.min(furthestExportStep.value, 4)
  if (activeExportStep.value > 4) activeExportStep.value = 4
}

function canVisitExportStep(step) {
  return step <= furthestExportStep.value
}

function goToExportStep(step) {
  if (!canVisitExportStep(step)) return
  activeExportStep.value = step
}

async function advanceExportStep() {
  if (!canAdvanceCurrentStep.value) return
  if (activeExportStep.value === 3) {
    activeExportStep.value = 4
    furthestExportStep.value = Math.max(furthestExportStep.value, 4)
    await loadExportPreview()
    return
  }
  activeExportStep.value += 1
  furthestExportStep.value = Math.max(furthestExportStep.value, activeExportStep.value)
}

async function loadExportPreview() {
  const source = selectedExportSource.value
  const fields = selectedExportFieldIds(exportFields.value)
  if (!source || !fields.length) return
  previewLoading.value = true
  previewError.value = ''
  try {
    exportPreview.value = await api('/api/exports/preview', {
      method: 'POST',
      body: JSON.stringify({
        source: source.id,
        fields,
        filters: { ...exportFilters },
        filename: ''
      })
    })
  } catch (error) {
    exportPreview.value = null
    previewError.value = error.message
  } finally {
    previewLoading.value = false
  }
}

function exportSourceIcon(sourceId) {
  return {
    members: UsersRound,
    attendance: ClipboardList,
    training: GraduationCap,
    schedule: CalendarDays,
    repairs: Wrench,
    logs: History
  }[sourceId] || FileSpreadsheet
}

async function exportCustomExcel() {
  const source = selectedExportSource.value
  const fields = selectedExportFieldIds(exportFields.value)
  if (!source || !fields.length) return
  localBusy.value = true
  try {
    const blob = await api('/api/exports/excel', {
      method: 'POST',
      body: JSON.stringify({
        source: source.id,
        fields,
        filters: { ...exportFilters },
        filename: exportFilename.value
      })
    })
    downloadBlob(blob, exportDownloadFilename(source.label))
    emit('notify', { message: `${source.label}已导出`, type: 'success' })
  } catch (error) {
    emit('notify', { message: error.message, type: 'error' })
  } finally {
    localBusy.value = false
  }
}

const previewValue = exportPreviewValue

async function exportFile(path, filename, message) {
  localBusy.value = true
  try {
    const blob = await api(path)
    downloadBlob(blob, filename)
    emit('notify', { message, type: 'success' })
  } catch (error) {
    emit('notify', { message: error.message, type: 'error' })
  } finally {
    localBusy.value = false
  }
}

function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  URL.revokeObjectURL(url)
}

function exportDownloadFilename(sourceLabel) {
  let filename = String(exportFilename.value || '').trim().replace(/\.xlsx$/i, '')
  filename = filename.replace(/[\\/:*?"<>|\u0000-\u001f]/g, '_').slice(0, 80)
  return `${filename || `${sourceLabel}_${todayValue}`}.xlsx`
}

function bytesText(value) {
  const size = Number(value || 0)
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function timeText(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

function formatLocalDate(value) {
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
</script>

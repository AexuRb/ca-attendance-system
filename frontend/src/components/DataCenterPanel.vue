<template>
  <section class="work-section tab-data tab-data-center">
    <div class="section-head">
      <div>
        <h3>数据中心</h3>
        <span>统一管理模板、导出文件、系统备份和数据恢复</span>
      </div>
      <div class="section-actions">
        <button class="ghost-button" :disabled="busy || localBusy" @click="$emit('refresh')">
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
        :aria-selected="activeArea === 'transfer'"
        :class="{ active: activeArea === 'transfer' }"
        @click="activeArea = 'transfer'"
      >
        <Download :size="17" />
        <span>导入与导出</span>
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
      <div v-if="activeArea === 'transfer'" key="transfer" class="data-center-pane">
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

        <section class="data-section-block" aria-labelledby="exportSectionTitle">
          <div class="subsection-head">
            <div>
              <h4 id="exportSectionTitle">自定义 Excel 导出</h4>
              <span>选择一个数据源，并决定筛选范围、字段和列顺序</span>
            </div>
          </div>
          <div v-if="selectedExportSource" class="custom-export-builder">
            <div class="custom-export-config">
              <label class="custom-export-source">
                <span>数据源</span>
                <select v-model="selectedExportSourceId" @change="initializeExportSource">
                  <option v-for="source in exportOptions" :key="source.id" :value="source.id">{{ source.label }}</option>
                </select>
              </label>

              <div v-if="selectedExportSource.filters.length" class="custom-export-filters">
                <label v-for="filter in selectedExportSource.filters" :key="filter.id">
                  <span>{{ filter.label }}</span>
                  <select v-if="filter.type === 'select'" v-model="exportFilters[filter.id]">
                    <option value="">全部</option>
                    <option v-for="option in filter.options" :key="option.value" :value="option.value">{{ option.label }}</option>
                  </select>
                  <input v-else v-model.trim="exportFilters[filter.id]" :type="filter.type" :placeholder="filter.type === 'text' ? filter.label : ''" />
                </label>
              </div>

              <label class="custom-export-filename">
                <span>文件名</span>
                <div>
                  <input v-model.trim="exportFilename" maxlength="80" />
                  <small>.xlsx</small>
                </div>
              </label>
            </div>

            <div class="custom-export-fields">
              <div class="custom-export-fields-head">
                <div>
                  <strong>导出字段</strong>
                  <span>已选 {{ selectedExportFieldCount }} / {{ exportFields.length }} 列</span>
                </div>
                <button class="ghost-button" type="button" @click="resetExportFields"><RotateCcw :size="15" />恢复默认</button>
              </div>
              <div class="custom-export-field-list">
                <div v-for="(field, index) in exportFields" :key="field.id" class="custom-export-field-row" :class="{ selected: field.selected }">
                  <label>
                    <input v-model="field.selected" type="checkbox" />
                    <span>{{ field.label }}</span>
                  </label>
                  <div>
                    <button type="button" title="上移" :aria-label="`${field.label}上移`" :disabled="index === 0" @click="moveExportField(index, -1)"><ArrowUp :size="15" /></button>
                    <button type="button" title="下移" :aria-label="`${field.label}下移`" :disabled="index === exportFields.length - 1" @click="moveExportField(index, 1)"><ArrowDown :size="15" /></button>
                  </div>
                </div>
              </div>
            </div>

            <div class="custom-export-actions">
              <span>将生成 {{ selectedExportFieldCount }} 列的 {{ selectedExportSource.label }} 表格</span>
              <button class="primary-action" type="button" :disabled="busy || localBusy || selectedExportFieldCount === 0" @click="exportCustomExcel">
                <FileSpreadsheet :size="17" />导出 Excel
              </button>
            </div>
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
              <button class="primary-action" :disabled="busy || localBusy" @click="$emit('create-backup')">
                <Save :size="16" />生成备份
              </button>
              <button class="ghost-button" :disabled="!latestBackup || busy || localBusy" @click="$emit('download-backup', latestBackup)">
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
              <input ref="restoreInput" type="file" accept=".zip,application/zip" hidden @change="$emit('select-restore-file', $event)" />
              <button class="ghost-button" type="button" @click="restoreInput?.click()">
                <Upload :size="16" />选择文件
              </button>
              <button class="ghost-button danger-button" type="button" :disabled="!restoreFile || busy || localBusy" @click="$emit('restore-backup')">
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
                    <button @click="$emit('download-backup', item)"><Download :size="14" />下载</button>
                    <button v-if="canDeleteBackups" class="danger" @click="$emit('delete-backup', item)"><Trash2 :size="14" />删除</button>
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
  ArrowDown,
  ArrowUp,
  Archive,
  CalendarDays,
  Download,
  FileSpreadsheet,
  GraduationCap,
  RefreshCw,
  RotateCcw,
  Save,
  ShieldCheck,
  Trash2,
  Upload,
  UsersRound
} from '@lucide/vue'
import { api } from '../api.js'

const props = defineProps({
  summary: { type: Object, default: null },
  backups: { type: Array, default: () => [] },
  busy: { type: Boolean, default: false },
  restoreFile: { type: Object, default: null },
  canDeleteBackups: { type: Boolean, default: false },
  canRestoreBackups: { type: Boolean, default: false }
})

const emit = defineEmits([
  'refresh',
  'create-backup',
  'download-backup',
  'delete-backup',
  'select-restore-file',
  'restore-backup',
  'notify'
])

const today = new Date()
const todayValue = formatLocalDate(today)
const activeArea = ref('transfer')
const restoreInput = ref(null)
const localBusy = ref(false)
const exportOptions = ref([])
const selectedExportSourceId = ref('')
const exportFields = ref([])
const exportFilters = reactive({})
const exportFilename = ref('')

const metrics = computed(() => props.summary?.datasets || [])
const backupOverview = computed(() => props.summary?.backups || {})
const latestBackup = computed(() => props.backups[0] || null)
const selectedExportSource = computed(() => (
  exportOptions.value.find(source => source.id === selectedExportSourceId.value) || null
))
const selectedExportFieldCount = computed(() => exportFields.value.filter(field => field.selected).length)

onMounted(loadExportOptions)

watch(() => props.restoreFile, value => {
  if (!value && restoreInput.value) restoreInput.value.value = ''
})

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
}

function resetExportFields() {
  exportFields.value = (selectedExportSource.value?.fields || [])
    .map(field => ({ ...field, selected: Boolean(field.defaultSelected) }))
}

function moveExportField(index, offset) {
  const target = index + offset
  if (target < 0 || target >= exportFields.value.length) return
  const next = exportFields.value.slice()
  const [field] = next.splice(index, 1)
  next.splice(target, 0, field)
  exportFields.value = next
}

async function exportCustomExcel() {
  const source = selectedExportSource.value
  const fields = exportFields.value.filter(field => field.selected).map(field => field.id)
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

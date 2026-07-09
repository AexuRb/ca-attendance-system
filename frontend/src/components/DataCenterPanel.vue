<template>
  <section class="work-section tab-data-center">
    <div class="section-head">
      <div>
        <h3>数据中心</h3>
        <span>本地离线数据的导出、备份、恢复和交接检查</span>
      </div>
      <div class="section-actions">
        <button class="ghost-button" :disabled="busy || localBusy" @click="$emit('refresh')">
          <RefreshCw :size="16" />刷新
        </button>
        <button class="primary-action" :disabled="busy || localBusy" @click="$emit('create-backup')">
          <Save :size="17" />一键备份
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

    <div class="data-center-layout">
      <div class="data-export-board">
        <article class="data-tool-card">
          <div class="tool-card-head">
            <UsersRound :size="20" />
            <div>
              <h4>成员导入模板</h4>
              <span>批量导入成员前先使用统一模板</span>
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
              <span>培训参与名单统一按模板导入</span>
            </div>
          </div>
          <button class="ghost-button" :disabled="busy || localBusy" @click="downloadTrainingTemplate">
            <Download :size="16" />下载模板
          </button>
        </article>

        <article class="data-tool-card wide">
          <div class="tool-card-head">
            <ClipboardList :size="20" />
            <div>
              <h4>值班统计导出</h4>
              <span>按日期范围导出 Excel，适合月度或学年归档</span>
            </div>
          </div>
          <div class="data-range-row">
            <input v-model="exportRange.from" type="date" />
            <input v-model="exportRange.to" type="date" />
            <button class="primary-action" :disabled="busy || localBusy" @click="exportAttendance">
              <Download :size="16" />导出
            </button>
          </div>
        </article>

        <article class="data-tool-card wide">
          <div class="tool-card-head">
            <GraduationCap :size="20" />
            <div>
              <h4>培训统计导出</h4>
              <span>导出培训场次和参与名单汇总</span>
            </div>
          </div>
          <div class="data-range-row">
            <input v-model="exportRange.from" type="date" />
            <input v-model="exportRange.to" type="date" />
            <button class="ghost-button" :disabled="busy || localBusy" @click="exportTrainings">
              <Download :size="16" />导出
            </button>
          </div>
        </article>

        <article class="data-tool-card wide">
          <div class="tool-card-head">
            <Wrench :size="20" />
            <div>
              <h4>维修事务导出</h4>
              <span>导出接修、处理记录和协议确认情况</span>
            </div>
          </div>
          <div class="data-range-row">
            <input v-model="exportRange.from" type="date" />
            <input v-model="exportRange.to" type="date" />
            <button class="ghost-button" :disabled="busy || localBusy" @click="exportRepairs">
              <Download :size="16" />导出
            </button>
          </div>
        </article>

        <article v-if="canViewLogs" class="data-tool-card wide sensitive">
          <div class="tool-card-head">
            <History :size="20" />
            <div>
              <h4>操作日志导出</h4>
              <span>仅管理员可导出，用于恢复和关键操作留痕</span>
            </div>
          </div>
          <div class="data-range-row">
            <input v-model="logRange.from" type="date" />
            <input v-model="logRange.to" type="date" />
            <button class="ghost-button" :disabled="busy || localBusy" @click="exportLogs">
              <Download :size="16" />导出
            </button>
          </div>
        </article>
      </div>

      <aside class="data-archive-board">
        <div class="backup-overview">
          <div class="tool-card-head">
            <Archive :size="20" />
            <div>
              <h4>完整备份</h4>
              <span>{{ backupOverview.count || 0 }} 个文件，{{ bytesText(backupOverview.totalSize || 0) }}</span>
            </div>
          </div>
          <div class="latest-backup-box">
            <p class="eyebrow">Latest Backup</p>
            <strong>{{ latestBackup?.filename || '暂无备份文件' }}</strong>
            <span>{{ latestBackup ? `${timeText(latestBackup.createdAt)} · ${bytesText(latestBackup.size)}` : '建议先生成一次备份' }}</span>
          </div>
          <div class="data-button-row">
            <button class="primary-action" :disabled="busy || localBusy" @click="$emit('create-backup')">
              <Save :size="16" />生成备份
            </button>
            <button class="ghost-button" :disabled="!latestBackup || busy || localBusy" @click="$emit('download-backup', latestBackup)">
              <Download :size="16" />下载最新
            </button>
          </div>
        </div>

        <div v-if="canRestoreBackups" class="restore-card">
          <div class="tool-card-head">
            <ShieldCheck :size="20" />
            <div>
              <h4>恢复备份</h4>
              <span>恢复前系统会自动生成安全备份</span>
            </div>
          </div>
          <div class="restore-file-line">
            <strong>{{ restoreFile ? restoreFile.name : '未选择备份文件' }}</strong>
            <span>{{ restoreFile ? bytesText(restoreFile.size) : '请选择 backup_*.zip' }}</span>
          </div>
          <div class="data-button-row">
            <input ref="restoreInput" type="file" accept=".zip,application/zip" hidden @change="$emit('select-restore-file', $event)" />
            <button class="ghost-button" type="button" @click="restoreInput?.click()">
              <Upload :size="16" />选择
            </button>
            <button class="ghost-button danger-button" type="button" :disabled="!restoreFile || busy || localBusy" @click="$emit('restore-backup')">
              <RefreshCw :size="16" />恢复
            </button>
          </div>
        </div>

        <div class="data-shortcuts">
          <button v-for="item in shortcuts" :key="item.tab" class="ghost-button" @click="$emit('open-tab', item.tab)">
            <component :is="item.icon" :size="16" />
            <span>{{ item.label }}</span>
          </button>
        </div>
      </aside>
    </div>

    <div class="recent-backup-panel">
      <div class="subsection-head">
        <h4>最近备份</h4>
        <span>用于拷贝到 U 盘或换届交接留档</span>
      </div>
      <div class="table-wrap compact-table">
        <table class="data-backup-table">
          <thead>
            <tr><th>文件名</th><th>生成时间</th><th>大小</th><th>操作</th></tr>
          </thead>
          <tbody>
            <tr v-for="item in recentBackups" :key="item.filename">
              <td class="mono">{{ item.filename }}</td>
              <td>{{ timeText(item.createdAt) }}</td>
              <td>{{ bytesText(item.size) }}</td>
              <td class="actions">
                <button @click="$emit('download-backup', item)"><Download :size="14" />下载</button>
                <button v-if="canDeleteBackups" class="danger" @click="$emit('delete-backup', item)"><Trash2 :size="14" />删除</button>
              </td>
            </tr>
            <tr v-if="recentBackups.length === 0"><td colspan="4" class="empty">暂无备份文件</td></tr>
          </tbody>
        </table>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import {
  Archive,
  CalendarDays,
  ClipboardList,
  Download,
  GraduationCap,
  History,
  RefreshCw,
  Save,
  ShieldCheck,
  Trash2,
  Upload,
  UsersRound,
  Wrench
} from '@lucide/vue'
import { api } from '../api.js'

const props = defineProps({
  summary: { type: Object, default: null },
  backups: { type: Array, default: () => [] },
  busy: { type: Boolean, default: false },
  restoreFile: { type: Object, default: null },
  canDeleteBackups: { type: Boolean, default: false },
  canRestoreBackups: { type: Boolean, default: false },
  canViewLogs: { type: Boolean, default: false }
})

const emit = defineEmits([
  'refresh',
  'create-backup',
  'download-backup',
  'delete-backup',
  'select-restore-file',
  'restore-backup',
  'open-tab',
  'notify'
])

const today = new Date()
const todayValue = formatLocalDate(today)
const exportRange = reactive({ from: `${today.getFullYear()}-01-01`, to: todayValue })
const logRange = reactive({ from: `${today.getFullYear()}-01-01`, to: todayValue })
const restoreInput = ref(null)
const localBusy = ref(false)

const metrics = computed(() => props.summary?.datasets || [])
const backupOverview = computed(() => props.summary?.backups || {})
const latestBackup = computed(() => props.backups[0] || null)
const recentBackups = computed(() => props.backups.slice(0, 5))
const shortcuts = computed(() => {
  const items = [
    { label: '成员', tab: 'members', icon: UsersRound },
    { label: '培训', tab: 'trainings', icon: GraduationCap },
    { label: '排班', tab: 'schedules', icon: CalendarDays },
    { label: '维修', tab: 'repairs', icon: Wrench }
  ]
  if (props.canViewLogs) items.push({ label: '日志', tab: 'logs', icon: History })
  return items
})

watch(() => props.restoreFile, value => {
  if (!value && restoreInput.value) {
    restoreInput.value.value = ''
  }
})

async function exportAttendance() {
  await exportFile(
    `/api/stats/export?${rangeParams(exportRange)}`,
    `值班记录_${exportRange.from}_${exportRange.to}.xlsx`,
    '值班统计已导出'
  )
}

async function exportTrainings() {
  await exportFile(
    `/api/trainings/export?${rangeParams(exportRange)}`,
    `培训统计_${exportRange.from}_${exportRange.to}.xlsx`,
    '培训统计已导出'
  )
}

async function downloadTrainingTemplate() {
  await exportFile(
    '/api/trainings/import-template',
    '培训名单导入模板.xlsx',
    '培训导入模板已下载'
  )
}

async function exportRepairs() {
  await exportFile(
    `/api/repairs/export?${rangeParams(exportRange)}`,
    `维修事务_${exportRange.from}_${exportRange.to}.xlsx`,
    '维修事务已导出'
  )
}

async function exportLogs() {
  if (!props.canViewLogs) return emit('notify', { message: '只有管理员可以导出操作日志', type: 'warn' })
  await exportFile(
    `/api/logs/export?${rangeParams(logRange)}`,
    `操作日志_${logRange.from}_${logRange.to}.xlsx`,
    '操作日志已导出'
  )
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

function rangeParams(range) {
  const params = new URLSearchParams()
  params.set('from', range.from)
  params.set('to', range.to)
  return params.toString()
}

function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
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

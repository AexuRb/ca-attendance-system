<template>
  <section class="work-section tab-maintenance">
    <div class="section-head">
      <h3>系统维护</h3>
      <div class="section-actions">
        <button class="ghost-button" @click="$emit('load-backups')"><RefreshCw :size="16" />刷新</button>
        <button class="primary-action" @click="$emit('create-backup')" :disabled="busy">
          <Save :size="17" />
          <span>一键备份</span>
        </button>
      </div>
    </div>
    <div class="maintenance-status-strip">
      <div>
        <span>安全备份</span>
        <strong>启用</strong>
      </div>
      <div>
        <span>备份范围</span>
        <strong>10 张核心表</strong>
      </div>
      <div>
        <span>运行方式</span>
        <strong>本机离线</strong>
      </div>
    </div>
    <div class="maintenance-grid">
      <div class="maintenance-panel">
        <div class="subsection-head">
          <h4>数据备份</h4>
          <span>成员、培训、排班、维修记录、签到记录、日志、值班星期和系统配置</span>
        </div>
        <div class="table-wrap">
          <table class="backup-table">
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
      </div>
      <div v-if="canRestoreBackups" class="maintenance-panel restore-panel">
        <div class="subsection-head">
          <h4>数据恢复</h4>
          <span>恢复前会自动备份当前数据，恢复后需要重新登录</span>
        </div>
        <div class="restore-box">
          <div>
            <p class="eyebrow">Restore ZIP</p>
            <strong>{{ restoreFile ? restoreFile.name : '未选择备份文件' }}</strong>
            <span>{{ restoreFile ? bytesText(restoreFile.size) : '请选择系统生成的 backup_*.zip' }}</span>
          </div>
          <div class="restore-actions">
            <input ref="restoreInput" type="file" accept=".zip,application/zip" hidden @change="$emit('select-restore-file', $event)" />
            <button class="ghost-button" type="button" @click="restoreInput?.click()">
              <Upload :size="16" />
              <span>选择备份</span>
            </button>
            <button class="ghost-button danger-button" type="button" :disabled="!restoreFile || busy" @click="$emit('restore-backup')">
              <RefreshCw :size="16" />
              <span>恢复数据</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { ref, watch } from 'vue'
import { Download, RefreshCw, Save, Trash2, Upload } from '@lucide/vue'

const props = defineProps({
  backups: { type: Array, default: () => [] },
  busy: { type: Boolean, default: false },
  restoreFile: { type: Object, default: null },
  canDeleteBackups: { type: Boolean, default: false },
  canRestoreBackups: { type: Boolean, default: false }
})

defineEmits([
  'load-backups',
  'create-backup',
  'download-backup',
  'delete-backup',
  'select-restore-file',
  'restore-backup'
])

const restoreInput = ref(null)

watch(() => props.restoreFile, value => {
  if (!value && restoreInput.value) {
    restoreInput.value.value = ''
  }
})

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
</script>

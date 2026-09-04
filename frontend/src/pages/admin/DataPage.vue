<template>
  <div class="page-stack data-center-page">
    <PageHeader title="数据与备份" />

    <div class="data-center-tabs page-tabs" role="tablist" aria-label="数据中心功能">
      <button
        id="data-tab-export"
        type="button"
        :class="{ active: tab === 'export' }"
        role="tab"
        aria-controls="data-panel-export"
        :aria-selected="tab === 'export'"
        :tabindex="tab === 'export' ? 0 : -1"
        @click="selectTab('export')"
        @keydown="onTabKeydown($event, 'export')"
      >
        <FileSpreadsheet aria-hidden="true" />自定义导出
      </button>
      <button
        id="data-tab-backups"
        type="button"
        :class="{ active: tab === 'backups' }"
        role="tab"
        aria-controls="data-panel-backups"
        :aria-selected="tab === 'backups'"
        :tabindex="tab === 'backups' ? 0 : -1"
        @click="selectTab('backups')"
        @keydown="onTabKeydown($event, 'backups')"
      >
        <DatabaseBackup aria-hidden="true" />本机备份
      </button>
      <button
        v-if="canViewRecycle"
        id="data-tab-recycle"
        type="button"
        :class="{ active: tab === 'recycle' }"
        role="tab"
        aria-controls="data-panel-recycle"
        :aria-selected="tab === 'recycle'"
        :tabindex="tab === 'recycle' ? 0 : -1"
        @click="selectTab('recycle')"
        @keydown="onTabKeydown($event, 'recycle')"
      >
        <ArchiveRestore aria-hidden="true" />维修回收站
      </button>
    </div>

    <div v-if="activeLoadError" class="inline-alert danger" role="alert">
      <span>{{ activeLoadError }}</span>
      <button class="button secondary small" type="button" @click="retryActiveTab">重试</button>
    </div>

    <section
      v-if="tab === 'export'"
      id="data-panel-export"
      class="data-tab-panel"
      role="tabpanel"
      aria-labelledby="data-tab-export"
      tabindex="0"
    >
      <DataExportWorkspace
        :options="options"
        :request="request"
        :preview="preview"
        :summary="summary"
        :date-error="exportDateError"
        :preview-pending="actions.isPending('preview')"
        :export-pending="actions.isPending('export')"
        @select-source="selectSource"
        @update-filter="updateFilter"
        @update-filename="request.filename = $event"
        @toggle-field="toggleField"
        @toggle-all="toggleAll"
        @reset-filters="resetFilters"
        @preview="makePreview"
        @export="exportCustom"
      />
    </section>

    <section
      v-else-if="tab === 'backups'"
      id="data-panel-backups"
      class="data-tab-panel"
      role="tabpanel"
      aria-labelledby="data-tab-backups"
      tabindex="0"
    >
      <BackupWorkspace
        :summary="summary"
        :backups="backups"
        :loading="backupRequest.loading.value"
        :create-pending="actions.isPending('create-backup')"
        :can-restore="canRestore"
        :can-delete="canDeleteBackups"
        :restore-file-error="restoreFileError"
        @request-create="createBackupOpen = true"
        @pick-restore="pickRestore"
        @download="downloadBackup"
        @request-delete="deleteBackupTarget = $event"
      />
    </section>

    <section
      v-else
      id="data-panel-recycle"
      class="data-tab-panel"
      role="tabpanel"
      aria-labelledby="data-tab-recycle"
      tabindex="0"
    >
      <RepairRecycleWorkspace
        :items="recycle"
        :loading="recycleRequest.loading.value"
        :can-purge="canViewRecycle"
        :is-restore-pending="isRestorePending"
        @refresh="loadRecycle"
        @restore="restoreRepair"
        @request-purge="purgeTarget = $event"
      />
    </section>

    <ConfirmDialog
      :open="createBackupOpen"
      title="创建本机备份"
      message="系统将创建当前业务数据的完整本机备份。"
      confirm-label="创建备份"
      :pending="actions.isPending('create-backup')"
      @cancel="createBackupOpen = false"
      @confirm="createBackup"
    />
    <ConfirmDialog
      :open="Boolean(deleteBackupTarget)"
      title="删除备份"
      :message="`永久删除备份 ${deleteBackupTarget?.filename || ''}。`"
      confirm-label="删除备份"
      danger
      :pending="actions.isPending('delete-backup')"
      @cancel="deleteBackupTarget = null"
      @confirm="deleteBackup"
    />
    <ConfirmDialog
      :open="Boolean(purgeTarget)"
      title="彻底删除维修事务"
      :message="`请输入编号 ${purgeTarget?.caseNo || ''} 以确认，系统会先自动备份。`"
      confirm-label="彻底删除"
      danger
      require-reason
      :pending="actions.isPending('purge-repair')"
      @cancel="purgeTarget = null"
      @confirm="purgeRepair"
    />
    <RestoreBackupDialog
      :open="Boolean(restoreTarget)"
      :file="restoreTarget"
      :busy="actions.isPending('restore-backup')"
      @cancel="restoreTarget = null"
      @confirm="restoreBackup"
    />
  </div>
</template>

<script setup lang="ts">
import { ArchiveRestore, DatabaseBackup, FileSpreadsheet } from "@lucide/vue";
import PageHeader from "../../shared/ui/PageHeader.vue";
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
import RestoreBackupDialog from "../../features/maintenance/RestoreBackupDialog.vue";
import DataExportWorkspace from "./data-center/DataExportWorkspace.vue";
import BackupWorkspace from "./data-center/BackupWorkspace.vue";
import RepairRecycleWorkspace from "./data-center/RepairRecycleWorkspace.vue";
import { useDataCenterWorkspace } from "../../features/maintenance/useDataCenterWorkspace";

const {
  tab,
  options,
  request,
  preview,
  summary,
  backups,
  recycle,
  deleteBackupTarget,
  purgeTarget,
  restoreTarget,
  restoreFileError,
  createBackupOpen,
  canDeleteBackups,
  canRestore,
  canViewRecycle,
  exportDateError,
  activeLoadError,
  backupRequest,
  recycleRequest,
  actions,
  selectTab,
  onTabKeydown,
  retryActiveTab,
  selectSource,
  updateFilter,
  resetFilters,
  toggleField,
  toggleAll,
  makePreview,
  exportCustom,
  createBackup,
  downloadBackup,
  deleteBackup,
  pickRestore,
  restoreBackup,
  loadRecycle,
  restoreRepair,
  isRestorePending,
  purgeRepair,
} = useDataCenterWorkspace();
</script>

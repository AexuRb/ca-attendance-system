<template>
  <ExpandedWorkspaceShell
    class="data-center-page system-workspace"
    :data-unified="tab === 'import' || tab === 'export' ? 'true' : undefined"
    :class="{ 'overview-import': tab === 'import', 'overview-export': tab === 'export', 'overview-backups backup-workspace': tab === 'backups', 'overview-recycle': tab === 'recycle', 'data-finish-workspace': tab === 'recycle' }"
    :current-page="tab === 'recycle' ? 'repairs' : tab === 'import' || tab === 'export' ? 'data-center' : tab">
    <template #heading>
      <h1 :id="`data-${tab}-title`" tabindex="-1">{{ tab === 'import' || tab === 'export' ? '数据中心' : tab === 'backups' ? '备份与恢复' : '维修回收站' }}</h1>
      <nav v-if="tab === 'import' || tab === 'export'" class="data-center-subpages data-mode-switch" aria-label="数据中心操作模式">
        <RouterLink :to="{ name: 'data', query: { tab: 'import' } }" :aria-current="tab === 'import' ? 'page' : undefined">数据导入</RouterLink>
        <RouterLink :to="{ name: 'data', query: { tab: 'export' } }" :aria-current="tab === 'export' ? 'page' : undefined">数据导出</RouterLink>
      </nav>
      <RouterLink v-if="tab === 'recycle'" class="button secondary" :to="{ name: 'repairs' }"><ArrowLeft aria-hidden="true" />返回维修事务</RouterLink>
    </template>

    <div v-if="activeLoadError" class="inline-alert danger" role="alert">
      <span>{{ activeLoadError }}</span>
      <button class="button secondary small" type="button" @click="retryActiveTab">重试</button>
    </div>

    <Transition name="data-panel-swap" mode="out-in">
    <DataImportWorkspace v-if="tab === 'import'" />
    <section
      v-else-if="tab === 'export'"
      id="data-panel-export"
      class="data-tab-panel"
      role="region"
      aria-labelledby="data-export-title"
      tabindex="0"
    >
      <DataExportWorkspace
        :options="options"
        :request="request"
        :preview="preview"
        :date-error="exportDateError"
        :action-error="exportActionError"
        :route-source="exportRouteSource"
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
      role="region"
      aria-labelledby="data-backups-title"
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
      role="region"
      aria-labelledby="data-recycle-title"
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
    </Transition>

    <ConfirmDialog
      :open="createBackupOpen"
      backdrop-class="backup-refined-backdrop"
      title="创建本机备份"
      message="系统将创建当前业务数据的完整本机备份。"
      confirm-label="创建备份"
      :pending="actions.isPending('create-backup')"
      @cancel="createBackupOpen = false"
      @confirm="createBackup"
    />
    <ConfirmDialog
      :open="Boolean(deleteBackupTarget)"
      backdrop-class="backup-refined-backdrop"
      title="删除备份"
      :message="`永久删除备份 ${deleteBackupTarget?.filename || ''}。`"
      confirm-label="删除备份"
      danger
      :pending="actions.isPending('delete-backup')"
      @cancel="deleteBackupTarget = null"
      @confirm="deleteBackup"
    />
    <RecyclePurgeDialog :item="purgeTarget" :pending="actions.isPending('purge-repair')"
      @cancel="purgeTarget = null" @confirm="purgeRepair" />
    <RestoreBackupDialog
      :open="Boolean(restoreTarget)"
      backdrop-class="backup-refined-backdrop"
      :file="restoreTarget"
      :busy="actions.isPending('restore-backup')"
      @cancel="restoreTarget = null"
      @confirm="restoreBackup"
    />
  </ExpandedWorkspaceShell>
</template>

<script setup lang="ts">
import { ArrowLeft } from "@lucide/vue";
import { RouterLink } from "vue-router";
import ExpandedWorkspaceShell from "../../layouts/ExpandedWorkspaceShell.vue";
import { provide } from "vue";
import { memberPresentationKey } from "../../shared/ui/presentation";
import "../../styles/workspace.css";
import "../../features/settings/presentation.css";
import "../../features/today/overview-presentation.css";
provide(memberPresentationKey, true);
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
import RestoreBackupDialog from "../../features/maintenance/RestoreBackupDialog.vue";
import DataImportWorkspace from "./data-center/DataImportWorkspace.vue";
import DataExportWorkspace from "./data-center/DataExportWorkspace.vue";
import BackupWorkspace from "./data-center/BackupWorkspace.vue";
import RecyclePurgeDialog from "./data-center/RecyclePurgeDialog.vue";
import RepairRecycleWorkspace from "./data-center/RepairRecycleWorkspace.vue";
import { useDataCenterWorkspace } from "../../features/maintenance/useDataCenterWorkspace";

const {
  tab,
  options,
  request,
  preview,
  exportActionError,
  exportRouteSource,
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

<style src="../../features/maintenance/presentation.css"></style>

<style src="../../features/maintenance/backup-presentation.css"></style>

<style src="../../features/maintenance/recycle-presentation.css"></style>

<style src="../../features/maintenance/import-presentation.css"></style>

<style src="../../features/maintenance/unified-presentation.css"></style>

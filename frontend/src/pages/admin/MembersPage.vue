<template>
  <MemberWorkspaceShell class="members-page member-workspace">
    <template #tools>
      <button class="mw-button" @click="openImport"><Upload />批量导入</button>
      <button class="mw-button primary" @click="openCreate"><UserPlus />新增成员</button>
    </template>
    <template #filters>
      <MemberFilters inline v-model:keyword="filters.keyword" v-model:role="filters.role" v-model:status="filters.status" v-model:grade="filters.grade" :grades="grades" @submit="applyFilters" />
    </template>
    <Transition name="soft-rise">
      <div v-if="selected.size" class="selection-toolbar">
        <div>
          <CheckSquare />
          <strong>已选 {{ selected.size }} 人</strong>
          <button class="text-button" type="button" @click="selected.clear()">
            清除选择
          </button>
        </div>
        <div>
          <button
            class="button secondary small"
            type="button"
            @click="openBulk('ACTIVE')"
          >
            <Power />批量启用
          </button>
          <button
            class="button secondary small"
            type="button"
            @click="openBulk('DISABLED')"
          >
            <PowerOff />批量停用
          </button>
        </div>
      </div>
    </Transition>

    <div v-if="bulkResult" class="result-note member-bulk-result">
      已更新 {{ bulkResult.updated }}，状态未变 {{ bulkResult.unchanged }}，跳过
      {{ bulkResult.skipped }}
      <ul v-if="bulkResult.errors?.length" class="result-issues">
        <li v-for="issue in bulkResult.errors" :key="issue">{{ issue }}</li>
      </ul>
    </div>

    <div v-if="listError" class="inline-alert danger" role="alert">
      <span>{{ listError }}</span>
      <button class="button secondary small" type="button" data-action="retry-members" @click="load()">
        重试
      </button>
    </div>
    <LoadingBlock v-if="listLoading && !members.length" />
    <EmptyState v-else-if="!members.length && !listError" title="没有符合条件的成员" />
    <MemberRecords v-else-if="members.length" :members="members" :selected="selected" :selectable-ids="selectableIds" :all-selected="pageAllSelected" @toggle-page="togglePage" @toggle-member="toggleMember">
      <template #actions="{ member: item }">
        <MemberRowActions :member="item" :editable="canEdit(item)" :self="item.id === user?.id" :deletable="user?.role === 'ADMIN'" :pending="actions.isPending(`member:${item.id}`)" @edit="openEdit(item)" @toggle-status="toggleStatus(item)" @reset-password="resetTarget = item" @delete="deleteTarget = item" />
      </template>
    </MemberRecords>
    <div v-if="total" class="pagination">
      <span>共 {{ total }} 人</span>
      <div>
        <button
          class="button secondary small"
          :disabled="page <= 1 || listLoading"
          @click="setPage(page - 1)"
        >
          <ChevronLeft />上一页
        </button>
        <span>第 {{ page }} / {{ totalPages }} 页</span>
        <button
          class="button secondary small"
          :disabled="page >= totalPages || listLoading"
          @click="setPage(page + 1)"
        >
          下一页<ChevronRight />
        </button>
      </div>
    </div>

    <MemberEditorDialog
      :open="editorOpen"
      :member="editorTarget"
      :operator-role="user?.role"
      :grade-choices="gradeChoices"
      :busy="actions.isPending('save')"
      :lock-account-controls="lockEditorAccountControls"
      @close="closeEditor"
      @save="saveMember"
    />

    <ModalDialog
      :open="importOpen"
      title="批量导入成员"
      eyebrow="EXCEL IMPORT"
      size="sm"
      @close="closeImport"
    >
      <div class="upload-zone">
        <Upload />
        <strong>选择成员 Excel</strong>
        <p>支持 .xlsx 与 .xls 文件</p>
        <input name="memberImportFile" type="file" accept=".xlsx,.xls" @change="pickFile" />
      </div>
      <div v-if="importResult" class="result-note">
        新增 {{ importResult.created }}，更新 {{ importResult.updated }}，跳过
        {{ importResult.skipped }}
        <ul v-if="importResult.errors?.length" class="result-issues">
          <li v-for="issue in importResult.errors" :key="issue">{{ issue }}</li>
        </ul>
      </div>
      <div v-if="importError" class="form-error member-import-error" role="alert">
        {{ importError }}
      </div>
      <template #footer>
        <a
          class="button secondary"
          href="/templates/member-import-template.xlsx"
          download="成员批量导入模板.xlsx"
        >
          <Download />下载模板
        </a>
        <button
          class="button primary"
          :disabled="!importFile || actions.isPending('import')"
          @click="importMembers"
        >
          开始导入
        </button>
      </template>
    </ModalDialog>

    <BulkMemberStatusDialog
      :open="bulkOpen"
      :count="selected.size"
      :status="bulkTargetStatus"
      :busy="actions.isPending('bulk')"
      @close="closeBulk"
      @confirm="applyBulkStatus"
    />

    <MemberPasswordResetDialog
      :open="Boolean(resetTarget)"
      :member="resetTarget"
      :busy="actions.isPending('reset-password')"
      @close="closeReset"
      @confirm="resetPassword"
    />
    <ConfirmDialog
      :open="Boolean(deleteTarget)"
      title="删除成员"
      :message="`仅可永久删除从未参与业务的空白账号。将删除 ${deleteTarget?.name || ''} 的账号，系统会先自动备份；如已有历史记录，请改为停用账号。`"
      confirm-label="删除成员"
      danger
      require-reason
      :pending="actions.isPending('delete')"
      @cancel="deleteTarget = null"
      @confirm="remove"
    />
  </MemberWorkspaceShell>
</template>

<script setup lang="ts">
import {
  CheckSquare,
  ChevronLeft,
  ChevronRight,
  Download,
  Power,
  PowerOff,
  Upload,
  UserPlus,
} from "@lucide/vue";
import MemberWorkspaceShell from "../../features/members/MemberWorkspaceShell.vue";
import MemberRecords from "../../features/members/MemberRecords.vue";
import { provide } from "vue";
import { memberPresentationKey } from "../../shared/ui/presentation";
import "../../features/members/presentation.css";
provide(memberPresentationKey, true);
import LoadingBlock from "../../shared/ui/LoadingBlock.vue";
import EmptyState from "../../shared/ui/EmptyState.vue";

import ModalDialog from "../../shared/ui/ModalDialog.vue";
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
import MemberEditorDialog from "../../features/members/MemberEditorDialog.vue";
import MemberPasswordResetDialog from "../../features/members/MemberPasswordResetDialog.vue";
import BulkMemberStatusDialog from "../../features/members/BulkMemberStatusDialog.vue";
import MemberFilters from "../../features/members/MemberFilters.vue";
import MemberRowActions from "../../features/members/MemberRowActions.vue";
import { useMemberDirectoryWorkspace } from "../../features/members/useMemberDirectoryWorkspace";

const {
  actions,
  applyBulkStatus,
  applyFilters,
  bulkOpen,
  bulkResult,
  bulkTargetStatus,
  canEdit,
  closeBulk,
  closeEditor,
  closeImport,
  closeReset,
  deleteTarget,
  editorOpen,
  editorTarget,
  filters,
  gradeChoices,
  grades,
  importError,
  importFile,
  importMembers,
  importOpen,
  importResult,
  listError,
  listLoading,
  load,
  lockEditorAccountControls,
  members,
  openBulk,
  openCreate,
  openEdit,
  openImport,
  page,
  pageAllSelected,
  pickFile,
  remove,
  resetPassword,
  resetTarget,
  saveMember,
  selected,
  selectableIds,
  setPage,
  toggleMember,
  togglePage,
  toggleStatus,
  total,
  totalPages,
  user,
} = useMemberDirectoryWorkspace();
</script>

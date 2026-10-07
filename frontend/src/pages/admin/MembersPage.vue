<template>
  <MemberWorkspaceShell class="members-page member-workspace">
    <template #tools>
      <button class="mw-button" @click="openImport"><Upload />批量导入</button>
      <button class="mw-button primary" @click="openCreate"><UserPlus />新增成员</button>
    </template>
    <template #filters>
      <MemberFilters inline v-model:keyword="filters.keyword" v-model:role="filters.role" v-model:status="filters.status" v-model:grade="filters.grade" :grades="grades" :applied-filters="appliedFilters" @submit="applyFilters" />
    </template>
    <div v-if="members.length" class="member-result-toolbar" :class="{ 'has-selection': selected.size }">
      <div class="member-result-summary">
        <strong>成员记录</strong>
        <span aria-live="polite">{{ selected.size ? `已选 ${selected.size} 人` : `共 ${total} 人` }}</span>
      </div>
      <ActionMenu :label="selected.size ? `批量操作，已选 ${selected.size} 人` : '请先选择成员'" trigger-text="批量操作" :disabled="!selected.size || listLoading || Boolean(listError)">
        <button role="menuitem" type="button" @click="openBulk('ACTIVE')"><Power aria-hidden="true" />批量启用</button>
        <button role="menuitem" type="button" @click="openBulk('DISABLED')"><PowerOff aria-hidden="true" />批量停用</button>
        <div class="member-bulk-menu-divider" role="separator" />
        <button role="menuitem" type="button" @click="clearSelection"><X aria-hidden="true" />清除选择</button>
      </ActionMenu>
    </div>

    <div v-if="bulkResult" class="result-note member-bulk-result">
      已更新 {{ bulkResult.updated }}，状态未变 {{ bulkResult.unchanged }}，跳过
      {{ bulkResult.skipped }}
      <ul v-if="bulkResult.errors?.length" class="result-issues">
        <li v-for="issue in bulkResult.errors" :key="issue">{{ issue }}</li>
      </ul>
    </div>

    <div class="mw-member-content" :class="{ 'mw-list-state': !members.length }">
      <div v-if="listError" class="inline-alert danger" role="alert">
        <span>{{ listError }}{{ members.length ? '；仍显示上次成功结果，重试将沿用失败时的查询条件' : '' }}</span>
        <button class="button secondary small" type="button" data-action="retry-members" @click="retry()">
          重试
        </button>
      </div>
      <LoadingBlock v-if="listLoading && !members.length" />
      <EmptyState
        v-else-if="!members.length && !listError"
        :title="hasActiveFilters || total > 0 ? '没有符合条件的成员' : '尚未添加成员'"
        :description="hasActiveFilters || total > 0 ? '调整或清除上方筛选条件后重新查询。' : '新增成员建立名册，也可以通过上方的批量导入添加。'"
      >
        <button v-if="!hasActiveFilters && !total" class="button secondary" type="button" @click="openCreate">
          <UserPlus aria-hidden="true" />新增第一位成员
        </button>
      </EmptyState>
      <MemberRecords v-else-if="members.length" :bind-scroll="bindTableScroll" :members="members" :selected="selected" :selectable-ids="selectableIds" :all-selected="allFilteredSelected" :selection-busy="actions.isPending('select-all') || listLoading || Boolean(listError)" @toggle-all="toggleAll" @toggle-member="toggleMember" @table-scroll="rememberTableScroll">
        <template #actions="{ member: item }">
          <MemberRowActions :member="item" :editable="canEdit(item)" :self="item.id === user?.id" :deletable="user?.role === 'ADMIN'" :pending="actions.isPending(`member:${item.id}`) || listLoading || Boolean(listError)" @edit="openEdit(item)" @toggle-status="toggleStatus(item)" @reset-password="resetTarget = item" @delete="deleteTarget = item" />
        </template>
      </MemberRecords>
    </div>
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
      size="lg"
      @close="closeImport"
    >
      <div class="upload-zone" :class="{ 'has-import-preview': importPreview }">
        <Upload />
        <strong>选择成员 Excel</strong>
        <p>支持 .xlsx 与 .xls 文件</p>
        <input name="memberImportFile" :disabled="actions.isPending('import')" type="file" accept=".xlsx,.xls" @change="pickFile" />
      </div>
      <p v-if="importFile" class="member-import-filename">已选择：{{ importFile.name }}</p>
      <section v-if="importPreview" class="member-import-preview" aria-label="服务端校验预览">
        <p aria-live="polite">预计新增 {{ importPreview.created }}，更新 {{ importPreview.updated }}，错误 {{ importPreview.errorCount }} 行。{{ importPreview.valid ? '请核对差异后确认整批导入。' : '校验未通过，整个文件不会写入。' }}</p>
        <ul v-if="importPreview.warnings.length"><li v-for="warning in importPreview.warnings" :key="warning">{{ warning }}</li></ul>
        <p>空白可选字段保留原资料；电话和 QQ 仅提示变更。确认时再次校验。</p>
        <div class="member-import-preview-scroll" tabindex="0">
          <p v-if="importPreview.errorCount > importPreview.errors.length">下方展示前 {{ importPreview.errors.length }} 条错误，请修正文件后重新校验。</p>
          <ul v-if="importPreview.errors.length" class="result-issues"><li v-for="issue in importPreview.errors" :key="issue">{{ issue }}</li></ul>
          <article v-for="change in importPreview.changes" :key="change.studentNo">
            <strong>{{ change.action }} · {{ change.name }} · {{ change.studentNo }}</strong>
            <ul v-if="change.fields.length"><li v-for="field in change.fields" :key="field.field">{{ field.field }}：{{ field.before || '未填写' }} → {{ field.after }}</li></ul>
            <p v-else>导入字段与现有资料一致</p>
          </article>
        </div>
      </section>
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
          @click="importPreview?.valid ? importMembers() : previewMembers()"
        >
          {{ actions.isPending('import') ? '处理中…' : importPreview?.valid ? '确认整批导入' : '服务端校验预览' }}
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
  ChevronLeft,
  ChevronRight,
  Download,
  Power,
  PowerOff,
  Upload,
  UserPlus,
  X,
} from "@lucide/vue";
import MemberWorkspaceShell from "../../features/members/MemberWorkspaceShell.vue";
import MemberRecords from "../../features/members/MemberRecords.vue";
import { provide } from "vue";
import { memberPresentationKey } from "../../shared/ui/presentation";
import "../../styles/workspace.css";
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
import ActionMenu from "../../shared/ui/ActionMenu.vue";
import { useMemberDirectoryWorkspace } from "../../features/members/useMemberDirectoryWorkspace";

const {
  bindTableScroll,
  rememberTableScroll,
  actions,
  applyBulkStatus,
  applyFilters,
  bulkOpen,
  bulkResult,
  bulkTargetStatus,
  canEdit,
  clearSelection,
  closeBulk,
  closeEditor,
  closeImport,
  closeReset,
  deleteTarget,
  editorOpen,
  editorTarget,
  filters,
  appliedFilters,
  hasActiveFilters,
  gradeChoices,
  grades,
  importPreview, previewMembers,
  importError,
  importFile,
  importMembers,
  importOpen,
  importResult,
  listError,
  listLoading,
  retry,
  lockEditorAccountControls,
  members,
  openBulk,
  openCreate,
  openEdit,
  openImport,
  page,
  allFilteredSelected,
  pickFile,
  remove,
  resetPassword,
  resetTarget,
  saveMember,
  selected,
  selectableIds,
  setPage,
  toggleMember,
  toggleAll,
  toggleStatus,
  total,
  totalPages,
  user,
} = useMemberDirectoryWorkspace();
</script>

<style scoped>
.member-import-preview { font-size: 13px; }
.member-import-filename { padding: 8px 0; overflow-wrap: anywhere; }
.upload-zone.has-import-preview { min-height: 88px; padding: 12px; }
.member-import-preview-scroll { max-height: 42vh; overflow: auto; padding: 8px; }
.member-import-preview article { padding: 10px 0; border-bottom: 1px solid var(--border-soft, #dce2e8); }
.member-import-preview li { margin: 4px 0; overflow-wrap: anywhere; }
</style>

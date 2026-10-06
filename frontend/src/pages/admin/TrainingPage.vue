<template>
  <RefinedWorkspaceShell class="affairs-workspace training-presentation" title="培训记录" description="管理培训安排与参与记录" section-key="work" filter-label="筛选培训记录">
    <template #tools
        ><button
          :ref="captureExportButton"
          class="button secondary"
          :disabled="isPending('export-summary') || !summaryExportReady"
          :title="summaryExportTitle"
          @click="exportSummary"
        >
          <Download />{{ isPending('export-summary') ? "正在导出" : "导出统计" }}</button
        ><button class="button primary" @click="openSession()">
          <Plus />新建培训
        </button></template>
    <template #filters><form class="filter-bar" @submit.prevent="applyFilters">
      <label class="filter-grow"
        ><span>关键词</span
        ><input
          v-model.trim="filters.keyword"
          name="trainingKeyword"
          type="search"
          autocomplete="off"
          placeholder="标题、地点或主讲人" /></label
      ><label
        ><span>开始日期</span
        ><input v-model="filters.from" name="trainingFrom" type="date" /></label
      ><label
        ><span>结束日期</span><input v-model="filters.to" name="trainingTo" type="date" /></label
      ><button class="button secondary training-query-submit" :class="{ 'has-pending-filters': filtersPending }" type="submit"><Search />{{ filtersPending ? "更新结果" : "查询" }}</button>
    </form>
      <div v-if="filterError" class="inline-alert danger" role="alert">
        {{ filterError }}
      </div>
    </template>
    <div class="training-query-context">
      <span v-if="hasAppliedSessionQuery" class="training-applied-filters">
        已查询：{{ appliedFilters.from }} 至 {{ appliedFilters.to }} · {{ sessionState.total }} 场
        <template v-if="appliedFilters.keyword"> · 场次关键词：{{ appliedFilters.keyword }}</template>
      </span>
      <QueryStatus
        :loading="sessionState.loading"
        :failed="Boolean(sessionState.error)"
        :dirty="filtersPending"
        :loading-text="hasAppliedSessionQuery ? '正在查询场次，暂示上次结果' : '正在查询场次'"
        :failed-text="hasAppliedSessionQuery ? '场次查询未成功，暂示上次结果；重试沿用上次请求条件' : '场次查询未成功，重试沿用上次请求条件'"
        dirty-text="筛选已修改，查询后生效；导出仍使用已查询条件"
        :idle-text="hasAppliedSessionQuery ? '' : '尚无成功场次查询'"
      />
    </div>
    <TrainingMonthRibbon
      :label="trainingRangeTitle"
      :items="sessions"
      :selected-id="selected?.id || null"
      :total="sessionState.total"
      :page="sessionState.page"
      :page-size="sessionState.pageSize"
      :has-more="sessionState.hasMore"
      :loading="sessionState.loading"
      :error="sessionState.error"
      :bind-scroll="bindRibbonScroll"
      :restoring-history="restoringHistory"
      @scroll="rememberScroll"
      @select="selectSession"
      @page="setSessionPage"
      @shift-month="shiftVisibleMonth"
      @retry="retrySessions"
    />

    <Transition name="training-stage-swap" mode="out-in">
      <section v-if="selected" :key="selected.id" class="training-time-stage">
        <TrainingSessionHeader
          :session="selected"
          :export-pending="isPending('export-session')"
          :archive-pending="isPending('archive-session')"
          @export="downloadSession"
          @edit="openSession(selected)"
          @archive="deleteTarget = selected"
        />
        <TrainingParticipantList
          v-model:keyword="participantKeyword"
          :applied-keyword="appliedParticipantKeyword"
          :items="participants"
          :total="participantState.total"
          :page="participantState.page"
          :page-size="participantState.pageSize"
          :has-more="participantState.hasMore"
          :loading="participantState.loading"
          :error="participantState.error"
          :session-id="selected.id"
          :bind-scroll="bindParticipantScroll"
          @scroll="rememberScroll"
          :delete-pending-id="isPending('delete-participant') ? participantDeleteTarget?.id : null"
          @search="searchParticipants"
          @page="setParticipantPage"
          @add="openParticipant()"
          @import="openImport"
          @edit="openParticipant"
          @delete="participantDeleteTarget = $event"
          @retry="retryParticipants"
        />
      </section>
      <div
        v-else-if="sessions.length"
        class="training-time-stage training-time-stage-empty"
      >
        <EmptyState title="请选择培训场次" />
      </div>
    </Transition>

    <TrainingSessionEditorDialog
      :open="sessionOpen"
      :form="sessionForm"
      :pending="isPending('save-session')"
      @close="closeSession"
      @save="saveSession"
    />
    <TrainingParticipantEditorDialog
      :session="participantSession"
      :saved-message="participantSavedMessage"
      :open="participantOpen"
      :form="participantForm"
      :pending="isPending('save-participant')"
      @close="closeParticipant"
      @save="saveParticipant"
    />
    <TrainingImportDialog
      :open="importOpen"
      :pending="isPending('import-participants')"
      :template-pending="isPending('export-template')"
      :error="importError"
      @close="importOpen = false"
      @import="importParticipants"
      @template="downloadTemplate"
    />
    <ConfirmDialog
      :open="Boolean(deleteTarget)"
      title="归档培训"
      :message="`归档培训“${deleteTarget?.title || ''}”，该场次将不再出现在列表中。`"
      confirm-label="确认归档"
      :pending="isPending('archive-session')"
      @cancel="deleteTarget = null"
      @confirm="archiveSession"
    />
    <ConfirmDialog
      :open="Boolean(participantDeleteTarget)"
      title="删除参与记录"
      :message="`删除 ${participantDeleteTarget?.name || ''} 的培训记录。`"
      confirm-label="删除记录"
      danger
      :pending="isPending('delete-participant')"
      @cancel="participantDeleteTarget = null"
      @confirm="deleteParticipant"
    />
    <ConfirmDialog
      :open="unsaved.confirmOpen.value"
      title="放弃未保存修改"
      message="当前表单还有未保存的内容，放弃后无法恢复。"
      confirm-label="放弃修改"
      danger
      @cancel="unsaved.cancel"
      @confirm="unsaved.discard"
    />
  </RefinedWorkspaceShell>
</template>

<script setup lang="ts">
import {
  Download,
  Plus,
  Search,
} from "@lucide/vue";
import { provide } from "vue";
import RefinedWorkspaceShell from "../../layouts/RefinedWorkspaceShell.vue";
import { memberPresentationKey } from "../../shared/ui/presentation";
import "../../styles/workspace.css";
import "../../features/repairs/presentation.css";
provide(memberPresentationKey, true);
import EmptyState from "../../shared/ui/EmptyState.vue";
import QueryStatus from "../../shared/ui/QueryStatus.vue";
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
import TrainingParticipantList from "../../features/training/TrainingParticipantList.vue";
import TrainingParticipantEditorDialog from "../../features/training/TrainingParticipantEditorDialog.vue";
import TrainingSessionEditorDialog from "../../features/training/TrainingSessionEditorDialog.vue";
import TrainingImportDialog from "../../features/training/TrainingImportDialog.vue";
import TrainingSessionHeader from "../../features/training/TrainingSessionHeader.vue";
import TrainingMonthRibbon from "../../features/training/TrainingMonthRibbon.vue";
import { useTrainingManagementWorkspace } from "../../features/training/useTrainingManagementWorkspace";

const {
  restoringHistory,
  bindRibbonScroll,
  bindParticipantScroll,
  rememberScroll,
  participantSession,
  participantSavedMessage,
  filters,
  appliedFilters,
  hasAppliedSessionQuery,
  sessionState,
  participantState,
  participantKeyword,
  appliedParticipantKeyword,
  selected,
  sessions,
  participants,
  filterError,
  filtersPending,
  summaryExportReady,
  summaryExportTitle,
  trainingRangeTitle,
  sessionOpen,
  participantOpen,
  importOpen,
  importError,
  deleteTarget,
  participantDeleteTarget,
  sessionForm,
  participantForm,
  unsaved,
  isPending,
  applyFilters,
  setSessionPage,
  selectSession,
  setParticipantPage,
  searchParticipants,
  retrySessions,
  retryParticipants,
  shiftVisibleMonth,
  openSession,
  closeSession,
  saveSession,
  archiveSession,
  openParticipant,
  closeParticipant,
  openImport,
  saveParticipant,
  deleteParticipant,
  importParticipants,
  downloadTemplate,
  downloadSession,
  exportSummary,
  captureExportButton,
} = useTrainingManagementWorkspace();
</script>

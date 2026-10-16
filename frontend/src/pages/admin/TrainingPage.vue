<template>
  <ExpandedWorkspaceShell class="overview-training training-presentation" current-page="trainings">
    <template #heading><h1>培训记录</h1><div class="training-page-tools">
      <button data-button-kind="soft" class="button primary workspace-create-button" @click="openSession()"><Plus />新建培训</button>
    </div></template>
    <section class="training-query" aria-label="筛选培训记录"><form class="filter-bar" @submit.prevent="applyFilters">
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
    </section>
    <div v-show="sessionState.loading || sessionState.error || filtersPending" class="training-query-context">
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
        dirty-text="筛选已修改，查询后生效"
        :idle-text="hasAppliedSessionQuery ? '' : '尚无成功场次查询'"
      />
    </div>
    <div :ref="bindWorkspaceScroll" class="training-workbench" :class="{ 'is-empty': !sessions.length }">
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
      @retry="retrySessions"
    />

      <section v-if="selected" class="training-time-stage">
        <TrainingSessionHeader
          :session="selected"
          :archive-pending="isPending('archive-session')"
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
    </div>

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

    <ConfirmDialog
      backdrop-class="training-refined-backdrop"
      :open="Boolean(deleteTarget)"
      title="归档培训"
      :message="`归档培训“${deleteTarget?.title || ''}”，该场次将不再出现在列表中。`"
      confirm-label="确认归档"
      :pending="isPending('archive-session')"
      @cancel="deleteTarget = null"
      @confirm="archiveSession"
    />
    <ConfirmDialog
      backdrop-class="training-refined-backdrop"
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
      backdrop-class="training-refined-backdrop"
      :open="unsaved.confirmOpen.value"
      title="放弃未保存修改"
      message="当前表单还有未保存的内容，放弃后无法恢复。"
      confirm-label="放弃修改"
      danger
      @cancel="unsaved.cancel"
      @confirm="unsaved.discard"
    />
  </ExpandedWorkspaceShell>
</template>

<script setup lang="ts">
import {
  Plus,
  Search,
} from "@lucide/vue";
import { provide } from "vue";
import ExpandedWorkspaceShell from "../../layouts/ExpandedWorkspaceShell.vue";
import { memberPresentationKey } from "../../shared/ui/presentation";
import "../../styles/workspace.css";
import "../../features/today/overview-presentation.css";
provide(memberPresentationKey, true);
import EmptyState from "../../shared/ui/EmptyState.vue";
import QueryStatus from "../../shared/ui/QueryStatus.vue";
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
import TrainingParticipantList from "../../features/training/TrainingParticipantList.vue";
import TrainingParticipantEditorDialog from "../../features/training/TrainingParticipantEditorDialog.vue";
import TrainingSessionEditorDialog from "../../features/training/TrainingSessionEditorDialog.vue";
import TrainingSessionHeader from "../../features/training/TrainingSessionHeader.vue";
import TrainingMonthRibbon from "../../features/training/TrainingMonthRibbon.vue";
import { useTrainingManagementWorkspace } from "../../features/training/useTrainingManagementWorkspace";

const {
  restoringHistory,
  bindWorkspaceScroll,
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
  trainingRangeTitle,
  sessionOpen,
  participantOpen,
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
  openSession,
  closeSession,
  saveSession,
  archiveSession,
  openParticipant,
  closeParticipant,
  saveParticipant,
  deleteParticipant,
} = useTrainingManagementWorkspace();
</script>

<style src="../../features/training/refined-presentation.css"></style>

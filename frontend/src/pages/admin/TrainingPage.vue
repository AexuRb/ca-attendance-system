<template>
  <div class="page-stack training-page">
    <PageHeader
      title="培训记录"
      ><template #actions
        ><button
          :ref="captureExportButton"
          class="button secondary"
          :disabled="isPending('export-summary') || Boolean(filterError)"
          @click="exportSummary"
        >
          <Download />{{ isPending('export-summary') ? "正在导出" : "导出统计" }}</button
        ><button class="button primary" @click="openSession()">
          <Plus />新建培训
        </button></template
      ></PageHeader
    >
    <form class="filter-bar" @submit.prevent="applyFilters">
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
      ><button class="button secondary" type="submit"><Search />查询</button>
    </form>
    <div v-if="filterError" class="inline-alert danger" role="alert">
      {{ filterError }}
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
          :items="participants"
          :total="participantState.total"
          :page="participantState.page"
          :page-size="participantState.pageSize"
          :has-more="participantState.hasMore"
          :loading="participantState.loading"
          :error="participantState.error"
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
  </div>
</template>

<script setup lang="ts">
import {
  Download,
  Plus,
  Search,
} from "@lucide/vue";
import PageHeader from "../../shared/ui/PageHeader.vue";
import EmptyState from "../../shared/ui/EmptyState.vue";
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
import TrainingParticipantList from "../../features/training/TrainingParticipantList.vue";
import TrainingParticipantEditorDialog from "../../features/training/TrainingParticipantEditorDialog.vue";
import TrainingSessionEditorDialog from "../../features/training/TrainingSessionEditorDialog.vue";
import TrainingImportDialog from "../../features/training/TrainingImportDialog.vue";
import TrainingSessionHeader from "../../features/training/TrainingSessionHeader.vue";
import TrainingMonthRibbon from "../../features/training/TrainingMonthRibbon.vue";
import { useTrainingManagementWorkspace } from "../../features/training/useTrainingManagementWorkspace";

const {
  filters,
  sessionState,
  participantState,
  participantKeyword,
  selected,
  sessions,
  participants,
  filterError,
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

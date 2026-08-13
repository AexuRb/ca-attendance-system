<template>
  <div class="page-stack">
    <PageHeader
      title="培训记录"
      description="记录培训场次、参与名单与计入值班的培训时长。"
      ><template #actions
        ><button class="button secondary" :disabled="isPending('export-summary')" @click="exportSummary">
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
          placeholder="标题、地点或主讲人" /></label
      ><label
        ><span>开始日期</span
        ><input v-model="filters.from" type="date" /></label
      ><label
        ><span>结束日期</span><input v-model="filters.to" type="date" /></label
      ><button class="button secondary" type="submit"><Search />查询</button>
    </form>
    <button
      class="button secondary training-mobile-directory-button"
      type="button"
      @click="drawerOpen = true"
    >
      <ListFilter aria-hidden="true" />切换培训场次
      <span>{{ sessionState.total }}</span>
    </button>

    <div class="training-workspace">
      <aside class="training-directory panel">
        <TrainingSessionList
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
          @retry="retrySessions"
        />
      </aside>

      <main class="training-detail panel">
        <EmptyState
          v-if="!selected"
          title="请选择培训场次"
          description="从场次目录中选择一场培训查看详情"
        />
        <template v-else>
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
        </template>
      </main>
    </div>

    <TrainingSessionDrawer
      :open="drawerOpen"
      :items="sessions"
      :selected-id="selected?.id || null"
      :total="sessionState.total"
      :page="sessionState.page"
      :page-size="sessionState.pageSize"
      :has-more="sessionState.hasMore"
      :loading="sessionState.loading"
      :error="sessionState.error"
      @close="drawerOpen = false"
      @select="selectSession"
      @page="setSessionPage"
      @retry="retrySessions"
    />

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
import { computed, onMounted, reactive, ref, watch } from "vue";
import { onBeforeRouteLeave, useRoute, useRouter } from "vue-router";
import {
  Download,
  ListFilter,
  Plus,
  Search,
} from "@lucide/vue";
import PageHeader from "../../shared/ui/PageHeader.vue";
import EmptyState from "../../shared/ui/EmptyState.vue";
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
import { del, get, post, put, downloadBlob } from "../../shared/api";
import { useAsyncTask } from "../../shared/composables/useAsyncTask";
import { usePendingActions } from "../../shared/composables/usePendingActions";
import { useUnsavedChanges } from "../../shared/composables/useUnsavedChanges";
import type {
  TrainingParticipant,
  TrainingParticipantForm,
  TrainingSession,
  TrainingSessionForm,
} from "../../features/training/trainingTypes";
import {
  fetchTrainingParticipantPage,
  fetchTrainingSessionPage,
} from "../../features/training/trainingApi";
import { useTrainingWorkspace } from "../../features/training/useTrainingWorkspace";
import TrainingParticipantList from "../../features/training/TrainingParticipantList.vue";
import TrainingParticipantEditorDialog from "../../features/training/TrainingParticipantEditorDialog.vue";
import TrainingSessionEditorDialog from "../../features/training/TrainingSessionEditorDialog.vue";
import TrainingImportDialog from "../../features/training/TrainingImportDialog.vue";
import TrainingSessionDrawer from "../../features/training/TrainingSessionDrawer.vue";
import TrainingSessionHeader from "../../features/training/TrainingSessionHeader.vue";
import TrainingSessionList from "../../features/training/TrainingSessionList.vue";
const task = useAsyncTask();
const actions = usePendingActions();
const { isPending } = actions;
const route = useRoute();
const router = useRouter();
const sessionOpen = ref(false);
const participantOpen = ref(false);
const importOpen = ref(false);
const drawerOpen = ref(false);
const importError = ref("");
const deleteTarget = ref<TrainingSession | null>(null);
const participantDeleteTarget = ref<TrainingParticipant | null>(null);
const today = localDate(new Date());
const workspace = useTrainingWorkspace({
  loadSessions: fetchTrainingSessionPage,
  loadParticipants: fetchTrainingParticipantPage,
  defaults: {
    from: `${new Date().getFullYear()}-01-01`,
    to: today,
  },
  initialQuery: route.query,
  onQueryChange: replaceRouteQuery,
});
const {
  filters,
  sessions: sessionState,
  participants: participantState,
  participantKeyword,
  selected,
  applyFilters,
  setSessionPage,
  selectSession,
  setParticipantPage,
  searchParticipants,
  retrySessions,
  retryParticipants,
} = workspace;
const sessions = computed(() => sessionState.items);
const participants = computed(() => participantState.items);
const sessionForm = reactive<TrainingSessionForm>({
  id: null,
  title: "",
  trainingDate: today,
  startTime: "",
  endTime: "",
  location: "",
  speaker: "",
  description: "",
});
const participantForm = reactive<TrainingParticipantForm>({
  id: null,
  studentNo: "",
  name: "",
  durationHours: 0,
  remark: "",
});
const sessionBaseline = ref("");
const participantBaseline = ref("");
const unsaved = useUnsavedChanges(() =>
  (sessionOpen.value && snapshot(sessionForm) !== sessionBaseline.value) ||
  (participantOpen.value && snapshot(participantForm) !== participantBaseline.value),
);
onMounted(workspace.initialize);
watch(
  () => route.query,
  async (query) => {
    if (sameQuery(query, workspace.currentQuery())) return;
    await workspace.restoreQuery(query);
  },
);
async function replaceRouteQuery(query: Record<string, string>) {
  if (sameQuery(route.query, query)) return;
  await router.replace({ query });
}
function openSession(item?: TrainingSession) {
  Object.assign(
    sessionForm,
    item
      ? { ...item }
      : {
          id: null,
          title: "",
          trainingDate: today,
          startTime: "",
          endTime: "",
          location: "",
          speaker: "",
          description: "",
        },
  );
  sessionBaseline.value = snapshot(sessionForm);
  sessionOpen.value = true;
}
function closeSession() {
  unsaved.request(() => {
    sessionOpen.value = false;
  });
}
async function saveSession() {
  const payload = {
    ...sessionForm,
    startTime: sessionForm.startTime || null,
    endTime: sessionForm.endTime || null,
  };
  const wasNew = !sessionForm.id;
  const result = await actions.run("save-session", () =>
    sessionForm.id
      ? task.run<TrainingSession>(
          () => put(`/api/trainings/${sessionForm.id}`, payload),
          "培训已更新",
        )
      : task.run<TrainingSession>(
          () => post("/api/trainings", payload),
          "培训已创建",
        ),
  );
  if (result) {
    sessionBaseline.value = snapshot(sessionForm);
    sessionOpen.value = false;
    await workspace.refreshAfterSessionMutation(result.id, wasNew);
  }
}
async function archiveSession() {
  const target = deleteTarget.value;
  if (!target) return;
  const removed = await actions.run("archive-session", () =>
    task.run(
      async () => {
        await del(`/api/trainings/${target.id}`);
        return true;
      },
      "培训已归档",
    ),
  );
  if (removed) {
    deleteTarget.value = null;
    await workspace.refreshAfterSessionMutation(null);
  }
}
function openParticipant(item?: TrainingParticipant) {
  Object.assign(
    participantForm,
    item
      ? { ...item }
      : {
          id: null,
          studentNo: "",
          name: participants.value.length ? "" : selected.value?.speaker || "",
          durationHours: defaultDuration(selected.value),
          remark: "",
        },
  );
  participantBaseline.value = snapshot(participantForm);
  participantOpen.value = true;
}
function closeParticipant() {
  unsaved.request(() => {
    participantOpen.value = false;
  });
}
function openImport() {
  importError.value = "";
  importOpen.value = true;
}
async function saveParticipant() {
  const session = selected.value;
  if (!session) return;
  const path = `/api/trainings/${session.id}/participants`;
  const result = await actions.run("save-participant", () =>
    participantForm.id
      ? task.run<TrainingParticipant>(
          () => put(`${path}/${participantForm.id}`, participantForm),
          "参与记录已更新",
        )
      : task.run<TrainingParticipant>(
          () => post(path, participantForm),
          "参与记录已添加",
        ),
  );
  if (result) {
    participantBaseline.value = snapshot(participantForm);
    participantOpen.value = false;
    await workspace.refreshAfterParticipantMutation();
  }
}
async function deleteParticipant() {
  const session = selected.value;
  const target = participantDeleteTarget.value;
  if (!session || !target) return;
  const removed = await actions.run("delete-participant", () =>
    task.run(
      async () => {
        await del(`/api/trainings/${session.id}/participants/${target.id}`);
        return true;
      },
      "参与记录已删除",
    ),
  );
  if (removed) {
    participantDeleteTarget.value = null;
    await workspace.refreshAfterParticipantMutation();
  }
}
async function importParticipants(file: File) {
  const session = selected.value;
  if (!session) return;
  const body = new FormData();
  body.append("file", file);
  importError.value = "";
  const imported = await actions.run("import-participants", () =>
    task.run(
      async () => {
        await post(`/api/trainings/${session.id}/participants/import`, body);
        return true;
      },
      "名单导入完成",
    ),
  );
  if (imported) {
    importOpen.value = false;
    await workspace.refreshAfterParticipantMutation();
  } else {
    importError.value = task.error.value;
  }
}
async function downloadTemplate() {
  const session = selected.value;
  if (!session) return;
  await actions.run("export-template", async () => {
    const blob = await task.run(() =>
      get<Blob>(`/api/trainings/${session.id}/participants/import-template`),
    );
    if (blob) downloadBlob(blob, `培训名单导入模板_${session.title}.xlsx`);
  });
}
async function downloadSession() {
  const session = selected.value;
  if (!session) return;
  await actions.run("export-session", async () => {
    const blob = await task.run(() =>
      get<Blob>(`/api/trainings/${session.id}/export`),
    );
    if (blob) downloadBlob(blob, `培训名单_${session.title}.xlsx`);
  });
}
async function exportSummary() {
  const p = new URLSearchParams(filters);
  await actions.run("export-summary", async () => {
    const blob = await task.run(() => get<Blob>(`/api/trainings/export?${p}`));
    if (blob) downloadBlob(blob, `培训统计_${filters.from}_${filters.to}.xlsx`);
  });
}
onBeforeRouteLeave(
  () =>
    new Promise<boolean>((resolve) => {
      unsaved.request(() => resolve(true), () => resolve(false));
    }),
);
function defaultDuration(v: TrainingSession | null) {
  if (!v?.startTime || !v?.endTime) return 0;
  const [sh, sm] = v.startTime.split(":").map(Number),
    [eh, em] = v.endTime.split(":").map(Number);
  return Math.max(0, Number(((eh * 60 + em - sh * 60 - sm) / 60).toFixed(2)));
}
function localDate(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function snapshot(value: object) {
  return JSON.stringify(value);
}

function sameQuery(
  current: Record<string, unknown>,
  next: Record<string, string>,
) {
  const currentEntries = Object.entries(current)
    .filter(([, value]) => typeof value === "string" && value)
    .sort(([left], [right]) => left.localeCompare(right));
  const nextEntries = Object.entries(next).sort(([left], [right]) =>
    left.localeCompare(right),
  );
  return JSON.stringify(currentEntries) === JSON.stringify(nextEntries);
}
</script>

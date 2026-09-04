import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { onBeforeRouteLeave, useRoute, useRouter } from "vue-router";
import { del, downloadBlob, get, post, put } from "../../shared/api";
import { useAsyncTask } from "../../shared/composables/useAsyncTask";
import { usePendingActions } from "../../shared/composables/usePendingActions";
import { useUnsavedChanges } from "../../shared/composables/useUnsavedChanges";
import { notify } from "../../shared/composables/useToast";
import { dateRangeError } from "../../shared/validation/dateRange";
import { fetchTrainingParticipantPage, fetchTrainingSessionPage } from "./trainingApi";
import { currentTrainingMonth, shiftTrainingMonth, trainingRangeLabel } from "./trainingCalendar";
import type {
  TrainingParticipant,
  TrainingParticipantForm,
  TrainingSession,
  TrainingSessionForm,
} from "./trainingTypes";
import { useTrainingWorkspace } from "./useTrainingWorkspace";

export function useTrainingManagementWorkspace() {
  const task = useAsyncTask();
  const actions = usePendingActions();
  const route = useRoute();
  const router = useRouter();
  const initialIntent = typeof route.query.intent === "string" ? route.query.intent : "";
  const sessionOpen = ref(false);
  const participantOpen = ref(false);
  const importOpen = ref(false);
  const importError = ref("");
  const exportButton = ref<HTMLButtonElement | null>(null);
  const deleteTarget = ref<TrainingSession | null>(null);
  const participantDeleteTarget = ref<TrainingParticipant | null>(null);
  const today = localDate(new Date());
  const initialMonth = currentTrainingMonth();
  const workspace = useTrainingWorkspace({
    loadSessions: fetchTrainingSessionPage,
    loadParticipants: fetchTrainingParticipantPage,
    defaults: { from: initialMonth.from, to: initialMonth.to },
    initialQuery: route.query,
    onQueryChange: replaceRouteQuery,
  });
  const {
    filters,
    sessions: sessionState,
    participants: participantState,
    participantKeyword,
    selected,
    applyFilters: applyWorkspaceFilters,
    setSessionPage,
    selectSession,
    setParticipantPage,
    searchParticipants,
    retrySessions,
    retryParticipants,
  } = workspace;
  const sessions = computed(() => sessionState.items);
  const participants = computed(() => participantState.items);
  const filterError = computed(() => dateRangeError(filters.from, filters.to));
  const trainingRangeTitle = computed(() => trainingRangeLabel(filters.from, filters.to));
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
  const unsaved = useUnsavedChanges(
    () =>
      (sessionOpen.value && snapshot(sessionForm) !== sessionBaseline.value) ||
      (participantOpen.value && snapshot(participantForm) !== participantBaseline.value),
  );

  onMounted(async () => {
    await workspace.initialize();
    if (initialIntent === "new") openSession();
    if (initialIntent === "import") {
      if (selected.value) openImport();
      else notify("请先选择一个培训场次，再导入参与记录", "warning");
    }
    if (initialIntent === "export") {
      await nextTick();
      exportButton.value?.focus();
    }
  });
  onBeforeUnmount(workspace.dispose);
  watch(
    () => route.query,
    async (query) => {
      if (sameQuery(query, workspace.currentQuery())) return;
      await workspace.restoreQuery(query);
    },
  );
  onBeforeRouteLeave(
    () =>
      new Promise<boolean>((resolve) => {
        unsaved.request(() => resolve(true), () => resolve(false));
      }),
  );

  async function replaceRouteQuery(query: Record<string, string>) {
    if (sameQuery(route.query, query)) return;
    await router.replace({ query });
  }

  async function applyFilters() {
    if (filterError.value) return;
    await applyWorkspaceFilters();
  }

  async function shiftVisibleMonth(step: number) {
    const next = shiftTrainingMonth(filters.from || today, step);
    filters.from = next.from;
    filters.to = next.to;
    await applyWorkspaceFilters();
  }

  function openSession(item?: TrainingSession) {
    Object.assign(
      sessionForm,
      item || {
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
        : task.run<TrainingSession>(() => post("/api/trainings", payload), "培训已创建"),
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
      task.run(async () => {
        await del(`/api/trainings/${target.id}`);
        return true;
      }, "培训已归档"),
    );
    if (removed) {
      deleteTarget.value = null;
      await workspace.refreshAfterSessionMutation(null);
    }
  }

  function openParticipant(item?: TrainingParticipant) {
    Object.assign(
      participantForm,
      item || {
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
        : task.run<TrainingParticipant>(() => post(path, participantForm), "参与记录已添加"),
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
      task.run(async () => {
        await del(`/api/trainings/${session.id}/participants/${target.id}`);
        return true;
      }, "参与记录已删除"),
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
      task.run(async () => {
        await post(`/api/trainings/${session.id}/participants/import`, body);
        return true;
      }, "名单导入完成"),
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
      const blob = await task.run(() => get<Blob>(`/api/trainings/${session.id}/export`));
      if (blob) downloadBlob(blob, `培训名单_${session.title}.xlsx`);
    });
  }

  async function exportSummary() {
    if (filterError.value) return;
    const params = new URLSearchParams(filters);
    await actions.run("export-summary", async () => {
      const blob = await task.run(() => get<Blob>(`/api/trainings/export?${params}`));
      if (blob) downloadBlob(blob, `培训统计_${filters.from}_${filters.to}.xlsx`);
    });
  }

  function captureExportButton(element: unknown) {
    exportButton.value = element instanceof HTMLButtonElement ? element : null;
  }

  return {
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
    isPending: actions.isPending,
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
  };
}

function defaultDuration(value: TrainingSession | null) {
  if (!value?.startTime || !value?.endTime) return 0;
  const [startHour = 0, startMinute = 0] = value.startTime.split(":").map(Number);
  const [endHour = 0, endMinute = 0] = value.endTime.split(":").map(Number);
  return Math.max(
    0,
    Number(((endHour * 60 + endMinute - startHour * 60 - startMinute) / 60).toFixed(2)),
  );
}

function localDate(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function snapshot(value: object) {
  return JSON.stringify(value);
}

function sameQuery(current: Record<string, unknown>, next: Record<string, string>) {
  const currentEntries = Object.entries(current)
    .filter(([, value]) => typeof value === "string" && value)
    .sort(([left], [right]) => left.localeCompare(right));
  const nextEntries = Object.entries(next).sort(([left], [right]) => left.localeCompare(right));
  return JSON.stringify(currentEntries) === JSON.stringify(nextEntries);
}

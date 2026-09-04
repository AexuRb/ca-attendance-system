import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { del, downloadBlob, get, post, put } from "../../shared/api";
import { useAsyncTask } from "../../shared/composables/useAsyncTask";
import { useLatestRequest } from "../../shared/composables/useLatestRequest";
import { usePendingActions } from "../../shared/composables/usePendingActions";
import { updateOwnedRouteQuery } from "../../shared/navigation/routeQueryState";
import type { DutyPeriod } from "../settings/dutyPeriods";
import {
  normalizeDutyWeekdays,
  type DutyWeekdaySetting,
} from "../settings/dutyWeekdays";
import type { ScheduleAssigneeOption } from "./scheduleAssignees";
import { schedulePayload } from "./scheduleEditor";
import type { ScheduleEditorForm, ScheduleSlot } from "./scheduleTypes";

export function useScheduleWorkspace() {
  const task = useAsyncTask();
  const route = useRoute();
  const router = useRouter();
  const request = useLatestRequest();
  const actions = usePendingActions();
  const { loading, error: loadError } = request;
  const editorOpen = ref(false);
  const importOpen = ref(false);
  const deleteTarget = ref<ScheduleSlot | null>(null);
  const slots = ref<ScheduleSlot[]>([]);
  const periods = ref<DutyPeriod[]>([]);
  const dutyWeekdays = ref<DutyWeekdaySetting[]>([]);
  const assigneeCandidates = ref<ScheduleAssigneeOption[]>([]);
  const preferredWeekday = computed(() => {
    const value = Number(
      typeof route.query.weekday === "string" ? route.query.weekday : 0,
    );
    return value >= 1 && value <= 7 ? value : undefined;
  });
  const fallbackWeekdays = [1, 2, 3, 4, 5, 6, 7].map((value, index) => ({
    value,
    label: `星期${"一二三四五六日"[index]}`,
    short: `周${"一二三四五六日"[index]}`,
    enabled: true,
  }));
  const weekdays = computed(() =>
    dutyWeekdays.value.length
      ? dutyWeekdays.value.map((day) => ({
          value: day.weekday,
          label: day.weekday_name,
          short: `周${"一二三四五六日"[day.weekday - 1]}`,
          enabled: day.enabled,
        }))
      : fallbackWeekdays,
  );
  const fixedForm = reactive<ScheduleEditorForm>({
    id: null,
    weekday: 1,
    period: "",
    title: "部长值班",
    location: "协会办公室",
    assignees: [] as ScheduleAssigneeOption[],
    enabled: true,
    note: "",
  });

  onMounted(async () => {
    await loadBase();
    if (route.query.intent === "import") importOpen.value = true;
  });

  async function loadBase() {
    const result = await request.run(
      (signal) =>
        Promise.all([
          get<ScheduleSlot[]>("/api/schedules", { signal }),
          get<DutyPeriod[]>("/api/settings/duty-periods", { signal }),
          get<DutyWeekdaySetting[]>("/api/settings/weekdays", { signal }),
          get<ScheduleAssigneeOption[]>("/api/schedules/assignee-candidates", {
            signal,
          }),
        ]),
      "排班数据加载失败",
    );
    if (!result) return;
    const [scheduleSlots, dutyPeriods, weekdaySettings, managerCandidates] = result;
    slots.value = scheduleSlots;
    periods.value = dutyPeriods.filter((period) => period.enabled !== false);
    dutyWeekdays.value = normalizeDutyWeekdays(weekdaySettings);
    assigneeCandidates.value = managerCandidates;
    if (!periods.value.some((period) => periodKey(period) === fixedForm.period)) {
      fixedForm.period = periods.value[0] ? periodKey(periods.value[0]) : "";
    }
  }

  function openFixed(
    item: ScheduleSlot | null,
    weekday = weekdays.value.find((day) => day.enabled)?.value || 1,
    period?: string,
  ) {
    Object.assign(
      fixedForm,
      item
        ? {
            id: item.id,
            weekday: item.weekday,
            period: periodKey(item),
            title: item.title,
            location: item.location || "",
            assignees: item.assignees
              .filter(
                (assignee): assignee is typeof assignee & { studentNo: string } =>
                  Boolean(assignee.studentNo),
              )
              .map(
                (assignee) =>
                  assigneeCandidates.value.find(
                    (candidate) => candidate.studentNo === assignee.studentNo,
                  ) || { studentNo: assignee.studentNo, name: assignee.name },
              ),
            enabled: item.enabled !== false,
            note: item.note || "",
          }
        : {
            id: null,
            weekday,
            period: period || (periods.value[0] ? periodKey(periods.value[0]) : ""),
            title: "部长值班",
            location: "协会办公室",
            assignees: [],
            enabled: true,
            note: "",
          },
    );
    editorOpen.value = true;
  }

  async function downloadImportTemplate() {
    await actions.run("template", async () => {
      const blob = await task.run(
        () => get<Blob>("/api/schedules/import-template"),
        "排班导入模板已下载",
      );
      if (blob) downloadBlob(blob, "部长排班导入模板.xlsx");
    });
  }

  async function setPreferredWeekday(weekday: number) {
    await updateOwnedRouteQuery(
      router,
      route.query,
      ["weekday"],
      { weekday },
      "push",
    );
  }

  async function saveFixed() {
    await actions.run("save", async () => {
      const payload = schedulePayload(fixedForm);
      const saved = fixedForm.id
        ? await task.run(
            () => put(`/api/schedules/${fixedForm.id}`, payload),
            "排班已更新",
          )
        : await task.run(() => post("/api/schedules", payload), "排班已新增");
      if (saved === undefined) return;
      editorOpen.value = false;
      await loadBase();
    });
  }

  function closeEditor() {
    if (!actions.isPending("save")) editorOpen.value = false;
  }

  function deleteFixed(item: ScheduleSlot) {
    deleteTarget.value = item;
  }

  async function confirmDeleteFixed() {
    const target = deleteTarget.value;
    if (!target) return;
    await actions.run("archive", async () => {
      const archived = await task.run(
        () => del(`/api/schedules/${target.id}`),
        "排班已归档",
      );
      if (archived === undefined) return;
      deleteTarget.value = null;
      await loadBase();
    });
  }

  function periodKey(value: Pick<DutyPeriod, "startTime" | "endTime">) {
    return `${shortTime(value.startTime)}-${shortTime(value.endTime)}`;
  }

  function shortTime(value?: string) {
    return value?.slice(0, 5) || "";
  }

  return {
    actions,
    assigneeCandidates,
    closeEditor,
    confirmDeleteFixed,
    deleteFixed,
    deleteTarget,
    downloadImportTemplate,
    editorOpen,
    fixedForm,
    importOpen,
    loadBase,
    loadError,
    loading,
    openFixed,
    periodKey,
    periods,
    preferredWeekday,
    saveFixed,
    setPreferredWeekday,
    slots,
    shortTime,
    weekdays,
  };
}

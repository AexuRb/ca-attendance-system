import { computed, nextTick, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { downloadBlob, get } from "../../shared/api";
import { useAsyncTask } from "../../shared/composables/useAsyncTask";
import { useLatestRequest } from "../../shared/composables/useLatestRequest";
import { usePendingActions } from "../../shared/composables/usePendingActions";
import {
  routeQuerySignature,
  stringRouteQuery,
  updateOwnedRouteQuery,
} from "../../shared/navigation/routeQueryState";
import { dateRangeError } from "../../shared/validation/dateRange";
import type { StatsSummaryRow } from "./statsSummary";
import type { WeeklyStatsDetail } from "./weeklyStats";

type StatsPreset = "week" | "month" | "year" | "custom";

export function useStatsWorkspace() {
  const task = useAsyncTask();
  const request = useLatestRequest();
  const actions = usePendingActions();
  const route = useRoute();
  const router = useRouter();
  const { loading, error: loadError } = request;
  const rows = ref<StatsSummaryRow[]>([]);
  const weeklyDetail = ref<WeeklyStatsDetail>({ days: [], users: [], cells: {} });
  const from = ref("");
  const to = ref("");
  const preset = ref<StatsPreset>("week");
  const exportButton = ref<HTMLButtonElement | null>(null);
  const routeKeys = ["from", "to", "preset"] as const;
  let routeReady = false;
  let suppressRouteRestore = false;
  const presets = [
    { id: "week", label: "本周" },
    { id: "month", label: "本月" },
    { id: "year", label: "本年" },
  ] as const;
  const totalHours = computed(() =>
    number(rows.value.reduce((sum, item) => sum + Number(item.totalHours || 0), 0)),
  );
  const totalAttendance = computed(() =>
    rows.value.reduce((sum, item) => sum + Number(item.attendanceCount || 0), 0),
  );
  const totalTraining = computed(() =>
    rows.value.reduce((sum, item) => sum + Number(item.trainingCount || 0), 0),
  );
  const hasData = computed(() =>
    preset.value === "week"
      ? weeklyDetail.value.users.length > 0
      : rows.value.length > 0,
  );
  const filterError = computed(() => dateRangeError(from.value, to.value));
  const displayError = computed(() => filterError.value || loadError.value);

  onMounted(async () => {
    restoreRouteState();
    routeReady = true;
    await syncRoute("replace");
    await load();
    if (stringRouteQuery(route.query.intent) === "export") {
      void nextTick(() => exportButton.value?.focus());
    }
  });

  watch(
    () => routeQuerySignature(route.query, routeKeys),
    () => {
      if (!routeReady || suppressRouteRestore) return;
      restoreRouteState();
      void load();
    },
  );

  async function applyPreset(id: "week" | "month" | "year") {
    preset.value = id;
    setPresetRange(id);
    await syncRoute("push");
    await load();
  }

  async function load() {
    if (filterError.value) return;
    const snapshot = { from: from.value, to: to.value, preset: preset.value };
    const query = new URLSearchParams({ from: snapshot.from, to: snapshot.to });
    const value = await request.run(async (signal) => {
      const summary = get<StatsSummaryRow[]>(`/api/stats/summary?${query}`, { signal });
      if (snapshot.preset !== "week") {
        return { summary: await summary, weekly: null };
      }
      const [summaryRows, weekly] = await Promise.all([
        summary,
        get<WeeklyStatsDetail>(`/api/stats/weekly-detail?${query}`, { signal }),
      ]);
      return { summary: summaryRows, weekly };
    }, "统计数据加载失败");
    if (!value) return;
    rows.value = value.summary;
    weeklyDetail.value = value.weekly || { days: [], users: [], cells: {} };
  }

  async function loadCustom() {
    preset.value = "custom";
    if (filterError.value) return;
    await syncRoute("push");
    await load();
  }

  async function exportExcel() {
    if (filterError.value) return;
    const snapshot = { from: from.value, to: to.value };
    await actions.run("export", async () => {
      const blob = await task.run(() =>
        get<Blob>(`/api/stats/export?from=${snapshot.from}&to=${snapshot.to}`),
      );
      if (blob) downloadBlob(blob, `值班统计_${snapshot.from}_${snapshot.to}.xlsx`);
    });
  }

  function captureExportButton(element: unknown) {
    exportButton.value = element instanceof HTMLButtonElement ? element : null;
  }

  const number = (value: number | string | null | undefined) =>
    Number(value || 0).toFixed(Number(value || 0) % 1 ? 1 : 0);
  const roleLabels: Record<string, string> = {
    MEMBER: "成员",
    MINISTER: "部长",
    PRESIDENT: "会长",
    ADMIN: "管理员",
  };
  const roleLabel = (value: string) => roleLabels[value] || value;

  function date(value: Date) {
    return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, "0")}-${String(value.getDate()).padStart(2, "0")}`;
  }

  function restoreRouteState() {
    const queryFrom = stringRouteQuery(route.query.from);
    const queryTo = stringRouteQuery(route.query.to);
    const queryPreset = stringRouteQuery(route.query.preset);
    const validPreset = ["week", "month", "year", "custom"].includes(queryPreset)
      ? (queryPreset as StatsPreset)
      : queryFrom && queryTo
        ? "custom"
        : "week";
    preset.value = validPreset;
    if (queryFrom && queryTo) {
      from.value = queryFrom;
      to.value = queryTo;
    } else {
      setPresetRange(validPreset === "custom" ? "week" : validPreset);
    }
  }

  function setPresetRange(id: "week" | "month" | "year") {
    const now = new Date();
    to.value = date(now);
    const start = new Date(now);
    if (id === "week") start.setDate(now.getDate() - ((now.getDay() + 6) % 7));
    else if (id === "month") start.setDate(1);
    else start.setMonth(0, 1);
    from.value = date(start);
  }

  async function syncRoute(mode: "push" | "replace") {
    suppressRouteRestore = true;
    try {
      await updateOwnedRouteQuery(
        router,
        route.query,
        routeKeys,
        { from: from.value, to: to.value, preset: preset.value },
        mode,
      );
    } finally {
      await nextTick();
      suppressRouteRestore = false;
    }
  }

  return {
    actions,
    applyPreset,
    captureExportButton,
    displayError,
    exportExcel,
    filterError,
    from,
    hasData,
    load,
    loadCustom,
    loading,
    loadError,
    number,
    preset,
    presets,
    roleLabel,
    rows,
    to,
    totalAttendance,
    totalHours,
    totalTraining,
    weeklyDetail,
  };
}

import { computed, nextTick, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { del, downloadBlob, get } from "../../shared/api";
import { useAsyncTask } from "../../shared/composables/useAsyncTask";
import { useLatestRequest } from "../../shared/composables/useLatestRequest";
import { usePendingActions } from "../../shared/composables/usePendingActions";
import { positiveRoutePage, routeQuerySignature, stringRouteQuery, updateOwnedRouteQuery } from "../../shared/navigation/routeQueryState";
import { dateRangeError } from "../../shared/validation/dateRange";
import { auditActionLabel, auditActionOptions, auditTargetLabel, buildAuditDiff } from "./logDisplay";
import type { OperationLog, OperationLogPage } from "./logTypes";

type LogFilters = { keyword: string; actionType: string; from: string; to: string };
type PendingQuery = { filters: LogFilters; page: number; routeMode?: "push" | "replace" };

export function useAuditLogWorkspace() {
  const task = useAsyncTask();
  const route = useRoute();
  const router = useRouter();
  const listRequest = useLatestRequest();
  const actions = usePendingActions();
  const { loading: listLoading, error: listError } = listRequest;
  const items = ref<OperationLog[]>([]);
  const total = ref(0);
  const page = ref(1);
  const pageSize = 20;
  const detail = ref<OperationLog | null>(null);
  const clearOpen = ref(false);
  const justCleared = ref(false);
  const filters = reactive<LogFilters>({ keyword: "", actionType: "", from: "", to: "" });
  const appliedFilters = ref<LogFilters>({ ...filters });
  const pendingQuery = ref<PendingQuery | null>(null);
  const routeKeys = ["actionType", "from", "to", "page", "keyword"] as const;
  let routeReady = false;
  let suppressRouteRestore = false;
  let queryVersion = 0;
  const filterError = computed(() => dateRangeError(filters.from, filters.to));
  const displayError = computed(() => filterError.value || listError.value);
  const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)));
  const detailRows = computed(() => detail.value ? buildAuditDiff(detail.value.beforeData, detail.value.afterData) : []);
  const hasAppliedFilters = computed(() => Object.values(appliedFilters.value).some(Boolean));

  onMounted(async () => {
    restoreRouteState(true);
    appliedFilters.value = { ...filters };
    const initialPage = positiveRoutePage(route.query.page);
    routeReady = true;
    await syncRoute(initialPage, "replace", appliedFilters.value);
    await load(initialPage);
  });
  watch(
    () => routeQuerySignature(route.query, routeKeys),
    () => {
      if (!routeReady || suppressRouteRestore) return;
      restoreRouteState(false);
      filters.keyword = appliedFilters.value.keyword;
      appliedFilters.value = { ...filters };
      void load(positiveRoutePage(route.query.page));
    },
  );

  async function load(target = page.value) {
    return runQuery({ filters: { ...appliedFilters.value }, page: target });
  }

  async function runQuery(request: PendingQuery) {
    const currentQuery = ++queryVersion;
    const query = params({ ...request.filters, page: request.page, pageSize });
    const value = await listRequest.run(
      (signal) => get<OperationLogPage>(`/api/logs?${query}`, { signal }),
      "操作日志加载失败",
    );
    if (!value) {
      if (currentQuery === queryVersion && listError.value) pendingQuery.value = request;
      return false;
    }
    items.value = value.items;
    if (value.items.length) justCleared.value = false;
    total.value = value.total;
    page.value = value.page;
    appliedFilters.value = { ...request.filters };
    pendingQuery.value = null;
    if (request.routeMode) await syncRoute(value.page, request.routeMode, request.filters);
    return true;
  }

  async function retryLoad() {
    await runQuery(pendingQuery.value || { filters: { ...appliedFilters.value }, page: page.value });
  }

  async function exportLogs() {
    const snapshot = { ...appliedFilters.value };
    await actions.run("export", async () => {
      const blob = await task.run(() => get<Blob>(`/api/logs/export?${params(snapshot)}`));
      if (blob) downloadBlob(blob, "操作日志.xlsx");
    });
  }

  async function clearLogs() {
    await actions.run("clear", async () => {
      const cleared = await task.run(() => del("/api/logs"), "日志已清空，安全备份已创建");
      if (cleared === undefined) return;
      clearOpen.value = false;
      justCleared.value = true;
      items.value = [];
      total.value = 0;
      page.value = 1;
      await runQuery({ filters: { ...appliedFilters.value }, page: 1, routeMode: "replace" });
    });
  }

  function params(value: Record<string, string | number | null | undefined>) {
    const query = new URLSearchParams();
    Object.entries(value).forEach(([key, entry]) => entry !== "" && entry != null && query.set(key, String(entry)));
    return query;
  }

  const date = (value: string) => value?.slice(0, 10);
  const time = (value: string) => value?.slice(11, 16);
  const actionLabel = auditActionLabel;
  const actionTone = (value: string): "neutral" | "info" | "success" | "danger" =>
    value?.includes("DELETE") ? "danger" : value?.includes("CREATE") ? "success" : value?.includes("UPDATE") ? "info" : "neutral";
  const targetLabel = auditTargetLabel;
  function pretty(value?: string) {
    if (!value) return "无";
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch {
      return value;
    }
  }

  async function applyFilters() {
    if (filterError.value) return;
    await runQuery({ filters: { ...filters }, page: 1, routeMode: "push" });
  }
  async function setPage(target: number) {
    await runQuery({ filters: { ...appliedFilters.value }, page: target, routeMode: "push" });
  }
  function restoreRouteState(includeSensitiveKeyword: boolean) {
    filters.actionType = stringRouteQuery(route.query.actionType);
    filters.from = stringRouteQuery(route.query.from);
    filters.to = stringRouteQuery(route.query.to);
    if (includeSensitiveKeyword) filters.keyword = stringRouteQuery(route.query.keyword);
  }
  async function syncRoute(targetPage: number, mode: "push" | "replace", selected: LogFilters) {
    suppressRouteRestore = true;
    try {
      await updateOwnedRouteQuery(router, route.query, routeKeys, {
        actionType: selected.actionType,
        from: selected.from,
        to: selected.to,
        page: targetPage > 1 ? targetPage : undefined,
      }, mode);
    } finally {
      await nextTick();
      suppressRouteRestore = false;
    }
  }

  return {
    actionLabel, actionTone, actions, applyFilters, auditActionOptions, clearLogs, clearOpen,
    date, detail, detailRows, displayError, exportLogs, filterError, filters, items, listError,
    hasAppliedFilters, justCleared, listLoading, load, page, pretty, retryLoad, setPage, targetLabel, time, total, totalPages,
  };
}

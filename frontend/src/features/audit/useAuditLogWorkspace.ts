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
  const filters = reactive({ keyword: "", actionType: "", from: "", to: "" });
  const routeKeys = ["actionType", "from", "to", "page", "keyword"] as const;
  let routeReady = false;
  let suppressRouteRestore = false;
  const filterError = computed(() => dateRangeError(filters.from, filters.to));
  const displayError = computed(() => filterError.value || listError.value);
  const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)));
  const detailRows = computed(() => detail.value ? buildAuditDiff(detail.value.beforeData, detail.value.afterData) : []);

  onMounted(async () => {
    restoreRouteState(true);
    const initialPage = positiveRoutePage(route.query.page);
    routeReady = true;
    await syncRoute(initialPage, "replace");
    await load(initialPage);
  });
  watch(
    () => routeQuerySignature(route.query, routeKeys),
    () => {
      if (!routeReady || suppressRouteRestore) return;
      restoreRouteState(false);
      void load(positiveRoutePage(route.query.page));
    },
  );

  async function load(target = page.value) {
    if (filterError.value) return;
    const query = params({ ...filters, page: target, pageSize });
    const value = await listRequest.run(
      (signal) => get<OperationLogPage>(`/api/logs?${query}`, { signal }),
      "操作日志加载失败",
    );
    if (!value) return;
    items.value = value.items;
    total.value = value.total;
    page.value = value.page;
  }

  async function exportLogs() {
    if (filterError.value) return;
    const snapshot = { ...filters };
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
      await load(1);
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
    await syncRoute(1, "push");
    await load(1);
  }
  async function setPage(target: number) {
    await syncRoute(target, "push");
    await load(target);
  }
  function restoreRouteState(includeSensitiveKeyword: boolean) {
    filters.actionType = stringRouteQuery(route.query.actionType);
    filters.from = stringRouteQuery(route.query.from);
    filters.to = stringRouteQuery(route.query.to);
    if (includeSensitiveKeyword) filters.keyword = stringRouteQuery(route.query.keyword);
  }
  async function syncRoute(targetPage: number, mode: "push" | "replace") {
    suppressRouteRestore = true;
    try {
      await updateOwnedRouteQuery(router, route.query, routeKeys, {
        actionType: filters.actionType,
        from: filters.from,
        to: filters.to,
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
    listLoading, load, page, pretty, setPage, targetLabel, time, total, totalPages,
  };
}

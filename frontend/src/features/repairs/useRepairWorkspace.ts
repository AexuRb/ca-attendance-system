import { reactive, ref } from "vue";
import type {
  RepairFilters,
  RepairPage,
  RepairStatus,
  RepairStatusCounts,
  RepairWorkspaceRouteState,
} from "./repairTypes";

const DEFAULT_PAGE_SIZE = 30;
const STATUSES: RepairStatus[] = ["REPAIRING", "COMPLETED", "CANCELED"];

type QueryValue = string | null | undefined | Array<string | null>;
export type RepairWorkspaceQuery = Record<string, QueryValue>;

export interface RepairPageRequest {
  status: RepairStatus;
  page: number;
  pageSize: number;
  filters: RepairFilters;
  signal: AbortSignal;
}

export interface RepairPageState extends RepairPage {
  loading: boolean;
  error: string;
}

interface WorkspaceOptions {
  loadPage: (request: RepairPageRequest) => Promise<RepairPage>;
  defaults: Pick<RepairFilters, "from" | "to">;
  initialQuery?: RepairWorkspaceQuery;
  onQueryChange?: (
    query: Record<string, string>,
    mode: "push" | "replace",
  ) => void;
  pageSize?: number;
}

export function useRepairWorkspace(options: WorkspaceOptions) {
  const initial = parseRepairWorkspaceQuery(
    options.initialQuery || {},
    options.defaults,
  );
  const activeStatus = ref<RepairStatus>(initial.status);
  const filters = reactive<RepairFilters>({
    keyword: initial.keyword,
    from: initial.from,
    to: initial.to,
  });
  const counts = reactive<RepairStatusCounts>(emptyCounts());
  const page = reactive<RepairPageState>(
    createPageState(options.pageSize || DEFAULT_PAGE_SIZE, initial.page),
  );
  let version = 0;
  let controller: AbortController | null = null;

  async function initialize() {
    await loadCurrent(initial.page);
    syncQuery("replace");
  }

  async function applyFilters() {
    await loadCurrent(1);
    syncQuery("push");
  }

  async function setStatus(status: RepairStatus) {
    if (activeStatus.value === status) return;
    activeStatus.value = status;
    await loadCurrent(1);
    syncQuery("push");
  }

  async function setPage(nextPage: number) {
    await loadCurrent(normalizePage(nextPage));
    syncQuery("push");
  }

  async function retry() {
    await loadCurrent(page.page, false);
    syncQuery("replace");
  }

  async function refreshAfterMutation(
    _previousStatus?: RepairStatus | null,
    _nextStatus?: RepairStatus | null,
  ) {
    await loadCurrent(page.page, false);
    syncQuery("replace");
  }

  async function restoreQuery(query: RepairWorkspaceQuery) {
    const restored = parseRepairWorkspaceQuery(query, options.defaults);
    activeStatus.value = restored.status;
    Object.assign(filters, {
      keyword: restored.keyword,
      from: restored.from,
      to: restored.to,
    });
    await loadCurrent(restored.page);
    syncQuery("replace");
  }

  function currentQuery() {
    return serializeRepairWorkspaceQuery({
      status: activeStatus.value,
      page: page.page,
      ...filters,
    });
  }

  async function loadCurrent(targetPage: number, clearItems = true): Promise<boolean> {
    controller?.abort();
    controller = new AbortController();
    const requestController = controller;
    const requestVersion = ++version;
    const requestStatus = activeStatus.value;
    page.page = normalizePage(targetPage);
    if (clearItems) page.items = [];
    page.loading = true;
    page.error = "";
    try {
      const result = await options.loadPage({
        status: requestStatus,
        page: page.page,
        pageSize: page.pageSize,
        filters: copyFilters(filters),
        signal: requestController.signal,
      });
      if (!isCurrent(requestVersion, requestStatus)) return false;
      if (!result.items.length && result.page > 1 && result.total > 0) {
        return loadCurrent(lastPage(result));
      }
      applyPage(page, result);
      Object.assign(counts, result.statusCounts);
      return true;
    } catch (cause) {
      if (isCurrent(requestVersion, requestStatus) && !requestController.signal.aborted) {
        page.error = cause instanceof Error ? cause.message : "维修事务加载失败";
      }
      return false;
    } finally {
      if (isCurrent(requestVersion, requestStatus)) page.loading = false;
    }
  }

  function isCurrent(requestVersion: number, requestStatus: RepairStatus) {
    return requestVersion === version && requestStatus === activeStatus.value;
  }

  function syncQuery(mode: "push" | "replace") {
    options.onQueryChange?.(currentQuery(), mode);
  }

  return {
    activeStatus,
    filters,
    counts,
    page,
    initialize,
    applyFilters,
    setStatus,
    setPage,
    retry,
    refreshAfterMutation,
    restoreQuery,
    currentQuery,
  };
}

export function parseRepairWorkspaceQuery(
  query: RepairWorkspaceQuery,
  defaults: Pick<RepairFilters, "from" | "to">,
): RepairWorkspaceRouteState {
  return {
    status: parseStatus(queryValue(query.status)),
    page: positiveNumber(queryValue(query.page)) || 1,
    keyword: (queryValue(query.keyword) || "").trim(),
    from: queryValue(query.from) || defaults.from,
    to: queryValue(query.to) || defaults.to,
  };
}

export function serializeRepairWorkspaceQuery(state: RepairWorkspaceRouteState) {
  const query: Record<string, string> = { status: state.status };
  if (state.page > 1) query.page = String(state.page);
  if (state.keyword.trim()) query.keyword = state.keyword.trim();
  if (state.from) query.from = state.from;
  if (state.to) query.to = state.to;
  return query;
}

function createPageState(pageSize: number, currentPage: number): RepairPageState {
  return {
    items: [],
    total: 0,
    page: normalizePage(currentPage),
    pageSize,
    hasMore: false,
    statusCounts: emptyCounts(),
    loading: false,
    error: "",
  };
}

function applyPage(state: RepairPageState, result: RepairPage) {
  state.items = result.items;
  state.total = result.total;
  state.page = result.page;
  state.pageSize = result.pageSize;
  state.hasMore = result.hasMore;
  state.statusCounts = { ...result.statusCounts };
}

function lastPage(result: RepairPage) {
  return Math.max(1, Math.ceil(result.total / result.pageSize));
}

function parseStatus(value?: string | null): RepairStatus {
  const normalized = value?.trim().toUpperCase();
  return STATUSES.includes(normalized as RepairStatus)
    ? (normalized as RepairStatus)
    : "REPAIRING";
}

function queryValue(value: QueryValue) {
  return Array.isArray(value) ? value[0] : value;
}

function positiveNumber(value?: string | null) {
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null;
}

function normalizePage(value: number) {
  return Number.isSafeInteger(value) && value > 0 ? value : 1;
}

function copyFilters(filters: RepairFilters): RepairFilters {
  return { keyword: filters.keyword.trim(), from: filters.from, to: filters.to };
}

function emptyCounts(): RepairStatusCounts {
  return { REPAIRING: 0, COMPLETED: 0, CANCELED: 0 };
}

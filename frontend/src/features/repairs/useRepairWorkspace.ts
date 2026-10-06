import { computed, reactive, ref } from "vue";
import { dateRangeError } from "../../shared/validation/dateRange";
import type {
  RepairFilters,
  RepairPage,
  RepairStatus,
  RepairStatusCounts,
  RepairWorkspaceRouteState,
} from "./repairTypes";

const DEFAULT_PAGE_SIZE = 20;
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

interface QuerySnapshot {
  status: RepairStatus;
  page: number;
  filters: RepairFilters;
  mode: "push" | "replace";
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
  const appliedFilters = reactive<RepairFilters>(copyFilters(filters));
  const hasAppliedQuery = ref(false);
  const pendingQuery = ref<QuerySnapshot | null>(null);
  const filtersPending = computed(() => JSON.stringify(copyFilters(filters)) !== JSON.stringify(appliedFilters));
  const page = reactive<RepairPageState>(
    createPageState(options.pageSize || DEFAULT_PAGE_SIZE, initial.page),
  );
  let version = 0;
  let controller: AbortController | null = null;
  let disposed = false;

  async function initialize() {
    await loadCurrent({ status: initial.status, page: initial.page, filters: copyFilters(filters), mode: "replace" });
  }

  async function applyFilters() {
    if (disposed || dateRangeError(filters.from, filters.to)) return;
    await loadCurrent({ status: activeStatus.value, page: 1, filters: copyFilters(filters), mode: "push" });
  }

  async function setStatus(status: RepairStatus) {
    if (disposed || (activeStatus.value === status && !page.loading && !page.error)) return;
    await loadCurrent({ status, page: 1, filters: copyFilters(appliedFilters), mode: "push" });
  }

  async function setPage(nextPage: number) {
    if (disposed) return;
    await loadCurrent({ status: activeStatus.value, page: normalizePage(nextPage), filters: copyFilters(appliedFilters), mode: "push" });
  }

  async function retry() {
    if (disposed) return;
    await loadCurrent(pendingQuery.value || { status: activeStatus.value, page: page.page, filters: copyFilters(appliedFilters), mode: "replace" });
  }

  async function refreshAfterMutation(
    _previousStatus?: RepairStatus | null,
    _nextStatus?: RepairStatus | null,
  ) {
    if (disposed) return;
    page.items = [];
    await loadCurrent({ status: activeStatus.value, page: page.page, filters: copyFilters(appliedFilters), mode: "replace" });
  }

  async function restoreQuery(query: RepairWorkspaceQuery) {
    if (disposed) return;
    const restored = parseRepairWorkspaceQuery(query, options.defaults);
    Object.assign(filters, {
      keyword: query.keyword === undefined ? appliedFilters.keyword : restored.keyword,
      from: restored.from,
      to: restored.to,
    });
    await loadCurrent({ status: restored.status, page: restored.page, filters: copyFilters(filters), mode: "replace" });
  }

  function currentQuery() {
    return serializeRepairWorkspaceQuery(currentState());
  }

  function currentState(): RepairWorkspaceRouteState {
    return {
      status: activeStatus.value,
      page: page.page,
      ...appliedFilters,
    };
  }

  async function loadCurrent(request: QuerySnapshot): Promise<boolean> {
    if (disposed || dateRangeError(request.filters.from, request.filters.to)) return false;
    controller?.abort();
    controller = new AbortController();
    const requestController = controller;
    const requestVersion = ++version;
    pendingQuery.value = request;
    page.loading = true;
    page.error = "";
    try {
      const result = await options.loadPage({
        status: request.status,
        page: normalizePage(request.page),
        pageSize: page.pageSize,
        filters: copyFilters(request.filters),
        signal: requestController.signal,
      });
      if (!isCurrent(requestVersion, requestController)) return false;
      if (!result.items.length && result.page > 1 && result.total > 0) {
        return loadCurrent({ ...request, page: lastPage(result) });
      }
      applyPage(page, result);
      Object.assign(counts, result.statusCounts);
      activeStatus.value = request.status;
      Object.assign(appliedFilters, request.filters);
      hasAppliedQuery.value = true;
      pendingQuery.value = null;
      syncQuery(request.mode);
      return true;
    } catch (cause) {
      if (isCurrent(requestVersion, requestController)) {
        page.error = cause instanceof Error ? cause.message : "维修事务加载失败";
      }
      return false;
    } finally {
      if (isCurrent(requestVersion, requestController)) page.loading = false;
    }
  }

  function isCurrent(
    requestVersion: number,
    requestController: AbortController,
  ) {
    return (
      !disposed &&
      requestVersion === version &&
      controller === requestController &&
      !requestController.signal.aborted
    );
  }

  function syncQuery(mode: "push" | "replace") {
    if (disposed) return;
    options.onQueryChange?.(currentQuery(), mode);
  }

  function dispose() {
    if (disposed) return;
    disposed = true;
    version += 1;
    controller?.abort();
    controller = null;
  }

  return {
    appliedFilters,
    hasAppliedQuery,
    filtersPending,
    pendingQuery,
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
    currentState,
    dispose,
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

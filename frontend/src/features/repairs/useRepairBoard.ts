import { reactive } from "vue";
import type {
  RepairCase,
  RepairFilters,
  RepairPage,
  RepairStatus,
} from "./repairTypes";

const STATUSES: RepairStatus[] = ["REPAIRING", "COMPLETED", "CANCELED"];

export interface RepairPageRequest {
  status: RepairStatus;
  page: number;
  pageSize: number;
  filters: RepairFilters;
  signal: AbortSignal;
}

export interface RepairColumnState {
  items: RepairCase[];
  total: number;
  page: number;
  pageSize: number;
  hasMore: boolean;
  loading: boolean;
  error: string;
}

type RepairPageLoader = (request: RepairPageRequest) => Promise<RepairPage>;

export function useRepairBoard(loader: RepairPageLoader, defaultPageSize = 30) {
  const columns = reactive<Record<RepairStatus, RepairColumnState>>({
    REPAIRING: createColumn(defaultPageSize),
    COMPLETED: createColumn(defaultPageSize),
    CANCELED: createColumn(defaultPageSize),
  });
  const versions: Record<RepairStatus, number> = {
    REPAIRING: 0,
    COMPLETED: 0,
    CANCELED: 0,
  };
  const controllers: Partial<Record<RepairStatus, AbortController>> = {};
  let activeFilters: RepairFilters = { keyword: "", from: "", to: "" };

  async function loadAll(filters: RepairFilters) {
    activeFilters = copyFilters(filters);
    await Promise.all(
      STATUSES.map((status) => loadFirstPage(status, activeFilters, true)),
    );
  }

  async function loadMore(status: RepairStatus) {
    const state = columns[status];
    if (state.loading || !state.hasMore) return;
    const version = nextRequest(status);
    const controller = controllers[status]!;
    state.loading = true;
    state.error = "";
    try {
      const result = await loader({
        status,
        page: state.page + 1,
        pageSize: state.pageSize,
        filters: copyFilters(activeFilters),
        signal: controller.signal,
      });
      if (!isCurrent(status, version)) return;
      state.items = mergeUnique(state.items, result.items);
      applyPageState(state, result);
    } catch (cause) {
      handleFailure(status, version, controller, cause);
    } finally {
      finishLoading(status, version);
    }
  }

  async function refresh(statuses: RepairStatus[], filters = activeFilters) {
    activeFilters = copyFilters(filters);
    await Promise.all(
      [...new Set(statuses)].map(async (status) => {
        const targetPages = Math.max(1, columns[status].page);
        const version = nextRequest(status);
        const controller = controllers[status]!;
        columns[status].loading = true;
        columns[status].error = "";
        const items: RepairCase[] = [];
        let latest: RepairPage | null = null;
        try {
          for (let page = 1; page <= targetPages; page += 1) {
            latest = await loader({
              status,
              page,
              pageSize: columns[status].pageSize,
              filters: copyFilters(activeFilters),
              signal: controller.signal,
            });
            if (!isCurrent(status, version)) return;
            items.push(...latest.items);
            if (!latest.hasMore) break;
          }
          if (!latest || !isCurrent(status, version)) return;
          columns[status].items = mergeUnique([], items);
          applyPageState(columns[status], latest);
        } catch (cause) {
          handleFailure(status, version, controller, cause);
        } finally {
          finishLoading(status, version);
        }
      }),
    );
  }

  async function retry(status: RepairStatus) {
    await refresh([status]);
  }

  async function loadFirstPage(
    status: RepairStatus,
    filters: RepairFilters,
    clearItems: boolean,
  ) {
    const version = nextRequest(status);
    const controller = controllers[status]!;
    const state = columns[status];
    if (clearItems) {
      Object.assign(state, createColumn(state.pageSize));
    }
    state.loading = true;
    state.error = "";
    try {
      const result = await loader({
        status,
        page: 1,
        pageSize: state.pageSize,
        filters: copyFilters(filters),
        signal: controller.signal,
      });
      if (!isCurrent(status, version)) return;
      state.items = result.items;
      applyPageState(state, result);
    } catch (cause) {
      handleFailure(status, version, controller, cause);
    } finally {
      finishLoading(status, version);
    }
  }

  function nextRequest(status: RepairStatus) {
    controllers[status]?.abort();
    controllers[status] = new AbortController();
    versions[status] += 1;
    return versions[status];
  }

  function isCurrent(status: RepairStatus, version: number) {
    return versions[status] === version;
  }

  function handleFailure(
    status: RepairStatus,
    version: number,
    controller: AbortController,
    cause: unknown,
  ) {
    if (!isCurrent(status, version) || controller.signal.aborted) return;
    columns[status].error = cause instanceof Error ? cause.message : "维修事务加载失败";
  }

  function finishLoading(status: RepairStatus, version: number) {
    if (isCurrent(status, version)) columns[status].loading = false;
  }

  return { columns, loadAll, loadMore, refresh, retry };
}

function createColumn(pageSize: number): RepairColumnState {
  return {
    items: [],
    total: 0,
    page: 0,
    pageSize,
    hasMore: false,
    loading: false,
    error: "",
  };
}

function applyPageState(state: RepairColumnState, result: RepairPage) {
  state.total = result.total;
  state.page = result.page;
  state.pageSize = result.pageSize;
  state.hasMore = result.hasMore;
}

function mergeUnique(existing: RepairCase[], incoming: RepairCase[]) {
  const result = [...existing];
  const ids = new Set(existing.map((item) => item.id));
  incoming.forEach((item) => {
    if (!ids.has(item.id)) {
      ids.add(item.id);
      result.push(item);
    }
  });
  return result;
}

function copyFilters(filters: RepairFilters): RepairFilters {
  return { keyword: filters.keyword, from: filters.from, to: filters.to };
}

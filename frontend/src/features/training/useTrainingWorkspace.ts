import { reactive, ref } from "vue";
import { dateRangeError } from "../../shared/validation/dateRange";
import type {
  TrainingFilters,
  TrainingPage,
  TrainingParticipant,
  TrainingSession,
  TrainingWorkspaceRouteState,
} from "./trainingTypes";

const SESSION_PAGE_SIZE = 20;
const PARTICIPANT_PAGE_SIZE = 20;

type QueryValue = string | null | undefined | Array<string | null>;
export type TrainingWorkspaceQuery = Record<string, QueryValue>;

export interface TrainingSessionPageRequest {
  page: number;
  pageSize: number;
  filters: TrainingFilters;
  signal: AbortSignal;
}

export interface TrainingParticipantPageRequest {
  sessionId: number;
  keyword: string;
  page: number;
  pageSize: number;
  signal: AbortSignal;
}

export interface TrainingPageState<T> extends TrainingPage<T> {
  loading: boolean;
  error: string;
}

interface WorkspaceOptions {
  loadSessions: (
    request: TrainingSessionPageRequest,
  ) => Promise<TrainingPage<TrainingSession>>;
  loadParticipants: (
    request: TrainingParticipantPageRequest,
  ) => Promise<TrainingPage<TrainingParticipant>>;
  defaults: Pick<TrainingFilters, "from" | "to">;
  initialQuery?: TrainingWorkspaceQuery;
  onQueryChange?: (query: Record<string, string>) => void;
}

export function useTrainingWorkspace(options: WorkspaceOptions) {
  const initial = parseTrainingWorkspaceQuery(
    options.initialQuery || {},
    options.defaults,
  );
  const filters = reactive<TrainingFilters>({
    keyword: initial.keyword,
    from: initial.from,
    to: initial.to,
  });
  const appliedFilters = reactive<TrainingFilters>(copyFilters(filters));
  const hasAppliedSessionQuery = ref(false);
  const sessions = reactive<TrainingPageState<TrainingSession>>(
    createPageState(SESSION_PAGE_SIZE, initial.sessionPage),
  );
  const participants = reactive<TrainingPageState<TrainingParticipant>>(
    createPageState(PARTICIPANT_PAGE_SIZE, initial.participantPage),
  );
  const selected = ref<TrainingSession | null>(null);
  const participantKeyword = ref(initial.participantKeyword);
  const appliedParticipantKeyword = ref(initial.participantKeyword);
  let retryParticipantQuery: { sessionId: number; page: number; keyword: string } | null = null;
  let requestedSessionId = initial.sessionId;
  let sessionVersion = 0;
  let participantVersion = 0;
  let sessionController: AbortController | null = null;
  let participantController: AbortController | null = null;
  let retryFilters: TrainingFilters | null = null;
  let retrySessionPage: number | null = null;
  let retryDirectorySelection: { sessionId: number | null; participantPage: number; keyword: string } | null = null;
  let disposed = false;

  async function initialize() {
    if (disposed) return;
    const loaded = await loadDirectoryAndSelection(
      initial.sessionPage,
      initial.sessionId,
      initial.participantPage,
      true,
    );
    if (disposed || !loaded) return;
    syncQuery();
  }

  async function applyFilters() {
    if (disposed || dateRangeError(filters.from, filters.to)) return;
    requestedSessionId = null;
    const loaded = await loadDirectoryAndSelection(1, null, 1, true, copyFilters(filters));
    if (disposed || !loaded) return;
    syncQuery();
  }

  async function setSessionPage(page: number) {
    if (disposed) return;
    requestedSessionId = null;
    const loaded = await loadDirectoryAndSelection(normalizePage(page), null, 1, true);
    if (disposed || !loaded) return;
    syncQuery();
  }

  async function selectSession(item: TrainingSession | null) {
    if (disposed) return;
    requestedSessionId = item?.id || null;
    setSelected(item, 1);
    if (!item || await loadParticipantPage(1)) syncQuery();
  }

  async function setParticipantPage(page: number) {
    if (disposed || !selected.value) return;
    if (await loadParticipantPage(page)) syncQuery();
  }

  async function searchParticipants() {
    if (disposed || !selected.value) return;
    if (await loadParticipantPage(1, participantKeyword.value.trim())) syncQuery();
  }

  async function retrySessions() {
    if (disposed) return;
    const loaded = await loadDirectoryAndSelection(
      retrySessionPage ?? sessions.page,
      retryDirectorySelection ? retryDirectorySelection.sessionId : selected.value?.id ?? requestedSessionId,
      retryDirectorySelection?.participantPage ?? participants.page,
      Boolean(retryDirectorySelection),
      retryFilters || appliedFilters,
      retryDirectorySelection?.keyword ?? appliedParticipantKeyword.value,
    );
    if (disposed || !loaded) return;
    syncQuery();
  }

  async function retryParticipants() {
    if (disposed || !selected.value) return;
    const pending = retryParticipantQuery;
    if (pending && pending.sessionId !== selected.value.id) return;
    if (await loadParticipantPage(pending?.page ?? participants.page, pending?.keyword ?? appliedParticipantKeyword.value)) syncQuery();
  }

  async function refreshSessions(preferredSessionId = selected.value?.id || null) {
    if (disposed) return;
    const previousId = selected.value?.id || null;
    const loaded = await loadSessionPage(sessions.page);
    if (!loaded || disposed) return;
    const next = chooseSession(preferredSessionId, previousId);
    selected.value = next;
    requestedSessionId = next?.id || null;
    if (!next) clearParticipants();
    else if (next.id !== previousId) {
      setSelected(next, 1);
      await loadParticipantPage(1);
    }
    syncQuery();
  }

  async function refreshAfterSessionMutation(
    preferredSessionId: number | null,
    firstPage = false,
  ) {
    if (disposed) return;
    const loaded = await loadDirectoryAndSelection(
      firstPage ? 1 : sessions.page,
      preferredSessionId,
      participants.page,
      true,
    );
    if (disposed || !loaded) return;
    syncQuery();
  }

  async function refreshAfterParticipantMutation() {
    if (disposed || !selected.value) return;
    participants.items = [];
    await Promise.all([
      refreshSessions(selected.value.id),
      loadParticipantPage(participants.page),
    ]);
    if (disposed) return;
    syncQuery();
  }

  async function restoreQuery(query: TrainingWorkspaceQuery) {
    if (disposed) return;
    const restored = parseTrainingWorkspaceQuery(query, options.defaults);
    Object.assign(filters, {
      keyword: query.keyword === undefined ? appliedFilters.keyword : restored.keyword,
      from: restored.from,
      to: restored.to,
    });
    participantKeyword.value = query.participantKeyword === undefined ? appliedParticipantKeyword.value : restored.participantKeyword;
    requestedSessionId = restored.sessionId;
    const loaded = await loadDirectoryAndSelection(
      restored.sessionPage,
      restored.sessionId,
      restored.participantPage,
      true,
      copyFilters(filters),
      participantKeyword.value,
    );
    if (disposed || !loaded) return;
    syncQuery();
  }

  function currentQuery() {
    return serializeTrainingWorkspaceQuery(currentState());
  }

  function currentState(): TrainingWorkspaceRouteState {
    return {
      ...appliedFilters,
      sessionId: selected.value?.id || requestedSessionId,
      sessionPage: sessions.page,
      participantPage: participants.page,
      participantKeyword: appliedParticipantKeyword.value,
    };
  }

  async function loadDirectoryAndSelection(
    page: number,
    preferredSessionId: number | null,
    participantPage: number,
    forceParticipantLoad: boolean,
    requestFilters: TrainingFilters = appliedFilters,
    requestParticipantKeyword = appliedParticipantKeyword.value,
  ) {
    const previousId = selected.value?.id || null;
    retryDirectorySelection = { sessionId: preferredSessionId, participantPage, keyword: requestParticipantKeyword };
    const loaded = await loadSessionPage(page, requestFilters);
    if (!loaded) return false;
    retryDirectorySelection = null;
    const next = chooseSession(preferredSessionId, null);
    const changed = next?.id !== previousId;
    selected.value = next;
    requestedSessionId = next?.id || null;
    if (!next) {
      clearParticipants();
      return true;
    }
    if (changed || forceParticipantLoad) {
      setSelected(next, participantPage);
      return loadParticipantPage(participantPage, requestParticipantKeyword);
    }
    return true;
  }

  async function loadSessionPage(page: number, requestFilters: TrainingFilters = appliedFilters): Promise<boolean> {
    const snapshot = copyFilters(requestFilters);
    if (disposed || dateRangeError(snapshot.from, snapshot.to)) return false;
    sessionController?.abort();
    sessionController = new AbortController();
    const controller = sessionController;
    const version = ++sessionVersion;
    sessions.loading = true;
    sessions.error = "";
    try {
      const result = await options.loadSessions({
        page: normalizePage(page),
        pageSize: sessions.pageSize,
        filters: snapshot,
        signal: controller.signal,
      });
      if (!isCurrentSession(version, controller)) return false;
      if (!result.items.length && result.page > 1 && result.total > 0) {
        return loadSessionPage(lastPage(result), snapshot);
      }
      applyPage(sessions, result);
      Object.assign(appliedFilters, snapshot);
      hasAppliedSessionQuery.value = true;
      retryFilters = null;
      retrySessionPage = null;
      return true;
    } catch (cause) {
      if (isCurrentSession(version, controller)) {
        sessions.error = errorMessage(cause, "培训场次加载失败");
        retryFilters = snapshot;
        retrySessionPage = normalizePage(page);
      }
      return false;
    } finally {
      if (isCurrentSession(version, controller)) sessions.loading = false;
    }
  }

  async function loadParticipantPage(page: number, keyword = appliedParticipantKeyword.value): Promise<boolean> {
    if (disposed) return false;
    const sessionId = selected.value?.id;
    if (!sessionId) {
      clearParticipants();
      return false;
    }
    participantController?.abort();
    participantController = new AbortController();
    const controller = participantController;
    const version = ++participantVersion;
    const snapshot = { sessionId, page: normalizePage(page), keyword: keyword.trim() };
    retryParticipantQuery = snapshot;
    participants.loading = true;
    participants.error = "";
    try {
      const result = await options.loadParticipants({
        ...snapshot,
        pageSize: participants.pageSize,
        signal: controller.signal,
      });
      if (!isCurrentParticipant(version, sessionId, controller)) return false;
      if (!result.items.length && result.page > 1 && result.total > 0) {
        return loadParticipantPage(lastPage(result), snapshot.keyword);
      }
      applyPage(participants, result);
      appliedParticipantKeyword.value = snapshot.keyword;
      retryParticipantQuery = null;
      return true;
    } catch (cause) {
      if (
        isCurrentParticipant(version, sessionId, controller)
      ) {
        participants.error = errorMessage(cause, "参与名单加载失败");
      }
      return false;
    } finally {
      if (isCurrentParticipant(version, sessionId, controller)) {
        participants.loading = false;
      }
    }
  }

  function chooseSession(
    preferredSessionId: number | null,
    fallbackSessionId: number | null,
  ) {
    return (
      sessions.items.find((item) => item.id === preferredSessionId) ||
      sessions.items.find((item) => item.id === fallbackSessionId) ||
      sessions.items[0] ||
      null
    );
  }

  function setSelected(item: TrainingSession | null, participantPage: number) {
    if (disposed) return;
    participantController?.abort();
    participantVersion += 1;
    retryParticipantQuery = null;
    selected.value = item;
    requestedSessionId = item?.id || null;
    Object.assign(
      participants,
      createPageState(participants.pageSize, normalizePage(participantPage)),
    );
  }

  function clearParticipants() {
    setSelected(null, 1);
  }

  function isCurrentSession(version: number, controller: AbortController) {
    return (
      !disposed &&
      version === sessionVersion &&
      sessionController === controller &&
      !controller.signal.aborted
    );
  }

  function isCurrentParticipant(
    version: number,
    sessionId: number,
    controller: AbortController,
  ) {
    return (
      !disposed &&
      version === participantVersion &&
      participantController === controller &&
      selected.value?.id === sessionId &&
      !controller.signal.aborted
    );
  }

  function syncQuery() {
    if (disposed) return;
    options.onQueryChange?.(currentQuery());
  }

  function dispose() {
    if (disposed) return;
    disposed = true;
    sessionVersion += 1;
    participantVersion += 1;
    sessionController?.abort();
    participantController?.abort();
    sessionController = null;
    participantController = null;
  }

  return {
    filters,
    appliedFilters,
    hasAppliedSessionQuery,
    sessions,
    participants,
    participantKeyword,
    appliedParticipantKeyword,
    selected,
    initialize,
    applyFilters,
    setSessionPage,
    selectSession,
    setParticipantPage,
    searchParticipants,
    retrySessions,
    retryParticipants,
    refreshSessions,
    refreshAfterSessionMutation,
    refreshAfterParticipantMutation,
    restoreQuery,
    currentQuery,
    currentState,
    dispose,
  };
}

export function parseTrainingWorkspaceQuery(
  query: TrainingWorkspaceQuery,
  defaults: Pick<TrainingFilters, "from" | "to">,
): TrainingWorkspaceRouteState {
  return {
    sessionId: positiveNumber(queryValue(query.sessionId)),
    sessionPage: positiveNumber(queryValue(query.sessionPage)) || 1,
    participantPage: positiveNumber(queryValue(query.participantPage)) || 1,
    participantKeyword: (queryValue(query.participantKeyword) || "").trim(),
    keyword: (queryValue(query.keyword) || "").trim(),
    from: queryValue(query.from) || defaults.from,
    to: queryValue(query.to) || defaults.to,
  };
}

export function serializeTrainingWorkspaceQuery(
  state: TrainingWorkspaceRouteState,
) {
  const query: Record<string, string> = {};
  if (state.sessionId) query.sessionId = String(state.sessionId);
  if (state.sessionPage > 1) query.sessionPage = String(state.sessionPage);
  if (state.participantPage > 1) {
    query.participantPage = String(state.participantPage);
  }
  if (state.from) query.from = state.from;
  if (state.to) query.to = state.to;
  return query;
}

function createPageState<T>(pageSize: number, page = 1): TrainingPageState<T> {
  return {
    items: [],
    total: 0,
    page: normalizePage(page),
    pageSize,
    hasMore: false,
    loading: false,
    error: "",
  };
}

function applyPage<T>(state: TrainingPageState<T>, result: TrainingPage<T>) {
  state.items = result.items;
  state.total = result.total;
  state.page = result.page;
  state.pageSize = result.pageSize;
  state.hasMore = result.hasMore;
}

function lastPage<T>(result: TrainingPage<T>) {
  return Math.max(1, Math.ceil(result.total / result.pageSize));
}

function normalizePage(value: number) {
  return Number.isSafeInteger(value) && value > 0 ? value : 1;
}

function positiveNumber(value: string | undefined) {
  if (!value || !/^\d+$/.test(value)) return null;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null;
}

function queryValue(value: QueryValue) {
  const candidate = Array.isArray(value) ? value[0] : value;
  return typeof candidate === "string" ? candidate : undefined;
}

function copyFilters(filters: TrainingFilters): TrainingFilters {
  return {
    keyword: filters.keyword,
    from: filters.from,
    to: filters.to,
  };
}

function errorMessage(cause: unknown, fallback: string) {
  return cause instanceof Error ? cause.message : fallback;
}

import { reactive, ref } from "vue";
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
  const sessions = reactive<TrainingPageState<TrainingSession>>(
    createPageState(SESSION_PAGE_SIZE, initial.sessionPage),
  );
  const participants = reactive<TrainingPageState<TrainingParticipant>>(
    createPageState(PARTICIPANT_PAGE_SIZE, initial.participantPage),
  );
  const selected = ref<TrainingSession | null>(null);
  const participantKeyword = ref(initial.participantKeyword);
  let requestedSessionId = initial.sessionId;
  let sessionVersion = 0;
  let participantVersion = 0;
  let sessionController: AbortController | null = null;
  let participantController: AbortController | null = null;

  async function initialize() {
    await loadDirectoryAndSelection(
      initial.sessionPage,
      initial.sessionId,
      initial.participantPage,
      true,
    );
    syncQuery();
  }

  async function applyFilters() {
    requestedSessionId = null;
    await loadDirectoryAndSelection(1, null, 1, true);
    syncQuery();
  }

  async function setSessionPage(page: number) {
    requestedSessionId = null;
    await loadDirectoryAndSelection(normalizePage(page), null, 1, true);
    syncQuery();
  }

  async function selectSession(item: TrainingSession | null) {
    requestedSessionId = item?.id || null;
    setSelected(item, 1);
    syncQuery();
    if (item) await loadParticipantPage(1);
  }

  async function setParticipantPage(page: number) {
    if (!selected.value) return;
    participants.page = normalizePage(page);
    syncQuery();
    await loadParticipantPage(participants.page);
  }

  async function searchParticipants() {
    if (!selected.value) return;
    await loadParticipantPage(1);
    syncQuery();
  }

  async function retrySessions() {
    await loadDirectoryAndSelection(
      sessions.page,
      selected.value?.id || requestedSessionId,
      participants.page,
      false,
    );
    syncQuery();
  }

  async function retryParticipants() {
    if (!selected.value) return;
    await loadParticipantPage(participants.page);
  }

  async function refreshSessions(preferredSessionId = selected.value?.id || null) {
    const previousId = selected.value?.id || null;
    const loaded = await loadSessionPage(sessions.page);
    if (!loaded) return;
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
    await loadDirectoryAndSelection(
      firstPage ? 1 : sessions.page,
      preferredSessionId,
      participants.page,
      true,
    );
    syncQuery();
  }

  async function refreshAfterParticipantMutation() {
    if (!selected.value) return;
    await Promise.all([
      refreshSessions(selected.value.id),
      loadParticipantPage(participants.page),
    ]);
    syncQuery();
  }

  async function restoreQuery(query: TrainingWorkspaceQuery) {
    const restored = parseTrainingWorkspaceQuery(query, options.defaults);
    Object.assign(filters, {
      keyword: restored.keyword,
      from: restored.from,
      to: restored.to,
    });
    participantKeyword.value = restored.participantKeyword;
    requestedSessionId = restored.sessionId;
    await loadDirectoryAndSelection(
      restored.sessionPage,
      restored.sessionId,
      restored.participantPage,
      true,
    );
    syncQuery();
  }

  function currentQuery() {
    return serializeTrainingWorkspaceQuery({
      ...filters,
      sessionId: selected.value?.id || requestedSessionId,
      sessionPage: sessions.page,
      participantPage: participants.page,
      participantKeyword: participantKeyword.value,
    });
  }

  async function loadDirectoryAndSelection(
    page: number,
    preferredSessionId: number | null,
    participantPage: number,
    forceParticipantLoad: boolean,
  ) {
    const previousId = selected.value?.id || null;
    const loaded = await loadSessionPage(page);
    if (!loaded) return;
    const next = chooseSession(preferredSessionId, null);
    const changed = next?.id !== previousId;
    selected.value = next;
    requestedSessionId = next?.id || null;
    if (!next) {
      clearParticipants();
      return;
    }
    if (changed || forceParticipantLoad) {
      setSelected(next, participantPage);
      await loadParticipantPage(participantPage);
    }
  }

  async function loadSessionPage(page: number): Promise<boolean> {
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
        filters: copyFilters(filters),
        signal: controller.signal,
      });
      if (!isCurrentSession(version)) return false;
      if (!result.items.length && result.page > 1 && result.total > 0) {
        return loadSessionPage(lastPage(result));
      }
      applyPage(sessions, result);
      return true;
    } catch (cause) {
      if (isCurrentSession(version) && !controller.signal.aborted) {
        sessions.error = errorMessage(cause, "培训场次加载失败");
      }
      return false;
    } finally {
      if (isCurrentSession(version)) sessions.loading = false;
    }
  }

  async function loadParticipantPage(page: number): Promise<boolean> {
    const sessionId = selected.value?.id;
    if (!sessionId) {
      clearParticipants();
      return false;
    }
    participantController?.abort();
    participantController = new AbortController();
    const controller = participantController;
    const version = ++participantVersion;
    participants.page = normalizePage(page);
    participants.items = [];
    participants.loading = true;
    participants.error = "";
    try {
      const result = await options.loadParticipants({
        sessionId,
        keyword: participantKeyword.value.trim(),
        page: participants.page,
        pageSize: participants.pageSize,
        signal: controller.signal,
      });
      if (!isCurrentParticipant(version, sessionId)) return false;
      if (!result.items.length && result.page > 1 && result.total > 0) {
        return loadParticipantPage(lastPage(result));
      }
      applyPage(participants, result);
      return true;
    } catch (cause) {
      if (
        isCurrentParticipant(version, sessionId) &&
        !controller.signal.aborted
      ) {
        participants.error = errorMessage(cause, "参与名单加载失败");
      }
      return false;
    } finally {
      if (isCurrentParticipant(version, sessionId)) {
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
    participantController?.abort();
    participantVersion += 1;
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

  function isCurrentSession(version: number) {
    return version === sessionVersion;
  }

  function isCurrentParticipant(version: number, sessionId: number) {
    return version === participantVersion && selected.value?.id === sessionId;
  }

  function syncQuery() {
    options.onQueryChange?.(currentQuery());
  }

  return {
    filters,
    sessions,
    participants,
    participantKeyword,
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
  if (state.participantKeyword.trim()) {
    query.participantKeyword = state.participantKeyword.trim();
  }
  if (state.keyword.trim()) query.keyword = state.keyword.trim();
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

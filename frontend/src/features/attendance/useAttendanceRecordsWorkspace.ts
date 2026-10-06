import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch, type ComponentPublicInstance } from "vue";
import { onBeforeRouteLeave, useRoute, useRouter } from "vue-router";
import { useUnsavedChanges } from "../../shared/composables/useUnsavedChanges";
import { useSession } from "../../app/session";
import type { AccountCandidate } from "../accounts/accountCandidates";
import { del, get, post, put } from "../../shared/api";
import { useAsyncTask } from "../../shared/composables/useAsyncTask";
import { useLatestRequest } from "../../shared/composables/useLatestRequest";
import { usePendingActions } from "../../shared/composables/usePendingActions";
import {
  positiveRoutePage,
  routeQuerySignature,
  stringRouteQuery,
  updateOwnedRouteQuery,
} from "../../shared/navigation/routeQueryState";
import { dateRangeError } from "../../shared/validation/dateRange";
import { createPrivateNavigationState } from "../../shared/navigation/privateNavigationState";
import {
  attendanceActionAccess,
  attendancePageQuery,
  localDateTimeInput,
  manualCheckoutStatus,
  totalAttendancePages,
  type AttendanceActionAccess,
  type AttendanceRecordItem,
  type AttendanceRecordPage,
  type AttendanceRecordFilters,
} from "./attendanceRecords";

type TablePosition = { top: number; left: number };
type HistorySnapshot = { filters: AttendanceRecordFilters; page: number; signature: string; scroll: TablePosition };
type Query = { filters: AttendanceRecordFilters; page: number; routeMode?: "push" | "replace"; restore?: TablePosition };
const historyMemory = createPrivateNavigationState<HistorySnapshot>();
const visitKey = "attendanceVisit";

export function useAttendanceRecordsWorkspace() {
  const { user } = useSession();
  const route = useRoute();
  const router = useRouter();
  const historyScope = historyMemory.scope();
  const tableScroll = ref<HTMLElement | null>(null);
  const bindTableScroll = (element: Element | ComponentPublicInstance | null) => {
    tableScroll.value = element instanceof HTMLElement ? element : null;
  };
  let activeVisit: string | undefined;
  let active = true;
  let queryVersion = 0;
  const records = ref<AttendanceRecordItem[]>([]);
  const hasAppliedQuery = ref(false);
  const total = ref(0);
  const page = ref(1);
  const pageSize = 20;
  const task = useAsyncTask();
  const listRequest = useLatestRequest();
  const actions = usePendingActions();
  const { loading: listLoading, error: listError } = listRequest;
  const editorOpen = ref(false);
  const editing = ref<AttendanceRecordItem | null>(null);
  const deleteTarget = ref<AttendanceRecordItem | null>(null);
  const manualCandidates = ref<AccountCandidate[]>([]);
  const selectedMember = ref<AccountCandidate | null>(null);
  const filters = reactive({ from: "", to: "", keyword: "", status: "" });
  const appliedFilters = ref({ ...filters });
  const pendingQuery = ref<Query | null>(null);
  const filtersPending = computed(() => JSON.stringify(filters) !== JSON.stringify(appliedFilters.value));
  const filterError = computed(() => dateRangeError(filters.from, filters.to));
  const displayError = computed(() => filterError.value || listError.value);
  const form = reactive({
    studentNo: "",
    checkInTime: "",
    checkOutTime: "",
    checkInStatus: "APPROVED",
    checkOutStatus: "APPROVED",
    recomputeSnapshot: false,
    reason: "",
  });
  const editorSnapshot = () => JSON.stringify({ ...form, memberId: selectedMember.value?.id });
  const editorBaseline = ref("");
  const unsaved = useUnsavedChanges(() => editorOpen.value && editorSnapshot() !== editorBaseline.value);
  onBeforeRouteLeave(() => {
    if (actions.isPending("save")) return false;
    return new Promise<boolean>((resolve) => unsaved.request(() => resolve(true), () => resolve(false)));
  });
  const canCreate = computed(() =>
    ["PRESIDENT", "ADMIN"].includes(user.value?.role || ""),
  );
  const canReviewStatus = canCreate;
  const routeKeys = ["from", "to", "status", "page", "keyword"] as const;
  let routeReady = false;
  let suppressRouteRestore = false;

  const publicSignature = () => routeQuerySignature(route.query, ["from", "to", "status", "page"]);
  function currentVisit() {
    const id = router.options?.history.state[visitKey];
    return typeof id === "string" ? id : undefined;
  }
  function historySnapshot() {
    const id = currentVisit();
    const saved = id ? historyScope.get(id) : undefined;
    return saved?.signature === publicSignature() ? saved : undefined;
  }
  async function ensureVisit() {
    if (!router.options || !active) return undefined;
    if (!currentVisit()) {
      await router.replace({ query: route.query, state: { [visitKey]: crypto.randomUUID() }, force: true });
    }
    return currentVisit();
  }
  function rememberTableScroll() {
    const saved = activeVisit ? historyScope.get(activeVisit) : undefined;
    if (!saved || !tableScroll.value) return;
    saved.scroll = { top: tableScroll.value.scrollTop, left: tableScroll.value.scrollLeft };
  }
  onBeforeUnmount(() => {
    rememberTableScroll();
    active = false;
  });

  watch(
    () => form.checkOutTime,
    (value) => {
      form.checkOutStatus = manualCheckoutStatus(form.checkOutStatus, value);
    },
    { flush: "sync" },
  );

  const totalPages = computed(() =>
    totalAttendancePages(total.value, pageSize),
  );

  onMounted(async () => {
    restoreRouteState(true);
    const saved = stringRouteQuery(route.query.keyword) ? undefined : historySnapshot();
    if (saved) Object.assign(filters, saved.filters);
    const initialPage = saved?.page || positiveRoutePage(route.query.page);
    // Consume command-provided keywords even when the initial request fails.
    await updateOwnedRouteQuery(router, route.query, ["keyword"], {}, "replace");
    routeReady = true;
    await Promise.all([
      runQuery({ filters: { ...filters }, page: initialPage, routeMode: "replace", restore: saved?.scroll }),
      canCreate.value ? loadManualCandidates() : undefined,
    ]);
    if (stringRouteQuery(route.query.intent) === "new" && canCreate.value) {
      openCreate();
    }
  });

  watch(
    () => routeQuerySignature(route.query, routeKeys),
    () => {
      if (!routeReady || suppressRouteRestore) return;
      rememberTableScroll();
      const saved = historySnapshot();
      restoreRouteState(false);
      filters.keyword = saved?.filters.keyword ?? appliedFilters.value.keyword;
      void runQuery({ filters: { ...filters }, page: positiveRoutePage(route.query.page), restore: saved?.scroll });
    },
  );

  async function load(target = page.value) {
    return runQuery({ filters: { ...appliedFilters.value }, page: target });
  }

  async function runQuery(request: Query) {
    if (dateRangeError(request.filters.from, request.filters.to)) return;
    rememberTableScroll();
    const version = ++queryVersion;
    const query = attendancePageQuery(request.filters, request.page, pageSize);
    pendingQuery.value = request;
    const value = await listRequest.run(
      (signal) =>
        get<AttendanceRecordPage>(`/api/attendance/page?${query}`, { signal }),
      "值班记录加载失败",
    );
    if (!value) return;
    activeVisit = undefined;
    records.value = value.items;
    total.value = value.total;
    page.value = value.page;
    appliedFilters.value = { ...request.filters };
    hasAppliedQuery.value = true;
    pendingQuery.value = null;
    if (request.routeMode) await syncRoute(value.page, request.routeMode, request.filters);
    const visit = await ensureVisit();
    await nextTick();
    if (!active || version !== queryVersion) return;
    if (request.restore && tableScroll.value) {
      tableScroll.value.scrollTop = request.restore.top;
      tableScroll.value.scrollLeft = request.restore.left;
    }
    activeVisit = visit;
    if (visit) historyScope.set(visit, {
      filters: { ...request.filters }, page: value.page, signature: publicSignature(),
      scroll: { top: tableScroll.value?.scrollTop || 0, left: tableScroll.value?.scrollLeft || 0 },
    });
  }

  async function retryLoad() {
    await runQuery(pendingQuery.value || { filters: { ...appliedFilters.value }, page: page.value });
  }

  async function applyFilters() {
    if (filterError.value) return;
    await runQuery({ filters: { ...filters }, page: 1, routeMode: "push" });
  }

  async function setPage(target: number) {
    await runQuery({ filters: { ...appliedFilters.value }, page: target, routeMode: "push" });
  }

  function openCreate() {
    editing.value = null;
    selectedMember.value = null;
    Object.assign(form, {
      studentNo: "",
      checkInTime: localDateTimeInput(new Date()),
      checkOutTime: "",
      checkInStatus: "APPROVED",
      checkOutStatus: "NOT_SUBMITTED",
      recomputeSnapshot: false,
      reason: "",
    });
    editorOpen.value = true;
    editorBaseline.value = editorSnapshot();
  }

  function openEdit(item: AttendanceRecordItem) {
    if (!actionAccess(item).allowed) return;
    editing.value = item;
    Object.assign(form, {
      studentNo: item.studentNo,
      checkInTime: toInput(item.checkInTime),
      checkOutTime: toInput(item.checkOutTime),
      checkInStatus: item.checkInStatus,
      checkOutStatus: manualCheckoutStatus(item.checkOutStatus, toInput(item.checkOutTime)),
      recomputeSnapshot: false,
      reason: "",
    });
    editorOpen.value = true;
    editorBaseline.value = editorSnapshot();
  }

  async function save() {
    await actions.run("save", async () => {
      const current = editing.value;
      const payload = {
        ...form,
        studentNo: current
          ? form.studentNo
          : selectedMember.value?.studentNo || "",
        checkOutTime: form.checkOutTime || null,
      };
      const result = current
        ? await task.run(
            () => put(`/api/attendance/${current.id}/manual`, payload),
            "记录已更新",
          )
        : await task.run(
            () => post("/api/attendance/manual", payload),
            "记录已补录",
          );
      if (result === undefined) return;
      editorOpen.value = false;
      await load();
    });
  }

  function askDelete(item: AttendanceRecordItem) {
    if (!actionAccess(item).allowed) return;
    deleteTarget.value = item;
  }

  async function remove(reason: string) {
    const target = deleteTarget.value;
    if (!target) return;
    await actions.run("delete", async () => {
      const removed = await task.run(
        () =>
          del(
            `/api/attendance/${target.id}?reason=${encodeURIComponent(reason)}`,
          ),
        "记录已删除",
      );
      if (removed === undefined) return;
      deleteTarget.value = null;
      await load();
    });
  }

  const statusLabels: Record<string, string> = {
    VALID: "有效",
    INCOMPLETE: "未签退",
    PENDING: "待审核",
    INVALID: "无效",
  };
  const statusLabel = (value: string) => statusLabels[value] || value;
  const statusTone = (value: string) =>
    value === "VALID"
      ? "success"
      : value === "INVALID"
        ? "danger"
        : value === "PENDING"
          ? "warning"
          : "info";
  const dateTime = (value?: string) =>
    value?.replace("T", " ").slice(0, 16) || "—";
  const toInput = (value?: string) => value?.slice(0, 16) || "";

  function actionAccess(item: AttendanceRecordItem): AttendanceActionAccess {
    return attendanceActionAccess(
      user.value?.role,
      item.userRole,
      item.dutyDate,
    );
  }

  async function loadManualCandidates() {
    const value = await task.run(() =>
      get<AccountCandidate[]>("/api/attendance/manual-candidates"),
    );
    if (value) manualCandidates.value = value;
  }

  function closeEditor() {
    if (!actions.isPending("save")) unsaved.request(() => { editorOpen.value = false; });
  }

  function localDate(value: Date) {
    return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, "0")}-${String(value.getDate()).padStart(2, "0")}`;
  }

  function restoreRouteState(includeSensitiveKeyword: boolean) {
    const now = new Date();
    const start = new Date(now);
    start.setDate(1);
    filters.from = stringRouteQuery(route.query.from) || localDate(start);
    filters.to = stringRouteQuery(route.query.to) || localDate(now);
    filters.status = stringRouteQuery(route.query.status);
    if (includeSensitiveKeyword) {
      filters.keyword = stringRouteQuery(route.query.keyword);
    }
  }

  async function syncRoute(targetPage: number, mode: "push" | "replace", selected: AttendanceRecordFilters) {
    suppressRouteRestore = true;
    try {
      await updateOwnedRouteQuery(
        router,
        route.query,
        routeKeys,
        {
          from: selected.from,
          to: selected.to,
          status: selected.status,
          page: targetPage > 1 ? targetPage : undefined,
        },
        mode,
      );
    } finally {
      await nextTick();
      suppressRouteRestore = false;
    }
  }

  return {
    bindTableScroll,
    tableScroll,
    rememberTableScroll,
    appliedFilters,
    hasAppliedQuery,
    filtersPending,
    retryLoad,
    unsaved,
    actionAccess,
    actions,
    applyFilters,
    askDelete,
    canCreate,
    canReviewStatus,
    closeEditor,
    dateTime,
    deleteTarget,
    displayError,
    editing,
    editorOpen,
    filterError,
    filters,
    form,
    listError,
    listLoading,
    load,
    manualCandidates,
    openCreate,
    openEdit,
    page,
    records,
    remove,
    save,
    selectedMember,
    setPage,
    statusLabel,
    statusTone,
    total,
    totalPages,
  };
}

import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch, type ComponentPublicInstance } from "vue";
import { onBeforeRouteLeave, useRoute, useRouter } from "vue-router";
import { api, del, downloadBlob, get, post, put } from "../../shared/api";
import { useSession } from "../../app/session";
import { useAsyncTask } from "../../shared/composables/useAsyncTask";
import { usePendingActions } from "../../shared/composables/usePendingActions";
import { useUnsavedChanges } from "../../shared/composables/useUnsavedChanges";
import { notify } from "../../shared/composables/useToast";
import { dateRangeError } from "../../shared/validation/dateRange";
import { createPrivateNavigationState } from "../../shared/navigation/privateNavigationState";
import { routeQuerySignature, updateOwnedRouteQuery } from "../../shared/navigation/routeQueryState";
import type { AccountCandidate } from "../accounts/accountCandidates";
import { fetchRepairPage } from "./repairApi";
import { repairAgreementFormType } from "./repairDisplay";
import { repairLocalDateTime } from "./repairForms";
import { canDeleteRepairs, canExportRepairs, canManageRepairs } from "./repairPermissions";
import type { RepairCase, RepairCaseForm, RepairWorkspaceRouteState } from "./repairTypes";
import { useRepairWorkspace, type RepairWorkspaceQuery } from "./useRepairWorkspace";

type TablePosition = { top: number; left: number };
type HistorySnapshot = { state: RepairWorkspaceRouteState; signature: string; scroll: TablePosition };
const historyMemory = createPrivateNavigationState<HistorySnapshot>();
const visitKey = "repairWorkspaceVisit";
const publicKeys = ["status", "page", "from", "to"] as const;

export function useRepairManagementWorkspace() {
  const { user } = useSession();
  const task = useAsyncTask();
  const actions = usePendingActions();
  const route = useRoute();
  const router = useRouter();
  const historyScope = historyMemory.scope();
  const tableScroll = ref<HTMLElement | null>(null);
  let active = true;
  let activeVisit: string | undefined;
  let routeReady = false;
  let suppressRouteRestore = false;
  let syncVersion = 0;
  let pendingRestore = historySnapshot();
  const initialQuery = privateQuery(route.query, pendingRestore);
  const initialIntent = typeof route.query.intent === "string" ? route.query.intent : "";
  const initialKeyword = typeof route.query.keyword === "string" ? route.query.keyword : "";
  const now = new Date();
  const workspace = useRepairWorkspace({
    loadPage: fetchRepairPage,
    defaults: { from: `${now.getFullYear()}-01-01`, to: localDate(now) },
    initialQuery,
    onQueryChange: updateRouteQuery,
  });
  const {
    activeStatus,
    filters,
    appliedFilters,
    hasAppliedQuery,
    filtersPending,
    pendingQuery,
    counts: statusCounts,
    page: repairPage,
    applyFilters: applyWorkspaceFilters,
    setStatus,
    setPage,
    retry,
    refreshAfterMutation,
  } = workspace;
  const editorOpen = ref(false);
  const editorInitialStep = ref<1 | 2>(1);
  const detailReturnTarget = ref<RepairCase | null>(null);
  const deleteTarget = ref<RepairCase | null>(null);
  const detailTarget = ref<RepairCase | null>(null);
  const detailIndex = computed(() => repairPage.items.findIndex(item => item.id === detailTarget.value?.id));
  const detailPosition = computed(() => detailIndex.value < 0 ? "此记录已不在当前页列表中" : `当前页 ${detailIndex.value + 1} / ${repairPage.items.length}`);
  const detailCanPrevious = computed(() => !repairPage.loading && !repairPage.error && detailIndex.value > 0);
  const detailCanNext = computed(() => !repairPage.loading && !repairPage.error && detailIndex.value >= 0 && detailIndex.value < repairPage.items.length - 1);
  const agreementOpen = ref(false);
  const agreementTarget = ref<RepairCase | null>(null);
  const agreementHtml = ref("");
  const agreementLoading = ref(false);
  const agreementError = ref("");
  const exportButton = ref<HTMLButtonElement | null>(null);
  const handlerCandidates = ref<AccountCandidate[]>([]);
  const selectedHandler = ref<AccountCandidate | null>(null);
  const revealedPhones = ref(new Set<number>());
  const form = reactive<RepairCaseForm>({
    id: null,
    agreementType: "REPAIR",
    ownerName: "",
    ownerPhone: "",
    deviceType: "",
    deviceBrand: "",
    deviceModel: "",
    accessories: "",
    faultDescription: "",
    serviceDescription: "",
    dataBackupConfirmed: false,
    riskAcknowledged: false,
    privacyAcknowledged: false,
    status: "REPAIRING",
    receivedAt: "",
    completedAt: "",
    handlerName: "",
    remark: "",
  });
  const canManage = computed(() => canManageRepairs(user.value?.role));
  const canDelete = computed(() => canDeleteRepairs(user.value?.role));
  const canExport = computed(() => canExportRepairs(user.value?.role));
  const exportDisabled = computed(() => !hasAppliedQuery.value || repairPage.loading || Boolean(repairPage.error) || actions.isPending("export-repairs"));
  const repairTotalPages = computed(() =>
    Math.max(1, Math.ceil(repairPage.total / repairPage.pageSize)),
  );
  const filterError = computed(() => dateRangeError(filters.from, filters.to));
  const editorBaseline = ref("");
  const unsaved = useUnsavedChanges(
    () => editorOpen.value && editorSnapshot() !== editorBaseline.value,
  );
  let agreementRequestVersion = 0;
  let leavingPage = false;
  const stopNavigationEnd = router.afterEach(() => { leavingPage = false; });
  const stopNavigationError = router.onError(() => { leavingPage = false; });

  onMounted(async () => {
    await consumeKeyword();
    if (!active) return;
    routeReady = true;
    await Promise.all([workspace.initialize(), loadHandlerCandidates()]);
    if (!active) return;
    if (initialIntent === "new" && canManage.value) openEditor();
    if (initialIntent === "export" && canExport.value) {
      await nextTick();
      exportButton.value?.focus();
    }
    if (initialIntent === "preview-agreement") {
      const target = repairPage.items.find((item) => item.caseNo === initialKeyword);
      if (target) await preview(target);
      else notify(`未找到维修事务 ${initialKeyword}`, "warning");
    }
  });
  onBeforeUnmount(() => {
    rememberTableScroll();
    active = false;
    stopNavigationEnd();
    stopNavigationError();
    workspace.dispose();
  });
  watch(
    () => route.query,
    async (query) => {
      if (!active || !routeReady || suppressRouteRestore || route.name !== "repairs") return;
      if (sameQuery(query, workspace.currentQuery())) return;
      rememberTableScroll();
      pendingRestore = historySnapshot();
      const request = privateQuery(query, pendingRestore);
      await consumeKeyword();
      await workspace.restoreQuery(request);
    },
  );
  onBeforeRouteLeave(
    () => {
      if (actions.isPending("save-repair") || actions.isPending("delete-repair")) return false;
      return new Promise<boolean>((resolve) => {
        unsaved.request(() => { leavingPage = true; resolve(true); }, () => resolve(false));
      });
    },
  );

  async function updateRouteQuery(query: Record<string, string>, mode: "push" | "replace") {
    if (!active || leavingPage || route.name !== "repairs") return;
    const version = ++syncVersion;
    const state = workspace.currentState();
    rememberTableScroll();
    activeVisit = undefined;
    suppressRouteRestore = true;
    try {
      if (!sameQuery(route.query, query)) await router[mode]({ query });
      if (!active || leavingPage || route.name !== "repairs" || version !== syncVersion) return;
      if (router.options && !currentVisit()) {
        await router.replace({ query, state: { [visitKey]: crypto.randomUUID() }, force: true });
      }
    } finally {
      suppressRouteRestore = false;
    }
    await nextTick();
    if (!active || leavingPage || route.name !== "repairs" || version !== syncVersion) return;
    const saved = pendingRestore;
    if (saved && (Object.keys(saved.state) as Array<keyof RepairWorkspaceRouteState>).every(key => saved.state[key] === state[key]) && tableScroll.value) {
      tableScroll.value.scrollTop = saved.scroll.top;
      tableScroll.value.scrollLeft = saved.scroll.left;
    }
    pendingRestore = undefined;
    activeVisit = currentVisit();
    if (activeVisit) historyScope.set(activeVisit, { state: { ...state }, signature: publicSignature(), scroll: tablePosition() });
  }

  function currentVisit() {
    const id = router.options?.history.state[visitKey];
    return typeof id === "string" ? id : undefined;
  }
  function publicSignature() { return routeQuerySignature(route.query, publicKeys); }
  function historySnapshot() {
    if (route.query.keyword !== undefined) return undefined;
    const id = currentVisit();
    const saved = id ? historyScope.get(id) : undefined;
    return saved?.signature === publicSignature() ? saved : undefined;
  }
  function privateQuery(query: RepairWorkspaceQuery, saved?: HistorySnapshot) {
    return saved ? { ...query, keyword: saved.state.keyword } : query;
  }
  async function consumeKeyword() {
    suppressRouteRestore = true;
    try { await updateOwnedRouteQuery(router, route.query, ["keyword"], {}, "replace"); }
    finally { suppressRouteRestore = false; }
  }
  function tablePosition(): TablePosition {
    return { top: tableScroll.value?.scrollTop || 0, left: tableScroll.value?.scrollLeft || 0 };
  }
  function rememberTableScroll() {
    const saved = activeVisit ? historyScope.get(activeVisit) : undefined;
    if (saved && tableScroll.value) saved.scroll = tablePosition();
  }
  function bindTableScroll(element: Element | ComponentPublicInstance | null) {
    tableScroll.value = element instanceof HTMLElement ? element : null;
  }

  async function load() {
    if (filterError.value) return;
    await applyWorkspaceFilters();
  }

  function openEditor(item?: RepairCase, initialStep: 1 | 2 = 1) {
    detailReturnTarget.value = null;
    editorInitialStep.value = item ? initialStep : 1;
    Object.assign(
      form,
      item
        ? {
            ...item,
            agreementType: repairAgreementFormType(item.agreementType),
            receivedAt: toInput(item.receivedAt),
            completedAt: toInput(item.completedAt),
          }
        : {
            id: null,
            agreementType: "REPAIR",
            ownerName: "",
            ownerPhone: "",
            deviceType: "",
            deviceBrand: "",
            deviceModel: "",
            accessories: "",
            faultDescription: "",
            serviceDescription: "",
            dataBackupConfirmed: false,
            riskAcknowledged: false,
            privacyAcknowledged: false,
            status: "REPAIRING",
            receivedAt: repairLocalDateTime(),
            completedAt: "",
            handlerName: user.value?.name || "",
            remark: "",
          },
    );
    selectedHandler.value = item
      ? handlerCandidates.value.find((candidate) => candidate.id === item.handlerUserId) ||
        (item.handlerUserId
          ? {
              id: item.handlerUserId,
              studentNo: "",
              name: item.handlerName || "原负责人",
              inactive: true,
            }
          : null)
      : handlerCandidates.value.find((candidate) => candidate.id === user.value?.id) || null;
    editorBaseline.value = editorSnapshot();
    editorOpen.value = true;
  }

  function closeEditor() {
    if (actions.isPending("save-repair")) return;
    unsaved.request(() => {
      editorOpen.value = false;
      detailTarget.value = detailReturnTarget.value;
      detailReturnTarget.value = null;
    });
  }

  function editFromDetail(item: RepairCase, initialStep: 1 | 2 = 1) {
    detailTarget.value = null;
    openEditor(item, initialStep);
    detailReturnTarget.value = item;
  }

  function moveDetail(direction: -1 | 1) {
    if (direction === -1 ? !detailCanPrevious.value : !detailCanNext.value) return;
    revealedPhones.value = new Set();
    detailTarget.value = repairPage.items[detailIndex.value + direction] || null;
  }

  function requestDelete(item: RepairCase) {
    detailTarget.value = null;
    deleteTarget.value = item;
  }

  async function save() {
    const previousStatus = form.id ? findLoadedCase(form.id)?.status : null;
    const payload = {
      ...form,
      handlerUserId: selectedHandler.value?.id || null,
      handlerName: selectedHandler.value?.name || null,
      ownerOrg: null,
      deviceSerial: null,
      completedAt: form.completedAt || null,
    };
    const value = await actions.run("save-repair", () =>
      form.id
        ? task.run<RepairCase>(() => put(`/api/repairs/${form.id}`, payload), "维修事务已更新")
        : task.run<RepairCase>(() => post("/api/repairs", payload), "维修事务已创建"),
    );
    if (value) {
      editorBaseline.value = editorSnapshot();
      editorOpen.value = false;
      if (detailReturnTarget.value) detailTarget.value = value;
      detailReturnTarget.value = null;
      await refreshAfterMutation(previousStatus, value.status);
    }
  }

  async function loadHandlerCandidates() {
    const value = await task.run(() => get<AccountCandidate[]>("/api/repairs/handler-candidates"));
    if (value) handlerCandidates.value = value;
  }

  async function remove() {
    const target = deleteTarget.value;
    if (!target) return;
    const removed = await actions.run("delete-repair", () =>
      task.run(async () => {
        await del(`/api/repairs/${target.id}`);
        return true;
      }, "已移入维修回收站"),
    );
    if (removed) {
      deleteTarget.value = null;
      await refreshAfterMutation(target.status, null);
    }
  }

  async function preview(item: RepairCase) {
    agreementTarget.value = item;
    agreementOpen.value = true;
    await loadAgreement();
  }

  async function loadAgreement() {
    const target = agreementTarget.value;
    if (!target) return;
    const version = ++agreementRequestVersion;
    agreementLoading.value = true;
    agreementError.value = "";
    try {
      const blob = await api<Blob>(`/api/repairs/${target.id}/agreement`);
      const html = await blob.text();
      if (
        version === agreementRequestVersion &&
        agreementOpen.value &&
        agreementTarget.value?.id === target.id
      ) {
        agreementHtml.value = html;
      }
    } catch (cause) {
      if (version === agreementRequestVersion && agreementOpen.value) {
        agreementError.value = cause instanceof Error ? cause.message : "协议暂时无法预览";
      }
    } finally {
      if (version === agreementRequestVersion) agreementLoading.value = false;
    }
  }

  function closeAgreement() {
    agreementRequestVersion += 1;
    agreementOpen.value = false;
    agreementTarget.value = null;
    agreementHtml.value = "";
    agreementError.value = "";
  }

  async function exportCases() {
    if (exportDisabled.value) return;
    const snapshot = { ...appliedFilters };
    const params = new URLSearchParams();
    Object.entries(snapshot).forEach(([key, value]) => value && params.set(key, value));
    params.set("status", "ALL");
    await actions.run("export-repairs", async () => {
      const blob = await task.run(() => get<Blob>(`/api/repairs/export?${params}`));
      if (blob) downloadBlob(blob, `维修事务_全部状态_${snapshot.from}_${snapshot.to}.xlsx`);
    });
  }

  function findLoadedCase(id: number) {
    return repairPage.items.find((item) => item.id === id);
  }

  function phoneVisible(id: number) {
    return revealedPhones.value.has(id);
  }

  function togglePhone(id: number) {
    const next = new Set(revealedPhones.value);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    revealedPhones.value = next;
  }

  function editorSnapshot() {
    return JSON.stringify({ form, handlerId: selectedHandler.value?.id || null });
  }

  function captureExportButton(element: unknown) {
    exportButton.value = element instanceof HTMLButtonElement ? element : null;
  }

  return {
    tableScroll,
    bindTableScroll,
    rememberTableScroll,
    appliedFilters,
    hasAppliedQuery,
    filtersPending,
    pendingQuery,
    exportDisabled,
    editorInitialStep,
    detailPosition,
    detailCanPrevious,
    detailCanNext,
    moveDetail,
    activeStatus,
    filters,
    statusCounts,
    repairPage,
    editorOpen,
    deleteTarget,
    detailTarget,
    agreementOpen,
    agreementTarget,
    agreementHtml,
    agreementLoading,
    agreementError,
    handlerCandidates,
    selectedHandler,
    revealedPhones,
    form,
    canManage,
    canDelete,
    canExport,
    repairTotalPages,
    filterError,
    unsaved,
    isPending: actions.isPending,
    load,
    setStatus,
    setPage,
    retry,
    openEditor,
    closeEditor,
    editFromDetail,
    requestDelete,
    save,
    remove,
    preview,
    loadAgreement,
    closeAgreement,
    exportCases,
    phoneVisible,
    togglePhone,
    captureExportButton,
  };
}

const toInput = (value?: string) => value?.slice(0, 16) || "";

function localDate(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function sameQuery(current: Record<string, unknown>, next: Record<string, string>) {
  const currentEntries = Object.entries(current)
    .filter(([, value]) => typeof value === "string" && value)
    .sort(([left], [right]) => left.localeCompare(right));
  const nextEntries = Object.entries(next).sort(([left], [right]) => left.localeCompare(right));
  return JSON.stringify(currentEntries) === JSON.stringify(nextEntries);
}

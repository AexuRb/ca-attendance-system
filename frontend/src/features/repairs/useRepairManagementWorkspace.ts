import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { onBeforeRouteLeave, useRoute, useRouter } from "vue-router";
import { api, del, downloadBlob, get, post, put } from "../../shared/api";
import { useSession } from "../../app/session";
import { useAsyncTask } from "../../shared/composables/useAsyncTask";
import { usePendingActions } from "../../shared/composables/usePendingActions";
import { useUnsavedChanges } from "../../shared/composables/useUnsavedChanges";
import { notify } from "../../shared/composables/useToast";
import { dateRangeError } from "../../shared/validation/dateRange";
import type { AccountCandidate } from "../accounts/accountCandidates";
import { fetchRepairPage } from "./repairApi";
import { repairAgreementFormType } from "./repairDisplay";
import { canDeleteRepairs, canExportRepairs, canManageRepairs } from "./repairPermissions";
import type { RepairCase, RepairCaseForm } from "./repairTypes";
import { useRepairWorkspace } from "./useRepairWorkspace";

export function useRepairManagementWorkspace() {
  const { user } = useSession();
  const task = useAsyncTask();
  const actions = usePendingActions();
  const route = useRoute();
  const router = useRouter();
  const initialIntent = typeof route.query.intent === "string" ? route.query.intent : "";
  const initialKeyword = typeof route.query.keyword === "string" ? route.query.keyword : "";
  const now = new Date();
  const workspace = useRepairWorkspace({
    loadPage: fetchRepairPage,
    defaults: { from: `${now.getFullYear()}-01-01`, to: localDate(now) },
    initialQuery: route.query,
    onQueryChange: updateRouteQuery,
  });
  const {
    activeStatus,
    filters,
    counts: statusCounts,
    page: repairPage,
    applyFilters: applyWorkspaceFilters,
    setStatus,
    setPage,
    retry,
    refreshAfterMutation,
  } = workspace;
  const editorOpen = ref(false);
  const filterOpen = ref(false);
  const deleteTarget = ref<RepairCase | null>(null);
  const detailTarget = ref<RepairCase | null>(null);
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
  const repairTotalPages = computed(() =>
    Math.max(1, Math.ceil(repairPage.total / repairPage.pageSize)),
  );
  const filterError = computed(() => dateRangeError(filters.from, filters.to));
  const editorBaseline = ref("");
  const unsaved = useUnsavedChanges(
    () => editorOpen.value && editorSnapshot() !== editorBaseline.value,
  );
  let agreementRequestVersion = 0;

  onMounted(async () => {
    await Promise.all([workspace.initialize(), loadHandlerCandidates()]);
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
  onBeforeUnmount(workspace.dispose);
  watch(
    () => route.query,
    async (query) => {
      if (sameQuery(query, workspace.currentQuery())) return;
      await workspace.restoreQuery(query);
    },
  );
  onBeforeRouteLeave(
    () =>
      new Promise<boolean>((resolve) => {
        unsaved.request(() => resolve(true), () => resolve(false));
      }),
  );

  async function updateRouteQuery(query: Record<string, string>, mode: "push" | "replace") {
    if (sameQuery(route.query, query)) return;
    await router[mode]({ query });
  }

  async function load() {
    if (filterError.value) return;
    await applyWorkspaceFilters();
  }

  function openEditor(item?: RepairCase) {
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
            receivedAt: toInput(new Date().toISOString()),
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
    unsaved.request(() => {
      editorOpen.value = false;
    });
  }

  function editFromDetail(item: RepairCase) {
    detailTarget.value = null;
    openEditor(item);
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
    if (filterError.value) return;
    const params = new URLSearchParams();
    Object.entries(filters).forEach(([key, value]) => value && params.set(key, value));
    params.set("status", "ALL");
    await actions.run("export-repairs", async () => {
      const blob = await task.run(() => get<Blob>(`/api/repairs/export?${params}`));
      if (blob) downloadBlob(blob, `维修事务_${filters.from}_${filters.to}.xlsx`);
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
    activeStatus,
    filters,
    statusCounts,
    repairPage,
    editorOpen,
    filterOpen,
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

import { computed, nextTick, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api, del, downloadBlob, get, post } from "../../shared/api";
import { useSession } from "../../app/session";
import { useAsyncTask } from "../../shared/composables/useAsyncTask";
import { useLatestRequest } from "../../shared/composables/useLatestRequest";
import { usePendingActions } from "../../shared/composables/usePendingActions";
import { dateRangeError } from "../../shared/validation/dateRange";
import { backupFileError } from "../../shared/validation/fileValidation";
import { exportSelectionFingerprint } from "./dataExportState";
import { canDeleteBackup, canRestoreBackup, canViewRepairRecycleBin } from "./dataPermissions";
import type {
  BackupItem,
  ExportOptions,
  ExportPreview,
  ExportRequest,
  ExportSource,
  MaintenanceSummary,
  RecycledRepairCase,
} from "./dataCenterTypes";

type DataCenterTab = "export" | "backups" | "recycle";

export function useDataCenterWorkspace() {
  const { user, expireSession } = useSession();
  const task = useAsyncTask();
  const actions = usePendingActions();
  const optionsRequest = useLatestRequest();
  const summaryRequest = useLatestRequest();
  const backupRequest = useLatestRequest();
  const recycleRequest = useLatestRequest();
  const route = useRoute();
  const router = useRouter();
  const tab = ref<DataCenterTab>(normalizedTab(route.query.tab));
  const options = reactive<ExportOptions>({ sources: [] });
  const request = reactive<ExportRequest>({
    source: "",
    fields: [],
    filters: {},
    filename: "",
  });
  const preview = ref<ExportPreview | null>(null);
  const previewSelection = computed(() => exportSelectionFingerprint(request));
  const summary = ref<MaintenanceSummary | null>(null);
  const backups = ref<BackupItem[]>([]);
  const recycle = ref<RecycledRepairCase[]>([]);
  const deleteBackupTarget = ref<BackupItem | null>(null);
  const purgeTarget = ref<RecycledRepairCase | null>(null);
  const restoreTarget = ref<File | null>(null);
  const restoreFileError = ref("");
  const createBackupOpen = ref(false);
  const canDeleteBackups = computed(() => canDeleteBackup(user.value?.role));
  const canRestore = computed(() => canRestoreBackup(user.value?.role));
  const canViewRecycle = computed(() => canViewRepairRecycleBin(user.value?.role));
  const currentSource = computed<ExportSource | undefined>(() =>
    options.sources.find((source) => source.id === request.source),
  );
  const exportDateError = computed(() =>
    dateRangeError(request.filters.from || "", request.filters.to || ""),
  );
  const activeLoadError = computed(() => {
    if (tab.value === "export") return optionsRequest.error.value || summaryRequest.error.value;
    if (tab.value === "backups") return backupRequest.error.value || summaryRequest.error.value;
    return recycleRequest.error.value;
  });

  onMounted(async () => {
    if (route.query.tab !== tab.value) {
      await router.replace({ query: { ...route.query, tab: tab.value } });
    }
    await loadOptions();
    if (tab.value === "backups") await Promise.all([loadBackups(), loadSummary()]);
    else if (options.sources.length) await loadSummary();
    if (tab.value === "recycle") await loadRecycle();
    if (route.query.intent === "create-backup" && tab.value === "backups") {
      createBackupOpen.value = true;
    }
  });

  watch(tab, async (value) => {
    if (value === "backups") await Promise.all([loadBackups(), loadSummary()]);
    if (value === "recycle") await loadRecycle();
  });

  watch(
    () => route.query.tab,
    (value) => {
      const next = normalizedTab(value);
      if (tab.value !== next) tab.value = next;
    },
  );

  watch(previewSelection, () => {
    preview.value = null;
  }, { flush: "sync" });

  function normalizedTab(value: unknown): DataCenterTab {
    if (value === "backups") return "backups";
    if (value === "recycle" && canViewRepairRecycleBin(user.value?.role)) return "recycle";
    return "export";
  }

  function selectTab(value: DataCenterTab) {
    if (tab.value === value) return;
    tab.value = value;
    void router.push({ query: { ...route.query, tab: value } });
  }

  function onTabKeydown(event: KeyboardEvent, current: DataCenterTab) {
    const availableTabs: DataCenterTab[] = canViewRecycle.value
      ? ["export", "backups", "recycle"]
      : ["export", "backups"];
    const currentIndex = availableTabs.indexOf(current);
    let nextIndex = currentIndex;

    if (event.key === "ArrowRight") nextIndex = (currentIndex + 1) % availableTabs.length;
    else if (event.key === "ArrowLeft") {
      nextIndex = (currentIndex - 1 + availableTabs.length) % availableTabs.length;
    } else if (event.key === "Home") nextIndex = 0;
    else if (event.key === "End") nextIndex = availableTabs.length - 1;
    else return;

    event.preventDefault();
    const nextTab = availableTabs[nextIndex];
    if (!nextTab) return;
    selectTab(nextTab);
    void nextTick(() => document.getElementById(`data-tab-${nextTab}`)?.focus());
  }

  async function loadOptions() {
    const value = await optionsRequest.run(
      (signal) => get<ExportOptions>("/api/exports/options", { signal }),
      "导出配置加载失败",
    );
    if (!value) return;
    options.sources = value.sources || [];
    if (!request.source && options.sources[0]) selectSource(options.sources[0]);
  }

  async function loadSummary() {
    const value = await summaryRequest.run(
      (signal) => get<MaintenanceSummary>("/api/maintenance/summary", { signal }),
      "数据概览加载失败",
    );
    if (value) summary.value = value;
  }

  async function loadBackups() {
    const value = await backupRequest.run(
      (signal) => get<BackupItem[]>("/api/maintenance/backups", { signal }),
      "备份列表加载失败",
    );
    if (value) backups.value = value;
  }

  function retryActiveTab() {
    if (tab.value === "export") void Promise.all([loadOptions(), loadSummary()]);
    else if (tab.value === "backups") void Promise.all([loadBackups(), loadSummary()]);
    else void loadRecycle();
  }

  function selectSource(source: ExportSource) {
    request.source = source.id;
    request.fields = source.fields
      .filter((field) => field.defaultSelected)
      .map((field) => field.id);
    request.filters = {};
    source.filters.forEach((filter) => {
      request.filters[filter.id] = filter.defaultValue || "";
    });
    request.filename = "";
    preview.value = null;
  }

  function updateFilter(id: string, value: string) {
    request.filters[id] = value;
  }

  function resetFilters() {
    if (!currentSource.value) return;
    currentSource.value.filters.forEach((filter) => {
      request.filters[filter.id] = filter.defaultValue || "";
    });
  }

  function toggleField(id: string) {
    request.fields = request.fields.includes(id)
      ? request.fields.filter((field) => field !== id)
      : [...request.fields, id];
  }

  function toggleAll() {
    if (!currentSource.value) return;
    request.fields =
      request.fields.length === currentSource.value.fields.length
        ? []
        : currentSource.value.fields.map((field) => field.id);
  }

  async function makePreview() {
    if (exportDateError.value || !request.fields.length) return;
    await actions.run("preview", async () => {
      const value = await task.run(() => post<ExportPreview>("/api/exports/preview", request));
      if (value) preview.value = value;
    });
  }

  async function exportCustom() {
    if (exportDateError.value) return;
    const snapshot = JSON.parse(JSON.stringify(request)) as ExportRequest;
    const filename = `${snapshot.filename || currentSource.value?.label || "自定义导出"}.xlsx`;
    await actions.run("export", async () => {
      const blob = await task.run(() =>
        api<Blob>("/api/exports/excel", {
          method: "POST",
          headers: {
            Accept: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          },
          body: JSON.stringify(snapshot),
        }),
      );
      if (blob) downloadBlob(blob, filename);
    });
  }

  async function createBackup() {
    await actions.run("create-backup", async () => {
      const created = await task.run(async () => {
        await post("/api/maintenance/backups");
        return true;
      }, "备份已创建");
      if (created) {
        createBackupOpen.value = false;
        await Promise.all([loadBackups(), loadSummary()]);
      }
    });
  }

  async function downloadBackup(item: BackupItem) {
    const blob = await get<Blob>(`/api/maintenance/backups/${encodeURIComponent(item.filename)}`);
    downloadBlob(blob, item.filename);
  }

  async function deleteBackup() {
    const target = deleteBackupTarget.value;
    if (!target) return;
    await actions.run("delete-backup", async () => {
      const removed = await task.run(
        () => del(`/api/maintenance/backups/${encodeURIComponent(target.filename)}`),
        "备份已删除",
      );
      if (removed === undefined) return;
      deleteBackupTarget.value = null;
      await Promise.all([loadBackups(), loadSummary()]);
    });
  }

  async function pickRestore(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = "";
    if (!file || !canRestore.value) return;
    restoreFileError.value = backupFileError(file);
    if (restoreFileError.value) return;
    restoreTarget.value = file;
  }

  async function restoreBackup() {
    if (!restoreTarget.value || !canRestore.value) return;
    await actions.run("restore-backup", async () => {
      const body = new FormData();
      body.append("file", restoreTarget.value as File);
      const restored = await task.run(
        () => post("/api/maintenance/backups/restore", body),
        "数据已恢复，请重新登录",
      );
      if (restored === undefined) return;
      restoreTarget.value = null;
      expireSession();
      await router.replace({ name: "login", query: { reason: "restored" } });
      window.location.reload();
    });
  }

  async function loadRecycle() {
    if (!canViewRecycle.value) return;
    const value = await recycleRequest.run(
      (signal) => get<RecycledRepairCase[]>("/api/repairs/recycle-bin", { signal }),
      "维修回收站加载失败",
    );
    if (value) recycle.value = value;
  }

  async function restoreRepair(item: RecycledRepairCase) {
    await actions.run(`restore-repair:${item.id}`, async () => {
      const restored = await task.run(
        () => post(`/api/repairs/${item.id}/restore`),
        "维修事务已恢复",
      );
      if (restored !== undefined) await loadRecycle();
    });
  }

  function isRestorePending(id: number) {
    return actions.isPending(`restore-repair:${id}`);
  }

  async function purgeRepair(value: string) {
    const target = purgeTarget.value;
    if (!target || value.trim() !== target.caseNo) return;
    await actions.run("purge-repair", async () => {
      const purged = await task.run(
        () => post(`/api/repairs/${target.id}/purge`, { caseNo: value }),
        "维修事务已彻底删除",
      );
      if (purged === undefined) return;
      purgeTarget.value = null;
      await loadRecycle();
    });
  }

  return {
    tab,
    options,
    request,
    preview,
    summary,
    backups,
    recycle,
    deleteBackupTarget,
    purgeTarget,
    restoreTarget,
    restoreFileError,
    createBackupOpen,
    canDeleteBackups,
    canRestore,
    canViewRecycle,
    exportDateError,
    activeLoadError,
    backupRequest,
    recycleRequest,
    actions,
    selectTab,
    onTabKeydown,
    retryActiveTab,
    selectSource,
    updateFilter,
    resetFilters,
    toggleField,
    toggleAll,
    makePreview,
    exportCustom,
    createBackup,
    downloadBackup,
    deleteBackup,
    pickRestore,
    restoreBackup,
    loadRecycle,
    restoreRepair,
    isRestorePending,
    purgeRepair,
  };
}

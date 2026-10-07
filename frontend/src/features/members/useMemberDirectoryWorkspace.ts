import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch, type ComponentPublicInstance } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useSession } from "../../app/session";
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
import { excelFileError } from "../../shared/validation/fileValidation";
import { createPrivateNavigationState } from "../../shared/navigation/privateNavigationState";
import {
  bulkStatusPayload,
  selectableMemberIds,
  type BulkStatusResult,
  type MemberImportResult,
  type MemberPage,
  type MemberStatus,
  type MemberSummary,
} from "./memberDirectory";

type DirectoryFilters = { keyword: string; role: string; status: string; grade: string };
type TablePosition = { top: number; left: number };
type HistorySnapshot = { filters: DirectoryFilters; page: number; signature: string; scroll: TablePosition };
const historyMemory = createPrivateNavigationState<HistorySnapshot>();
const visitKey = "memberDirectoryVisit";

export function useMemberDirectoryWorkspace() {
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
  const task = useAsyncTask();
  const listRequest = useLatestRequest();
  const actions = usePendingActions();
  const { loading: listLoading, error: listError } = listRequest;
  const members = ref<MemberSummary[]>([]);
  const grades = ref<string[]>([]);
  const total = ref(0);
  const page = ref(1);
  const pageSize = 20;
  const selected = ref(new Set<number>());
  const allFilteredSelected = ref(false);
  const activeFilters = ref({ keyword: "", role: "", status: "", grade: "" });
  let activeFilterKey: string | null = null;
  let lastQuery: { page: number; filters: DirectoryFilters; mode: "push" | "replace"; restore?: TablePosition } | null = null;
  const editorOpen = ref(false);
  const editorTarget = ref<MemberSummary | null>(null);
  type ImportPreview = { valid: boolean; created: number; updated: number; errorCount: number; warnings: string[]; errors: string[]; token: string; changes: { studentNo: string; name: string; action: string; fields: { field: string; before: string; after: string }[] }[] };
  const importPreview = ref<ImportPreview | null>(null);
  const importOpen = ref(false);
  const importFile = ref<File | null>(null);
  const importResult = ref<MemberImportResult | null>(null);
  const bulkOpen = ref(false);
  const bulkTargetStatus = ref<MemberStatus>("ACTIVE");
  const bulkResult = ref<BulkStatusResult | null>(null);
  const resetTarget = ref<MemberSummary | null>(null);
  const deleteTarget = ref<MemberSummary | null>(null);
  const filters = reactive({ keyword: "", role: "", status: "", grade: "" });
  const importError = ref("");
  const routeKeys = ["role", "status", "grade", "page", "keyword"] as const;
  let routeReady = false;
  let suppressRouteRestore = false;

  const publicSignature = () => routeQuerySignature(route.query, ["role", "status", "grade", "page"]);
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

  const totalPages = computed(() =>
    Math.max(1, Math.ceil(total.value / pageSize)),
  );
  const gradeChoices = Array.from(
    { length: 33 },
    (_, index) => `${new Date().getFullYear() + 2 - index}级`,
  );
  const selectableIds = computed(() =>
    selectableMemberIds(members.value, user.value?.role, user.value?.id),
  );
  const lockEditorAccountControls = computed(
    () =>
      editorTarget.value?.id === user.value?.id &&
      editorTarget.value?.role === "ADMIN",
  );

  onMounted(async () => {
    restoreRouteState(true);
    const saved = stringRouteQuery(route.query.keyword) ? undefined : historySnapshot();
    if (saved) Object.assign(filters, saved.filters);
    const initialPage = saved?.page || positiveRoutePage(route.query.page);
    await updateOwnedRouteQuery(router, route.query, ["keyword"], {}, "replace");
    routeReady = true;
    await Promise.all([load(initialPage, { ...filters }, "replace", saved?.scroll), loadGrades()]);
    const intent = stringRouteQuery(route.query.intent);
    if (intent === "new") openCreate();
    if (intent === "import") openImport();
  });

  watch(
    () => routeQuerySignature(route.query, routeKeys),
    () => {
      if (!routeReady || suppressRouteRestore) return;
      rememberTableScroll();
      const saved = historySnapshot();
      restoreRouteState(false);
      if (saved) filters.keyword = saved.filters.keyword;
      void load(positiveRoutePage(route.query.page), { ...filters }, "replace", saved?.scroll);
    },
  );

  async function load(target = page.value, values = activeFilters.value, mode: "push" | "replace" = "replace", restore?: TablePosition) {
    rememberTableScroll();
    const version = ++queryVersion;
    const requestFilters = { ...values };
    lastQuery = { page: target, filters: requestFilters, mode, restore };
    const requestFilterKey = filterKey(requestFilters);
    const query = new URLSearchParams({
      page: String(target),
      pageSize: String(pageSize),
    });
    appendFilters(query, requestFilters);
    const value = await listRequest.run(
      (signal) => get<MemberPage>(`/api/users/page?${query}`, { signal }),
      "成员名册加载失败",
    );
    if (!value) return;
    activeVisit = undefined;
    if (activeFilterKey !== null && activeFilterKey !== requestFilterKey) clearSelection();
    members.value = value.items;
    total.value = value.total;
    page.value = value.page;
    activeFilters.value = requestFilters;
    activeFilterKey = requestFilterKey;
    lastQuery = null;
    await syncRoute(value.page, mode);
    const visit = await ensureVisit();
    await nextTick();
    if (!active || version !== queryVersion) return;
    if (restore && tableScroll.value) {
      tableScroll.value.scrollTop = restore.top;
      tableScroll.value.scrollLeft = restore.left;
    }
    activeVisit = visit;
    if (visit) historyScope.set(visit, {
      filters: { ...requestFilters }, page: value.page, signature: publicSignature(),
      scroll: { top: tableScroll.value?.scrollTop || 0, left: tableScroll.value?.scrollLeft || 0 },
    });
  }

  async function applyFilters() {
    await load(1, { ...filters }, "push");
  }

  async function setPage(target: number) {
    await load(target, activeFilters.value, "push");
  }

  async function retry() {
    const request = lastQuery;
    if (request) await load(request.page, request.filters, request.mode, request.restore);
    else await load();
  }

  async function loadGrades() {
    const value = await task.run(() => get<string[]>("/api/users/grades"));
    if (value) grades.value = value;
  }

  function canEdit(member: MemberSummary) {
    return user.value?.role === "ADMIN" || member.role !== "ADMIN";
  }

  function openCreate() {
    editorTarget.value = null;
    editorOpen.value = true;
  }

  function openEdit(member: MemberSummary) {
    editorTarget.value = member;
    editorOpen.value = true;
  }

  function closeEditor() {
    if (actions.isPending("save")) return;
    editorOpen.value = false;
    editorTarget.value = null;
  }

  async function saveMember(payload: {
    studentNo: string;
    name: string;
    role: MemberSummary["role"];
    status: MemberStatus;
    phone: string;
    major: string;
    grade: string;
    qq: string;
    reason?: string;
  }) {
    await actions.run("save", async () => {
      const target = editorTarget.value;
      const value = target
        ? await task.run(
            () =>
              put<MemberSummary>(`/api/users/${target.id}`, {
                name: payload.name,
                role: payload.role,
                status: payload.status,
                phone: payload.phone,
                major: payload.major,
                grade: payload.grade,
                qq: payload.qq,
                reason: payload.reason,
              }),
            "成员资料已更新",
          )
        : await task.run(
            () =>
              post<MemberSummary>("/api/users", {
                studentNo: payload.studentNo,
                name: payload.name,
                role: payload.role,
                phone: payload.phone,
                major: payload.major,
                grade: payload.grade,
                qq: payload.qq,
              }),
            "成员已新增，初始密码为学号后六位",
          );
      if (!value) return;
      editorOpen.value = false;
      editorTarget.value = null;
      await Promise.all([load(target ? page.value : 1), loadGrades()]);
    });
  }

  async function toggleStatus(member: MemberSummary) {
    const status: MemberStatus =
      member.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
    await actions.run(`member:${member.id}`, async () => {
      const value = await task.run(
        () =>
          put<MemberSummary>(`/api/users/${member.id}`, {
            name: member.name,
            role: member.role,
            status,
            phone: member.phone,
            major: member.major,
            grade: member.grade,
            qq: member.qq,
            reason: status === "ACTIVE" ? "启用成员账号" : "停用成员账号",
          }),
        "账号状态已更新",
      );
      if (value) await load();
    });
  }

  function toggleMember(id: number) {
    if (listLoading.value || listError.value) return;
    const next = new Set(selected.value);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    selected.value = next;
    allFilteredSelected.value = false;
  }

  async function toggleAll(event: Event) {
    if (listLoading.value || listError.value) return;
    if (!(event.target as HTMLInputElement).checked) {
      clearSelection();
      return;
    }
    const requestFilters = { ...activeFilters.value };
    const requestFilterKey = filterKey(requestFilters);
    await actions.run("select-all", async () => {
      const query = new URLSearchParams();
      appendFilters(query, requestFilters);
      const queryText = query.toString();
      const suffix = queryText ? `?${queryText}` : "";
      const ids = await task.run(
        () => get<number[]>(`/api/users/selection${suffix}`),
      );
      if (!ids || listLoading.value || listError.value || activeFilterKey !== requestFilterKey) return;
      selected.value = new Set(ids);
      allFilteredSelected.value = ids.length > 0;
    });
  }

  function clearSelection() {
    selected.value = new Set();
    allFilteredSelected.value = false;
  }

  function openBulk(status: MemberStatus) {
    if (listLoading.value || listError.value) return;
    bulkTargetStatus.value = status;
    bulkOpen.value = true;
  }

  async function applyBulkStatus(reason: string) {
    await actions.run("bulk", async () => {
      const result = await task.run(
        () =>
          put<BulkStatusResult>(
            "/api/users/bulk-status",
            bulkStatusPayload(selected.value, bulkTargetStatus.value, reason),
          ),
        "批量状态操作已完成",
      );
      if (!result) return;
      bulkResult.value = result;
      bulkOpen.value = false;
      clearSelection();
      await load();
    });
  }

  function openImport() {
    importError.value = "";
    importResult.value = null;
    importPreview.value = null;
    importFile.value = null;
    importOpen.value = true;
  }

  function pickFile(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] || null;
    importResult.value = null;
    importPreview.value = null;
    importError.value = "";
    if (file) importError.value = excelFileError(file, "成员 Excel 文件");
    importFile.value = importError.value ? null : file;
    if (importError.value) input.value = "";
  }

  async function previewMembers() {
    if (!importFile.value) return;
    await actions.run("import", async () => {
      importError.value = "";
      importPreview.value = null;
      const body = new FormData();
      body.append("file", importFile.value!);
      const value = await task.run(() => post<ImportPreview>("/api/users/import/preview", body));
      if (value) importPreview.value = value;
      else importError.value = task.error.value;
    });
  }

  async function importMembers() {
    if (!importFile.value || !importPreview.value?.valid) return;
    await actions.run("import", async () => {
      importError.value = "";
      const body = new FormData();
      body.append("file", importFile.value as File);
      body.append("previewToken", importPreview.value!.token);
      const value = await task.run(
        () => post<MemberImportResult>("/api/users/import", body),
        "成员导入完成",
      );
      if (!value) {
        importError.value = task.error.value;
        importPreview.value = null;
        return;
      }
      importResult.value = value;
      importFile.value = null;
      importPreview.value = null;
      await Promise.all([load(1), loadGrades()]);
    });
  }

  async function resetPassword(newPassword: string) {
    if (!resetTarget.value) return;
    await actions.run("reset-password", async () => {
      const target = resetTarget.value;
      if (!target) return;
      const result = await task.run(
        async () => {
          await post(`/api/users/${target.id}/reset-password`, {
            newPassword: newPassword || undefined,
            reason: "后台重置密码",
          });
          return true;
        },
        "密码已重置",
      );
      if (!result) return;
      resetTarget.value = null;
    });
  }

  async function remove(reason: string) {
    if (!deleteTarget.value) return;
    await actions.run("delete", async () => {
      const target = deleteTarget.value;
      if (!target) return;
      const removed = await task.run(
        () => del(`/api/users/${target.id}`, { reason }),
        "成员已删除",
      );
      if (removed === undefined) return;
      deleteTarget.value = null;
      await load();
    });
  }

  function closeImport() {
    if (!actions.isPending("import")) importOpen.value = false;
  }

  function closeBulk() {
    if (!actions.isPending("bulk")) bulkOpen.value = false;
  }

  function closeReset() {
    if (!actions.isPending("reset-password")) resetTarget.value = null;
  }

  const roleLabel = (role: string) =>
    ({
      MEMBER: "成员",
      MINISTER: "部长",
      PRESIDENT: "会长",
      ADMIN: "管理员",
    })[role] || role;

  function restoreRouteState(includeSensitiveKeyword: boolean) {
    filters.role = stringRouteQuery(route.query.role);
    filters.status = stringRouteQuery(route.query.status);
    filters.grade = stringRouteQuery(route.query.grade);
    if (includeSensitiveKeyword) {
      filters.keyword = stringRouteQuery(route.query.keyword);
    } else {
      filters.keyword = activeFilters.value.keyword;
    }
  }

  function appendFilters(
    query: URLSearchParams,
    values: { keyword: string; role: string; status: string; grade: string },
  ) {
    if (values.keyword) query.set("keyword", values.keyword);
    if (values.role) query.set("role", values.role);
    if (values.status) query.set("status", values.status);
    if (values.grade) query.set("grade", values.grade);
  }

  function filterKey(values: {
    keyword: string;
    role: string;
    status: string;
    grade: string;
  }) {
    return JSON.stringify(values);
  }

  async function syncRoute(targetPage: number, mode: "push" | "replace") {
    suppressRouteRestore = true;
    try {
      await updateOwnedRouteQuery(
        router,
        route.query,
        routeKeys,
        {
          role: activeFilters.value.role,
          status: activeFilters.value.status,
          grade: activeFilters.value.grade,
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
    actions,
    applyBulkStatus,
    applyFilters,
    bulkOpen,
    bulkResult,
    bulkTargetStatus,
    canEdit,
    clearSelection,
    closeBulk,
    closeEditor,
    closeImport,
    closeReset,
    deleteTarget,
    editorOpen,
    editorTarget,
    filters,
    appliedFilters: computed(() => activeFilters.value),
    hasActiveFilters: computed(() => Object.values(activeFilters.value).some(value => value.trim() !== "")),
    gradeChoices,
    grades,
    importPreview, previewMembers,
    importError,
    importFile,
    importMembers,
    importOpen,
    importResult,
    listError,
    listLoading,
    load,
    retry,
    lockEditorAccountControls,
    members,
    openBulk,
    openCreate,
    openEdit,
    openImport,
    page,
    allFilteredSelected,
    pickFile,
    remove,
    resetPassword,
    resetTarget,
    roleLabel,
    saveMember,
    selected,
    selectableIds,
    setPage,
    toggleMember,
    toggleAll,
    toggleStatus,
    total,
    totalPages,
    user,
  };
}

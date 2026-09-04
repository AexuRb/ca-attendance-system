import { computed, nextTick, onMounted, reactive, ref, watch } from "vue";
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
import {
  bulkStatusPayload,
  selectableMemberIds,
  togglePageSelection,
  type BulkStatusResult,
  type MemberImportResult,
  type MemberPage,
  type MemberStatus,
  type MemberSummary,
} from "./memberDirectory";

export function useMemberDirectoryWorkspace() {
  const { user } = useSession();
  const route = useRoute();
  const router = useRouter();
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
  const editorOpen = ref(false);
  const editorTarget = ref<MemberSummary | null>(null);
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
  const pageAllSelected = computed(
    () =>
      selectableIds.value.length > 0 &&
      selectableIds.value.every((id) => selected.value.has(id)),
  );
  const lockEditorAccountControls = computed(
    () =>
      editorTarget.value?.id === user.value?.id &&
      editorTarget.value?.role === "ADMIN",
  );

  onMounted(async () => {
    restoreRouteState(true);
    const initialPage = positiveRoutePage(route.query.page);
    routeReady = true;
    await syncRoute(initialPage, "replace");
    await Promise.all([load(initialPage), loadGrades()]);
    const intent = stringRouteQuery(route.query.intent);
    if (intent === "new") openCreate();
    if (intent === "import") openImport();
  });

  watch(
    () => routeQuerySignature(route.query, routeKeys),
    () => {
      if (!routeReady || suppressRouteRestore) return;
      restoreRouteState(false);
      void load(positiveRoutePage(route.query.page));
    },
  );

  async function load(target = page.value) {
    const query = new URLSearchParams({
      page: String(target),
      pageSize: String(pageSize),
    });
    if (filters.keyword) query.set("keyword", filters.keyword);
    if (filters.role) query.set("role", filters.role);
    if (filters.status) query.set("status", filters.status);
    if (filters.grade) query.set("grade", filters.grade);
    const value = await listRequest.run(
      (signal) => get<MemberPage>(`/api/users/page?${query}`, { signal }),
      "成员名册加载失败",
    );
    if (!value) return;
    members.value = value.items;
    total.value = value.total;
    page.value = value.page;
  }

  async function applyFilters() {
    await syncRoute(1, "push");
    await load(1);
  }

  async function setPage(target: number) {
    await syncRoute(target, "push");
    await load(target);
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
    const next = new Set(selected.value);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    selected.value = next;
  }

  function togglePage(event: Event) {
    selected.value = togglePageSelection(
      selected.value,
      selectableIds.value,
      (event.target as HTMLInputElement).checked,
    );
  }

  function openBulk(status: MemberStatus) {
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
      selected.value = new Set();
      await load();
    });
  }

  function openImport() {
    importError.value = "";
    importResult.value = null;
    importFile.value = null;
    importOpen.value = true;
  }

  function pickFile(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] || null;
    importResult.value = null;
    importError.value = "";
    if (file) importError.value = excelFileError(file, "成员 Excel 文件");
    importFile.value = importError.value ? null : file;
    if (importError.value) input.value = "";
  }

  async function importMembers() {
    if (!importFile.value) return;
    await actions.run("import", async () => {
      importError.value = "";
      const body = new FormData();
      body.append("file", importFile.value as File);
      const value = await task.run(
        () => post<MemberImportResult>("/api/users/import", body),
        "成员导入完成",
      );
      if (!value) {
        importError.value = task.error.value;
        return;
      }
      importResult.value = value;
      importFile.value = null;
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
    }
  }

  async function syncRoute(targetPage: number, mode: "push" | "replace") {
    suppressRouteRestore = true;
    try {
      await updateOwnedRouteQuery(
        router,
        route.query,
        routeKeys,
        {
          role: filters.role,
          status: filters.status,
          grade: filters.grade,
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
    actions,
    applyBulkStatus,
    applyFilters,
    bulkOpen,
    bulkResult,
    bulkTargetStatus,
    canEdit,
    closeBulk,
    closeEditor,
    closeImport,
    closeReset,
    deleteTarget,
    editorOpen,
    editorTarget,
    filters,
    gradeChoices,
    grades,
    importError,
    importFile,
    importMembers,
    importOpen,
    importResult,
    listError,
    listLoading,
    load,
    lockEditorAccountControls,
    members,
    openBulk,
    openCreate,
    openEdit,
    openImport,
    page,
    pageAllSelected,
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
    togglePage,
    toggleStatus,
    total,
    totalPages,
    user,
  };
}

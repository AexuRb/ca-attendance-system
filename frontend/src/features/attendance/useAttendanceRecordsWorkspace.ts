import { computed, nextTick, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
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
import {
  attendanceActionAccess,
  attendancePageQuery,
  localDateTimeInput,
  manualCheckoutStatus,
  totalAttendancePages,
  type AttendanceActionAccess,
  type AttendanceRecordItem,
  type AttendanceRecordPage,
} from "./attendanceRecords";

export function useAttendanceRecordsWorkspace() {
  const { user } = useSession();
  const route = useRoute();
  const router = useRouter();
  const records = ref<AttendanceRecordItem[]>([]);
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
  const canCreate = computed(() =>
    ["PRESIDENT", "ADMIN"].includes(user.value?.role || ""),
  );
  const canReviewStatus = canCreate;
  const routeKeys = ["from", "to", "status", "page", "keyword"] as const;
  let routeReady = false;
  let suppressRouteRestore = false;

  watch(
    () => form.checkOutTime,
    (value) => {
      form.checkOutStatus = manualCheckoutStatus(form.checkOutStatus, value);
    },
  );

  const totalPages = computed(() =>
    totalAttendancePages(total.value, pageSize),
  );

  onMounted(async () => {
    restoreRouteState(true);
    const initialPage = positiveRoutePage(route.query.page);
    routeReady = true;
    await syncRoute(initialPage, "replace");
    await Promise.all([
      load(initialPage),
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
      restoreRouteState(false);
      void load(positiveRoutePage(route.query.page));
    },
  );

  async function load(target = page.value) {
    if (filterError.value) return;
    const query = attendancePageQuery(filters, target, pageSize);
    const value = await listRequest.run(
      (signal) =>
        get<AttendanceRecordPage>(`/api/attendance/page?${query}`, { signal }),
      "值班记录加载失败",
    );
    if (!value) return;
    records.value = value.items;
    total.value = value.total;
    page.value = value.page;
  }

  async function applyFilters() {
    if (filterError.value) return;
    await syncRoute(1, "push");
    await load(1);
  }

  async function setPage(target: number) {
    await syncRoute(target, "push");
    await load(target);
  }

  function openCreate() {
    editing.value = null;
    selectedMember.value = null;
    Object.assign(form, {
      studentNo: "",
      checkInTime: localDateTimeInput(new Date()),
      checkOutTime: "",
      checkInStatus: "APPROVED",
      checkOutStatus: "APPROVED",
      recomputeSnapshot: false,
      reason: "",
    });
    editorOpen.value = true;
  }

  function openEdit(item: AttendanceRecordItem) {
    if (!actionAccess(item).allowed) return;
    editing.value = item;
    Object.assign(form, {
      studentNo: item.studentNo,
      checkInTime: toInput(item.checkInTime),
      checkOutTime: toInput(item.checkOutTime),
      checkInStatus: item.checkInStatus,
      checkOutStatus: item.checkOutStatus,
      recomputeSnapshot: false,
      reason: "",
    });
    editorOpen.value = true;
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
    if (!actions.isPending("save")) editorOpen.value = false;
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

  async function syncRoute(targetPage: number, mode: "push" | "replace") {
    suppressRouteRestore = true;
    try {
      await updateOwnedRouteQuery(
        router,
        route.query,
        routeKeys,
        {
          from: filters.from,
          to: filters.to,
          status: filters.status,
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

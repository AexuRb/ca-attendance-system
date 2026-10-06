import { computed, onMounted, ref } from "vue";
import { onBeforeRouteLeave } from "vue-router";
import { useUnsavedChanges } from "../../shared/composables/useUnsavedChanges";
import { get, post } from "../../shared/api";
import { useAsyncTask } from "../../shared/composables/useAsyncTask";
import { useLatestRequest } from "../../shared/composables/useLatestRequest";
import { usePendingActions } from "../../shared/composables/usePendingActions";
import { notify } from "../../shared/composables/useToast";
import { buildBulkApprovalRequest } from "./reviewBulkApproval";

interface BulkReviewResult {
  matched: number;
  reviewed: number;
  skipped: number;
  errors: string[];
}

interface PendingReviewQueue {
  items: ReviewRecord[];
  recordCount: number;
  itemCount: number;
  truncated: boolean;
}

export type ReviewPart = "CHECK_IN" | "CHECK_OUT";
type ReviewAction = "APPROVE" | "REJECT";

export interface ReviewRecord {
  id: number;
  studentNo: string;
  name: string;
  dutyDate: string;
  checkInTime?: string;
  checkOutTime?: string;
  checkInStatus: string;
  checkOutStatus: string;
}

export function useAttendanceReviewWorkspace() {
  const records = ref<ReviewRecord[]>([]);
  const pendingRecordCount = ref(0);
  const pendingItemCount = ref(0);
  const queueTruncated = ref(false);
  const task = useAsyncTask();
  const listRequest = useLatestRequest();
  const actions = usePendingActions();
  const { loading, error: loadError } = listRequest;
  const actionInProgress = computed(() => actions.pending.size > 0);
  const interactionLocked = computed(
    () => loading.value || Boolean(loadError.value) || actionInProgress.value,
  );
  const rejectTarget = ref<ReviewRecord | null>(null);
  const rejectPart = ref<ReviewPart>("CHECK_IN");
  const rejectReason = ref("");
  const bulkConfirmOpen = ref(false);
  const selectedIds = ref<number[]>([]);
  const bulkTargetIds = ref<number[] | undefined>();
  const selectedRecords = computed(() => records.value.filter(record => selectedIds.value.includes(record.id)));
  const selectedItemCount = computed(() => selectedRecords.value.reduce((count, record) => count + pendingParts(record), 0));
  const allVisibleSelected = computed(() => records.value.length > 0 && selectedRecords.value.length === records.value.length);
  const bulkIsSelected = computed(() => bulkTargetIds.value !== undefined);
  const bulkRecordCount = ref(0);
  const bulkItemCount = ref(0);
  const bulkErrors = ref<string[]>([]);
  const unsaved = useUnsavedChanges(() => Boolean(rejectTarget.value && rejectReason.value.trim()));
  onBeforeRouteLeave(() => {
    if (actionInProgress.value) return false;
    return new Promise<boolean>(resolve => unsaved.request(() => resolve(true), () => resolve(false)));
  });
  const rejectPending = computed(() =>
    rejectTarget.value
      ? actions.isPending(reviewKey(rejectTarget.value.id, rejectPart.value))
      : false,
  );

  onMounted(load);

  async function load() {
    const value = await listRequest.run(
      (signal) =>
        get<PendingReviewQueue>("/api/attendance/reviews/pending", { signal }),
      "待审核记录加载失败",
    );
    if (!value) return;
    records.value = value.items;
    selectedIds.value = selectedIds.value.filter(id => value.items.some(record => record.id === id && pendingParts(record) > 0));
    pendingRecordCount.value = value.recordCount;
    pendingItemCount.value = value.itemCount;
    queueTruncated.value = value.truncated;
  }

  async function review(
    id: number,
    part: ReviewPart,
    action: ReviewAction,
    reason = "",
  ) {
    if (interactionLocked.value) return false;
    const result = await actions.run(reviewKey(id, part), async () => {
      const reviewed = await task.run(
        () => post(`/api/attendance/${id}/review`, { part, action, reason }),
        action === "APPROVE" ? "审核已通过" : "记录已驳回",
      );
      if (reviewed === undefined) return false;
      await load();
      return true;
    });
    return result === true;
  }

  async function bulkApprove() {
    if (interactionLocked.value || !bulkConfirmOpen.value) return;
    if (bulkTargetIds.value?.length === 0) return;
    await actions.run("bulk", async () => {
      const result = await task.run(() =>
        post<BulkReviewResult>(
          "/api/attendance/reviews/bulk",
          buildBulkApprovalRequest(bulkTargetIds.value),
        ),
      );
      if (!result) return;
      bulkConfirmOpen.value = false;
      if (result.errors.length) {
        bulkErrors.value = result.errors;
        notify(`已通过 ${result.reviewed} 项，${result.skipped} 条未处理`, "warning");
      } else {
        notify(
          `已处理 ${result.matched} 条记录，通过 ${result.reviewed} 项审核`,
          "success",
        );
      }
      await load();
    });
  }

  function pendingParts(record: ReviewRecord) {
    return Number(record.checkInStatus === "PENDING") + Number(record.checkOutStatus === "PENDING");
  }

  function toggleRecord(id: number) {
    if (interactionLocked.value) return;
    selectedIds.value = selectedIds.value.includes(id)
      ? selectedIds.value.filter(selected => selected !== id)
      : [...selectedIds.value, id];
  }

  function toggleVisible() {
    if (interactionLocked.value) return;
    selectedIds.value = allVisibleSelected.value ? [] : records.value.map(record => record.id);
  }

  function openBulk(selected: boolean) {
    if (interactionLocked.value || (selected ? !selectedItemCount.value : !pendingItemCount.value)) return;
    bulkTargetIds.value = selected ? selectedRecords.value.map(record => record.id) : undefined;
    bulkRecordCount.value = selected ? selectedRecords.value.length : pendingRecordCount.value;
    bulkItemCount.value = selected ? selectedItemCount.value : pendingItemCount.value;
    bulkConfirmOpen.value = true;
  }

  function openReject(record: ReviewRecord, part: ReviewPart) {
    if (
      interactionLocked.value ||
      (part === "CHECK_IN" ? record.checkInStatus : record.checkOutStatus) !==
        "PENDING"
    ) return;
    rejectTarget.value = record;
    rejectPart.value = part;
    rejectReason.value = "";
  }

  async function confirmReject() {
    const target = rejectTarget.value;
    if (!target || !rejectReason.value.trim()) return false;
    const succeeded = await review(
      target.id,
      rejectPart.value,
      "REJECT",
      rejectReason.value.trim(),
    );
    if (succeeded) rejectTarget.value = null;
    return succeeded;
  }

  function closeReject() {
    if (!rejectPending.value) unsaved.request(() => { rejectTarget.value = null; });
  }

  function reviewKey(id: number, part: ReviewPart) {
    return `review:${id}:${part}`;
  }

  const clock = (value?: string) => value?.slice(11, 16) || "—";

  return {
    selectedIds,
    selectedItemCount,
    allVisibleSelected,
    toggleRecord,
    toggleVisible,
    openBulk,
    bulkIsSelected,
    bulkRecordCount,
    bulkItemCount,
    unsaved,
    actions,
    actionInProgress,
    bulkApprove,
    bulkConfirmOpen,
    bulkErrors,
    clock,
    closeReject,
    confirmReject,
    load,
    loadError,
    loading,
    interactionLocked,
    openReject,
    pendingItemCount,
    pendingRecordCount,
    queueTruncated,
    records,
    rejectPart,
    rejectPending,
    rejectReason,
    rejectTarget,
    review,
    reviewKey,
  };
}

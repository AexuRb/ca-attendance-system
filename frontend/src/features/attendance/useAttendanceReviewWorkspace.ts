import { computed, onMounted, ref } from "vue";
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
  const rejectTarget = ref<ReviewRecord | null>(null);
  const rejectPart = ref<ReviewPart>("CHECK_IN");
  const rejectReason = ref("");
  const bulkConfirmOpen = ref(false);
  const bulkErrors = ref<string[]>([]);
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
    await actions.run("bulk", async () => {
      const result = await task.run(() =>
        post<BulkReviewResult>(
          "/api/attendance/reviews/bulk",
          buildBulkApprovalRequest(),
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

  function openReject(record: ReviewRecord) {
    rejectTarget.value = record;
    rejectPart.value =
      record.checkInStatus === "PENDING" ? "CHECK_IN" : "CHECK_OUT";
    rejectReason.value = "";
  }

  async function confirmReject() {
    const target = rejectTarget.value;
    if (!target) return;
    const succeeded = await review(
      target.id,
      rejectPart.value,
      "REJECT",
      rejectReason.value,
    );
    if (succeeded) rejectTarget.value = null;
  }

  function closeReject() {
    if (!rejectPending.value) rejectTarget.value = null;
  }

  function reviewKey(id: number, part: ReviewPart) {
    return `review:${id}:${part}`;
  }

  function recordActionPending(id: number) {
    return (
      actions.isPending(reviewKey(id, "CHECK_IN")) ||
      actions.isPending(reviewKey(id, "CHECK_OUT"))
    );
  }

  const clock = (value?: string) => value?.slice(11, 16) || "—";

  return {
    actions,
    bulkApprove,
    bulkConfirmOpen,
    bulkErrors,
    clock,
    closeReject,
    confirmReject,
    load,
    loadError,
    loading,
    openReject,
    pendingItemCount,
    pendingRecordCount,
    queueTruncated,
    recordActionPending,
    records,
    rejectPart,
    rejectPending,
    rejectReason,
    rejectTarget,
    review,
    reviewKey,
  };
}

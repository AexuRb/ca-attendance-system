<template>
  <div class="page-stack reviews-page">
    <PageHeader
      title="签到审核"
    >
      <template #actions
        ><button
          class="button secondary"
          :disabled="actions.isPending('bulk') || !pendingItemCount"
          @click="bulkConfirmOpen = true"
        >
          <CheckCheck />全部通过
        </button></template
      >
    </PageHeader>
    <div class="filter-bar">
      <span class="filter-summary">
        <ListChecks />
        <span>
          {{ pendingItemCount }} 项待审核
          <small v-if="queueTruncated">
            当前显示最近 {{ records.length }} 条，共 {{ pendingRecordCount }} 条记录
          </small>
        </span>
      </span>
      <button class="icon-button" title="刷新" aria-label="刷新" :disabled="loading" @click="load">
        <RefreshCw :class="{ spin: loading }" />
      </button>
    </div>
    <div v-if="loadError" class="inline-alert danger" role="alert">
      <span>{{ loadError }}</span>
      <button class="button secondary small" type="button" data-action="retry-reviews" @click="load">
        重试
      </button>
    </div>
    <LoadingBlock v-if="loading && !records.length" />
    <EmptyState
      v-else-if="!records.length && !loadError"
      title="待审核已清空"
      description="当前没有需要处理的签到或签退。"
    />
    <div v-else class="review-list">
      <article v-for="record in records" :key="record.id" class="review-row">
        <span class="avatar">{{ record.name.slice(0, 1) }}</span>
        <div class="review-person">
          <strong>{{ record.name }}</strong
          ><span>{{ record.studentNo }} · {{ record.dutyDate }}</span>
        </div>
        <div
          class="review-state-actions"
          :aria-label="`${record.name}的签到与签退状态`"
        >
          <ReviewStateAction
            class="review-approve-check-in"
            label="签到"
            :time="clock(record.checkInTime)"
            :status="record.checkInStatus"
            :action-pending="actions.isPending(reviewKey(record.id, 'CHECK_IN'))"
            @approve="review(record.id, 'CHECK_IN', 'APPROVE')"
          />
          <ReviewStateAction
            class="review-approve-check-out"
            label="签退"
            :time="clock(record.checkOutTime)"
            :status="record.checkOutStatus"
            :action-pending="actions.isPending(reviewKey(record.id, 'CHECK_OUT'))"
            @approve="review(record.id, 'CHECK_OUT', 'APPROVE')"
          />
        </div>
        <button
          class="icon-button danger-ghost review-reject"
          title="驳回"
          :aria-label="`驳回${record.name}的记录`"
          :disabled="recordActionPending(record.id)"
          @click="openReject(record)"
        >
          <X />
        </button>
      </article>
    </div>
    <ModalDialog
      :open="Boolean(rejectTarget)"
      title="驳回记录"
      size="sm"
      @close="closeReject"
    >
      <label class="field"
        ><span>驳回部分</span
        ><select v-model="rejectPart">
          <option value="CHECK_IN">签到</option>
          <option value="CHECK_OUT">签退</option>
        </select></label
      >
      <label class="field"
        ><span>驳回原因</span
        ><textarea v-model="rejectReason" rows="3" placeholder="请说明原因" />
      </label>
      <template #footer
        ><button class="button secondary" :disabled="rejectPending" @click="closeReject">
          取消</button
        ><button
          class="button danger"
          :disabled="rejectPending || !rejectReason.trim()"
          @click="confirmReject"
        >
          确认驳回
        </button></template
      >
    </ModalDialog>
    <ConfirmDialog
      :open="bulkConfirmOpen"
      title="通过全部待审核记录"
      :message="`将通过全部 ${pendingItemCount} 项待审核，涉及 ${pendingRecordCount} 条记录。提交时将按数据库中的最新待审核范围处理。`"
      confirm-label="全部通过"
      :pending="actions.isPending('bulk')"
      @cancel="bulkConfirmOpen = false"
      @confirm="bulkApprove"
    />
    <ModalDialog
      :open="bulkErrors.length > 0"
      title="部分记录未处理"
      size="sm"
      @close="bulkErrors = []"
    >
      <p class="confirm-copy">以下记录需要单独检查：</p>
      <ul class="review-error-list">
        <li v-for="message in bulkErrors" :key="message">{{ message }}</li>
      </ul>
      <template #footer>
        <button class="button primary" type="button" @click="bulkErrors = []">
          知道了
        </button>
      </template>
    </ModalDialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { CheckCheck, ListChecks, RefreshCw, X } from "@lucide/vue";
import PageHeader from "../../shared/ui/PageHeader.vue";
import EmptyState from "../../shared/ui/EmptyState.vue";
import LoadingBlock from "../../shared/ui/LoadingBlock.vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
import ReviewStateAction from "./reviews/ReviewStateAction.vue";
import { get, post } from "../../shared/api";
import { useAsyncTask } from "../../shared/composables/useAsyncTask";
import { useLatestRequest } from "../../shared/composables/useLatestRequest";
import { usePendingActions } from "../../shared/composables/usePendingActions";
import { notify } from "../../shared/composables/useToast";
import { buildBulkApprovalRequest } from "../../features/attendance/reviewBulkApproval";

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

type ReviewPart = "CHECK_IN" | "CHECK_OUT";
type ReviewAction = "APPROVE" | "REJECT";

interface ReviewRecord {
  id: number;
  studentNo: string;
  name: string;
  dutyDate: string;
  checkInTime?: string;
  checkOutTime?: string;
  checkInStatus: string;
  checkOutStatus: string;
}

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
      notify(
        `已通过 ${result.reviewed} 项，${result.skipped} 条未处理`,
        "warning",
      );
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
const clock = (v?: string) => v?.slice(11, 16) || "—";
</script>

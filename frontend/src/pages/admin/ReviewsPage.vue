<template>
  <div class="page-stack reviews-page">
    <PageHeader
      title="签到审核"
      description="逐项确认成员提交的签到与签退。"
    >
      <template #actions
        ><button
          class="button secondary"
          :disabled="busy || !pendingItemCount"
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
      <button class="icon-button" title="刷新" aria-label="刷新" @click="load">
        <RefreshCw :class="{ spin: busy }" />
      </button>
    </div>
    <LoadingBlock v-if="busy && !records.length" />
    <EmptyState
      v-else-if="!records.length"
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
        <div class="review-events">
          <div class="review-event">
            <span>签到 {{ clock(record.checkInTime) }}</span
            ><StatusBadge
              :label="reviewLabel(record.checkInStatus)"
              :tone="
                record.checkInStatus === 'PENDING' ? 'warning' : 'success'
              "
            />
          </div>
          <div class="review-event">
            <span>签退 {{ clock(record.checkOutTime) }}</span
            ><StatusBadge
              :label="reviewLabel(record.checkOutStatus)"
              :tone="
                record.checkOutStatus === 'PENDING' ? 'warning' : 'neutral'
              "
            />
          </div>
        </div>
        <div class="row-actions">
          <button
            v-if="record.checkInStatus === 'PENDING'"
            class="button small primary review-approve-check-in"
            @click="review(record.id, 'CHECK_IN', 'APPROVE')"
          >
            通过签到</button
          ><button
            v-if="record.checkOutStatus === 'PENDING'"
            class="button small primary review-approve-check-out"
            @click="review(record.id, 'CHECK_OUT', 'APPROVE')"
          >
            通过签退</button
          ><button
            class="icon-button danger-ghost review-reject"
            title="驳回"
            aria-label="驳回"
            @click="openReject(record)"
          >
            <X />
          </button>
        </div>
      </article>
    </div>
    <ModalDialog
      :open="Boolean(rejectTarget)"
      title="驳回记录"
      size="sm"
      @close="rejectTarget = null"
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
        ><button class="button secondary" @click="rejectTarget = null">
          取消</button
        ><button
          class="button danger"
          :disabled="busy || !rejectReason.trim()"
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
import { onMounted, ref } from "vue";
import { CheckCheck, ListChecks, RefreshCw, X } from "@lucide/vue";
import PageHeader from "../../shared/ui/PageHeader.vue";
import EmptyState from "../../shared/ui/EmptyState.vue";
import LoadingBlock from "../../shared/ui/LoadingBlock.vue";
import StatusBadge from "../../shared/ui/StatusBadge.vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
import { get, post } from "../../shared/api";
import { useAsyncTask } from "../../shared/composables/useAsyncTask";
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
const { busy, run } = useAsyncTask();
const rejectTarget = ref<ReviewRecord | null>(null);
const rejectPart = ref<ReviewPart>("CHECK_IN");
const rejectReason = ref("");
const bulkConfirmOpen = ref(false);
const bulkErrors = ref<string[]>([]);
onMounted(load);
async function load() {
  const value = await run(() =>
    get<PendingReviewQueue>("/api/attendance/reviews/pending"),
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
  const result = await run(
    () => post(`/api/attendance/${id}/review`, { part, action, reason }),
    action === "APPROVE" ? "审核已通过" : "记录已驳回",
  );
  if (result === undefined) return false;
  await load();
  return true;
}
async function bulkApprove() {
  if (busy.value) return;
  bulkConfirmOpen.value = false;
  const result = await run(() =>
    post<BulkReviewResult>(
      "/api/attendance/reviews/bulk",
      buildBulkApprovalRequest(),
    ),
  );
  if (!result) {
    bulkConfirmOpen.value = true;
    return;
  }
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
const clock = (v?: string) => v?.slice(11, 16) || "—";
const reviewLabels: Record<string, string> = {
  PENDING: "待审核",
  APPROVED: "已通过",
  AUTO_APPROVED: "自动通过",
  NOT_SUBMITTED: "未提交",
  REJECTED: "已驳回",
};
const reviewLabel = (v: string) =>
  reviewLabels[v] || v;
</script>

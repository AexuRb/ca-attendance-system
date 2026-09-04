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
        ><select v-model="rejectPart" name="reviewRejectPart">
          <option value="CHECK_IN">签到</option>
          <option value="CHECK_OUT">签退</option>
        </select></label
      >
      <label class="field"
        ><span>驳回原因</span
        ><textarea
          v-model="rejectReason"
          name="reviewRejectReason"
          rows="3"
          autocomplete="off"
          placeholder="请说明原因"
        />
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
import { CheckCheck, ListChecks, RefreshCw, X } from "@lucide/vue";
import PageHeader from "../../shared/ui/PageHeader.vue";
import EmptyState from "../../shared/ui/EmptyState.vue";
import LoadingBlock from "../../shared/ui/LoadingBlock.vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
import ReviewStateAction from "./reviews/ReviewStateAction.vue";
import { useAttendanceReviewWorkspace } from "../../features/attendance/useAttendanceReviewWorkspace";

const {
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
} = useAttendanceReviewWorkspace();
</script>

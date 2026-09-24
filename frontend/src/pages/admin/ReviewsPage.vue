<template>
  <RefinedWorkspaceShell class="daily-workspace review-workspace" title="签到审核" description="核对记录后通过签到与签退申请" section-key="today">
    <aside class="daily-review-summary" aria-label="待审核概况">
      <div class="filter-summary">
        <ListChecks aria-hidden="true" />
        <div>
          <span class="review-summary-label">待处理队列</span>
          <div class="review-summary-metrics">
            <strong class="daily-review-count">{{ (loading || loadError) && !records.length ? "—" : pendingItemCount }}</strong>
            <span>项待审核</span>
            <span class="review-summary-divider" aria-hidden="true">/</span>
            <span>{{ (loading || loadError) && !records.length ? "—" : pendingRecordCount }} 条记录</span>
          </div>
          <small v-if="queueTruncated">当前仅显示最近 {{ records.length }} 条；全部通过将处理队列中所有待审核项</small>
        </div>
      </div>
      <div class="review-summary-actions">
        <button class="icon-button" type="button" title="刷新" aria-label="刷新" :disabled="loading || actionInProgress" @click="load">
          <RefreshCw :class="{ spin: loading }" />
        </button>
        <button
          class="button secondary"
          type="button"
          :disabled="interactionLocked || !pendingItemCount"
          @click="bulkConfirmOpen = true"
        >
          <CheckCheck />全部通过
        </button>
      </div>
    </aside>

    <div v-if="loadError" class="inline-alert danger" role="alert">
      <span>{{ loadError }}{{ records.length ? "；当前列表暂不可审核，请重试刷新" : "" }}</span>
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
        <span class="avatar" aria-hidden="true">{{ record.name.slice(0, 1) }}</span>
        <div class="review-person">
          <strong>{{ record.name }}</strong>
          <span class="review-person__details">
            <span>{{ record.studentNo }}</span>
            <time :datetime="record.dutyDate">{{ record.dutyDate }}</time>
          </span>
        </div>
        <div
          class="review-state-actions"
          role="group"
          :aria-label="`${record.name}的签到与签退状态`"
        >
          <ReviewStateAction
            class="review-approve-check-in"
            label="签到"
            :time="clock(record.checkInTime)"
            :status="record.checkInStatus"
            :action-pending="actions.isPending(reviewKey(record.id, 'CHECK_IN'))"
            :disabled="interactionLocked"
            @approve="review(record.id, 'CHECK_IN', 'APPROVE')"
            @reject="openReject(record, 'CHECK_IN')"
          />
          <ReviewStateAction
            class="review-approve-check-out"
            label="签退"
            :time="clock(record.checkOutTime)"
            :status="record.checkOutStatus"
            :action-pending="actions.isPending(reviewKey(record.id, 'CHECK_OUT'))"
            :disabled="interactionLocked"
            @approve="review(record.id, 'CHECK_OUT', 'APPROVE')"
            @reject="openReject(record, 'CHECK_OUT')"
          />
        </div>
      </article>
    </div>
    <ModalDialog
      :open="Boolean(rejectTarget)"
      :title="`驳回${rejectPart === 'CHECK_IN' ? '签到' : '签退'}`"
      size="sm"
      @close="closeReject"
    >
      <p class="review-reject-context" v-if="rejectTarget">
        <strong>{{ rejectTarget.name }}</strong>
        <span>{{ rejectTarget.studentNo }} · {{ rejectTarget.dutyDate }} · {{ clock(rejectPart === 'CHECK_IN' ? rejectTarget.checkInTime : rejectTarget.checkOutTime) }}</span>
      </p>
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
  </RefinedWorkspaceShell>
</template>

<script setup lang="ts">
import { CheckCheck, ListChecks, RefreshCw } from "@lucide/vue";
import { provide } from "vue";
import RefinedWorkspaceShell from "../../layouts/RefinedWorkspaceShell.vue";
import { memberPresentationKey } from "../../shared/ui/presentation";
import "../../features/members/presentation.css";
import "../../features/attendance/presentation.css";
provide(memberPresentationKey, true);
import EmptyState from "../../shared/ui/EmptyState.vue";
import LoadingBlock from "../../shared/ui/LoadingBlock.vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
import ReviewStateAction from "./reviews/ReviewStateAction.vue";
import { useAttendanceReviewWorkspace } from "../../features/attendance/useAttendanceReviewWorkspace";

const {
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
} = useAttendanceReviewWorkspace();
</script>

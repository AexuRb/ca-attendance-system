<template>
  <ExpandedWorkspaceShell class="overview-reviews review-workspace" :class="{ 'review-has-records': records.length > 0, 'review-queue-truncated': queueTruncated }" :style="{ '--review-row-count': records.length }" current-page="reviews">
    <template #heading>
      <div class="review-page-title">
        <h1>签到审核</h1>
        <p v-if="queueTruncated" class="review-truncated-note">列表仅显示最近的待审核记录，全部通过将处理完整待审核范围。</p>
      </div>
      <div class="review-page-actions">
        <button class="review-approve-all button primary" type="button" :disabled="interactionLocked || !pendingItemCount" @click="requestAllApproval"><span>全部通过</span><Check aria-hidden="true" /></button>
      </div>
    </template>
    <div v-if="loadError" class="inline-alert danger" role="alert"><span>{{ loadError }}{{ records.length ? '；当前列表暂不可审核，请重试' : '' }}</span><button class="button secondary small" type="button" data-action="retry-reviews" @click="refreshSafely">重试</button></div>
    <LoadingBlock v-if="loading && !records.length" />
    <EmptyState v-else-if="!records.length && !loadError" title="待审核已清空" description="当前没有需要处理的签到或签退。" />
    <template v-else-if="records.length">
      <div class="review-columns" aria-hidden="true"><span>成员 / 学号</span><span>值班日期</span><span>签到</span><span>签退</span></div>
      <div ref="listRegion" class="review-list-region">
      <div ref="listViewport" class="review-list" :style="{ height: listHeight === null ? undefined : `${listHeight}px` }" @scroll="updateScrollHint">
      <TransitionGroup name="review-queue" tag="div" class="review-records">
        <article v-for="record in records" :key="record.id" class="review-record" :data-review-id="record.id" tabindex="-1" :aria-label="record.name + ' ' + record.dutyDate + ' 的待审核记录'">
          <div class="review-row">
            <div class="review-person"><strong>{{ record.name }}</strong><span class="review-person-meta">{{ record.studentNo }}</span></div>
            <time class="review-date" :datetime="record.dutyDate">{{ record.dutyDate }}</time>
            <ReviewStateAction class="review-approve-check-in" role="group" :aria-label="record.name + '的签到审核'" label="签到" :time="clock(record.checkInTime)" :status="record.checkInStatus" :action-pending="actions.isPending(reviewKey(record.id, 'CHECK_IN'))" :disabled="interactionLocked" @approve="approveAndContinue(record.id, 'CHECK_IN')" @reject="beginReject(record, 'CHECK_IN')" />
            <ReviewStateAction class="review-approve-check-out" role="group" :aria-label="record.name + '的签退审核'" label="签退" :time="clock(record.checkOutTime)" :status="record.checkOutStatus" :action-pending="actions.isPending(reviewKey(record.id, 'CHECK_OUT'))" :disabled="interactionLocked" @approve="approveAndContinue(record.id, 'CHECK_OUT')" @reject="beginReject(record, 'CHECK_OUT')" />
          </div>
        </article>
      </TransitionGroup>
      </div>
      <p v-if="listOverflow" class="review-scroll-hint"><ChevronUp v-if="atListEnd" aria-hidden="true" /><ChevronDown v-else aria-hidden="true" />{{ atListEnd ? '向上滚动查看更多' : '向下滚动查看更多' }}</p>
      </div>
    </template>
    <div v-else class="review-unavailable-space" />
    <ModalDialog :open="Boolean(rejectTarget)" backdrop-class="review-dialog-backdrop" :title="`驳回${rejectTarget?.name || ''}的${rejectPart === 'CHECK_IN' ? '签到' : '签退'}`" size="sm" @close="cancelReject">
      <p v-if="rejectTarget" class="review-reject-context">{{ rejectTarget.studentNo }} · {{ rejectTarget.dutyDate }} · {{ clock(rejectPart === 'CHECK_IN' ? rejectTarget.checkInTime : rejectTarget.checkOutTime) }}</p>
      <form class="review-reject-form" @submit.prevent="rejectAndContinue"><label class="field"><span>驳回原因</span><textarea v-model="rejectReason" name="reviewRejectReason" rows="3" autocomplete="off" placeholder="请说明原因" autofocus :disabled="rejectPending" /></label></form>
      <template #footer><div class="review-reject-actions"><button class="button secondary" type="button" :disabled="rejectPending" @click="cancelReject">取消</button><button class="button danger" type="button" :disabled="interactionLocked || !rejectReason.trim()" @click="rejectAndContinue">{{ rejectPending ? '正在提交' : '确认驳回' }}</button></div></template>
    </ModalDialog>
    <ConfirmDialog :open="bulkConfirmOpen" backdrop-class="review-dialog-backdrop" title="通过全部待审核记录" :message="`将通过全部 ${bulkItemCount} 项待审核，涉及 ${bulkRecordCount} 条记录，包括当前未展示的记录。提交时将按数据库中的最新待审核范围处理。`" confirm-label="全部通过" :pending="actions.isPending('bulk')" @cancel="bulkConfirmOpen = false" @confirm="bulkApprove" />
    <ConfirmDialog backdrop-class="review-dialog-backdrop" :open="unsaved.confirmOpen.value" title="放弃驳回原因" message="已填写的驳回原因尚未提交，放弃后无法恢复。" cancel-label="继续填写" confirm-label="放弃修改" danger @cancel="unsaved.cancel" @confirm="unsaved.discard" />
    <ModalDialog :open="bulkErrors.length > 0" backdrop-class="review-dialog-backdrop" title="部分记录未处理" size="sm" @close="bulkErrors = []"><p class="confirm-copy">以下记录需要单独检查：</p><ul class="review-error-list"><li v-for="message in bulkErrors" :key="message">{{ message }}</li></ul><template #footer><button data-button-kind="entry" class="button primary" type="button" @click="bulkErrors = []">知道了</button></template></ModalDialog>
  </ExpandedWorkspaceShell>
</template>

<script setup lang="ts">
import { Check, ChevronDown, ChevronUp } from "@lucide/vue";
import { nextTick, onBeforeUnmount, provide, ref, watch } from "vue";
import ExpandedWorkspaceShell from "../../layouts/ExpandedWorkspaceShell.vue";
import { memberPresentationKey } from "../../shared/ui/presentation";
import "../../styles/workspace.css";
import "../../features/today/overview-presentation.css";
import "../../features/attendance/reviews-presentation.css";
provide(memberPresentationKey, true);
import EmptyState from "../../shared/ui/EmptyState.vue";
import LoadingBlock from "../../shared/ui/LoadingBlock.vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
import ReviewStateAction from "./reviews/ReviewStateAction.vue";
import { useAttendanceReviewWorkspace, type ReviewPart, type ReviewRecord } from "../../features/attendance/useAttendanceReviewWorkspace";

const {
  openBulk, bulkConfirmOpen, bulkItemCount, bulkRecordCount, bulkApprove, bulkErrors,
  unsaved,
  actions,
  actionInProgress,
  clock,
  confirmReject,
  load,
  loadError,
  loading,
  interactionLocked,
  openReject,
  pendingItemCount,
  queueTruncated,
  records,
  rejectPart,
  rejectPending,
  rejectReason,
  rejectTarget,
  review,
  reviewKey,
} = useAttendanceReviewWorkspace();

const listRegion = ref<HTMLElement | null>(null);
const listViewport = ref<HTMLElement | null>(null);
const listHeight = ref<number | null>(null);
const listOverflow = ref(false);
const atListEnd = ref(false);
let listObserver: ResizeObserver | undefined;

function updateScrollHint() {
  const list = listViewport.value;
  atListEnd.value = Boolean(list && list.scrollTop + list.clientHeight >= list.scrollHeight - 2);
}

function measureList() {
  const region = listRegion.value;
  const list = listViewport.value;
  if (!region || !list || !region.clientHeight) return;
  const rows = Array.from(list.querySelectorAll<HTMLElement>('.review-record'));
  const contentHeight = rows.reduce((height, row) => height + row.getBoundingClientRect().height, 0);
  const overflow = contentHeight > region.clientHeight + 1;
  const available = Math.max(0, region.clientHeight - (overflow ? 24 : 0));
  const rowHeight = rows[0]?.getBoundingClientRect().height || 0;
  // Desktop rows have a stable height. Keep complete rows visible without
  // shrinking their controls; stacked narrow-screen records scroll normally.
  const uniformRows = window.innerWidth > 1200 && rowHeight > 0 && rows.every(row => Math.abs(row.getBoundingClientRect().height - rowHeight) < 1);
  listHeight.value = overflow && uniformRows && available >= rowHeight
    ? Math.floor(available / rowHeight) * rowHeight
    : available;
  listOverflow.value = overflow;
  void nextTick(updateScrollHint);
}

watch([listRegion, () => records.value], async () => {
  await nextTick();
  listObserver?.disconnect();
  if (typeof ResizeObserver !== 'undefined') {
    listObserver = new ResizeObserver(measureList);
    if (listRegion.value) listObserver.observe(listRegion.value);
    const firstRow = listViewport.value?.querySelector('.review-record');
    if (firstRow) listObserver.observe(firstRow);
  }
  measureList();
}, { flush: 'post', deep: true });
onBeforeUnmount(() => listObserver?.disconnect());

async function focusNextRecord(id: number, index: number) {
  await nextTick();
  const next = records.value.find(record => record.id === id) || records.value[Math.min(index, records.value.length - 1)];
  const target = loadError.value
    ? document.querySelector<HTMLElement>('[data-action="retry-reviews"]')
    : next
    ? document.querySelector<HTMLElement>(`.review-workspace [data-review-id="${next.id}"]`)
    : document.querySelector<HTMLElement>('.review-workspace .overview-content');
  target?.focus({ preventScroll: true });
  target?.scrollIntoView?.({ block: "nearest" });
}

function withDraftGuard(action: () => void) {
  if (interactionLocked.value) return;
  unsaved.request(() => { rejectTarget.value = null; action(); });
}
function refreshSafely() {
  if (loading.value || actionInProgress.value) return;
  unsaved.request(() => { rejectTarget.value = null; void load(); });
}
function requestAllApproval() { withDraftGuard(() => openBulk(false)); }
function beginReject(record: ReviewRecord, part: ReviewPart) {
  withDraftGuard(() => {
    openReject(record, part);
    void nextTick(() => document.querySelector<HTMLTextAreaElement>('[name="reviewRejectReason"]')?.focus());
  });
}
function cancelReject() {
  if (rejectPending.value) return;
  const id = rejectTarget.value?.id;
  const partClass = rejectPart.value === 'CHECK_IN' ? 'review-approve-check-in' : 'review-approve-check-out';
  unsaved.request(() => {
    rejectTarget.value = null;
    void nextTick(() => document.querySelector<HTMLElement>(`[data-review-id="${id}"] .${partClass} .review-state-action__reject`)?.focus());
  });
}
function approveAndContinue(id: number, part: ReviewPart) {
  withDraftGuard(() => { void performApproval(id, part); });
}
async function performApproval(id: number, part: ReviewPart) {
  const index = records.value.findIndex(record => record.id === id);
  if (await review(id, part, 'APPROVE')) await focusNextRecord(id, index);
}
async function rejectAndContinue() {
  const id = rejectTarget.value?.id;
  if (id === undefined) return;
  const index = records.value.findIndex(record => record.id === id);
  if (await confirmReject()) await focusNextRecord(id, index);
}
</script>

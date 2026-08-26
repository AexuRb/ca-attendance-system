<template>
  <button
    class="review-state-action"
    :class="[`is-${meta.tone}`, { 'is-actionable': actionable }]"
    type="button"
    :disabled="!actionable || actionPending"
    :aria-label="ariaLabel"
    :aria-busy="actionPending ? 'true' : undefined"
    @click="$emit('approve')"
  >
    <span class="review-state-action__symbol" aria-hidden="true">
      <LoaderCircle v-if="actionPending" class="spin" />
      <CircleAlert v-else-if="status === 'PENDING'" />
      <Check v-else-if="status === 'APPROVED' || status === 'AUTO_APPROVED'" />
      <X v-else-if="status === 'REJECTED'" />
      <Minus v-else />
    </span>
    <span class="review-state-action__copy">
      <small>{{ label }}</small>
      <strong>{{ time }}</strong>
    </span>
    <span class="review-state-action__status" aria-live="polite">
      {{ actionPending ? "处理中" : meta.label }}
    </span>
  </button>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { Check, CircleAlert, LoaderCircle, Minus, X } from "@lucide/vue";

const props = defineProps<{
  label: string;
  time: string;
  status: string;
  actionPending: boolean;
}>();

defineEmits<{ approve: [] }>();

const statusMeta: Record<string, { label: string; tone: string }> = {
  PENDING: { label: "待审核", tone: "pending" },
  APPROVED: { label: "已通过", tone: "passed" },
  AUTO_APPROVED: { label: "自动通过", tone: "passed" },
  NOT_SUBMITTED: { label: "未提交", tone: "empty" },
  REJECTED: { label: "已驳回", tone: "rejected" },
};

const meta = computed(
  () => statusMeta[props.status] ?? { label: props.status, tone: "neutral" },
);
const actionable = computed(() => props.status === "PENDING");
const ariaLabel = computed(() => {
  const state = props.actionPending ? "处理中" : meta.value.label;
  const action = actionable.value && !props.actionPending ? "，点击通过" : "";
  return `${props.label} ${props.time}，${state}${action}`;
});
</script>

<style scoped>
.review-state-action {
  display: grid;
  width: 100%;
  min-width: 0;
  min-height: 58px;
  grid-template-columns: 36px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  border: 1px solid #d1e4ee;
  border-radius: 7px;
  padding: 8px 11px;
  background: #f9fcfd;
  color: var(--ink-950);
  font: inherit;
  text-align: left;
  transition:
    transform 0.42s cubic-bezier(0.22, 1, 0.36, 1),
    border-color 0.42s cubic-bezier(0.22, 1, 0.36, 1),
    background 0.42s cubic-bezier(0.22, 1, 0.36, 1),
    box-shadow 0.42s cubic-bezier(0.22, 1, 0.36, 1);
}

.review-state-action:disabled {
  cursor: default;
  opacity: 1;
}

.review-state-action.is-actionable {
  cursor: pointer;
}

.review-state-action.is-actionable:hover:not(:disabled) {
  transform: translateY(-2px);
  border-color: #81bdd9;
  background: #eef8fc;
  box-shadow: 0 10px 24px rgba(45, 116, 151, 0.12);
}

.review-state-action.is-actionable:active:not(:disabled) {
  transform: translateY(0) scale(0.985);
}

.review-state-action:focus-visible {
  outline: 3px solid rgba(52, 143, 189, 0.2);
  outline-offset: 2px;
  border-color: var(--blue-500);
}

.review-state-action__symbol {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: 6px;
  background: #fff0d4;
  color: #ad7019;
}

.review-state-action__symbol :deep(svg) {
  width: 16px;
  height: 16px;
  stroke-width: 1.8;
}

.review-state-action__copy {
  min-width: 0;
}

.review-state-action__copy small,
.review-state-action__copy strong {
  display: block;
}

.review-state-action__copy small {
  margin-bottom: 3px;
  color: var(--ink-500);
  font-size: 10px;
  font-weight: 650;
}

.review-state-action__copy strong {
  overflow: hidden;
  color: var(--ink-950);
  font-size: 14px;
  font-variant-numeric: tabular-nums;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.review-state-action__status {
  color: #ad7019;
  font-size: 10px;
  font-weight: 800;
  white-space: nowrap;
}

.review-state-action.is-passed {
  border-color: #d3e9df;
  background: #f3faf7;
}

.review-state-action.is-passed .review-state-action__symbol {
  background: #e1f3eb;
  color: #287d61;
}

.review-state-action.is-passed .review-state-action__status {
  color: #287d61;
}

.review-state-action.is-empty {
  border-style: dashed;
  background: #fbfcfd;
}

.review-state-action.is-empty .review-state-action__symbol,
.review-state-action.is-neutral .review-state-action__symbol {
  background: #edf3f6;
  color: #8da2ad;
}

.review-state-action.is-empty .review-state-action__status,
.review-state-action.is-neutral .review-state-action__status {
  color: #8196a2;
}

.review-state-action.is-rejected {
  border-color: #efdada;
  background: #fff8f8;
}

.review-state-action.is-rejected .review-state-action__symbol {
  background: #fdeaea;
  color: #ae5b5b;
}

.review-state-action.is-rejected .review-state-action__status {
  color: #a95454;
}

@media (max-width: 520px) {
  .review-state-action {
    min-height: 54px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .review-state-action {
    transition-duration: 0.01ms;
  }
}
</style>

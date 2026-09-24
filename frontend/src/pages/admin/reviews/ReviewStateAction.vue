<template>
  <div
    class="review-state-action"
    :class="`is-${meta.tone}`"
    :aria-busy="actionPending ? 'true' : undefined"
  >
    <div class="review-state-action__heading">
      <span>{{ label }}</span>
      <span v-if="actionable" class="review-state-action__status" aria-live="polite">{{ meta.label }}</span>
    </div>
    <strong class="review-state-action__time">{{ time }}</strong>
    <div v-if="actionable" class="review-state-action__actions">
      <button
        class="review-state-action__approve"
        type="button"
        :disabled="disabled || actionPending"
        :aria-label="`通过${label} ${time}`"
        @click="$emit('approve')"
      >
        <LoaderCircle v-if="actionPending" class="spin" aria-hidden="true" />
        {{ actionPending ? "处理中" : "通过" }}
      </button>
      <button
        class="review-state-action__reject"
        type="button"
        :disabled="disabled || actionPending"
        :aria-label="`驳回${label} ${time}`"
        @click="$emit('reject')"
      >
        驳回
      </button>
    </div>
    <div v-else class="review-state-action__result">
      <Check v-if="status === 'APPROVED' || status === 'AUTO_APPROVED'" aria-hidden="true" />
      <X v-else-if="status === 'REJECTED'" aria-hidden="true" />
      <Minus v-else aria-hidden="true" />
      <span class="review-state-action__status">{{ meta.label }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { Check, LoaderCircle, Minus, X } from "@lucide/vue";

const props = defineProps<{
  label: string;
  time: string;
  status: string;
  actionPending: boolean;
  disabled?: boolean;
}>();

defineEmits<{ approve: []; reject: [] }>();

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
</script>

<style scoped>
.review-state-action {
  display: flex;
  flex-direction: column;
  gap: 7px;
  min-width: 0;
  min-height: 94px;
  border: 1px solid var(--mw-line, #d1e4ee);
  border-radius: 8px;
  padding: 11px 12px;
  background: var(--mw-control, #fff);
  color: var(--mw-ink, #20313d);
}

.review-state-action__heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 2px 8px;
  color: var(--mw-muted, #677b88);
  font-size: 12px;
  line-height: 1.5;
}

.review-state-action__status { font-weight: 650; white-space: nowrap; }
.review-state-action__time { font-size: 18px; line-height: 1.35; font-variant-numeric: tabular-nums; }
.review-state-action.is-pending .review-state-action__status { color: var(--mw-accent, #315f8a); }
.review-state-action.is-passed .review-state-action__status { color: #287d61; }
.review-state-action.is-rejected .review-state-action__status { color: #a95454; }
.review-state-action.is-empty { border-style: dashed; background: transparent; }
.review-state-action__actions { display: flex; align-items: center; gap: 6px; margin-top: auto; }
.review-state-action__result { display:flex; align-items:center; gap:5px; margin-top:auto; font-size:12px; }
.review-state-action__result svg { width:14px; height:14px; color:currentColor; }
.review-state-action.is-passed .review-state-action__result { color:#287d61; }
.review-state-action.is-rejected .review-state-action__result { color:#a95454; }
.review-state-action:is(.is-empty,.is-neutral) .review-state-action__result { color:var(--mw-muted,#677b88); }
.review-state-action__actions button {
  min-height: 34px;
  border-radius: 5px;
  padding: 3px 10px;
  font-family: inherit;
  font-size: 12px;
  font-weight: 650;
  line-height: 1.4;
  white-space: nowrap;
  cursor: pointer;
  transition: background-color .18s, border-color .18s, box-shadow .18s, transform .18s;
}
.review-state-action__approve {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 1px solid var(--mw-accent, #315f8a);
  background: var(--mw-accent, #315f8a);
  color: white;
}
.review-state-action__approve svg { width: 13px; height: 13px; }
.review-state-action__approve:hover:not(:disabled) { background: color-mix(in srgb, var(--mw-accent, #315f8a) 82%, black); }
.review-state-action__reject { border: 1px solid var(--mw-line, #d1e4ee); background: transparent; color: var(--mw-ink, #20313d); }
.review-state-action__reject:hover:not(:disabled) { border-color: #ba7777; background: #fff3f3; color: #934b4b; }
.review-state-action__actions button:active:not(:disabled) { transform: translateY(1px); }
.review-state-action__actions button:focus-visible { outline: 2px solid var(--mw-accent, #315f8a); outline-offset: 2px; }
.review-state-action__actions button:disabled { cursor: default; opacity: .55; }

@media (max-width: 700px) {
  .review-state-action__actions button { min-height: 36px; }
}

@media (prefers-reduced-motion: reduce) {
  .review-state-action__actions button { transition-duration: .01ms; }
}
</style>

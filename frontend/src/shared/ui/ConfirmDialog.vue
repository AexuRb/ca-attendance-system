<template>
  <ModalDialog :open="open" :title="title" :description="description" :busy="pending" :error="error" size="sm" @close="cancel">
    <dl v-if="context?.length" class="dialog-summary"><div v-for="row in context" :key="row.label"><dt>{{ row.label }}</dt><dd>{{ row.value }}</dd></div></dl>
    <p class="confirm-copy">{{ message }}</p>
    <ReasonPicker v-if="requireReason && reasonOptions?.length" :key="`${open}-${title}`" v-model="reason"
      :options="reasonOptions" name="confirmReason" :label="reasonLabel" :disabled="pending" />
    <label v-else-if="requireReason" class="field">
      <span>{{ reasonLabel || "操作原因" }}</span>
      <textarea v-model="reason" name="confirmReason" rows="3" :disabled="pending" :placeholder="reasonPlaceholder || '请说明原因'" autofocus />
    </label>
    <template #footer>
      <button
        class="button secondary"
        type="button"
        :disabled="pending"
        @click="cancel"
      >
        {{ cancelLabel || "取消" }}
      </button>
      <button
        class="button"
        :class="danger ? 'danger' : 'primary'"
        type="button"
        :disabled="pending || (requireReason && !reason.trim())"
        @click="confirm"
      >
        {{ pending ? pendingLabel || "处理中..." : confirmLabel || "确认" }}
      </button>
    </template>
  </ModalDialog>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import ModalDialog from "./ModalDialog.vue";
import ReasonPicker from "./ReasonPicker.vue";
const props = defineProps<{
  open: boolean;
  title: string;
  description?: string;
  error?: string;
  context?: { label: string; value: string }[];
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  danger?: boolean;
  requireReason?: boolean;
  reasonLabel?: string;
  reasonPlaceholder?: string;
  reasonOptions?: readonly string[];
  pending?: boolean;
  pendingLabel?: string;
}>();
const emit = defineEmits<{ cancel: []; confirm: [reason: string] }>();
const reason = ref("");
function cancel() {
  if (!props.pending) emit("cancel");
}
function confirm() {
  if (!props.pending && (!props.requireReason || reason.value.trim())) emit("confirm", reason.value.trim());
}
watch(
  () => props.open,
  (value) => {
    if (value) reason.value = "";
  },
);
</script>

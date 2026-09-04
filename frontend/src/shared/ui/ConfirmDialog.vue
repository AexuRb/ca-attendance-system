<template>
  <ModalDialog :open="open" :title="title" size="sm" @close="cancel">
    <p class="confirm-copy">{{ message }}</p>
    <label v-if="requireReason" class="field">
      <span>操作原因</span>
      <textarea v-model="reason" name="confirmReason" rows="3" placeholder="请说明原因" autofocus />
    </label>
    <template #footer>
      <button
        class="button secondary"
        type="button"
        :disabled="pending"
        @click="cancel"
      >
        取消
      </button>
      <button
        class="button"
        :class="danger ? 'danger' : 'primary'"
        type="button"
        :disabled="pending || (requireReason && !reason.trim())"
        @click="$emit('confirm', reason.trim())"
      >
        {{ pending ? pendingLabel || "处理中..." : confirmLabel || "确认" }}
      </button>
    </template>
  </ModalDialog>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import ModalDialog from "./ModalDialog.vue";
const props = defineProps<{
  open: boolean;
  title: string;
  message: string;
  confirmLabel?: string;
  danger?: boolean;
  requireReason?: boolean;
  pending?: boolean;
  pendingLabel?: string;
}>();
const emit = defineEmits<{ cancel: []; confirm: [reason: string] }>();
const reason = ref("");
function cancel() {
  if (!props.pending) emit("cancel");
}
watch(
  () => props.open,
  (value) => {
    if (value) reason.value = "";
  },
);
</script>

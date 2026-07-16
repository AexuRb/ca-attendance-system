<template>
  <ModalDialog :open="open" :title="title" size="sm" @close="$emit('cancel')">
    <p class="confirm-copy">{{ message }}</p>
    <label v-if="requireReason" class="field">
      <span>操作原因</span>
      <textarea v-model="reason" rows="3" placeholder="请说明原因" autofocus />
    </label>
    <template #footer>
      <button class="button secondary" type="button" @click="$emit('cancel')">
        取消
      </button>
      <button
        class="button"
        :class="danger ? 'danger' : 'primary'"
        type="button"
        :disabled="requireReason && !reason.trim()"
        @click="$emit('confirm', reason.trim())"
      >
        {{ confirmLabel || "确认" }}
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
}>();
defineEmits<{ cancel: []; confirm: [reason: string] }>();
const reason = ref("");
watch(
  () => props.open,
  (value) => {
    if (value) reason.value = "";
  },
);
</script>

<template>
  <ModalDialog :error="submitError" :busy="busy"
    :open="open"
    :title="status === 'ACTIVE' ? '批量启用账号' : '批量停用账号'"
    eyebrow="BULK ACTION"
    size="sm"
    @close="close"
  >
    <div class="bulk-member-summary">
      <strong>{{ count }} 人</strong>
      <p>
        {{
          status === "ACTIVE"
            ? "所选账号将恢复登录与签到权限。"
            : "所选账号将立即退出登录并停止使用。"
        }}
      </p>
    </div>
    <ReasonPicker :key="`${open}-${status}`" v-model="reason" name="bulkStatusReason"
      :options="status === 'ACTIVE' ? reasonOptions.memberEnable : reasonOptions.memberDisable" :disabled="busy" />
    <template #footer>
      <button class="button secondary" type="button" :disabled="busy" @click="close">
        取消
      </button>
      <button
        class="button"
        :class="status === 'ACTIVE' ? 'primary' : 'danger'"
        type="button"
        :disabled="busy || !reason"
        @click="$emit('confirm', reason)"
      >
        确认{{ status === "ACTIVE" ? "启用" : "停用" }}
      </button>
    </template>
  </ModalDialog>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import ReasonPicker from "../../shared/ui/ReasonPicker.vue";
import { reasonOptions } from "../../shared/config/reasonOptions";
import type { MemberStatus } from "./memberDirectory";

const props = defineProps<{
  open: boolean;
  count: number;
  status: MemberStatus;
  busy?: boolean; submitError?: string;
}>();
const emit = defineEmits<{
  close: [];
  confirm: [reason: string];
}>();
const reason = ref("");

function close() {
  if (!props.busy) emit("close");
}

watch(
  () => [props.open, props.status] as const,
  ([open]) => {
    if (open) reason.value = "";
  },
  { immediate: true },
);
</script>

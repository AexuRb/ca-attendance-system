<template>
  <ModalDialog
    :open="open"
    :title="status === 'ACTIVE' ? '批量启用账号' : '批量停用账号'"
    eyebrow="BULK ACTION"
    size="sm"
    @close="$emit('close')"
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
    <label class="field">
      <span>操作原因</span>
      <textarea
        v-model.trim="reason"
        rows="3"
        placeholder="用于安全备份和操作日志"
        autofocus
      />
    </label>
    <template #footer>
      <button class="button secondary" type="button" @click="$emit('close')">
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
import type { MemberStatus } from "./memberDirectory";

const props = defineProps<{
  open: boolean;
  count: number;
  status: MemberStatus;
  busy?: boolean;
}>();
defineEmits<{
  close: [];
  confirm: [reason: string];
}>();
const reason = ref("");

watch(
  () => [props.open, props.status] as const,
  ([open, status]) => {
    if (open) reason.value = status === "ACTIVE" ? "批量启用成员账号" : "批量停用成员账号";
  },
  { immediate: true },
);
</script>

<template>
  <ModalDialog
    :open="open"
    title="恢复本机备份"
    size="sm"
    @close="cancel"
  >
    <p class="confirm-copy">
      即将使用 <strong>{{ file?.name }}</strong> 替换当前系统数据。
    </p>
    <div class="inline-alert danger" role="alert">
      恢复前系统会自动创建安全备份；恢复完成后，所有账号都需要重新登录。
    </div>
    <label class="field">
      <span>请输入“恢复”确认操作</span>
      <input
        v-model="confirmation"
        autocomplete="off"
        placeholder="恢复"
        @keyup.enter="confirm"
      />
    </label>
    <template #footer>
      <button
        class="button secondary"
        type="button"
        :disabled="busy"
        @click="cancel"
      >
        取消
      </button>
      <button
        class="button danger"
        type="button"
        :disabled="busy || confirmation.trim() !== requiredText"
        @click="confirm"
      >
        {{ busy ? "正在恢复" : "确认恢复" }}
      </button>
    </template>
  </ModalDialog>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";

const requiredText = "恢复";
const confirmation = ref("");
const props = defineProps<{
  open: boolean;
  file: File | null;
  busy: boolean;
}>();
const emit = defineEmits<{ cancel: []; confirm: [] }>();

watch(
  () => props.open,
  (open) => {
    if (open) confirmation.value = "";
  },
);

function confirm() {
  if (!props.busy && confirmation.value.trim() === requiredText) {
    emit("confirm");
  }
}

function cancel() {
  if (!props.busy) emit("cancel");
}
</script>

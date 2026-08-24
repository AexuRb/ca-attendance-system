<template>
  <ModalDialog
    :open="open"
    title="重置成员密码"
    eyebrow="ACCOUNT SECURITY"
    size="sm"
    @close="close"
  >
    <form
      ref="formElement"
      id="member-password-reset-form"
      class="form-grid"
      novalidate
      @submit.prevent="submit"
    >
      <p class="confirm-copy">
        正在重置 <strong>{{ member?.name }}</strong> 的登录密码。
      </p>
      <p v-if="supportsDefault" class="field-hint reset-password-default">
        留空将恢复为学号后六位：<strong>{{ defaultPassword }}</strong>
      </p>
      <p v-else class="form-error" role="alert">
        该历史账号不符合当前学号规则，必须手动设置新密码。
      </p>
      <label class="field">
        <span>{{ supportsDefault ? "自定义新密码（可选）" : "新密码" }}</span>
        <input
          v-model="newPassword"
          name="newPassword"
          type="password"
          autocomplete="new-password"
          minlength="6"
          maxlength="64"
          :required="!supportsDefault"
          :aria-invalid="Boolean(error)"
          :aria-describedby="error ? 'reset-password-error' : undefined"
        />
        <small v-if="error" id="reset-password-error" class="field-error" role="alert">
          {{ error }}
        </small>
      </label>
    </form>
    <template #footer>
      <button class="button secondary" type="button" :disabled="busy" @click="close">
        取消
      </button>
      <button
        class="button primary"
        type="submit"
        form="member-password-reset-form"
        :disabled="busy"
      >
        {{ busy ? "正在重置" : "确认重置" }}
      </button>
    </template>
  </ModalDialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import { focusFirstInvalid, validatePassword } from "../../shared/validation/userInput";
import type { MemberSummary } from "./memberDirectory";

const props = defineProps<{
  open: boolean;
  member: MemberSummary | null;
  busy?: boolean;
}>();
const emit = defineEmits<{ close: []; confirm: [newPassword: string] }>();
const formElement = ref<HTMLFormElement | null>(null);
const newPassword = ref("");
const error = ref("");
const supportsDefault = computed(() => /^\d{6,32}$/.test(props.member?.studentNo || ""));
const defaultPassword = computed(() => props.member?.studentNo.slice(-6) || "");

function close() {
  if (!props.busy) emit("close");
}

watch(
  () => props.open,
  (open) => {
    if (!open) return;
    newPassword.value = "";
    error.value = "";
  },
);

function submit() {
  error.value = "";
  if (newPassword.value || !supportsDefault.value) {
    error.value = validatePassword(newPassword.value);
  }
  if (error.value) {
    focusFirstInvalid(formElement.value, { newPassword: error.value });
    return;
  }
  emit("confirm", newPassword.value);
}
</script>

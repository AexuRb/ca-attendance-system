<template>
  <ModalDialog
    :open="open"
    title="修改登录密码"
    eyebrow="ACCOUNT SECURITY"
    size="sm"
    @close="close"
  >
    <form
      id="profile-password-form"
      ref="formElement"
      class="form-grid"
      novalidate
      @submit.prevent="submit"
    >
      <label class="field">
        <span>当前密码</span>
        <input
          v-model="form.oldPassword"
          type="password"
          name="oldPassword"
          autocomplete="current-password"
          required
          maxlength="128"
          :aria-invalid="Boolean(fieldErrors.oldPassword)"
        />
        <small v-if="fieldErrors.oldPassword" class="field-error" role="alert">{{ fieldErrors.oldPassword }}</small>
      </label>
      <label class="field">
        <span>新密码</span>
        <input
          v-model="form.newPassword"
          type="password"
          name="newPassword"
          autocomplete="new-password"
          minlength="6"
          maxlength="64"
          required
          :aria-invalid="Boolean(fieldErrors.newPassword)"
        />
        <small v-if="fieldErrors.newPassword" class="field-error" role="alert">{{ fieldErrors.newPassword }}</small>
      </label>
      <label class="field">
        <span>确认新密码</span>
        <input
          v-model="confirmation"
          type="password"
          name="confirmation"
          autocomplete="new-password"
          minlength="6"
          maxlength="64"
          required
          :aria-invalid="Boolean(fieldErrors.confirmation)"
        />
        <small v-if="fieldErrors.confirmation" class="field-error" role="alert">{{ fieldErrors.confirmation }}</small>
      </label>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    </form>
    <template #footer>
      <button class="button secondary" type="button" @click="close">取消</button>
      <button
        class="button primary"
        type="submit"
        form="profile-password-form"
        :disabled="busy"
      >
        保存新密码
      </button>
    </template>
  </ModalDialog>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from "vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import { post } from "../../shared/api";
import {
  focusFirstInvalid,
  validatePassword,
  type InputErrors,
} from "../../shared/validation/userInput";

const props = defineProps<{ open: boolean }>();
const emit = defineEmits<{ close: []; changed: [] }>();
const form = reactive({ oldPassword: "", newPassword: "" });
const confirmation = ref("");
const error = ref("");
const busy = ref(false);
const formElement = ref<HTMLFormElement | null>(null);
const fieldErrors = reactive<InputErrors>({});

watch(
  () => props.open,
  (open) => {
    if (!open) return;
    form.oldPassword = "";
    form.newPassword = "";
    confirmation.value = "";
    error.value = "";
    Object.keys(fieldErrors).forEach((key) => delete fieldErrors[key]);
  },
);

function close() {
  if (!busy.value) emit("close");
}

async function submit() {
  if (busy.value) return;
  error.value = "";
  const nextErrors: InputErrors = {};
  if (!form.oldPassword) nextErrors.oldPassword = "请输入当前密码";
  const passwordError = validatePassword(form.newPassword);
  if (passwordError) nextErrors.newPassword = passwordError;
  if (form.newPassword !== confirmation.value) {
    nextErrors.confirmation = "两次输入的新密码不一致";
  }
  Object.keys(fieldErrors).forEach((key) => delete fieldErrors[key]);
  Object.assign(fieldErrors, nextErrors);
  if (Object.keys(fieldErrors).length) {
    focusFirstInvalid(formElement.value, fieldErrors);
    return;
  }
  busy.value = true;
  error.value = "";
  try {
    await post("/api/auth/change-password", form);
    emit("changed");
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : "密码更新失败";
  } finally {
    busy.value = false;
  }
}
</script>

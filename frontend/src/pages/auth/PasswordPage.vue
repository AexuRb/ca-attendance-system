<template>
  <AuthLayout>
    <form ref="formElement" class="auth-form" novalidate @submit.prevent="submit">
      <div class="auth-heading">
        <p class="eyebrow">ACCOUNT SECURITY</p>
        <h2>设置新密码</h2>
        <p>首次登录需要更换初始密码，完成后会重新登录。</p>
      </div>
      <label class="field"
        ><span>原密码</span
        ><input
          v-model="form.oldPassword"
          name="oldPassword"
          type="password"
          autocomplete="current-password"
          required
          maxlength="128"
          :aria-invalid="Boolean(fieldErrors.oldPassword)"
      /><small v-if="fieldErrors.oldPassword" class="field-error" role="alert">{{ fieldErrors.oldPassword }}</small></label>
      <label class="field"
        ><span>新密码</span
        ><input
          v-model="form.newPassword"
          name="newPassword"
          type="password"
          autocomplete="new-password"
          minlength="6"
          maxlength="64"
          required
          :aria-invalid="Boolean(fieldErrors.newPassword)"
      /><small v-if="fieldErrors.newPassword" class="field-error" role="alert">{{ fieldErrors.newPassword }}</small></label>
      <label class="field"
        ><span>确认新密码</span
        ><input
          v-model="confirmation"
          name="confirmation"
          type="password"
          autocomplete="new-password"
          minlength="6"
          maxlength="64"
          required
          :aria-invalid="Boolean(fieldErrors.confirmation)"
      /><small v-if="fieldErrors.confirmation" class="field-error" role="alert">{{ fieldErrors.confirmation }}</small></label>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <button class="button primary auth-submit" type="submit" :disabled="busy">
        <span>{{ busy ? "正在更新" : "更新密码" }}</span>
        <span class="auth-submit-icon" aria-hidden="true">
          <LoaderCircle v-if="busy" class="spin" />
          <KeyRound v-else />
        </span>
      </button>
    </form>
  </AuthLayout>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { KeyRound, LoaderCircle } from "@lucide/vue";
import AuthLayout from "../../layouts/AuthLayout.vue";
import { post, setToken } from "../../shared/api";
import { useSession } from "../../app/session";
import {
  focusFirstInvalid,
  validatePassword,
  type InputErrors,
} from "../../shared/validation/userInput";
const router = useRouter();
const { state } = useSession();
const form = reactive({ oldPassword: "", newPassword: "" });
const confirmation = ref("");
const busy = ref(false);
const error = ref("");
const formElement = ref<HTMLFormElement | null>(null);
const fieldErrors = reactive<InputErrors>({});
async function submit() {
  error.value = "";
  const nextErrors: InputErrors = {};
  if (!form.oldPassword) nextErrors.oldPassword = "请输入原密码";
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
    setToken("");
    state.user = null;
    await router.replace({ name: "login" });
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : "密码更新失败";
  } finally {
    busy.value = false;
  }
}
</script>

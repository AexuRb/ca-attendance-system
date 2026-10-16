<template>
  <AuthLayout :entry-mode="state.access.mode" :class="{ 'password-change-page': voluntaryChange }">
    <template v-if="voluntaryChange" #header-actions>
      <button class="button secondary password-return" data-button-kind="entry" type="button" :disabled="busy" @click="router.push({ name: 'profile' })">返回个人资料</button>
    </template>
    <section class="password-stage" :aria-label="voluntaryChange ? '修改密码' : '设置新密码'">
      <div class="password-brand">
        <img :src="logoUrl" data-shared-emblem="large" width="890" height="892" alt="计算机协会会徽" />
        <strong>计算机协会</strong>
        <span>值班管理系统</span>
      </div>
    <form ref="formElement" class="auth-form" novalidate @submit.prevent="submit">
      <div class="auth-heading">
        <h2>{{ voluntaryChange ? "修改密码" : "设置新密码" }}</h2>
        <p>{{ voluntaryChange ? "输入原密码，设置新的登录密码" : "首次登录需修改初始密码" }}</p>
      </div>
      <div class="field">
        <label for="password-current">原密码</label>
        <div class="input-with-icon">
          <LockKeyhole aria-hidden="true" />
          <input
            id="password-current"
            ref="oldPasswordInput"
            v-model="form.oldPassword"
            name="oldPassword"
            :type="showOldPassword ? 'text' : 'password'"
            autocomplete="current-password"
            required
            maxlength="128"
            placeholder="输入原密码"
            :aria-invalid="Boolean(fieldErrors.oldPassword)"
          />
          <button data-button-kind="icon" class="input-icon-button" type="button" :aria-label="showOldPassword ? '隐藏原密码' : '显示原密码'" @click="showOldPassword = !showOldPassword">
            <EyeOff v-if="showOldPassword" /><Eye v-else />
          </button>
        </div>
        <small v-if="fieldErrors.oldPassword" class="field-error" role="alert">{{ fieldErrors.oldPassword }}</small>
        <small v-else aria-hidden="true"></small>
      </div>
      <div class="field">
        <label for="password-new">新密码</label>
        <div class="input-with-icon">
          <KeyRound aria-hidden="true" />
          <input
            id="password-new"
            v-model="form.newPassword"
            name="newPassword"
            :type="showNewPassword ? 'text' : 'password'"
            autocomplete="new-password"
            minlength="6"
            maxlength="64"
            required
            placeholder="设置新密码"
            :aria-invalid="Boolean(fieldErrors.newPassword)"
          />
          <button data-button-kind="icon" class="input-icon-button" type="button" :aria-label="showNewPassword ? '隐藏新密码' : '显示新密码'" @click="showNewPassword = !showNewPassword">
            <EyeOff v-if="showNewPassword" /><Eye v-else />
          </button>
        </div>
        <small v-if="fieldErrors.newPassword" class="field-error" role="alert">{{ fieldErrors.newPassword }}</small>
        <small v-else class="auth-field-hint">6–64 个字符</small>
      </div>
      <div class="field">
        <label for="password-confirmation">确认新密码</label>
        <div class="input-with-icon">
          <ShieldCheck aria-hidden="true" />
          <input
            id="password-confirmation"
            v-model="confirmation"
            name="confirmation"
            :type="showConfirmation ? 'text' : 'password'"
            autocomplete="new-password"
            minlength="6"
            maxlength="64"
            required
            placeholder="再次输入新密码"
            :aria-invalid="Boolean(fieldErrors.confirmation)"
          />
          <button data-button-kind="icon" class="input-icon-button" type="button" :aria-label="showConfirmation ? '隐藏确认新密码' : '显示确认新密码'" @click="showConfirmation = !showConfirmation">
            <EyeOff v-if="showConfirmation" /><Eye v-else />
          </button>
        </div>
        <small v-if="fieldErrors.confirmation" class="field-error" role="alert">{{ fieldErrors.confirmation }}</small>
        <small v-else aria-hidden="true"></small>
      </div>
      <div class="auth-feedback-slot">
        <Transition name="auth-feedback-swap">
          <p v-if="error" key="error" class="form-error" role="alert">{{ error }}</p>
          <p v-else key="hint" class="auth-action-hint">修改后需重新登录</p>
        </Transition>
      </div>
      <button class="button primary auth-submit" type="submit" :disabled="busy">
        <span>{{ busy ? "正在保存" : "保存新密码" }}</span>
        <span class="auth-submit-icon" aria-hidden="true">
          <LoaderCircle v-if="busy" class="spin" />
          <KeyRound v-else />
        </span>
      </button>
    </form>
    </section>
  </AuthLayout>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { Eye, EyeOff, KeyRound, LoaderCircle, LockKeyhole, ShieldCheck } from "@lucide/vue";
import AuthLayout from "../../layouts/AuthLayout.vue";
import { post, setToken } from "../../shared/api";
import { useSession } from "../../app/session";
import {
  focusFirstInvalid,
  validatePassword,
  type InputErrors,
} from "../../shared/validation/userInput";
const logoUrl = "/brand/ca-logo-transparent.svg";
const router = useRouter();
const { state } = useSession();
const voluntaryChange = computed(() => Boolean(state.user && !state.user.mustChangePassword));
const form = reactive({ oldPassword: "", newPassword: "" });
const confirmation = ref("");
const oldPasswordInput = ref<HTMLInputElement | null>(null);
const showOldPassword = ref(false);
const showNewPassword = ref(false);
const showConfirmation = ref(false);
const busy = ref(false);
const error = ref("");
const formElement = ref<HTMLFormElement | null>(null);
const fieldErrors = reactive<InputErrors>({});
onMounted(async () => {
  await nextTick();
  oldPasswordInput.value?.focus();
});
async function submit() {
  if (busy.value) return;
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
    await router.replace({ name: "login", query: { reason: "password-changed" } });
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : "密码更新失败";
  } finally {
    busy.value = false;
  }
}
</script>

<style src="../../features/auth/password-presentation.css"></style>

<template>
  <AuthLayout>
    <form
      ref="formElement"
      class="auth-form auth-form--setup"
      novalidate
      @submit.prevent="submit"
    >
      <div class="auth-heading">
        <h2>初始化本机</h2>
        <p>创建首个管理员账号，完成后即可进入后台</p>
      </div>
      <div class="field">
        <label for="setup-account">管理员账号</label>
        <div class="input-with-icon">
          <UserRound aria-hidden="true" />
          <input
            id="setup-account"
            ref="accountInput"
            v-model.trim="form.account"
            name="account"
            autocomplete="username"
            required
            inputmode="numeric"
            pattern="[0-9]{6,32}"
            minlength="6"
            maxlength="32"
            placeholder="6 至 32 位数字"
            :aria-invalid="Boolean(errors.account)"
          />
        </div>
        <small v-if="errors.account" class="field-error" role="alert">
          {{ errors.account }}
        </small>
      </div>
      <div class="field">
        <label for="setup-name">姓名</label>
        <div class="input-with-icon">
          <IdCard aria-hidden="true" />
          <input
            id="setup-name"
            v-model.trim="form.name"
            name="name"
            autocomplete="name"
            required
            maxlength="64"
            placeholder="管理员姓名"
            :aria-invalid="Boolean(errors.name)"
          />
        </div>
        <small v-if="errors.name" class="field-error" role="alert">
          {{ errors.name }}
        </small>
      </div>
      <div class="field">
        <label for="setup-password">初始密码</label>
        <div class="input-with-icon">
          <LockKeyhole aria-hidden="true" />
          <input
            id="setup-password"
            v-model="form.password"
            name="password"
            :type="showPassword ? 'text' : 'password'"
            autocomplete="new-password"
            required
            minlength="6"
            maxlength="64"
            placeholder="至少 6 位"
            :aria-invalid="Boolean(errors.password)"
          />
          <button
            class="input-icon-button"
            type="button"
            :aria-label="showPassword ? '隐藏初始密码' : '显示初始密码'"
            @click="showPassword = !showPassword"
          >
            <EyeOff v-if="showPassword" /><Eye v-else />
          </button>
        </div>
        <small v-if="errors.password" class="field-error" role="alert">
          {{ errors.password }}
        </small>
      </div>
      <div class="field">
        <label for="setup-confirmation">确认密码</label>
        <div class="input-with-icon">
          <ShieldCheck aria-hidden="true" />
          <input
            id="setup-confirmation"
            v-model="confirmation"
            name="confirmation"
            :type="showConfirmation ? 'text' : 'password'"
            autocomplete="new-password"
            required
            minlength="6"
            maxlength="64"
            placeholder="再次输入密码"
            :aria-invalid="Boolean(errors.confirmation)"
          />
          <button
            class="input-icon-button"
            type="button"
            :aria-label="showConfirmation ? '隐藏确认密码' : '显示确认密码'"
            @click="showConfirmation = !showConfirmation"
          >
            <EyeOff v-if="showConfirmation" /><Eye v-else />
          </button>
        </div>
        <small v-if="errors.confirmation" class="field-error" role="alert">
          {{ errors.confirmation }}
        </small>
      </div>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <button class="button primary auth-submit" type="submit" :disabled="busy">
        <span>{{ busy ? "正在初始化" : "创建本地系统" }}</span>
        <span class="auth-submit-icon" aria-hidden="true">
          <LoaderCircle v-if="busy" class="spin" />
          <DatabaseZap v-else />
        </span>
      </button>
    </form>
  </AuthLayout>
</template>

<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import {
  DatabaseZap,
  Eye,
  EyeOff,
  IdCard,
  LoaderCircle,
  LockKeyhole,
  ShieldCheck,
  UserRound,
} from "@lucide/vue";
import AuthLayout from "../../layouts/AuthLayout.vue";
import { useSession } from "../../app/session";
import {
  focusFirstInvalid,
  validateMemberInput,
  validatePassword,
  type InputErrors,
} from "../../shared/validation/userInput";
const { initialize } = useSession();
const router = useRouter();
const form = reactive({ account: "", name: "", password: "" });
const confirmation = ref("");
const showPassword = ref(false);
const showConfirmation = ref(false);
const error = ref("");
const busy = ref(false);
const formElement = ref<HTMLFormElement | null>(null);
const accountInput = ref<HTMLInputElement | null>(null);
const errors = reactive<InputErrors>({});

onMounted(async () => {
  await nextTick();
  accountInput.value?.focus();
});

async function submit() {
  if (busy.value) return;
  error.value = "";
  const memberErrors = validateMemberInput({
    studentNo: form.account,
    name: form.name,
  });
  const nextErrors: InputErrors = { ...memberErrors };
  if (nextErrors.studentNo) {
    nextErrors.account = nextErrors.studentNo;
    delete nextErrors.studentNo;
  }
  const passwordError = validatePassword(form.password);
  if (passwordError) nextErrors.password = passwordError;
  if (form.password !== confirmation.value) {
    nextErrors.confirmation = "两次输入的密码不一致";
  }
  Object.keys(errors).forEach((key) => delete errors[key]);
  Object.assign(errors, nextErrors);
  if (Object.keys(errors).length) {
    focusFirstInvalid(formElement.value, errors);
    return;
  }
  busy.value = true;
  error.value = "";
  try {
    await initialize(form.account, form.name, form.password);
    await router.replace({ name: "today" });
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : "初始化失败";
  } finally {
    busy.value = false;
  }
}
</script>

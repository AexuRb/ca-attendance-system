<template>
  <main class="setup-page" data-auth-page="setup">
    <div class="setup-context" aria-hidden="true"></div>
    <section class="setup-stage" aria-label="初始化本机">
      <div class="setup-brand">
        <img :src="logoUrl" data-shared-emblem="large" width="890" height="892" alt="计算机协会会徽" />
        <strong>计算机协会</strong>
        <span>值班管理系统</span>
      </div>
    <form
      ref="formElement"
      class="setup-form"
      novalidate
      @submit.prevent="submit"
    >
      <div class="setup-heading">
        <h2>初始化本机</h2>
        <p>创建管理员账号</p>
      </div>
      <div class="setup-field">
        <label for="setup-account">管理员账号</label>
        <div class="setup-input">
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
            placeholder="6–32 位数字"
            :aria-invalid="Boolean(errors.account)"
          />
        </div>
        <small v-if="errors.account" class="setup-field-error" role="alert">{{ errors.account }}</small>
        <small v-else aria-hidden="true"></small>
      </div>
      <div class="setup-field">
        <label for="setup-name">姓名</label>
        <div class="setup-input">
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
        <small v-if="errors.name" class="setup-field-error" role="alert">{{ errors.name }}</small>
        <small v-else aria-hidden="true"></small>
      </div>
      <div class="setup-field">
        <label for="setup-password">初始密码</label>
        <div class="setup-input">
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
            placeholder="设置密码"
            :aria-invalid="Boolean(errors.password)"
          />
          <button data-button-kind="icon"
            class="setup-password-toggle"
            type="button"
            :aria-label="showPassword ? '隐藏初始密码' : '显示初始密码'"
            @click="showPassword = !showPassword"
          >
            <EyeOff v-if="showPassword" /><Eye v-else />
          </button>
        </div>
        <small v-if="errors.password" class="setup-field-error" role="alert">{{ errors.password }}</small>
        <small v-else>6–64 个字符</small>
      </div>
      <div class="setup-field">
        <label for="setup-confirmation">确认密码</label>
        <div class="setup-input">
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
          <button data-button-kind="icon"
            class="setup-password-toggle"
            type="button"
            :aria-label="showConfirmation ? '隐藏确认密码' : '显示确认密码'"
            @click="showConfirmation = !showConfirmation"
          >
            <EyeOff v-if="showConfirmation" /><Eye v-else />
          </button>
        </div>
        <small v-if="errors.confirmation" class="setup-field-error" role="alert">{{ errors.confirmation }}</small>
        <small v-else aria-hidden="true"></small>
      </div>
      <div class="setup-feedback">
        <Transition name="setup-feedback-swap">
          <p v-if="error" key="error" class="setup-form-error" role="alert">{{ error }}</p>
        </Transition>
      </div>
      <button data-button-kind="action" class="setup-submit" type="submit" :disabled="busy">
        <span>{{ busy ? "正在初始化" : "完成初始化" }}</span>
        <span class="setup-submit-icon" aria-hidden="true">
          <LoaderCircle v-if="busy" class="spin" />
          <DatabaseZap v-else />
        </span>
      </button>
    </form>
    </section>
    <footer class="setup-footer">计算机协会 · 本机运行</footer>
  </main>
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
import { useSession } from "../../app/session";
import {
  focusFirstInvalid,
  validateMemberInput,
  validatePassword,
  type InputErrors,
} from "../../shared/validation/userInput";
const { initialize } = useSession();
const logoUrl = "/brand/ca-logo-transparent.svg";
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
  // Rename the account key without moving it behind the name field.
  const nextErrors: InputErrors = Object.fromEntries(
    Object.entries(memberErrors).map(([key, message]) => [
      key === "studentNo" ? "account" : key,
      key === "studentNo" ? message.replace(/^学号必须/, "管理员账号须") : message,
    ]),
  );
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

<style src="../../features/auth/setup-presentation.css"></style>

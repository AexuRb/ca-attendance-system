<template>
  <AuthLayout>
    <form class="auth-form" @submit.prevent="submit">
      <div class="auth-heading">
        <h2>登录后台</h2>
        <p v-if="state.access.mode === 'REMOTE_ADMIN'">
          远程入口仅允许会长和管理员
        </p>
      </div>
      <p v-if="sessionNotice" class="auth-session-notice" role="status">
        {{ sessionNotice }}
      </p>
      <div class="field">
        <label for="login-account">账号</label>
        <div class="input-with-icon">
          <UserRound aria-hidden="true" /><input
            id="login-account"
            ref="accountInput"
            v-model.trim="form.studentNo"
            name="username"
            autocomplete="username"
            required
            placeholder="学号或管理员账号"
          />
        </div>
      </div>
      <div class="field">
        <label for="login-password">密码</label>
        <div class="input-with-icon">
          <LockKeyhole aria-hidden="true" /><input
            id="login-password"
            v-model="form.password"
            name="password"
            :type="showPassword ? 'text' : 'password'"
            autocomplete="current-password"
            required
            placeholder="请输入密码"
          /><button
            class="input-icon-button"
            type="button"
            :aria-label="showPassword ? '隐藏密码' : '显示密码'"
            @click="showPassword = !showPassword"
          >
            <EyeOff v-if="showPassword" /><Eye v-else />
          </button>
        </div>
      </div>
      <div class="auth-form-options">
        <label class="check-row"
          ><input v-model="remember" name="rememberAccount" type="checkbox" /><span>{{
            rememberLabel
          }}</span></label
        >
        <RouterLink
          v-if="state.access.kioskAvailable"
          class="auth-back"
          to="/"
          ><ArrowLeft aria-hidden="true" /><span>返回签到台</span></RouterLink
        >
      </div>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <button class="button primary auth-submit" type="submit" :disabled="busy">
        <span>{{ busy ? "正在验证" : "进入后台" }}</span>
        <span class="auth-submit-icon" aria-hidden="true">
          <LoaderCircle v-if="busy" class="spin" />
          <LogIn v-else />
        </span>
      </button>
    </form>
  </AuthLayout>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter, RouterLink } from "vue-router";
import {
  ArrowLeft,
  Eye,
  EyeOff,
  LoaderCircle,
  LockKeyhole,
  LogIn,
  UserRound,
} from "@lucide/vue";
import AuthLayout from "../../layouts/AuthLayout.vue";
import { useSession } from "../../app/session";
import { safeLoginNext } from "../../app/router";
import {
  clearRememberedLogin,
  isDesktopCredentialMode,
  loadRememberedLogin,
  saveRememberedLogin,
} from "../../features/auth/rememberedCredentials";

const { state, login } = useSession();
const router = useRouter();
const route = useRoute();
const form = reactive({ studentNo: "", password: "" });
const remember = ref(false);
const showPassword = ref(false);
const busy = ref(false);
const error = ref("");
const accountInput = ref<HTMLInputElement>();
const rememberLabel = isDesktopCredentialMode()
  ? "记住账号和密码"
  : "记住账号";
const sessionNotice = computed(() => {
  if (route.query.reason === "restored") return "数据已恢复，请重新登录";
  if (route.query.reason === "expired") return "登录状态已失效，请重新登录";
  if (route.query.reason === "password-changed") return "密码修改成功，请使用新密码登录";
  return "";
});

onMounted(async () => {
  const saved = await loadRememberedLogin();
  if (saved) {
    form.studentNo = saved.studentNo;
    form.password = saved.password;
    remember.value = true;
  }
  await nextTick();
  if (!form.studentNo) accountInput.value?.focus();
});

async function submit() {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  try {
    const user = await login(form.studentNo, form.password);
    try {
      if (remember.value) await saveRememberedLogin({ ...form });
      else await clearRememberedLogin();
    } catch {
      await clearRememberedLogin().catch(() => undefined);
    }
    const next = safeLoginNext(route.query.next);
    if (user.mustChangePassword) await router.replace({ name: "password" });
    else if (next) await router.replace(next);
    else
      await router.replace({
        name: user.role === "MEMBER" ? "profile" : "today",
      });
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : "登录失败";
  } finally {
    busy.value = false;
  }
}
</script>

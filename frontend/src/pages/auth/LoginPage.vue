<template>
  <AuthLayout>
    <form class="auth-form" @submit.prevent="submit">
      <div class="auth-heading">
        <p class="eyebrow">
          {{
            state.access.mode === "REMOTE_ADMIN"
              ? "REMOTE ACCESS"
              : "ADMIN ACCESS"
          }}
        </p>
        <h2>登录后台</h2>
        <p>
          {{
            state.access.mode === "REMOTE_ADMIN"
              ? "远程入口仅允许会长和管理员。"
              : "使用协会账号进入本地管理后台。"
          }}
        </p>
      </div>
      <label class="field">
        <span>账号</span>
        <div class="input-with-icon">
          <UserRound aria-hidden="true" /><input
            ref="accountInput"
            v-model.trim="form.studentNo"
            name="username"
            autocomplete="username"
            required
            placeholder="学号或管理员账号"
          />
        </div>
      </label>
      <label class="field">
        <span>密码</span>
        <div class="input-with-icon">
          <LockKeyhole aria-hidden="true" /><input
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
      </label>
      <label class="check-row"
        ><input v-model="remember" type="checkbox" /><span
          >记住账号和密码</span
        ></label
      >
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <button class="button primary auth-submit" type="submit" :disabled="busy">
        <LoaderCircle v-if="busy" class="spin" /><LogIn v-else />{{
          busy ? "正在验证" : "进入后台"
        }}
      </button>
      <RouterLink v-if="state.access.kioskAvailable" class="auth-back" to="/"
        ><ArrowLeft />返回签到台</RouterLink
      >
    </form>
  </AuthLayout>
</template>

<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from "vue";
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

const CREDENTIALS_KEY = "ca_remembered_credentials";
const { state, login } = useSession();
const router = useRouter();
const route = useRoute();
const form = reactive({ studentNo: "", password: "" });
const remember = ref(false);
const showPassword = ref(false);
const busy = ref(false);
const error = ref("");
const accountInput = ref<HTMLInputElement>();

onMounted(async () => {
  try {
    const saved = JSON.parse(localStorage.getItem(CREDENTIALS_KEY) || "null");
    if (saved?.studentNo && saved?.password) {
      form.studentNo = saved.studentNo;
      form.password = saved.password;
      remember.value = true;
    }
  } catch {
    localStorage.removeItem(CREDENTIALS_KEY);
  }
  await nextTick();
  accountInput.value?.focus();
});

async function submit() {
  busy.value = true;
  error.value = "";
  try {
    const user = await login(form.studentNo, form.password);
    if (remember.value)
      localStorage.setItem(CREDENTIALS_KEY, JSON.stringify(form));
    else localStorage.removeItem(CREDENTIALS_KEY);
    if (user.mustChangePassword) await router.replace({ name: "password" });
    else if (route.query.next) await router.replace(String(route.query.next));
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

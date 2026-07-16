<template>
  <AuthLayout>
    <form class="auth-form" @submit.prevent="submit">
      <div class="auth-heading">
        <p class="eyebrow">ACCOUNT SECURITY</p>
        <h2>设置新密码</h2>
        <p>首次登录需要更换初始密码，完成后会重新登录。</p>
      </div>
      <label class="field"
        ><span>原密码</span
        ><input
          v-model="form.oldPassword"
          type="password"
          autocomplete="current-password"
          required
      /></label>
      <label class="field"
        ><span>新密码</span
        ><input
          v-model="form.newPassword"
          type="password"
          autocomplete="new-password"
          minlength="6"
          required
      /></label>
      <label class="field"
        ><span>确认新密码</span
        ><input
          v-model="confirmation"
          type="password"
          autocomplete="new-password"
          minlength="6"
          required
      /></label>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <button class="button primary auth-submit" type="submit" :disabled="busy">
        <KeyRound />{{ busy ? "正在更新" : "更新密码" }}
      </button>
    </form>
  </AuthLayout>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { KeyRound } from "@lucide/vue";
import AuthLayout from "../../layouts/AuthLayout.vue";
import { post, setToken } from "../../shared/api";
import { useSession } from "../../app/session";
const router = useRouter();
const { state } = useSession();
const form = reactive({ oldPassword: "", newPassword: "" });
const confirmation = ref("");
const busy = ref(false);
const error = ref("");
async function submit() {
  if (form.newPassword !== confirmation.value) {
    error.value = "两次输入的新密码不一致";
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

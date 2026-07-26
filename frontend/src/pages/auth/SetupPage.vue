<template>
  <AuthLayout>
    <form class="auth-form auth-form--setup" @submit.prevent="submit">
      <div class="auth-heading">
        <p class="eyebrow">FIRST RUN</p>
        <h2>初始化本机</h2>
        <p>创建首个管理员账号，数据将保存在程序根目录。</p>
      </div>
      <label class="field"
        ><span>管理员账号</span
        ><input
          v-model.trim="form.account"
          autocomplete="username"
          required
          minlength="4"
          placeholder="字母、数字、下划线或短横线"
      /></label>
      <label class="field"
        ><span>姓名</span
        ><input
          v-model.trim="form.name"
          autocomplete="name"
          required
          placeholder="管理员姓名"
      /></label>
      <label class="field"
        ><span>初始密码</span
        ><input
          v-model="form.password"
          type="password"
          autocomplete="new-password"
          required
          minlength="6"
          placeholder="至少 6 位"
      /></label>
      <label class="field"
        ><span>确认密码</span
        ><input
          v-model="confirmation"
          type="password"
          autocomplete="new-password"
          required
          placeholder="再次输入密码"
      /></label>
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
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { DatabaseZap, LoaderCircle } from "@lucide/vue";
import AuthLayout from "../../layouts/AuthLayout.vue";
import { useSession } from "../../app/session";
const { initialize } = useSession();
const router = useRouter();
const form = reactive({ account: "", name: "", password: "" });
const confirmation = ref("");
const error = ref("");
const busy = ref(false);
async function submit() {
  if (form.password !== confirmation.value) {
    error.value = "两次输入的密码不一致";
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

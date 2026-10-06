<template>
  <AuthLayout entry-mode="UNKNOWN">
    <section class="auth-form startup-status" :aria-busy="state.startupPending">
      <div class="auth-heading" role="status" aria-live="polite">
        <h2>{{ state.startupError ? '暂时无法连接系统' : '正在连接系统' }}</h2>
        <p v-if="state.startupError">尚未确认入口和初始化状态。请确认服务已启动，再重试连接。</p>
        <p v-else>正在确认入口、初始化状态和登录信息，请稍候。</p>
      </div>
      <button v-if="state.startupError || retrying" ref="retryButton" class="button primary" type="button" :disabled="retrying || state.startupPending" @click="retry">{{ state.startupPending ? '正在连接…' : '重试连接' }}</button>
    </section>
  </AuthLayout>
</template>

<script setup lang="ts">
import { nextTick, ref } from "vue";
import { useRouter } from "vue-router";
import AuthLayout from "../../layouts/AuthLayout.vue";
import { useSession } from "../../app/session";

const { state } = useSession();
const router = useRouter();
const retrying = ref(false);
const retryButton = ref<HTMLButtonElement | null>(null);
async function retry() {
  if (retrying.value || state.startupPending) return;
  retrying.value = true;
  try { await router.replace(state.startupTarget); }
  catch { /* The navigation recovery dialog owns resource-load errors. */ }
  finally {
    retrying.value = false;
    await nextTick();
    if (state.startupError) retryButton.value?.focus();
  }
}
</script>

<style scoped>
/* Classic and Editorial place the heading beside the form; a short status
   still needs enough height for that heading and its explanation. */
html:root[data-appearance] .public-presentation.public-auth .auth-form.startup-status {
  min-height: 360px;
}
</style>

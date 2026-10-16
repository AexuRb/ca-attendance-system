<template>
  <AuthLayout entry-mode="UNKNOWN" large-brand>
    <section class="auth-form startup-status" :aria-busy="state.startupPending">
      <div class="auth-heading" role="status" aria-live="polite">
        <h2>{{ state.startupError ? '系统连接失败' : '正在连接系统' }}</h2>
        <p v-if="state.startupError">请确认服务已启动，再重试连接。</p>
        <p v-else>正在连接，请稍候。</p>
      </div>
      <div class="startup-indicator" :class="{ 'startup-indicator-failed': Boolean(state.startupError) }" aria-hidden="true">
        <CircleAlert v-if="state.startupError" />
        <LoaderCircle v-else class="spin" />
      </div>
      <button data-button-kind="entry" v-if="state.startupError || retrying" ref="retryButton" class="button primary auth-submit" type="button" :disabled="retrying || state.startupPending" @click="retry">{{ state.startupPending ? '正在连接' : '重试连接' }}</button>
    </section>
  </AuthLayout>
</template>

<script setup lang="ts">
import { nextTick, ref } from "vue";
import { useRouter } from "vue-router";
import { CircleAlert, LoaderCircle } from "@lucide/vue";
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

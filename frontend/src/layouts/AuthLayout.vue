<template>
  <main class="public-presentation public-auth" :data-auth-page="String(route.name || 'auth')">
    <header class="auth-signal-header">
      <div class="auth-brand-lockup">
        <span class="auth-brand-mark">
          <img src="/brand/ca-logo-black.png" alt="计算机协会会徽" />
        </span>
        <div>
          <strong>计算机协会</strong>
          <span>值班管理系统</span>
        </div>
      </div>
      <div class="auth-header-actions">
        <div class="auth-signal-context">
          <i aria-hidden="true"></i>
          <span>{{ entryMode === 'REMOTE_ADMIN' ? '远程管理入口' : '本机管理后台' }}</span>
        </div>
        <slot name="header-actions" />
      </div>
    </header>

    <section class="auth-form-panel">
      <div class="auth-form-shell"><slot /></div>
    </section>

    <footer class="auth-signal-footer">
      <span>计算机协会 · {{ entryMode === 'REMOTE_ADMIN' ? '远程管理' : '本机运行' }}</span>
    </footer>
  </main>
</template>

<script setup lang="ts">
import { useRoute } from "vue-router";

withDefaults(defineProps<{ entryMode?: 'LOCAL' | 'REMOTE_ADMIN' }>(), {
  entryMode: 'LOCAL',
});

const route = useRoute();
</script>

<style src="../features/public/presentation.css"></style>

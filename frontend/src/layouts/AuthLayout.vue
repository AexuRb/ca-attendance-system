<template>
  <main class="public-presentation public-auth" :data-auth-page="String(route.name || 'auth')" :data-auth-layout="largeBrand ? 'brand' : 'standard'">
    <header class="auth-signal-header">
      <div class="auth-brand-lockup">
        <span class="auth-brand-mark">
          <img :src="logoUrl" data-shared-emblem="small" width="890" height="892" alt="计算机协会会徽" />
        </span>
        <div>
          <strong>计算机协会</strong>
          <span>值班管理系统</span>
        </div>
      </div>
      <div class="auth-header-actions">
        <div v-if="entryMode !== 'LOCAL'" class="auth-signal-context">
          <i aria-hidden="true"></i>
          <span>{{ entryMode === 'UNKNOWN' ? '系统连接' : '远程管理入口' }}</span>
        </div>
        <slot name="header-actions" />
      </div>
    </header>

    <section class="auth-form-panel">
      <div class="auth-form-shell">
        <div v-if="largeBrand" class="auth-entry-stage">
          <div class="auth-entry-brand">
            <img :src="logoUrl" data-shared-emblem="large" width="890" height="892" alt="计算机协会会徽" />
            <strong>计算机协会</strong>
            <span>值班管理系统</span>
          </div>
          <slot />
        </div>
        <slot v-else />
      </div>
    </section>

    <footer class="auth-signal-footer">
      <span>计算机协会 · {{ entryMode === 'UNKNOWN' ? '系统连接' : entryMode === 'REMOTE_ADMIN' ? '远程管理' : '本机运行' }}</span>
    </footer>
  </main>
</template>

<script setup lang="ts">
import { useRoute } from "vue-router";

withDefaults(defineProps<{ entryMode?: 'LOCAL' | 'REMOTE_ADMIN' | 'UNKNOWN'; largeBrand?: boolean }>(), {
  entryMode: 'LOCAL',
  largeBrand: false,
});

const route = useRoute();
const logoUrl = "/brand/ca-logo-transparent.svg";
</script>

<style src="../features/public/presentation.css"></style>
<style src="../features/auth/entry-presentation.css"></style>

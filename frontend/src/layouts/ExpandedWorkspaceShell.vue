<template>
  <div ref="shell" class="overview-shell mw-scope" :class="{ 'menu-open': menuOpen }" :style="{ '--overview-window-density': windowDensity }" @keydown.esc="closeMenu">
    <a class="overview-skip" href="#overview-content" @click.prevent="focusContent">跳到主要内容</a>
    <button v-if="menuOpen" class="overview-backdrop" type="button" aria-label="关闭菜单" @click="closeMenu" />
    <aside id="overview-navigation" class="overview-sidebar" aria-label="后台导航" :inert="smallScreen && !menuOpen">
      <nav class="overview-navigation" aria-label="功能菜单">
        <section v-for="section in sections" :key="section.key" class="overview-nav-group" :aria-labelledby="`overview-group-${section.key}`">
          <h2 :id="`overview-group-${section.key}`">{{ section.label }}</h2>
          <RouterLink v-for="item in section.items" :key="item.key" :to="item.target" :class="{ 'is-current': item.key === currentPage }" :aria-current="item.key === currentPage ? 'page' : undefined" @click="closeMenu">
            <component :is="item.icon" aria-hidden="true" /><span>{{ item.label }}</span>
          </RouterLink>
        </section>
      </nav>
      <div class="overview-account">
        <span class="overview-account-icon" aria-hidden="true"><UserRound /></span>
        <div><strong :title="user?.name">{{ user?.name || '账号' }}</strong><small>{{ roleLabel(user?.role) }}</small></div>
        <button data-button-kind="icon" type="button" aria-label="退出登录" title="退出登录" @click="signOut"><LogOut aria-hidden="true" /></button>
      </div>
    </aside>
    <header class="overview-topbar">
      <div class="overview-brand">
        <button data-button-kind="icon" ref="menuButton" class="overview-menu-toggle" type="button" :aria-expanded="menuOpen" aria-controls="overview-navigation" aria-label="打开功能菜单" @click="menuOpen = !menuOpen"><Menu aria-hidden="true" /></button>
        <img :src="logoUrl" data-shared-emblem="small" width="890" height="892" alt="计算机协会会徽">
        <strong>计算机值班管理系统</strong>
      </div>
      <div class="overview-topbar-actions">
        <span v-if="!checking && !online" class="overview-service-alert" role="status"><CircleAlert aria-hidden="true" />服务连接异常</span>
        <HeaderQuickActions :kiosk-available="state.access.kioskAvailable" />
      </div>
    </header>
    <main id="overview-content" ref="content" class="overview-content" tabindex="-1">
      <header class="overview-page-heading"><slot name="heading" /></header>
      <div class="overview-workspace"><slot /></div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import type { Component } from "vue";
import { RouterLink, useRouter } from "vue-router";
import type { RouteLocationRaw } from "vue-router";
import { CircleAlert, DatabaseBackup, FileSpreadsheet, LogOut, Menu, UserRound } from "@lucide/vue";
import { navigationForRole, roleLabel } from "../app/adminNavigation";
import { useSession } from "../app/session";
import { useServiceHealth } from "../shared/composables/useServiceHealth";
import HeaderQuickActions from "./HeaderQuickActions.vue";

// Pages opt into the approved shell individually.
// Data center, backups and recycle keep their existing query routes.
withDefaults(defineProps<{ currentPage?: string }>(), { currentPage: "today" });
const { user, state, logout } = useSession();
const logoUrl = "/brand/ca-logo-transparent.svg";
const router = useRouter();
const { online, checking } = useServiceHealth();
const menuOpen = ref(false);
const shell = ref<HTMLElement | null>(null);
const windowDensity = ref(1);
let shellObserver: ResizeObserver | undefined;
const content = ref<HTMLElement | null>(null);
const menuButton = ref<HTMLButtonElement | null>(null);
const media = window.matchMedia?.("(max-width: 900px)");
const smallScreen = ref(media?.matches ?? false);
const sections = computed(() => navigationForRole(user.value?.role).map(section => ({
  key: section.key,
  label: section.key === "system" ? "设置" : section.label,
  items: section.items.flatMap<{ key: string; label: string; icon: Component; target: RouteLocationRaw }>(item => item.name === "data" ? [
    { key: "data-center", label: "数据中心", icon: FileSpreadsheet, target: { name: "data", query: { tab: "import" } } },
    { key: "backups", label: "备份与恢复", icon: DatabaseBackup, target: { name: "data", query: { tab: "backups" } } },
  ] : [{ key: item.name, label: item.label, icon: item.icon, target: { name: item.name } }]),
})).map(section => ({
  ...section,
  items: section.key === "system" ? [...section.items].sort((a, b) =>
    ["settings", "data-center", "backups", "logs"].indexOf(a.key) - ["settings", "data-center", "backups", "logs"].indexOf(b.key)) : section.items,
})));

// Fit the expanded menu into the real content viewport, including desktop
// DPI/zoom changes. Type stays readable; spacing and row height vary together.
function fitWindow() {
  if (!shell.value) return;
  const groups = sections.value.length;
  const links = sections.value.reduce((total, section) => total + section.items.length, 0);
  const separators = Math.max(0, links - groups);
  const groupGaps = Math.max(0, groups - 1);
  const compactMenu = 16 + groups * (8 + 20) + links * 32 + separators + groupGaps * 6;
  const relaxedMenu = 32 + groups * (16 + 27) + links * 40 + separators * 3 + groupGaps * 10;
  const compactFrame = 96 + 64 + 12 + compactMenu;
  const relaxedFrame = 128 + 88 + 24 + relaxedMenu;
  windowDensity.value = Math.max(0, Math.min(1,
    (shell.value.clientHeight - compactFrame) / (relaxedFrame - compactFrame)));
}
watch(sections, fitWindow, { flush: 'post' });

function syncScreen(event: MediaQueryListEvent) {
  smallScreen.value = event.matches;
  menuOpen.value = false;
}
function closeMenu() {
  if (menuOpen.value) {
    menuOpen.value = false;
    menuButton.value?.focus();
  }
}
function focusContent() { closeMenu(); content.value?.focus({ preventScroll: true }); }
async function signOut() { await logout(); await router.replace({ name: "login" }); }
onMounted(() => {
  media?.addEventListener("change", syncScreen);
  fitWindow();
  if (typeof ResizeObserver !== 'undefined') {
    shellObserver = new ResizeObserver(fitWindow);
    shellObserver.observe(shell.value!);
  } else {
    window.addEventListener('resize', fitWindow);
  }
});
onBeforeUnmount(() => {
  media?.removeEventListener("change", syncScreen);
  shellObserver?.disconnect();
  window.removeEventListener('resize', fitWindow);
});
</script>

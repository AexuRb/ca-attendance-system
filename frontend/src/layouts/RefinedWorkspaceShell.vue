<template>
  <div class="mw-canvas mw-scope">
    <a class="mw-skip" href="#admin-main-content" @click.prevent="focusContent">跳到主要内容</a>
    <header class="mw-primary">
      <div class="mw-brand"><img :src="logoUrl" alt="计算机协会会徽"><div><strong>计算机协会</strong><small>值班管理系统</small></div></div>
      <nav aria-label="主要区域"><RouterLink v-for="section in sections" :key="section.key" :to="{ name: section.items[0]?.name || 'profile' }" :class="{ current: section.key === sectionKey }"><component :is="section.icon" /><span>{{ section.label }}</span></RouterLink></nav>
      <ActionMenu :label="`${user?.name || '用户'}的账号菜单`" :trigger-text="user?.name || '账号'">
        <button role="menuitem" @click="router.push({name:'profile'})"><UserRound />个人资料</button>
        <button role="menuitem" @click="signOut"><LogOut />退出登录</button>
      </ActionMenu>
      <HeaderQuickActions :kiosk-available="sessionState.access.kioskAvailable" @open-credits="credits=true" />
    </header>
    <aside class="mw-side" aria-label="后台导航">
      <div class="mw-brand"><img :src="logoUrl" alt="计算机协会会徽"><div><strong>计算机协会</strong><small>值班管理系统</small></div></div>
      <nav><div v-for="section in sections" :key="section.key" class="mw-nav-group"><RouterLink :to="{name:section.items[0]?.name || 'profile'}" :class="{current:section.key===sectionKey}"><component :is="section.icon" />{{ section.label }}</RouterLink><div v-if="section.key===sectionKey" class="mw-subitems"><RouterLink v-for="item in section.items" :key="item.name" :to="{name:item.name}">{{ item.label }}</RouterLink></div></div></nav>
      <button class="mw-signout" @click="signOut"><LogOut />退出登录</button>
    </aside>
    <div class="mw-workbar">
      <span>{{ title }}</span>
      <div class="mw-workbar-end">
        <span>{{ user?.name }} · {{ roleLabel(user?.role || 'MEMBER') }}</span>
        <HeaderQuickActions :kiosk-available="sessionState.access.kioskAvailable" @open-credits="credits=true" />
      </div>
    </div>
    <main id="admin-main-content" ref="content" class="mw-main" tabindex="-1"><div class="mw-surface">
      <nav class="mw-subnav" aria-label="当前区域导航"><small>{{ sectionLabel }} / 页面导航</small><RouterLink v-for="item in sectionItems" :key="item.name" :to="{name:item.name}">{{ item.label }}</RouterLink></nav>
      <header class="mw-heading"><slot name="heading"><small>计算机协会 / {{ sectionLabel }}</small><h1>{{ title }}</h1><p>{{ description }}</p></slot></header>
      <div v-if="$slots.tools" class="mw-tools"><slot name="tools" /></div>
      <aside v-if="$slots.filters" class="mw-query" :aria-label="filterLabel"><slot name="filters" /></aside>
      <section class="mw-results" aria-label="工作内容"><slot /></section>
    </div></main>
    <footer class="mw-footer">
      <ServiceStatus :online="online" compact />
    </footer>
    <CreditsDialog :open="credits" @close="credits=false" />
  </div>
</template>
<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount } from "vue";
import { workspaceEntrance } from "../shared/ui/workspaceEntrance";
import { RouterLink, useRouter } from "vue-router";
import { LogOut, UserRound } from "@lucide/vue";
import { navigationForRole, roleLabel } from "../app/adminNavigation";
import { useSession } from "../app/session";
import { useServiceHealth } from "../shared/composables/useServiceHealth";
import ActionMenu from "../shared/ui/ActionMenu.vue";
import HeaderQuickActions from "./HeaderQuickActions.vue";
import CreditsDialog from "../shared/ui/CreditsDialog.vue";
import ServiceStatus from "../shared/ui/ServiceStatus.vue";
const { user, state: sessionState, logout } = useSession();
const props = withDefaults(defineProps<{ title?: string; description?: string; sectionKey?: string; filterLabel?: string }>(), { title: "成员名册", description: "管理协会成员、角色与账号状态", sectionKey: "people", filterLabel: "筛选成员" });
const sectionLabel = computed(() => sections.value.find(s => s.key === props.sectionKey)?.label || "系统");
const logoUrl = "/brand/ca-logo-black.png";
const router = useRouter();
const { online } = useServiceHealth();
const credits = ref(false);
const content = ref<HTMLElement | null>(null);
let cancelEntrance = () => {};
const motionPreference = window.matchMedia?.("(prefers-reduced-motion: reduce)");
function stopEntrance() { cancelEntrance(); }
onMounted(() => {
  // A replaced navigation link leaves focus on body. Keep keyboard traversal in
  // the new workspace without overriding a field's own focus or moving scroll.
  if (document.activeElement === document.body) content.value?.focus({ preventScroll: true });
  if (content.value) cancelEntrance = workspaceEntrance(content.value, document.documentElement.dataset.appearance || "classic", motionPreference?.matches ?? false);
  motionPreference?.addEventListener("change", stopEntrance);
});
onBeforeUnmount(() => {
  stopEntrance();
  motionPreference?.removeEventListener("change", stopEntrance);
});
const sections = computed(()=>navigationForRole(user.value?.role));
const sectionItems = computed(()=>sections.value.find(s=>s.key===props.sectionKey)?.items || []);
function focusContent() { document.getElementById('admin-main-content')?.focus(); }
async function signOut(){await logout();await router.replace({name:'login'});}
</script>

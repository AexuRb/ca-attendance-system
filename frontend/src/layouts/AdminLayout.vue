<template>
  <RouterView v-if="['members', 'settings', 'data', 'today', 'attendance', 'reviews', 'repairs', 'trainings', 'schedules', 'stats', 'profile', 'logs'].includes(String(route.name))" />
  <div
    v-else-if="user && activeSection"
    class="admin-layout refined-admin-layout s2-foundation"
    :data-page="String(route.name || 'admin')"
    :class="{
      'nav-open': navOpen,
      'section-collapsed': sidebarCollapsed,
    }"
  >
    <a
      class="admin-skip-link"
      href="#admin-main-content"
      @click.prevent="focusMainContent"
    >跳到主要内容</a>
    <div
      ref="navigation"
      class="admin-navigation"
      :inert="compactNavigation && !navOpen ? true : undefined"
      :aria-hidden="compactNavigation && !navOpen ? 'true' : undefined"
      :role="navigationModalOpen ? 'dialog' : undefined"
      :aria-modal="navigationModalOpen ? 'true' : undefined"
      :aria-label="navigationModalOpen ? '后台导航' : undefined"
      tabindex="-1"
    >
      <PrimaryRail
        :sections="visibleSections"
        :active-section-key="activeSection.key"
        :user-name="user.name"
        @navigate="navOpen = false"
      />
      <SectionSidebar
        :section="activeSection"
        :user="user"
        :user-role-label="roleLabel(user.role)"
        :aria-hidden="!sidebarInteractive"
        :inert="sidebarInteractive ? undefined : true"
        @collapse="collapseSidebar"
        @logout="signOut"
        @navigate="navOpen = false"
      />
    </div>

    <button
      v-if="navOpen"
      class="nav-scrim"
      type="button"
      aria-label="关闭导航"
      @click="navOpen = false"
    ></button>

    <div class="admin-stage" :inert="navigationModalOpen ? true : undefined">
      <AdminTopbar
        :current-section="activeSection.label"
        :current-title="currentTitle"
        :sidebar-collapsed="sidebarCollapsed"
        :clock="clock"
        @open-navigation="navOpen = true"
        @expand-sidebar="expandSidebar"
        @open-credits="creditsOpen = true"
      />
      <main
        id="admin-main-content"
        ref="mainContent"
        class="admin-content"
        tabindex="-1"
      >
        <RouterView v-slot="{ Component, route: activeRoute }">
          <Transition name="admin-view" mode="out-in">
            <component
              :is="Component"
              :key="String(activeRoute.name || activeRoute.path)"
              class="admin-route-frame"
            />
          </Transition>
        </RouterView>
      </main>
    </div>

    <CreditsDialog :open="creditsOpen" @close="creditsOpen = false" />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { RouterView, useRoute, useRouter } from "vue-router";
import PrimaryRail from "./admin/PrimaryRail.vue";
import SectionSidebar from "./admin/SectionSidebar.vue";
import AdminTopbar from "./admin/AdminTopbar.vue";
import CreditsDialog from "../shared/ui/CreditsDialog.vue";
import { useSession } from "../app/session";
import { navigationForRole, roleLabel } from "../app/adminNavigation";
import { safeStorageGet, safeStorageSet } from "../shared/storage";
import type { Role } from "../shared/types";
import { useDialogFocus } from "../shared/ui/useDialogFocus";

const sidebarStorageKey = "ca-admin-section-sidebar-collapsed";
const { user, logout } = useSession();
const route = useRoute();
const router = useRouter();
const navOpen = ref(false);
const navigation = ref<HTMLElement | null>(null);
const navigationMedia = window.matchMedia?.("(max-width: 900px)");
const compactNavigation = ref(navigationMedia?.matches ?? false);
const navigationModalOpen = computed(() => compactNavigation.value && navOpen.value);
useDialogFocus({
  root: navigation,
  open: () => navigationModalOpen.value,
  close: () => { navOpen.value = false; },
});
const mainContent = ref<HTMLElement | null>(null);
const creditsOpen = ref(false);
const sidebarCollapsed = ref(
  safeStorageGet(sidebarStorageKey) === "true",
);
const clock = ref("");
let timer = 0;

const role = computed(() => user.value?.role as Role | undefined);
const visibleSections = computed(() => navigationForRole(role.value));
const activeSection = computed(
  () =>
    visibleSections.value.find((section) =>
      section.items.some((item) => item.name === route.name),
    ) || visibleSections.value[0],
);
const activeItem = computed(() =>
  visibleSections.value
    .flatMap((section) => section.items)
    .find((item) => item.name === route.name),
);
const currentTitle = computed(() => activeItem.value?.label || "后台");
const sidebarInteractive = computed(
  () => compactNavigation.value ? navOpen.value : !sidebarCollapsed.value,
);

function syncNavigationMode(event: MediaQueryListEvent) {
  compactNavigation.value = event.matches;
  navOpen.value = false;
}

watch(
  () => route.name,
  () => {
    navOpen.value = false;
  },
);

onMounted(() => {
  navigationMedia?.addEventListener("change", syncNavigationMode);
  updateClock();
  timer = window.setInterval(updateClock, 30_000);
});

onBeforeUnmount(() => {
  window.clearInterval(timer);
  navigationMedia?.removeEventListener("change", syncNavigationMode);
});

function collapseSidebar() {
  sidebarCollapsed.value = true;
  navOpen.value = false;
  safeStorageSet(sidebarStorageKey, "true");
}

function expandSidebar() {
  sidebarCollapsed.value = false;
  safeStorageSet(sidebarStorageKey, "false");
}

function focusMainContent() {
  mainContent.value?.focus();
  mainContent.value?.scrollIntoView({ block: "start" });
}

function updateClock() {
  clock.value = new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date());
}

async function signOut() {
  await logout();
  router.replace({ name: "login" });
}
</script>

<template>
  <div
    v-if="user && activeSection"
    class="admin-layout refined-admin-layout"
    :class="{
      'nav-open': navOpen,
      'section-collapsed': sidebarCollapsed,
    }"
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
      @credits="creditsOpen = true"
      @logout="signOut"
      @navigate="navOpen = false"
    />

    <button
      v-if="navOpen"
      class="nav-scrim"
      type="button"
      aria-label="关闭导航"
      @click="navOpen = false"
    ></button>

    <div class="admin-stage">
      <AdminTopbar
        :current-section="activeSection.label"
        :current-title="currentTitle"
        :sidebar-collapsed="sidebarCollapsed"
        :clock="clock"
        @open-navigation="navOpen = true"
        @expand-sidebar="expandSidebar"
      />
      <main class="admin-content">
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
import type { Role } from "../shared/types";

const sidebarStorageKey = "ca-admin-section-sidebar-collapsed";
const { user, logout } = useSession();
const route = useRoute();
const router = useRouter();
const navOpen = ref(false);
const creditsOpen = ref(false);
const sidebarCollapsed = ref(
  window.localStorage.getItem(sidebarStorageKey) === "true",
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
  () => !sidebarCollapsed.value || navOpen.value,
);

watch(
  () => route.name,
  () => {
    navOpen.value = false;
  },
);

onMounted(() => {
  updateClock();
  timer = window.setInterval(updateClock, 30_000);
});

onBeforeUnmount(() => window.clearInterval(timer));

function collapseSidebar() {
  sidebarCollapsed.value = true;
  navOpen.value = false;
  window.localStorage.setItem(sidebarStorageKey, "true");
}

function expandSidebar() {
  sidebarCollapsed.value = false;
  window.localStorage.setItem(sidebarStorageKey, "false");
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

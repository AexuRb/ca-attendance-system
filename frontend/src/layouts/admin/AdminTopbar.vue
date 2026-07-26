<template>
  <header class="admin-topbar refined-topbar">
    <button
      type="button"
      class="icon-button mobile-menu"
      title="打开导航"
      aria-label="打开导航"
      @click="$emit('openNavigation')"
    >
      <Menu aria-hidden="true" />
    </button>
    <button
      v-if="sidebarCollapsed"
      type="button"
      class="icon-button desktop-sidebar-toggle"
      title="展开二级导航"
      aria-label="展开二级导航"
      @click="$emit('expandSidebar')"
    >
      <PanelLeftOpen aria-hidden="true" />
    </button>

    <div class="topbar-breadcrumbs">
      <span>协会后台</span>
      <ChevronRight aria-hidden="true" />
      <span>{{ currentSection }}</span>
      <ChevronRight aria-hidden="true" />
      <strong>{{ currentTitle }}</strong>
    </div>

    <div class="topbar-tools">
      <ServiceStatus :online="true" compact />
      <span class="local-clock">{{ clock }}</span>
    </div>
  </header>
</template>

<script setup lang="ts">
import { ChevronRight, Menu, PanelLeftOpen } from "@lucide/vue";
import ServiceStatus from "../../shared/ui/ServiceStatus.vue";

defineProps<{
  currentSection: string;
  currentTitle: string;
  sidebarCollapsed: boolean;
  clock: string;
}>();

defineEmits<{
  openNavigation: [];
  expandSidebar: [];
}>();
</script>

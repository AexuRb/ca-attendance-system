<template>
  <aside class="admin-primary-rail" aria-label="主要区域">
    <img
      class="primary-brand-mark"
      src="/brand/ca-logo-white.png"
      alt="计算机协会会徽"
    />
    <nav class="primary-nav">
      <RouterLink
        v-for="section in sections"
        :key="section.key"
        :to="{ name: section.items[0].name }"
        :class="{ active: section.key === activeSectionKey }"
        :title="section.label"
        @click="$emit('navigate')"
      >
        <component :is="section.icon" aria-hidden="true" />
        <span>{{ section.label }}</span>
      </RouterLink>
    </nav>
    <RouterLink
      class="primary-kiosk-link"
      :to="{ name: 'kiosk' }"
      title="签到台"
      aria-label="打开签到台"
    >
      <Gauge aria-hidden="true" />
    </RouterLink>
    <RouterLink
      class="primary-avatar"
      :to="{ name: 'profile' }"
      title="个人资料"
      :aria-label="`${userName}的个人资料`"
    >
      {{ userName.slice(0, 1) }}
    </RouterLink>
  </aside>
</template>

<script setup lang="ts">
import { RouterLink } from "vue-router";
import { Gauge } from "@lucide/vue";
import type { AdminNavSection } from "../../app/adminNavigation";

defineProps<{
  sections: AdminNavSection[];
  activeSectionKey: string;
  userName: string;
}>();

defineEmits<{ navigate: [] }>();
</script>

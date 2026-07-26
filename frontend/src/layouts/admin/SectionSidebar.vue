<template>
  <aside class="admin-section-sidebar" aria-label="当前区域导航">
    <div class="section-brand-copy">
      <small>COMPUTER ASSOCIATION</small>
      <strong>协会管理后台</strong>
      <button
        type="button"
        class="section-collapse"
        title="收起二级导航"
        aria-label="收起二级导航"
        @click="$emit('collapse')"
      >
        <PanelLeftClose aria-hidden="true" />
      </button>
    </div>

    <nav class="section-navigation">
      <p>{{ section.label }}</p>
      <RouterLink
        v-for="item in section.items"
        :key="item.name"
        :to="{ name: item.name }"
        @click="$emit('navigate')"
      >
        <component :is="item.icon" aria-hidden="true" />
        <span>{{ item.label }}</span>
        <ChevronRight aria-hidden="true" />
      </RouterLink>
    </nav>

    <div class="section-sidebar-foot">
      <div class="section-user">
        <span class="avatar">{{ user.name.slice(0, 1) }}</span>
        <div>
          <strong>{{ user.name }}</strong>
          <span>{{ userRoleLabel }}</span>
        </div>
        <button
          type="button"
          class="icon-button ghost"
          title="退出登录"
          aria-label="退出登录"
          @click="$emit('logout')"
        >
          <LogOut aria-hidden="true" />
        </button>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { RouterLink } from "vue-router";
import { ChevronRight, LogOut, PanelLeftClose } from "@lucide/vue";
import type { AdminNavSection } from "../../app/adminNavigation";
import type { UserSession } from "../../shared/types";

defineProps<{
  section: AdminNavSection;
  user: UserSession;
  userRoleLabel: string;
}>();

defineEmits<{
  collapse: [];
  logout: [];
  navigate: [];
}>();

</script>

<template>
  <div class="command-welcome" :class="{ 'is-compact': compact }">
    <div class="command-welcome-mark">
      <img :src="logoPath" alt="计算机协会会徽">
    </div>
    <p class="command-welcome-context">
      <span>{{ dateLabel }}</span><i aria-hidden="true"></i><span>{{ roleName }}工作区</span>
    </p>
    <Transition name="command-welcome-detail">
      <div v-if="!compact" class="command-welcome-detail">
        <h1>今天要处理什么？</h1>
        <p class="command-welcome-hint">输入 <kbd>/</kbd> 进入命令模式</p>
        <div v-if="loading" class="command-welcome-loading" aria-label="正在加载今日状态">
          <i v-for="index in 3" :key="index"></i>
        </div>
        <TodayQuickActions v-else :items="actions" @execute="$emit('execute', $event)" />
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import TodayQuickActions from "./TodayQuickActions.vue";
import type { TodayQuickAction } from "./types";

const logoPath = "/brand/ca-logo-black.png";

defineProps<{
  dateLabel: string;
  roleName: string;
  actions: TodayQuickAction[];
  loading?: boolean;
  compact?: boolean;
}>();
defineEmits<{ execute: [command: string] }>();
</script>

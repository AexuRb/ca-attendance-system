<template>
  <div class="command-welcome-actions" aria-label="今日快捷操作">
    <button
      v-for="item in items.slice(0, 3)"
      :key="item.id"
      class="command-welcome-action"
      :data-tone="item.tone"
      type="button"
      @click="$emit('execute', item.command)"
    >
      <span class="command-welcome-action-icon">
        <component :is="iconFor(item.id)" aria-hidden="true" />
      </span>
      <span class="command-welcome-action-copy">
        <strong>{{ item.label }}</strong>
        <small>{{ item.detail }}</small>
      </span>
      <ArrowUpRight aria-hidden="true" />
    </button>
  </div>
  <details v-if="items.length > 3" class="today-other-actions">
    <summary>其他待办（{{ items.length - 3 }}）</summary>
    <button v-for="item in items.slice(3)" :key="item.id" class="button secondary small" type="button" @click="$emit('execute', item.command)">{{ item.label }} · {{ item.detail }}</button>
  </details>
</template>

<script setup lang="ts">
import {
  ArrowUpRight,
  CalendarClock,
  ChartColumn,
  ClipboardCheck,
  ClipboardList,
  Database,
  History,
  Settings2,
  UserRound,
  UsersRound,
  Wrench,
} from "@lucide/vue";
import type { Component } from "vue";
import type { TodayQuickAction } from "./types";

defineProps<{ items: TodayQuickAction[] }>();
defineEmits<{ execute: [command: string] }>();

const icons: Record<string, Component> = {
  reviews: ClipboardCheck,
  "attendance-open": ClipboardList,
  "attendance-week": ClipboardList,
  "stats-week": ChartColumn,
  repairs: Wrench,
  "repairs-new": Wrench,
  schedules: CalendarClock,
  "schedules-today": CalendarClock,
  members: UsersRound,
  profile: UserRound,
  settings: Settings2,
  logs: History,
  data: Database,
};

function iconFor(id: string) {
  return icons[id] || ClipboardList;
}
</script>

<style scoped>
.today-other-actions { margin-top: 10px; font-size: 13px; }
.today-other-actions summary { cursor: pointer; min-height: 34px; padding: 8px 0; }
.today-other-actions button { margin: 4px 8px 4px 0; white-space: normal; }
</style>

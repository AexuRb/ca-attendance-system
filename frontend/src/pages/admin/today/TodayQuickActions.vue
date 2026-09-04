<template>
  <div class="command-welcome-actions" aria-label="今日快捷操作">
    <button
      v-for="item in items"
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

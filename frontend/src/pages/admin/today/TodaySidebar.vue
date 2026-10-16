<template>
  <aside class="today-context-rail" aria-label="今日待办与常用功能">
    <section class="today-rail-tasks" aria-label="今日待办">
      <div v-if="loadError" class="today-rail-error" role="alert"><p>今日事项暂时没能读取，请重新加载。</p><button class="button secondary small" data-action="retry-today" type="button" :disabled="loading" @click="$emit('retry')">重新加载</button></div>
      <button v-for="item in tasks" :key="item.id" class="today-task-entry" :data-task="item.id" type="button" :disabled="opening || loading || unavailable || !item.action" @click="item.action && $emit('open', item.action.command)">
        <component :is="item.icon" class="today-task-icon" aria-hidden="true" />
        <span class="today-task-label">{{ item.label }}</span>
        <strong v-if="!unavailable && !loading" class="today-task-count">{{ item.count }}</strong>
        <small v-if="loading || unavailable || !item.count">{{ loading ? '正在加载…' : unavailable ? '暂时无法读取' : item.empty }}</small>
        <ArrowUpRight v-if="!unavailable && !loading" class="today-task-arrow" aria-hidden="true" />
      </button>
      <button v-if="!unavailable && scheduleNotice && scheduleAction" class="today-schedule-link" type="button" :disabled="opening" @click="$emit('open', scheduleAction.command)">{{ scheduleNotice }}<ArrowUpRight aria-hidden="true" /></button>
    </section>
    <section v-if="quickActions.length" class="today-rail-common" aria-label="常用功能">
      <div class="today-command-shortcuts">
        <button data-button-kind="entry" v-for="item in quickActions" :key="item.id" type="button" :disabled="opening || busy" @click="$emit('shortcut', item)"><component :is="shortcutIcon(item.id)" aria-hidden="true" /><span>{{ item.label }}</span><ChevronRight class="today-shortcut-arrow" aria-hidden="true" /></button>
      </div>
    </section>
  </aside>
</template>

<script setup lang="ts">
import { computed, type Component } from 'vue';
import { ArrowUpRight, ChartColumn, ChevronRight, ClipboardCheck, ClipboardList, DatabaseBackup, Wrench } from '@lucide/vue';
import type { TodayDashboardData, TodayQuickAction } from './types';
const props = defineProps<{ dashboard: TodayDashboardData | null; actions: TodayQuickAction[]; quickActions: TodayQuickAction[]; scheduleNotice: string; loading: boolean; loadError: string; opening: boolean; busy: boolean }>();
defineEmits<{ open: [string]; shortcut: [TodayQuickAction]; retry: [] }>();
const unavailable = computed(() => Boolean(props.loadError) || !props.dashboard);
const scheduleAction = computed(() => props.actions.find(item => item.id === 'schedules'));
const tasks = computed(() => [
  { id:'reviews', label:'待审核', count:props.dashboard?.todayPendingCount || 0, icon:ClipboardCheck, empty:'暂无待审核', action:props.actions.find(item => item.id === 'reviews') },
  { id:'repairs', label:'维修中', count:props.dashboard?.ongoingRepairCount || 0, icon:Wrench, empty:'暂无进行中维修', action:props.actions.find(item => item.id === 'repairs') },
]);
function shortcutIcon(id: string) {
  const icons: Record<string, Component> = { reviews:ClipboardCheck, 'today-records':ClipboardList, 'stats-week':ChartColumn, 'repairs-new':Wrench, backups:DatabaseBackup };
  return icons[id] || ClipboardList;
}
</script>

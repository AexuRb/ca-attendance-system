<template>
  <section class="today-summary" aria-labelledby="today-summary-heading">
    <h2 id="today-summary-heading">今日信息</h2>
    <div v-if="loadError" class="today-summary-error" role="alert">
      <p>今日事项暂时没能读取，请重新加载。</p>
      <button class="button secondary small" data-action="retry-today" type="button" :disabled="loading"
        @click="$emit('retry')">重新加载</button>
    </div>
    <p v-else-if="loading" class="today-summary-state" role="status">正在读取今日信息…</p>
    <ul v-else-if="dashboard" class="today-task-list">
      <li v-for="task in tasks" :key="task.id" class="today-task-entry" :data-task="task.id">
        <span class="today-task-label">{{ task.label }}</span>
        <span class="today-task-value">
          <strong class="today-task-count" :class="{ 'is-zero': task.count === 0, 'needs-review': task.id === 'reviews' && task.count > 0 }">
            {{ task.count }}
          </strong>
          <span>{{ task.unit }}</span>
        </span>
      </li>
    </ul>
  </section>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { TodayDashboardData } from "./types";

const props = defineProps<{
  canReview: boolean;
  dashboard: TodayDashboardData | null;
  loading: boolean;
  loadError: string;
}>();
defineEmits<{ retry: [] }>();

const tasks = computed(() => [
  { id: "reviews", label: "待审核", count: props.dashboard?.todayPendingCount ?? 0, unit: "条", visible: props.canReview },
  { id: "repairs", label: "进行中维修", count: props.dashboard?.ongoingRepairCount ?? 0, unit: "单", visible: true },
].filter(task => task.visible));
</script>

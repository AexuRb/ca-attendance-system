<template>
  <section class="today-priority" :class="{ clear: totalAttention === 0 }">
    <div class="priority-message">
      <span>{{ totalAttention ? "今日优先" : "今日状态" }}</span>
      <strong>
        {{
          totalAttention
            ? pendingCount
              ? `先处理 ${pendingCount} 条待审核记录`
              : `今天有 ${totalAttention} 项工作需要关注`
            : "今天没有待处理异常"
        }}
      </strong>
      <p v-if="openCount">还有 {{ openCount }} 条记录尚未签退</p>
    </div>

    <RouterLink
      class="priority-item"
      :class="{
        attention: pendingCount > 0,
        primary: primaryKind === 'pending',
      }"
      data-tone="amber"
      :to="{ name: 'reviews' }"
    >
      <span>待审核</span>
      <strong>{{ pendingCount }}</strong>
      <small>进入审核</small>
      <ArrowUpRight aria-hidden="true" />
    </RouterLink>
    <RouterLink
      class="priority-item"
      :class="{
        attention: openCount > 0,
        primary: primaryKind === 'open',
      }"
      data-tone="red"
      :to="{ name: 'attendance', query: { status: 'INCOMPLETE' } }"
    >
      <span>未签退</span>
      <strong>{{ openCount }}</strong>
      <small>查看记录</small>
      <ArrowUpRight aria-hidden="true" />
    </RouterLink>
    <RouterLink
      class="priority-item"
      :class="{
        attention: repairCount > 0,
        primary: primaryKind === 'repair',
      }"
      data-tone="blue"
      :to="{ name: 'repairs' }"
    >
      <span>处理中维修</span>
      <strong>{{ repairCount }}</strong>
      <small>查看进度</small>
      <ArrowUpRight aria-hidden="true" />
    </RouterLink>
  </section>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { RouterLink } from "vue-router";
import { ArrowUpRight } from "@lucide/vue";
import type { TodayDashboardData } from "./types";

const props = defineProps<{ dashboard: TodayDashboardData }>();
const pendingCount = computed(() => props.dashboard.todayPendingCount || 0);
const openCount = computed(() => props.dashboard.todayOpenCount || 0);
const repairCount = computed(() => props.dashboard.ongoingRepairCount || 0);
const totalAttention = computed(
  () => pendingCount.value + openCount.value + repairCount.value,
);
const primaryKind = computed(() => {
  const candidates = [
    { kind: "pending", value: pendingCount.value, weight: 3 },
    { kind: "open", value: openCount.value, weight: 2 },
    { kind: "repair", value: repairCount.value, weight: 1 },
  ];
  return candidates
    .filter((item) => item.value > 0)
    .sort(
      (left, right) =>
        right.value - left.value || right.weight - left.weight,
    )[0]?.kind;
});
</script>

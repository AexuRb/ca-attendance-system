<template>
  <footer class="kiosk-signal-week">
    <strong class="kiosk-signal-week-title">本周部长排班</strong>
    <div class="kiosk-signal-week-days">
      <article
        v-for="day in weekSchedule"
        :key="day.date"
        :class="{ today: day.date === todayValue }"
      >
        <span>{{ day.weekdayName?.replace("星期", "周") }}</span>
        <strong>{{ dayCount(day) || "—" }}</strong>
      </article>
      <template v-if="!weekSchedule.length">
        <article v-for="label in fallbackDays" :key="label">
          <span>{{ label }}</span>
          <strong>—</strong>
        </article>
      </template>
    </div>
    <RouterLink
      class="kiosk-signal-admin"
      :to="{ name: 'login' }"
      title="后台登录"
    >
      <Settings2 aria-hidden="true" />
      <span>后台</span>
    </RouterLink>
  </footer>
</template>

<script setup lang="ts">
import { Settings2 } from "@lucide/vue";
import { RouterLink } from "vue-router";
import type { ScheduleDay } from "../../features/kiosk/types";

defineProps<{
  weekSchedule: ScheduleDay[];
  todayValue: string;
}>();

const fallbackDays = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"];

function dayCount(day: ScheduleDay) {
  return day.slots?.reduce(
    (sum, slot) => sum + (slot.assignees?.length || 0),
    0,
  );
}
</script>

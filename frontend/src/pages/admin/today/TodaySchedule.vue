<template>
  <section
    class="panel today-schedule-panel"
    :class="{ 'is-empty': !schedule?.slots?.length }"
  >
    <div class="section-heading">
      <div>
        <h2>今日部长排班</h2>
        <span
          >{{ schedule?.weekdayName || weekdayLabel }} · {{ total }} 人</span
        >
      </div>
      <RouterLink v-if="canSchedule" :to="{ name: 'schedules' }">
        管理排班<ArrowUpRight aria-hidden="true" />
      </RouterLink>
    </div>

    <EmptyState
      v-if="!schedule?.slots?.length"
      title="今日暂无排班"
    />
    <div v-else class="today-timeline">
      <article v-for="slot in schedule.slots" :key="slot.key">
        <time
          >{{ shortTime(slot.startTime) }}-{{ shortTime(slot.endTime) }}</time
        >
        <i aria-hidden="true"></i>
        <div>
          <strong v-if="slot.assignees.length">
            {{ slot.assignees.map((person) => person.name).join("、") }}
          </strong>
          <strong v-else class="empty-slot">待安排部长</strong>
          <p>{{ slot.assignees.length }} 位部长</p>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { RouterLink } from "vue-router";
import { ArrowUpRight } from "@lucide/vue";
import EmptyState from "../../../shared/ui/EmptyState.vue";
import type { TodayScheduleData } from "./types";

const props = defineProps<{
  schedule: TodayScheduleData | null;
  canSchedule: boolean;
  weekdayLabel: string;
}>();

const total = computed(
  () =>
    props.schedule?.slots?.reduce(
      (sum, slot) => sum + slot.assignees.length,
      0,
    ) || 0,
);

const shortTime = (value?: string) => value?.slice(0, 5) || "--:--";
</script>

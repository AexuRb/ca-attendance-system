<template>
  <section
    class="kiosk-signal-band"
    aria-labelledby="kiosk-signal-title"
    aria-live="polite"
  >
    <header class="kiosk-signal-caption">
      <h2 id="kiosk-signal-title">今日部长排班</h2>
      <span>{{ todaySchedule?.weekdayName || weekdayLabel }} · {{ scheduleCount }} 位部长</span>
    </header>

    <div v-if="scheduleError" class="kiosk-signal-message" role="alert">
      <CalendarX2 aria-hidden="true" />
      <span>{{ scheduleError }}</span>
    </div>
    <div v-else-if="!todaySchedule" class="kiosk-signal-message" role="status">
      <LoaderCircle class="spin" aria-hidden="true" />
      <span>正在读取排班</span>
    </div>
    <div
      v-else-if="!todaySchedule.slots?.length"
      class="kiosk-signal-message"
      role="status"
    >
      <CalendarClock aria-hidden="true" />
      <span>今日暂无排班</span>
    </div>
    <div v-else class="kiosk-signal-track">
      <article
        v-for="(slot, index) in todaySchedule.slots"
        :key="slot.key || `${slot.startTime}-${slot.endTime}`"
        class="kiosk-signal-shift"
        :class="{ current: isCurrentShift(slot.startTime, slot.endTime) }"
        :style="{ '--shift-index': index }"
      >
        <i aria-hidden="true"></i>
        <time>{{ shortTime(slot.startTime) }}–{{ shortTime(slot.endTime) }}</time>
        <strong>{{ assigneeNames(slot.assignees) }}</strong>
        <span>{{ shiftState(slot.startTime, slot.endTime, index) }}</span>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from "vue";
import {
  CalendarClock,
  CalendarX2,
  LoaderCircle,
} from "@lucide/vue";
import type { ScheduleDay } from "../../features/kiosk/types";

const props = defineProps<{
  todaySchedule: ScheduleDay | null;
  scheduleError: string;
  scheduleCount: number;
  weekdayLabel: string;
  now: Date;
}>();

const nextShiftIndex = computed(() => {
  const slots = props.todaySchedule?.slots || [];
  const minutes = nowMinutes();
  let result = -1;
  let nearestStart = Number.POSITIVE_INFINITY;
  slots.forEach((slot, index) => {
    const start = toMinutes(slot.startTime);
    if (start > minutes && start < nearestStart) {
      result = index;
      nearestStart = start;
    }
  });
  return result;
});

function shortTime(value?: string) {
  return value?.slice(0, 5) || "--:--";
}

function assigneeNames(assignees: ScheduleDay["slots"][number]["assignees"]) {
  return assignees?.map((person) => person.name).join("、") || "待安排";
}

function shiftState(start: string | undefined, end: string | undefined, index: number) {
  if (!start || !end) return "";
  if (isCurrentShift(start, end)) return "当前时段";
  if (nowMinutes() < toMinutes(start)) {
    return index === nextShiftIndex.value ? "下一时段" : "待开始";
  }
  return "已结束";
}

function isCurrentShift(start?: string, end?: string) {
  if (!start || !end) return false;
  const minutes = nowMinutes();
  return minutes >= toMinutes(start) && minutes < toMinutes(end);
}

function nowMinutes() {
  const now = props.now;
  return now.getHours() * 60 + now.getMinutes();
}

function toMinutes(value: string) {
  const [hours, minute] = value.slice(0, 5).split(":").map(Number);
  return hours * 60 + minute;
}
</script>

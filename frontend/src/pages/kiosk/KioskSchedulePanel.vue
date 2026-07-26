<template>
  <section class="kiosk-signal-band" aria-labelledby="kiosk-signal-title">
    <header class="kiosk-signal-caption">
      <h2 id="kiosk-signal-title">今日值班信号</h2>
      <span>{{ todaySchedule?.weekdayName || weekdayLabel }} · {{ scheduleCount }} 位部长</span>
    </header>

    <div v-if="scheduleError" class="kiosk-signal-message">
      <CalendarX2 aria-hidden="true" />
      <span>{{ scheduleError }}</span>
    </div>
    <div v-else-if="!todaySchedule" class="kiosk-signal-message">
      <LoaderCircle class="spin" aria-hidden="true" />
      <span>正在读取排班</span>
    </div>
    <div v-else-if="!todaySchedule.slots?.length" class="kiosk-signal-message">
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
        <span>{{ shiftState(slot.startTime, slot.endTime) }}</span>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import {
  CalendarClock,
  CalendarX2,
  LoaderCircle,
} from "@lucide/vue";
import type { ScheduleDay } from "../../features/kiosk/types";

defineProps<{
  todaySchedule: ScheduleDay | null;
  scheduleError: string;
  scheduleCount: number;
  weekdayLabel: string;
}>();

function shortTime(value?: string) {
  return value?.slice(0, 5) || "--:--";
}

function assigneeNames(assignees: ScheduleDay["slots"][number]["assignees"]) {
  return assignees?.map((person) => person.name).join("、") || "待安排";
}

function shiftState(start?: string, end?: string) {
  if (!start || !end) return "";
  if (isCurrentShift(start, end)) return "当前时段";
  return nowMinutes() < toMinutes(start) ? "下一时段" : "已结束";
}

function isCurrentShift(start?: string, end?: string) {
  if (!start || !end) return false;
  const minutes = nowMinutes();
  return minutes >= toMinutes(start) && minutes < toMinutes(end);
}

function nowMinutes() {
  const now = new Date();
  return now.getHours() * 60 + now.getMinutes();
}

function toMinutes(value: string) {
  const [hours, minute] = value.slice(0, 5).split(":").map(Number);
  return hours * 60 + minute;
}
</script>

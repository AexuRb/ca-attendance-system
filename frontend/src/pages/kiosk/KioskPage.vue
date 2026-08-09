<template>
  <main class="kiosk-signal-app">
    <section class="kiosk-focus-shell kiosk-signal-shell">
      <KioskHeader :online="online" :now="currentDate" />
      <KioskSchedulePanel
        :today-schedule="todaySchedule"
        :schedule-error="scheduleError"
        :schedule-count="scheduleCount"
        :weekday-label="weekdayLabel"
      />
      <KioskAttendanceCourt
        v-model:query="query"
        :step="step"
        :date-label="dateLabel"
        :busy="busy"
        :error="error"
        :lookup-result="lookupResult"
        :matches="matches"
        :success-name="successName"
        :success-action="successAction"
        :success-time="successTime"
        @clear-error="clearError"
        @lookup="lookup"
        @reset="reset"
        @select-member="selectMember"
        @submit="submitAttendance"
      />
      <KioskWeekStrip
        :week-schedule="weekSchedule"
        :today-value="todayValue"
      />
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useKioskAttendance } from "../../features/kiosk/useKioskAttendance";
import KioskAttendanceCourt from "./KioskAttendanceCourt.vue";
import KioskHeader from "./KioskHeader.vue";
import KioskSchedulePanel from "./KioskSchedulePanel.vue";
import KioskWeekStrip from "./KioskWeekStrip.vue";

const {
  step,
  query,
  lookupResult,
  matches,
  busy,
  error,
  online,
  todaySchedule,
  weekSchedule,
  scheduleError,
  scheduleCount,
  currentDate,
  successName,
  successAction,
  successTime,
  lookup,
  selectMember,
  submitAttendance,
  clearError,
  reset,
} = useKioskAttendance();

const todayValue = computed(() => localDate(currentDate.value));
const dateLabel = computed(() =>
  new Intl.DateTimeFormat("zh-CN", {
    month: "long",
    day: "numeric",
    weekday: "long",
  }).format(currentDate.value),
);
const weekdayLabel = computed(() =>
  new Intl.DateTimeFormat("zh-CN", {
    weekday: "long",
  }).format(currentDate.value),
);

function localDate(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}
</script>

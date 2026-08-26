<template>
  <div class="weekday-calendar-selector">
    <div class="weekday-calendar-grid" aria-label="允许值班的星期">
      <button
        v-for="day in days"
        :key="day.weekday"
        class="weekday-calendar-day"
        type="button"
        :data-weekday="day.weekday"
        :aria-label="dayAriaLabel(day)"
        :aria-pressed="day.enabled"
        @click="emit('toggle', day.weekday)"
      >
        <span class="weekday-calendar-leaf">
          <span class="weekday-calendar-cap">
            <span>{{ englishDay(day.weekday) }}</span>
            <i aria-hidden="true"></i>
          </span>
          <span class="weekday-calendar-body">
            <strong>{{ shortDay(day) }}</strong>
            <small>{{ String(day.weekday).padStart(2, "0") }}</small>
          </span>
          <span class="weekday-calendar-state">
            {{ day.enabled ? "开放" : "关闭" }}
          </span>
        </span>
      </button>
    </div>
    <p class="weekday-calendar-count" aria-live="polite">
      本周启用 <strong>{{ enabledCount }}</strong> 天
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { DutyWeekdaySetting } from "../../../features/settings/dutyWeekdays";

const props = defineProps<{
  days: DutyWeekdaySetting[];
}>();

const emit = defineEmits<{
  toggle: [weekday: number];
}>();

const englishDays = ["", "MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"];
const chineseDays = ["", "周一", "周二", "周三", "周四", "周五", "周六", "周日"];

const enabledCount = computed(
  () => props.days.filter((day) => day.enabled).length,
);

function englishDay(weekday: number) {
  return englishDays[weekday] || String(weekday);
}

function shortDay(day: DutyWeekdaySetting) {
  return day.weekday_name?.replace("星期", "周") || chineseDays[day.weekday];
}

function dayAriaLabel(day: DutyWeekdaySetting) {
  const status = day.enabled ? "开放" : "关闭";
  const action = day.enabled ? "关闭" : "开放";
  return `${shortDay(day)}，当前${status}，点击${action}`;
}
</script>

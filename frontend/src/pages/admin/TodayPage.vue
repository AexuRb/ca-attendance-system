<template>
  <div class="page-stack today-page">
    <PageHeader
      title="今日"
      :meta="todayLabel"
    >
      <template #actions>
        <RouterLink class="button secondary today-kiosk-link" :to="{ name: 'kiosk' }">
          <Eye aria-hidden="true" />
          查看签到台
        </RouterLink>
      </template>
    </PageHeader>
    <LoadingBlock v-if="busy && !dashboard" />
    <template v-else>
      <TodayPriority :dashboard="dashboard || {}" />
      <div class="today-grid">
        <TodayRecords
          :records="records.slice(0, 8)"
          :record-count="dashboard?.todayRecordCount || records.length"
          :valid-hours="dashboard?.todayValidHours || 0"
        />
        <TodaySchedule
          :schedule="schedule"
          :can-schedule="canSchedule"
          :weekday-label="weekdayLabel"
        />
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { Eye } from "@lucide/vue";
import { RouterLink } from "vue-router";
import PageHeader from "../../shared/ui/PageHeader.vue";
import LoadingBlock from "../../shared/ui/LoadingBlock.vue";
import TodayPriority from "./today/TodayPriority.vue";
import TodayRecords from "./today/TodayRecords.vue";
import TodaySchedule from "./today/TodaySchedule.vue";
import { get } from "../../shared/api";
import { useSession } from "../../app/session";
import type {
  TodayAttendanceRecord,
  TodayDashboardData,
  TodayScheduleData,
} from "./today/types";

const { user } = useSession();
const busy = ref(false);
const dashboard = ref<TodayDashboardData | null>(null);
const schedule = ref<TodayScheduleData | null>(null);
const records = ref<TodayAttendanceRecord[]>([]);
let timer = 0;

const now = new Date();
const today = localDate(now);
const todayLabel = new Intl.DateTimeFormat("zh-CN", {
  year: "numeric",
  month: "long",
  day: "numeric",
  weekday: "long",
}).format(now);
const weekdayLabel = new Intl.DateTimeFormat("zh-CN", {
  weekday: "long",
}).format(now);
const canSchedule = computed(
  () => user.value?.role === "PRESIDENT" || user.value?.role === "ADMIN",
);

onMounted(() => {
  load();
  timer = window.setInterval(load, 60_000);
});

onBeforeUnmount(() => window.clearInterval(timer));

async function load() {
  busy.value = true;
  try {
    [dashboard.value, schedule.value, records.value] = await Promise.all([
      get<TodayDashboardData>(`/api/stats/dashboard?date=${today}`),
      get<TodayScheduleData>("/api/public/schedules/today"),
      get<TodayAttendanceRecord[]>(`/api/attendance?from=${today}&to=${today}`),
    ]);
  } finally {
    busy.value = false;
  }
}

function localDate(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}
</script>

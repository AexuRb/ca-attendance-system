<template>
  <div class="today-command-page">
    <div v-if="error" class="inline-alert danger today-load-error" role="alert">
      <span>{{ error }}</span>
      <button class="button secondary small" type="button" data-action="retry-today" @click="refresh">重试</button>
    </div>
    <TodayCommandCenter
      v-model="commandInput"
      :date-label="todayLabel"
      :role-name="currentRoleLabel"
      :role="currentRole"
      :quick-actions="quickActions"
      :error-message="commandError"
      :loading="loading && !dashboard"
      @execute="executeCommand"
      @clear-error="commandError = ''"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import TodayCommandCenter from "./today/TodayCommandCenter.vue";
import { get } from "../../shared/api";
import { roleLabel } from "../../app/adminNavigation";
import { useSession } from "../../app/session";
import { resolveCommand } from "../../features/command-center/commandParser";
import { useLatestRequest } from "../../shared/composables/useLatestRequest";
import { notify } from "../../shared/composables/useToast";
import type { Role } from "../../shared/types";
import { buildTodayQuickActions } from "./today/todayQuickActions";
import type { TodayDashboardData, TodayScheduleData } from "./today/types";

const { user } = useSession();
const router = useRouter();
const request = useLatestRequest();
const { loading, error } = request;
const dashboard = ref<TodayDashboardData | null>(null);
const schedule = ref<TodayScheduleData | null>(null);
const commandInput = ref("");
const commandError = ref("");
const currentDate = ref(new Date());
let timer = 0;

const today = computed(() => localDate(currentDate.value));
const todayLabel = computed(() =>
  new Intl.DateTimeFormat("zh-CN", { month: "long", day: "numeric" }).format(currentDate.value),
);
const currentRole = computed<Role>(() => user.value?.role || "MINISTER");
const currentRoleLabel = computed(() => roleLabel(currentRole.value));
const canSchedule = computed(() =>
  user.value?.role === "PRESIDENT" || user.value?.role === "ADMIN",
);
const missingScheduleCount = computed(() =>
  canSchedule.value
    ? schedule.value?.slots?.filter((slot) => !slot.assignees.length).length || 0
    : 0,
);
const quickActions = computed(() => buildTodayQuickActions(
  dashboard.value,
  missingScheduleCount.value,
  canSchedule.value,
  currentRole.value,
));

onMounted(() => {
  refresh();
  timer = window.setInterval(refresh, 60_000);
  window.addEventListener("focus", refresh);
});
onBeforeUnmount(() => {
  window.clearInterval(timer);
  window.removeEventListener("focus", refresh);
});

function refresh() {
  void load();
}

async function load() {
  currentDate.value = new Date();
  const queryDate = today.value;
  const result = await request.run(
    (signal) =>
      Promise.all([
        get<TodayDashboardData>("/api/stats/dashboard?date=" + queryDate, { signal }),
        get<TodayScheduleData>("/api/public/schedules/today", { signal }),
      ]),
    "今日数据加载失败",
  );
  if (!result) return;
  [dashboard.value, schedule.value] = result;
}

async function executeCommand(value: string) {
  const resolution = resolveCommand(value, currentRole.value, currentDate.value);
  if (resolution.kind !== "resolved") {
    commandError.value = resolution.message;
    return;
  }
  commandInput.value = resolution.canonical;
  commandError.value = "";
  await router.push(resolution.target);
  notify(resolution.feedback, "success");
}

function localDate(date: Date) {
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, "0"),
    String(date.getDate()).padStart(2, "0"),
  ].join("-");
}
</script>

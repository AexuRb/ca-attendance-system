<template>
  <ExpandedWorkspaceShell class="overview-today">
    <template #heading>
      <h1>今日概览</h1>
      <time :datetime="dateKey">{{ dateLabel }}</time>
    </template>
    <div class="today-layout" :class="{ 'has-roster': canReadRoster }">
      <div class="today-welcome"><p class="today-entry-message">{{ welcome }}</p></div>
      <TodayRosterPanel v-if="canReadRoster" :roster="roster" :loading="rosterLoading"
        :failed="Boolean(rosterError)" @retry="refreshRoster" />
      <div class="today-lower">
        <section class="today-shortcuts" aria-labelledby="today-shortcuts-heading">
          <h2 id="today-shortcuts-heading">常用功能</h2>
          <div class="today-shortcut-list">
            <RouterLink v-for="action in actions" :key="action.label" data-button-kind="soft"
              class="button secondary" :to="action.target">
              <component :is="action.icon" aria-hidden="true" />{{ action.label }}
            </RouterLink>
          </div>
        </section>
        <TodaySummary :dashboard="dashboard" :can-review="canReview"
          :loading="loading && !dashboard" :load-error="error" @retry="refreshSummary" />
      </div>
    </div>
  </ExpandedWorkspaceShell>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { RouterLink } from "vue-router";
import { BarChart3, ClipboardCheck, ClipboardList, DatabaseBackup, Wrench } from "@lucide/vue";
import ExpandedWorkspaceShell from "../../layouts/ExpandedWorkspaceShell.vue";
import { useSession } from "../../app/session";
import { get } from "../../shared/api";
import { canConductSkillAssessments, canReviewAttendance, isRemoteMinister } from "../../shared/access/entryAccess";
import { useLatestRequest } from "../../shared/composables/useLatestRequest";
import TodayRosterPanel from "./today/TodayRosterPanel.vue";
import TodaySummary from "./today/TodaySummary.vue";
import type { TodayDashboardData, TodayRoster } from "./today/types";
import "../../styles/workspace.css";
import "../../features/today/overview-presentation.css";
import "../../features/today/display.css";

const { user, state } = useSession();
const request = useLatestRequest();
const { loading, error } = request;
const rosterRequest = useLatestRequest();
const { loading: rosterLoading, error: rosterError } = rosterRequest;
const dashboard = ref<TodayDashboardData | null>(null);
const roster = ref<TodayRoster | null>(null);
const today = ref(new Date());
const dateKey = computed(() => localDate(today.value));
const dateLabel = computed(() => new Intl.DateTimeFormat("zh-CN", {
  month: "long", day: "numeric", weekday: "long",
}).format(today.value));
const welcome = computed(() => {
  const name = user.value?.name?.trim();
  return name ? `欢迎回来，${name}。` : "欢迎回来。";
});
const canReview = computed(() => canReviewAttendance(user.value?.role, state.access.mode));
const isLeader = computed(() => user.value?.role === "PRESIDENT" || user.value?.role === "ADMIN");
const canReadRoster = computed(() => isLeader.value || canConductSkillAssessments(user.value?.role, state.access.mode));
const actions = computed(() => [
  { label: "签到审核", icon: ClipboardCheck, target: { name: "reviews" }, visible: canReview.value },
  { label: "今日值班记录", icon: ClipboardList, target: { name: "attendance", query: { from: dateKey.value, to: dateKey.value } }, visible: true },
  { label: "本周统计", icon: BarChart3, target: { name: "stats", query: { preset: "week" } }, visible: true },
  { label: "新建维修", icon: Wrench, target: { name: "repairs", query: { intent: "new" } }, visible: !isRemoteMinister(user.value?.role, state.access.mode) },
  { label: "备份与恢复", icon: DatabaseBackup, target: { name: "data", query: { tab: "backups" } }, visible: isLeader.value },
].filter(action => action.visible));
let timer: number | undefined;

onMounted(() => {
  void refresh();
  timer = window.setInterval(refresh, 30_000);
  window.addEventListener("focus", refresh);
});
onBeforeUnmount(() => {
  window.clearInterval(timer);
  window.removeEventListener("focus", refresh);
});
watch(() => [user.value?.id, user.value?.role, state.access.mode], () => {
  clearOverview();
  void refresh();
});

function localDate(date: Date) {
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, "0"),
    String(date.getDate()).padStart(2, "0"),
  ].join("-");
}

function clearOverview() {
  dashboard.value = null;
  roster.value = null;
}

function refresh() {
  const now = new Date();
  if (localDate(now) !== dateKey.value) clearOverview();
  today.value = now;
  void refreshSummary();
  if (canReadRoster.value) void refreshRoster();
}

async function refreshSummary() {
  const result = await request.run(
    signal => get<TodayDashboardData>(`/api/stats/dashboard?date=${dateKey.value}`, { signal }),
    "今日数据加载失败",
  );
  if (result) dashboard.value = result;
}

async function refreshRoster() {
  const result = await rosterRequest.run(
    signal => get<TodayRoster>("/api/skill-assessments/overview", { signal }),
    "当前在场名单读取失败",
  );
  if (result && canReadRoster.value) roster.value = result;
}
</script>

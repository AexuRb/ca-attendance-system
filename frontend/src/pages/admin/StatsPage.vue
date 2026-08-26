<template>
  <div class="page-stack">
    <PageHeader
      title="值班统计"
      ><template #actions
        ><button class="button primary" :disabled="actions.isPending('export') || Boolean(filterError)" @click="exportExcel">
          <Download />导出 Excel
        </button></template
      ></PageHeader
    >
    <form class="filter-bar" @submit.prevent="loadCustom">
      <div class="segmented">
        <button
          v-for="option in presets"
          :key="option.id"
          type="button"
          :class="{ active: preset === option.id }"
          @click="applyPreset(option.id)"
        >
          {{ option.label }}
        </button>
      </div>
      <label><span>开始日期</span><input v-model="from" type="date" /></label
      ><label><span>结束日期</span><input v-model="to" type="date" /></label
      ><button class="button secondary" type="submit"><BarChart3 />统计</button>
    </form>
    <div v-if="displayError" class="inline-alert danger" role="alert">
      <span>{{ displayError }}</span>
      <button
        v-if="loadError"
        class="button secondary small"
        type="button"
        data-action="retry-stats"
        @click="load"
      >
        重试
      </button>
    </div>
    <section class="metric-strip compact">
      <article>
        <span>统计成员</span><strong>{{ rows.length }}</strong
        ><small>人</small>
      </article>
      <article>
        <span>总有效时长</span><strong>{{ totalHours }}</strong
        ><small>小时</small>
      </article>
      <article>
        <span>值班记录</span><strong>{{ totalAttendance }}</strong
        ><small>次</small>
      </article>
      <article>
        <span>培训记录</span><strong>{{ totalTraining }}</strong
        ><small>次</small>
      </article>
    </section>
    <LoadingBlock v-if="loading && !hasData" />
    <EmptyState v-else-if="!hasData && !loadError" title="该时间段暂无有效统计" />
    <WeeklyStatsTable
      v-else-if="preset === 'week'"
      :detail="weeklyDetail"
    />
    <div v-else class="table-shell">
      <table>
        <thead>
          <tr>
            <th>#</th>
            <th>成员</th>
            <th>年级</th>
            <th>角色</th>
            <th>值班时长</th>
            <th>培训时长</th>
            <th>合计时长</th>
            <th>有效次数</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="(item, index) in rows"
            :key="item.userId || item.studentNo"
          >
            <td>
              <span class="rank" :data-rank="index + 1">{{ index + 1 }}</span>
            </td>
            <td>
              <strong>{{ item.name }}</strong
              ><small>{{ item.studentNo }}</small>
            </td>
            <td>{{ item.grade || "—" }}</td>
            <td>{{ roleLabel(item.role) }}</td>
            <td>{{ number(item.attendanceHours ?? item.dutyHours) }} 小时</td>
            <td>{{ number(item.trainingHours) }} 小时</td>
            <td>
              <strong class="total-hours">{{ number(item.totalHours) }}</strong>
              小时
            </td>
            <td>{{ effectiveDutyCount(item) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { BarChart3, Download } from "@lucide/vue";
import PageHeader from "../../shared/ui/PageHeader.vue";
import LoadingBlock from "../../shared/ui/LoadingBlock.vue";
import EmptyState from "../../shared/ui/EmptyState.vue";
import WeeklyStatsTable from "../../features/stats/WeeklyStatsTable.vue";
import { downloadBlob, get } from "../../shared/api";
import { useAsyncTask } from "../../shared/composables/useAsyncTask";
import { useLatestRequest } from "../../shared/composables/useLatestRequest";
import { usePendingActions } from "../../shared/composables/usePendingActions";
import { dateRangeError } from "../../shared/validation/dateRange";
import {
  effectiveDutyCount,
  type StatsSummaryRow,
} from "../../features/stats/statsSummary";
import type { WeeklyStatsDetail } from "../../features/stats/weeklyStats";
const task = useAsyncTask();
const request = useLatestRequest();
const actions = usePendingActions();
const { loading, error: loadError } = request;
const rows = ref<StatsSummaryRow[]>([]);
const weeklyDetail = ref<WeeklyStatsDetail>({
  days: [],
  users: [],
  cells: {},
});
const from = ref("");
const to = ref("");
const preset = ref<"week" | "month" | "year" | "custom">("week");
const presets = [
  { id: "week", label: "本周" },
  { id: "month", label: "本月" },
  { id: "year", label: "本年" },
] as const;
const totalHours = computed(() =>
  number(rows.value.reduce((s, i) => s + Number(i.totalHours || 0), 0)),
);
const totalAttendance = computed(() =>
  rows.value.reduce(
    (s, i) => s + Number(i.attendanceCount || 0),
    0,
  ),
);
const totalTraining = computed(() =>
  rows.value.reduce((s, i) => s + Number(i.trainingCount || 0), 0),
);
const hasData = computed(() =>
  preset.value === "week"
    ? weeklyDetail.value.users.length > 0
    : rows.value.length > 0,
);
const filterError = computed(() => dateRangeError(from.value, to.value));
const displayError = computed(() => filterError.value || loadError.value);
onMounted(() => {
  applyPreset("week");
});
function applyPreset(id: "week" | "month" | "year") {
  preset.value = id;
  const now = new Date();
  to.value = date(now);
  const start = new Date(now);
  if (id === "week") start.setDate(now.getDate() - ((now.getDay() + 6) % 7));
  else if (id === "month") start.setDate(1);
  else if (id === "year") start.setMonth(0, 1);
  from.value = date(start);
  void load();
}
async function load() {
  if (filterError.value) return;
  const snapshot = { from: from.value, to: to.value, preset: preset.value };
  const query = new URLSearchParams({ from: snapshot.from, to: snapshot.to });
  const value = await request.run(async (signal) => {
    const summary = get<StatsSummaryRow[]>(`/api/stats/summary?${query}`, { signal });
    if (snapshot.preset !== "week") {
      return { summary: await summary, weekly: null };
    }
    const [summaryRows, weekly] = await Promise.all([
      summary,
      get<WeeklyStatsDetail>(`/api/stats/weekly-detail?${query}`, { signal }),
    ]);
    return { summary: summaryRows, weekly };
  }, "统计数据加载失败");
  if (!value) return;
  rows.value = value.summary;
  weeklyDetail.value = value.weekly || { days: [], users: [], cells: {} };
}
function loadCustom() {
  preset.value = "custom";
  void load();
}
async function exportExcel() {
  if (filterError.value) return;
  const snapshot = { from: from.value, to: to.value };
  await actions.run("export", async () => {
    const blob = await task.run(() =>
      get<Blob>(`/api/stats/export?from=${snapshot.from}&to=${snapshot.to}`),
    );
    if (blob) downloadBlob(blob, `值班统计_${snapshot.from}_${snapshot.to}.xlsx`);
  });
}
const number = (v: number | string | null | undefined) =>
  Number(v || 0).toFixed(Number(v || 0) % 1 ? 1 : 0);
const roleLabels: Record<string, string> = {
  MEMBER: "成员",
  MINISTER: "部长",
  PRESIDENT: "会长",
  ADMIN: "管理员",
};
const roleLabel = (v: string) =>
  roleLabels[v] || v;
function date(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}
</script>

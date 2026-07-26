<template>
  <div class="page-stack">
    <PageHeader
      eyebrow="DUTY / ANALYTICS"
      title="值班统计"
      description="签到与培训时长按成员合并统计。"
      ><template #actions
        ><button class="button primary" @click="exportExcel">
          <Download />导出 Excel
        </button></template
      ></PageHeader
    >
    <form class="filter-bar" @submit.prevent="load">
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
    <LoadingBlock v-if="busy && !rows.length" />
    <EmptyState v-else-if="!rows.length" title="该时间段暂无有效统计" />
    <div v-else class="table-shell">
      <table>
        <thead>
          <tr>
            <th>#</th>
            <th>成员</th>
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
            <td>{{ roleLabel(item.role) }}</td>
            <td>{{ number(item.attendanceHours ?? item.dutyHours) }} 小时</td>
            <td>{{ number(item.trainingHours) }} 小时</td>
            <td>
              <strong class="total-hours">{{ number(item.totalHours) }}</strong>
              小时
            </td>
            <td>{{ item.validCount ?? item.attendanceCount ?? 0 }}</td>
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
import { downloadBlob, get } from "../../shared/api";
import { useAsyncTask } from "../../shared/composables/useAsyncTask";
const { busy, run } = useAsyncTask();
const rows = ref<any[]>([]);
const from = ref("");
const to = ref("");
const preset = ref("year");
const presets = [
  { id: "week", label: "本周" },
  { id: "month", label: "本月" },
  { id: "year", label: "本年" },
];
const totalHours = computed(() =>
  number(rows.value.reduce((s, i) => s + Number(i.totalHours || 0), 0)),
);
const totalAttendance = computed(() =>
  rows.value.reduce(
    (s, i) => s + Number(i.attendanceCount || i.validCount || 0),
    0,
  ),
);
const totalTraining = computed(() =>
  rows.value.reduce((s, i) => s + Number(i.trainingCount || 0), 0),
);
onMounted(() => {
  applyPreset("year");
});
function applyPreset(id: string) {
  preset.value = id;
  const now = new Date();
  to.value = date(now);
  const start = new Date(now);
  if (id === "week") start.setDate(now.getDate() - ((now.getDay() + 6) % 7));
  else if (id === "month") start.setDate(1);
  else if (id === "year") start.setMonth(0, 1);
  from.value = date(start);
  load();
}
async function load() {
  const value = await run(() =>
    get<any[]>(`/api/stats/summary?from=${from.value}&to=${to.value}`),
  );
  if (value) rows.value = value;
}
async function exportExcel() {
  const blob = await run(() =>
    get<Blob>(`/api/stats/export?from=${from.value}&to=${to.value}`),
  );
  if (blob) downloadBlob(blob, `值班统计_${from.value}_${to.value}.xlsx`);
}
const number = (v: any) => Number(v || 0).toFixed(Number(v || 0) % 1 ? 1 : 0);
const roleLabel = (v: string) =>
  (
    ({
      MEMBER: "成员",
      MINISTER: "部长",
      PRESIDENT: "会长",
      ADMIN: "管理员",
    }) as any
  )[v] || v;
function date(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}
</script>

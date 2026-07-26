<template>
  <div class="page-stack settings-page">
    <PageHeader
      eyebrow="SYSTEM / SETTINGS"
      title="系统设置"
      description="设置每周开放日和值班时间段，排班只能选用这里保存的时段。"
    />
    <section class="panel setting-section">
      <div class="section-heading">
        <div>
          <p class="eyebrow">WEEKDAYS</p>
          <h2>值班星期</h2>
          <span>未开放日期仍可签到签退，是否计入有效时长由审核结果决定。</span>
        </div>
        <button class="button primary small" @click="saveWeekdays">
          <Save />保存星期
        </button>
      </div>
      <div class="weekday-selector">
        <label
          v-for="day in weekdays"
          :key="day.weekday"
          :class="{ selected: day.enabled }"
          ><input v-model="day.enabled" type="checkbox" /><span>{{
            shortDay(day)
          }}</span
          ><strong>{{ day.enabled ? "开放" : "关闭" }}</strong
          ><Check v-if="day.enabled"
        /></label>
      </div>
    </section>
    <section class="panel setting-section">
      <div class="section-heading">
        <div>
          <p class="eyebrow">DUTY PERIODS</p>
          <h2>值班时间段</h2>
          <span>保存后签到台与固定排班会同步使用。</span>
        </div>
        <button class="button secondary small" @click="addPeriod">
          <Plus />新增时段
        </button>
      </div>
      <div class="period-editor">
        <article v-for="(period, index) in periods" :key="index">
          <span class="period-index">{{
            String(index + 1).padStart(2, "0")
          }}</span
          ><label class="field"
            ><span>开始</span
            ><input v-model="period.startTime" type="time" /></label
          ><ArrowRight /><label class="field"
            ><span>结束</span
            ><input v-model="period.endTime" type="time" /></label
          ><strong>{{ duration(period) }}</strong
          ><button
            class="icon-button danger-ghost"
            title="删除时段"
            @click="periods.splice(index, 1)"
          >
            <Trash2 />
          </button>
        </article>
        <EmptyState v-if="!periods.length" title="还没有值班时间段" />
      </div>
      <div v-if="periodError" class="inline-alert danger">
        <TriangleAlert /><span>{{ periodError }}</span>
      </div>
      <footer class="panel-footer">
        <span>共 {{ periods.length }} 个时间段</span
        ><button class="button primary" @click="savePeriods">
          <Save />保存时间段
        </button>
      </footer>
    </section>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import {
  ArrowRight,
  Check,
  Plus,
  Save,
  Trash2,
  TriangleAlert,
} from "@lucide/vue";
import PageHeader from "../../shared/ui/PageHeader.vue";
import EmptyState from "../../shared/ui/EmptyState.vue";
import { get, put } from "../../shared/api";
import { useAsyncTask } from "../../shared/composables/useAsyncTask";
import { normalizeDutyWeekdays } from "../../features/settings/dutyWeekdays";
const { run } = useAsyncTask();
const weekdays = ref<any[]>([]);
const periods = ref<any[]>([]);
const periodError = computed(() => validatePeriods(periods.value));
onMounted(async () => {
  const [weekdayRows, dutyPeriods] = await Promise.all([
    get("/api/settings/weekdays"),
    get("/api/settings/duty-periods"),
  ]);
  weekdays.value = normalizeDutyWeekdays(weekdayRows as any[]);
  periods.value = dutyPeriods as any[];
});
async function saveWeekdays() {
  await run(
    () =>
      put("/api/settings/weekdays", {
        enabledWeekdays: weekdays.value
          .filter((i) => i.enabled)
          .map((i) => i.weekday),
      }),
    "值班星期已保存",
  );
}
function addPeriod() {
  const end = periods.value.at(-1)?.endTime?.slice(0, 5) || "14:00";
  periods.value.push({ startTime: end, endTime: addHours(end, 2) });
}
async function savePeriods() {
  if (periodError.value) return;
  const saved = await run(
    () =>
      put<any[]>("/api/settings/duty-periods", {
        periods: periods.value.map((i) => ({
          startTime: i.startTime.slice(0, 5),
          endTime: i.endTime.slice(0, 5),
        })),
      }),
    "值班时间段已保存",
  );
  if (saved) periods.value = saved;
}
function validatePeriods(values: any[]) {
  for (const [index, item] of values.entries()) {
    if (!item.startTime || !item.endTime) return `第 ${index + 1} 个时段不完整`;
    if (item.startTime >= item.endTime)
      return `第 ${index + 1} 个时段的结束时间必须晚于开始时间`;
  }
  const sorted = [...values].sort((a, b) =>
    a.startTime.localeCompare(b.startTime),
  );
  for (let i = 1; i < sorted.length; i++)
    if (sorted[i].startTime < sorted[i - 1].endTime)
      return "值班时间段不能重叠";
  return "";
}
function addHours(value: string, hours: number) {
  const [h, m] = value.split(":").map(Number);
  return `${String(Math.min(23, h + hours)).padStart(2, "0")}:${String(m).padStart(2, "0")}`;
}
function duration(v: any) {
  if (!v.startTime || !v.endTime || v.endTime <= v.startTime) return "—";
  const [sh, sm] = v.startTime.split(":").map(Number),
    [eh, em] = v.endTime.split(":").map(Number);
  return `${((eh * 60 + em - sh * 60 - sm) / 60).toFixed(1).replace(".0", "")} 小时`;
}
function shortDay(v: any) {
  return (
    v.weekday_name ||
    ["", "周一", "周二", "周三", "周四", "周五", "周六", "周日"][v.weekday]
  );
}
</script>

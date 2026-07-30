<template>
  <div class="page-stack settings-page">
    <PageHeader
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
        <article
          v-for="(period, index) in periods"
          :key="index"
          :class="{ disabled: !period.enabled }"
        >
          <div class="period-order">
            <span class="period-index">{{
              String(index + 1).padStart(2, "0")
            }}</span>
            <span>
              <button
                class="icon-button ghost"
                title="上移"
                :disabled="index === 0"
                @click="move(index, -1)"
              >
                <ChevronUp />
              </button>
              <button
                class="icon-button ghost"
                title="下移"
                :disabled="index === periods.length - 1"
                @click="move(index, 1)"
              >
                <ChevronDown />
              </button>
            </span>
          </div>
          <label class="field"
            ><span>开始</span
            ><input v-model="period.startTime" type="time" /></label
          ><ArrowRight /><label class="field"
            ><span>结束</span
            ><input v-model="period.endTime" type="time" /></label
          ><strong>{{ duration(period) }}</strong>
          <label class="period-enabled-toggle"
            ><input v-model="period.enabled" type="checkbox" /><span>{{
              period.enabled ? "启用" : "停用"
            }}</span></label
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
        ><button
          class="button primary"
          :disabled="Boolean(periodError)"
          @click="savePeriods"
        >
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
  ChevronDown,
  ChevronUp,
  Plus,
  Save,
  Trash2,
  TriangleAlert,
} from "@lucide/vue";
import PageHeader from "../../shared/ui/PageHeader.vue";
import EmptyState from "../../shared/ui/EmptyState.vue";
import { get, put } from "../../shared/api";
import { useAsyncTask } from "../../shared/composables/useAsyncTask";
import {
  normalizeDutyWeekdays,
  type DutyWeekdaySetting,
} from "../../features/settings/dutyWeekdays";
import {
  moveDutyPeriod,
  validateDutyPeriods,
  type DutyPeriod,
} from "../../features/settings/dutyPeriods";
const { run } = useAsyncTask();
const weekdays = ref<DutyWeekdaySetting[]>([]);
const periods = ref<DutyPeriod[]>([]);
const periodError = computed(() => validateDutyPeriods(periods.value));
onMounted(async () => {
  const [weekdayRows, dutyPeriods] = await Promise.all([
    get<DutyWeekdaySetting[]>("/api/settings/weekdays"),
    get<DutyPeriod[]>("/api/settings/duty-periods"),
  ]);
  weekdays.value = normalizeDutyWeekdays(weekdayRows);
  periods.value = dutyPeriods.map((period) => ({
    ...period,
    enabled: period.enabled !== false,
  }));
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
  periods.value.push({
    startTime: end,
    endTime: addHours(end, 2),
    enabled: true,
  });
}
function move(index: number, direction: -1 | 1) {
  periods.value = moveDutyPeriod(periods.value, index, direction);
}
async function savePeriods() {
  if (periodError.value) return;
  const saved = await run(
    () =>
      put<DutyPeriod[]>("/api/settings/duty-periods", {
        periods: periods.value.map((i) => ({
          startTime: i.startTime.slice(0, 5),
          endTime: i.endTime.slice(0, 5),
          enabled: i.enabled,
        })),
      }),
    "值班时间段已保存",
  );
  if (saved) periods.value = saved;
}
function addHours(value: string, hours: number) {
  const [h, m] = value.split(":").map(Number);
  return `${String(Math.min(23, h + hours)).padStart(2, "0")}:${String(m).padStart(2, "0")}`;
}
function duration(v: DutyPeriod) {
  if (!v.startTime || !v.endTime || v.endTime <= v.startTime) return "—";
  const [sh, sm] = v.startTime.split(":").map(Number),
    [eh, em] = v.endTime.split(":").map(Number);
  return `${((eh * 60 + em - sh * 60 - sm) / 60).toFixed(1).replace(".0", "")} 小时`;
}
function shortDay(v: DutyWeekdaySetting) {
  return (
    v.weekday_name ||
    ["", "周一", "周二", "周三", "周四", "周五", "周六", "周日"][v.weekday]
  );
}
</script>

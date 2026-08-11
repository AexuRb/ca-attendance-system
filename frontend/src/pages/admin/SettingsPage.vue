<template>
  <div class="page-stack settings-page">
    <PageHeader
      title="系统设置"
      description="设置开放日、值班时间段和有效时长规则。"
    />
    <section class="panel setting-section">
      <div class="section-heading">
        <div>
          <p class="eyebrow">WEEKDAYS</p>
          <h2>值班星期</h2>
          <span>未开放日期仍可签到签退，计时结果由审核和下方规则共同决定。</span>
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
          ><Check v-if="day.enabled" aria-hidden="true"
        /></label>
      </div>
    </section>
    <section class="panel setting-section attendance-policy-section">
      <div class="section-heading">
        <div>
          <p class="eyebrow">ATTENDANCE POLICY</p>
          <h2>有效时长规则</h2>
          <span>规则在签到时保存，后续修改不会改变历史记录。</span>
        </div>
        <button
          v-if="canEditAttendancePolicy"
          class="button primary small"
          @click="saveAttendancePolicy"
        >
          <Save />保存规则
        </button>
      </div>
      <div class="attendance-policy-grid">
        <label
          class="attendance-policy-option"
          :class="{ selected: attendancePolicy.requireDutyDay }"
        >
          <span class="attendance-policy-icon"
            ><CalendarCheck2 aria-hidden="true"
          /></span>
          <span class="attendance-policy-copy">
            <strong>强制值班日</strong>
            <small>非开放日记录审核通过后仍不计时</small>
          </span>
          <span class="attendance-policy-switch">
            <input
              v-model="attendancePolicy.requireDutyDay"
              type="checkbox"
              role="switch"
              :disabled="!canEditAttendancePolicy"
            />
            <i aria-hidden="true"></i>
          </span>
        </label>
        <label
          class="attendance-policy-option"
          :class="{ selected: attendancePolicy.requireDutyPeriod }"
        >
          <span class="attendance-policy-icon"
            ><Clock3 aria-hidden="true"
          /></span>
          <span class="attendance-policy-copy">
            <strong>强制值班时段</strong>
            <small>时段外记录审核通过后仍不计时</small>
          </span>
          <span class="attendance-policy-switch">
            <input
              v-model="attendancePolicy.requireDutyPeriod"
              type="checkbox"
              role="switch"
              :disabled="!canEditAttendancePolicy"
            />
            <i aria-hidden="true"></i>
          </span>
        </label>
      </div>
      <p v-if="!canEditAttendancePolicy" class="setting-readonly-note">
        当前规则仅管理员可以修改。
      </p>
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
                aria-label="上移"
                type="button"
                :disabled="index === 0"
                @click="move(index, -1)"
              >
                <ChevronUp aria-hidden="true" />
              </button>
              <button
                class="icon-button ghost"
                title="下移"
                aria-label="下移"
                type="button"
                :disabled="index === periods.length - 1"
                @click="move(index, 1)"
              >
                <ChevronDown aria-hidden="true" />
              </button>
            </span>
          </div>
          <label class="field period-start"
            ><span>开始</span
            ><input
              v-model="period.startTime"
              :name="`period-${index + 1}-start`"
              type="time"
            /></label
          ><ArrowRight class="period-arrow" aria-hidden="true" /><label
            class="field period-end"
            ><span>结束</span
            ><input
              v-model="period.endTime"
              :name="`period-${index + 1}-end`"
              type="time"
            /></label
          ><strong class="period-duration">{{ duration(period) }}</strong>
          <label class="period-enabled-toggle"
            ><input
              v-model="period.enabled"
              :name="`period-${index + 1}-enabled`"
              :aria-label="`第 ${index + 1} 个时段是否启用`"
              type="checkbox"
            /><span>{{
              period.enabled ? "启用" : "停用"
            }}</span></label
          ><button
            class="icon-button danger-ghost period-delete"
            title="删除时段"
            aria-label="删除时段"
            type="button"
            @click="periods.splice(index, 1)"
          >
            <Trash2 aria-hidden="true" />
          </button>
        </article>
        <EmptyState v-if="!periods.length" title="还没有值班时间段" />
      </div>
      <div v-if="periodError" class="inline-alert danger" role="alert">
        <TriangleAlert aria-hidden="true" /><span>{{ periodError }}</span>
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
  CalendarCheck2,
  Check,
  ChevronDown,
  ChevronUp,
  Clock3,
  Plus,
  Save,
  Trash2,
  TriangleAlert,
} from "@lucide/vue";
import PageHeader from "../../shared/ui/PageHeader.vue";
import EmptyState from "../../shared/ui/EmptyState.vue";
import { get, put } from "../../shared/api";
import { useAsyncTask } from "../../shared/composables/useAsyncTask";
import { useSession } from "../../app/session";
import {
  normalizeDutyWeekdays,
  type DutyWeekdaySetting,
} from "../../features/settings/dutyWeekdays";
import {
  moveDutyPeriod,
  validateDutyPeriods,
  type DutyPeriod,
} from "../../features/settings/dutyPeriods";
import {
  canManageAttendancePolicy,
  normalizeAttendancePolicy,
  type AttendancePolicy,
} from "../../features/settings/attendancePolicy";
const { run } = useAsyncTask();
const { user } = useSession();
const weekdays = ref<DutyWeekdaySetting[]>([]);
const periods = ref<DutyPeriod[]>([]);
const attendancePolicy = ref<AttendancePolicy>(normalizeAttendancePolicy());
const canEditAttendancePolicy = computed(() =>
  canManageAttendancePolicy(user.value?.role),
);
const periodError = computed(() => validateDutyPeriods(periods.value));
onMounted(async () => {
  const [weekdayRows, dutyPeriods, policy] = await Promise.all([
    get<DutyWeekdaySetting[]>("/api/settings/weekdays"),
    get<DutyPeriod[]>("/api/settings/duty-periods"),
    get<AttendancePolicy>("/api/settings/attendance-policy"),
  ]);
  weekdays.value = normalizeDutyWeekdays(weekdayRows);
  periods.value = dutyPeriods.map((period) => ({
    ...period,
    enabled: period.enabled !== false,
  }));
  attendancePolicy.value = normalizeAttendancePolicy(policy);
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
async function saveAttendancePolicy() {
  if (!canEditAttendancePolicy.value) return;
  const saved = await run(
    () =>
      put<AttendancePolicy>(
        "/api/settings/attendance-policy",
        attendancePolicy.value,
      ),
    "有效时长规则已保存",
  );
  if (saved) attendancePolicy.value = normalizeAttendancePolicy(saved);
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

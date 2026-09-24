<template>
  <section class="duty-time-workspace setting-section" :class="{ 'sw-duty': refined }">
    <aside class="duty-time-side">
      <section id="settings-policy" class="duty-policy-panel" :class="{ 'sw-section sw-policy': refined }">
        <div class="duty-section-heading" :class="{ 'sw-heading': refined }">
          <div>
            <p v-if="!refined" class="eyebrow">ATTENDANCE POLICY</p>
            <h2>有效时长规则</h2>
            <span v-if="refined">时长认定遵循现有审核规则。</span>
            <span v-if="refined && policyDirty" class="sw-section-status" role="status">时长规则未保存</span>
          </div>
          <button
            v-if="canEditPolicy"
            class="button primary small duty-save-button"
            :class="{ dirty: policyDirty }"
            type="button"
            :disabled="policyPending || !policyDirty"
            :title="policyDirty ? '有未保存修改' : undefined"
            @click="$emit('save-policy')"
          >
            <Save aria-hidden="true" />
            {{ policyPending ? "正在保存" : "保存规则" }}
          </button>
          <span v-else class="duty-readonly-state">仅管理员可修改</span>
        </div>

        <div class="duty-policy-grid">
          <label
            class="duty-policy-option"
            :class="{
              active: policy.requireDutyDay,
              inactive: !policy.requireDutyDay,
              readonly: !canEditPolicy,
            }"
          >
            <input
              name="requireDutyDay"
              type="checkbox"
              role="switch"
              :checked="policy.requireDutyDay"
              :disabled="!canEditPolicy"
              aria-label="强制值班日"
              @change="updatePolicy('requireDutyDay', checkedValue($event))"
            />
            <span class="duty-policy-icon">
              <CalendarCheck2 aria-hidden="true" />
            </span>
            <span class="duty-policy-copy">
              <strong>强制值班日</strong>
              <small>非开放日不计时</small>
            </span>
            <span class="duty-policy-state">
              {{ policy.requireDutyDay ? "已限制" : "已放开" }}
            </span>
          </label>

          <label
            class="duty-policy-option"
            :class="{
              active: policy.requireDutyPeriod,
              inactive: !policy.requireDutyPeriod,
              readonly: !canEditPolicy,
            }"
          >
            <input
              name="requireDutyPeriod"
              type="checkbox"
              role="switch"
              :checked="policy.requireDutyPeriod"
              :disabled="!canEditPolicy"
              aria-label="强制值班时段"
              @change="updatePolicy('requireDutyPeriod', checkedValue($event))"
            />
            <span class="duty-policy-icon">
              <Clock3 aria-hidden="true" />
            </span>
            <span class="duty-policy-copy">
              <strong>强制值班时段</strong>
              <small>时段外不计时</small>
            </span>
            <span class="duty-policy-state">
              {{ policy.requireDutyPeriod ? "已限制" : "已放开" }}
            </span>
          </label>
        </div>
      </section>

      <section :id="refined ? 'settings-periods' : undefined" class="duty-period-panel" :class="{ 'sw-section sw-periods': refined }">
        <div class="duty-section-heading" :class="{ 'sw-heading': refined }">
          <div>
            <p v-if="!refined" class="eyebrow">PERIOD EDITOR</p>
            <h2>{{ refined ? '值班时间段' : '编辑时间段' }}</h2>
            <span v-if="refined">选择时段后编辑起止时间。</span>
          </div>
          <button class="button secondary small" type="button" @click="addPeriod">
            <Plus aria-hidden="true" />新增时段
          </button>
        </div>

        <template v-if="refined && periods.length">
          <div class="duty-period-overview" aria-label="时间段概览">
            <div><span>计时时长</span><strong>{{ enabledDuration }}</strong></div>
            <div><span>启用时段</span><strong>{{ enabledCount }} <small>段</small></strong></div>
            <div><span>计时覆盖</span><strong>{{ coverageRange }}</strong></div>
          </div>

          <div class="duty-rail-caption">
            <strong>日内时间尺 · 06:00—24:00</strong>
            <span>时段宽度按持续时间等比显示</span>
          </div>
          <div ref="railViewport" class="duty-day-rail-viewport">
            <div
              class="duty-day-rail"
              :style="{ '--rail-height': railHeight }"
              aria-label="按时间比例显示的值班时段"
            >
              <span class="duty-rail-zone morning" aria-hidden="true">Morning</span>
              <span class="duty-rail-zone afternoon" aria-hidden="true">Afternoon</span>
              <span class="duty-rail-zone evening" aria-hidden="true">Evening</span>
              <span class="duty-rail-axis" aria-hidden="true"></span>
              <span
                v-for="hour in railHours"
                :key="`rail-${hour}`"
                class="duty-rail-tick"
                :class="{ major: hour % 6 === 0 }"
                :style="railHourStyle(hour)"
                aria-hidden="true"
              >
                <small>{{ formatHour(hour) }}</small>
              </span>
              <button
                v-for="block in railBlocks"
                :key="`rail-${block.period.startTime}-${block.period.endTime}-${block.index}`"
                class="duty-rail-block"
                :class="{
                  selected: selectedIndex === block.index,
                  disabled: !block.period.enabled,
                  conflict: Boolean(periodIssues[block.index]),
                }"
                :style="block.style"
                :data-period-index="block.index"
                type="button"
                :aria-pressed="selectedIndex === block.index"
                :aria-label="`${compactRange(block.period)}，${periodIssues[block.index]?.short || (block.period.enabled ? '启用' : '停用')}`"
                @click="selectPeriod(block.index, true)"
              >
                {{ compactRange(block.period) }}
              </button>
            </div>
          </div>
          <span class="duty-rail-scroll-hint">左右滑动查看完整时间尺</span>

          <div class="duty-period-tabs duty-period-cards" aria-label="选择时间段">
            <button
              v-for="(period, index) in periods"
              :key="`card-${period.startTime}-${period.endTime}-${index}`"
              class="duty-period-tab"
              :class="{
                selected: selectedIndex === index,
                disabled: !period.enabled,
                conflict: Boolean(periodIssues[index]),
              }"
              type="button"
              :aria-pressed="selectedIndex === index"
              @click="selectPeriod(index, true)"
            >
              <i class="duty-period-card-dot" aria-hidden="true"></i>
              <span class="duty-period-copy">
                <strong>{{ compactRange(period) }}</strong>
                <small>{{ duration(period) }} · {{ periodIssues[index]?.short || (period.enabled ? '计入值班' : '暂停计时') }}</small>
              </span>
              <em>第 {{ String(index + 1).padStart(2, '0') }} 段</em>
            </button>
          </div>
        </template>

        <div v-else-if="periods.length" class="duty-period-tabs" aria-label="选择时间段">
          <button
            v-for="(period, index) in periods"
            :key="`${period.startTime}-${period.endTime}-${index}`"
            class="duty-period-tab"
            :class="{
              selected: selectedIndex === index,
              disabled: !period.enabled,
              conflict: Boolean(periodIssues[index]),
            }"
            type="button"
            :aria-pressed="selectedIndex === index"
            @click="selectedIndex = index"
          >
            <span class="duty-period-copy">
              <strong>{{ compactRange(period) }}</strong>
              <small v-if="periodIssues[index]">{{ periodIssues[index]?.short }}</small>
              <small v-else>{{ period.enabled ? '启用' : '停用' }}</small>
            </span>
          </button>
        </div>

        <div
          v-if="selectedPeriod"
          class="duty-period-form"
          :class="{ invalid: Boolean(selectedIssue), 'duty-period-rail-editor': refined }"
        >
          <div class="duty-period-form-head">
            <div>
              <span>当前时段</span>
              <strong>{{ compactRange(selectedPeriod) }}</strong>
            </div>
            <div class="duty-period-actions">
              <button
                class="icon-button ghost"
                title="上移"
                aria-label="上移"
                type="button"
                :disabled="selectedIndex === 0"
                @click="moveSelected(-1)"
              >
                <ChevronUp aria-hidden="true" />
              </button>
              <button
                class="icon-button ghost"
                title="下移"
                aria-label="下移"
                type="button"
                :disabled="selectedIndex === periods.length - 1"
                @click="moveSelected(1)"
              >
                <ChevronDown aria-hidden="true" />
              </button>
              <button
                class="icon-button danger-ghost"
                title="删除时段"
                aria-label="删除时段"
                type="button"
                @click="removeSelected"
              >
                <Trash2 aria-hidden="true" />
              </button>
            </div>
          </div>

          <div class="duty-period-fields">
            <label class="field">
              <span>开始时间</span>
              <input
                ref="startInput"
                :value="selectedPeriod.startTime.slice(0, 5)"
                :name="`period-${selectedIndex + 1}-start`"
                type="time"
                @input="updatePeriod({ startTime: inputValue($event) })"
              />
            </label>
            <span class="duty-time-divider" aria-hidden="true">—</span>
            <label class="field">
              <span>结束时间</span>
              <input
                :value="selectedPeriod.endTime.slice(0, 5)"
                :name="`period-${selectedIndex + 1}-end`"
                type="time"
                @input="updatePeriod({ endTime: inputValue($event) })"
              />
            </label>
          </div>

          <div class="duty-period-meta">
            <strong>{{ duration(selectedPeriod) }}</strong>
            <label
              class="duty-period-enabled"
              :class="{ active: selectedPeriod.enabled }"
            >
              <input
                type="checkbox"
                :name="`period-${selectedIndex + 1}-enabled`"
                :checked="selectedPeriod.enabled"
                :aria-label="`第 ${selectedIndex + 1} 个时段是否启用`"
                @change="updatePeriod({ enabled: checkedValue($event) })"
              />
              <i aria-hidden="true"></i>
              <span>{{ selectedPeriod.enabled ? "启用" : "停用" }}</span>
            </label>
          </div>
        </div>

        <EmptyState v-else title="暂无值班时间段" />

        <div v-if="periodError" class="inline-alert danger duty-period-error" role="alert">
          <TriangleAlert aria-hidden="true" />
          <span>{{ selectedIssue?.message || periodError }}</span>
        </div>

        <div class="duty-period-footer">
          <span>
            共 {{ periods.length }} 个时间段
            <em v-if="periodsDirty" class="duty-dirty-state">未保存</em>
          </span>
          <button
            class="button primary duty-save-button"
            :class="{ dirty: periodsDirty }"
            type="button"
            :disabled="Boolean(periodError) || periodsPending"
            :title="periodsDirty ? '有未保存修改' : undefined"
            @click="$emit('save-periods')"
          >
            <Save aria-hidden="true" />
            {{ periodsPending ? "正在保存" : "保存时间段" }}
          </button>
        </div>
      </section>
    </aside>

    <section v-if="!refined" id="settings-periods" class="duty-time-calendar">
      <div class="duty-section-heading">
        <div>
          <p class="eyebrow">DAILY TIMELINE</p>
          <h2>值班时间段</h2>
        </div>
        <span class="duty-period-count">{{ enabledCount }} 个启用</span>
      </div>

      <div class="duty-calendar-layout">
        <div class="duty-calendar-hours" aria-hidden="true">
          <span
            v-for="hour in timelineHours"
            :key="hour"
            :style="hourStyle(hour)"
          >
            {{ formatHour(hour) }}
          </span>
        </div>
        <div class="duty-calendar-canvas">
          <span
            v-for="hour in timelineHours"
            :key="hour"
            class="duty-calendar-line"
            :style="hourStyle(hour)"
            aria-hidden="true"
          ></span>
          <button
            v-for="block in calendarBlocks"
            :key="`${block.period.startTime}-${block.period.endTime}-${block.index}`"
            class="duty-calendar-block"
            :class="{
              selected: selectedIndex === block.index,
              disabled: !block.period.enabled,
              conflict: Boolean(periodIssues[block.index]),
              'multi-lane': block.laneCount > 1,
            }"
            :style="block.style"
            type="button"
            :aria-pressed="selectedIndex === block.index"
            :aria-label="`${compactRange(block.period)}，${periodIssues[block.index]?.short || (block.period.enabled ? '启用' : '停用')}`"
            @click="selectedIndex = block.index"
          >
            <strong v-if="block.laneCount > 1" class="duty-calendar-range">
              <span>{{ block.period.startTime.slice(0, 5) }}</span>
              <span>{{ block.period.endTime.slice(0, 5) }}</span>
            </strong>
            <strong v-else>{{ compactRange(block.period) }}</strong>
            <span v-if="periodIssues[block.index]">
              {{ periodIssues[block.index]?.short }}
            </span>
            <span v-else>
              {{ block.period.enabled ? `时段 ${block.index + 1}` : "已停用" }}
            </span>
          </button>
        </div>
      </div>

      <div class="duty-calendar-compact" aria-label="值班时间段列表">
        <button
          v-for="(period, index) in periods"
          :key="`compact-${period.startTime}-${period.endTime}-${index}`"
          class="duty-calendar-compact-row"
          :class="{
            selected: selectedIndex === index,
            disabled: !period.enabled,
            conflict: Boolean(periodIssues[index]),
          }"
          type="button"
          :aria-pressed="selectedIndex === index"
          @click="selectedIndex = index"
        >
          <span>{{ String(index + 1).padStart(2, "0") }}</span>
          <strong>{{ compactRange(period) }}</strong>
          <small>
            {{ periodIssues[index]?.short || (period.enabled ? "启用" : "停用") }}
          </small>
        </button>
        <EmptyState v-if="!periods.length" title="暂无值班时间段" />
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import {
  CalendarCheck2,
  ChevronDown,
  ChevronUp,
  Clock3,
  Plus,
  Save,
  Trash2,
  TriangleAlert,
} from "@lucide/vue";
import EmptyState from "../../../shared/ui/EmptyState.vue";
import {
  moveDutyPeriod,
  type DutyPeriod,
} from "../../../features/settings/dutyPeriods";
import { layoutDutyPeriodLanes } from "../../../features/settings/dutyPeriodLayout";
import type { AttendancePolicy } from "../../../features/settings/attendancePolicy";

const props = defineProps<{
  refined?: boolean;
  periods: DutyPeriod[];
  policy: AttendancePolicy;
  canEditPolicy: boolean;
  periodError: string;
  policyDirty: boolean;
  periodsDirty: boolean;
  policyPending: boolean;
  periodsPending: boolean;
}>();

const emit = defineEmits<{
  "update:periods": [value: DutyPeriod[]];
  "update:policy": [value: AttendancePolicy];
  "save-policy": [];
  "save-periods": [];
}>();

const selectedIndex = ref(0);
const startInput = ref<HTMLInputElement | null>(null);
const railViewport = ref<HTMLElement | null>(null);
const selectedPeriod = computed(() => props.periods[selectedIndex.value] ?? null);
const enabledCount = computed(() => props.periods.filter((period) => period.enabled).length);
const periodIssues = computed(() => findPeriodIssues(props.periods));
const selectedIssue = computed(() => periodIssues.value[selectedIndex.value] ?? null);
const enabledDuration = computed(() => {
  const minutes = props.periods.reduce((total, period) => {
    if (!period.enabled) return total;
    const start = toMinutes(period.startTime);
    const end = toMinutes(period.endTime);
    return start === null || end === null || end <= start ? total : total + end - start;
  }, 0);
  const hours = (minutes / 60).toFixed(1).replace(".0", "");
  return `${hours} 小时`;
});
const coverageRange = computed(() => {
  const valid = props.periods.flatMap((period) => {
    if (!period.enabled) return [];
    const start = toMinutes(period.startTime);
    const end = toMinutes(period.endTime);
    return start === null || end === null || end <= start ? [] : [{ start, end }];
  });
  if (!valid.length) return "—";
  return `${formatMinutes(Math.min(...valid.map((period) => period.start)))}—${formatMinutes(Math.max(...valid.map((period) => period.end)))}`;
});

watch(
  () => props.periods.length,
  (length) => {
    if (!length) selectedIndex.value = 0;
    else if (selectedIndex.value >= length) selectedIndex.value = length - 1;
  },
);

function updatePolicy(key: keyof AttendancePolicy, value: boolean) {
  if (!props.canEditPolicy) return;
  emit("update:policy", { ...props.policy, [key]: value });
}

function updatePeriod(patch: Partial<DutyPeriod>) {
  emit(
    "update:periods",
    props.periods.map((period, index) =>
      index === selectedIndex.value ? { ...period, ...patch } : period,
    ),
  );
}

async function addPeriod() {
  const startTime = props.periods.at(-1)?.endTime?.slice(0, 5) || "14:00";
  const next = [
    ...props.periods,
    { startTime, endTime: addHours(startTime, 2), enabled: true },
  ];
  selectedIndex.value = next.length - 1;
  emit("update:periods", next);
  await nextTick();
  startInput.value?.focus();
  await scrollSelectedRail();
}

function moveSelected(direction: -1 | 1) {
  const target = selectedIndex.value + direction;
  const next = moveDutyPeriod(props.periods, selectedIndex.value, direction);
  if (next === props.periods) return;
  selectedIndex.value = target;
  emit("update:periods", next);
  void scrollSelectedRail();
}

function removeSelected() {
  const next = props.periods.filter((_, index) => index !== selectedIndex.value);
  selectedIndex.value = Math.min(selectedIndex.value, Math.max(0, next.length - 1));
  emit("update:periods", next);
}

function selectPeriod(index: number, scroll = false) {
  selectedIndex.value = index;
  if (scroll) void scrollSelectedRail(index);
}

async function scrollSelectedRail(index = selectedIndex.value) {
  await nextTick();
  const viewport = railViewport.value;
  const block = viewport?.querySelector<HTMLElement>(`[data-period-index="${index}"]`);
  if (!viewport || !block) return;
  const left = block.offsetLeft + block.offsetWidth / 2 - viewport.clientWidth / 2;
  viewport.scrollTo({
    left: Math.max(0, left),
    behavior:
      typeof window.matchMedia === "function" &&
      window.matchMedia("(prefers-reduced-motion: reduce)").matches
        ? "auto"
        : "smooth",
  });
}

function inputValue(event: Event) {
  return (event.target as HTMLInputElement).value;
}

function checkedValue(event: Event) {
  return (event.target as HTMLInputElement).checked;
}

function compactRange(period: DutyPeriod) {
  return `${period.startTime.slice(0, 5)}—${period.endTime.slice(0, 5)}`;
}

function duration(period: DutyPeriod) {
  const start = toMinutes(period.startTime);
  const end = toMinutes(period.endTime);
  if (start === null || end === null || end <= start) return "时长 —";
  const hours = ((end - start) / 60).toFixed(1).replace(".0", "");
  return `${hours} 小时`;
}

function addHours(value: string, hours: number) {
  const [rawHour = 0, rawMinute = 0] = value.split(":").map(Number);
  const total = Math.min(23 * 60 + 59, rawHour * 60 + rawMinute + hours * 60);
  return `${String(Math.floor(total / 60)).padStart(2, "0")}:${String(total % 60).padStart(2, "0")}`;
}

function toMinutes(value: string) {
  const [hour, minute] = value.slice(0, 5).split(":").map(Number);
  if (!Number.isFinite(hour) || !Number.isFinite(minute)) return null;
  return (hour ?? 0) * 60 + (minute ?? 0);
}

function formatMinutes(value: number) {
  return `${String(Math.floor(value / 60)).padStart(2, "0")}:${String(value % 60).padStart(2, "0")}`;
}

interface PeriodIssue {
  short: "不完整" | "时间错误" | "重复" | "冲突";
  message: string;
}

function findPeriodIssues(periodRows: DutyPeriod[]): Array<PeriodIssue | null> {
  const issues: Array<PeriodIssue | null> = periodRows.map(() => null);

  periodRows.forEach((period, index) => {
    if (!period.startTime || !period.endTime) {
      issues[index] = { short: "不完整", message: "当前时间段不完整" };
      return;
    }
    const start = toMinutes(period.startTime);
    const end = toMinutes(period.endTime);
    if (start === null || end === null || end <= start) {
      issues[index] = {
        short: "时间错误",
        message: "结束时间必须晚于开始时间",
      };
    }
  });

  for (let left = 0; left < periodRows.length; left += 1) {
    const current = periodRows[left];
    if (!current?.startTime || !current.endTime) continue;
    for (let right = left + 1; right < periodRows.length; right += 1) {
      const other = periodRows[right];
      if (!other?.startTime || !other.endTime) continue;
      if (
        current.startTime.slice(0, 5) === other.startTime.slice(0, 5) &&
        current.endTime.slice(0, 5) === other.endTime.slice(0, 5)
      ) {
        const issue = { short: "重复", message: "值班时间段不能重复" } as const;
        issues[left] = issue;
        issues[right] = issue;
      }
    }
  }

  for (let left = 0; left < periodRows.length; left += 1) {
    const current = periodRows[left];
    const currentStart = current ? toMinutes(current.startTime) : null;
    const currentEnd = current ? toMinutes(current.endTime) : null;
    if (!current?.enabled || currentStart === null || currentEnd === null) continue;
    for (let right = left + 1; right < periodRows.length; right += 1) {
      const other = periodRows[right];
      const otherStart = other ? toMinutes(other.startTime) : null;
      const otherEnd = other ? toMinutes(other.endTime) : null;
      if (!other?.enabled || otherStart === null || otherEnd === null) continue;
      if (Math.max(currentStart, otherStart) < Math.min(currentEnd, otherEnd)) {
        const issue = {
          short: "冲突",
          message: "启用中的值班时间段不能重叠",
        } as const;
        if (!issues[left]) issues[left] = issue;
        if (!issues[right]) issues[right] = issue;
      }
    }
  }

  return issues;
}

const railStartMinutes = 6 * 60;
const railEndMinutes = 24 * 60;
const railSpanMinutes = railEndMinutes - railStartMinutes;
const railInsetPercent = 2.4;
const railUsablePercent = 100 - railInsetPercent * 2;
const railHours = Array.from({ length: 10 }, (_, index) => 6 + index * 2);
const railBlocks = computed(() => {
  const lanes = layoutDutyPeriodLanes(props.periods);
  return props.periods.flatMap((period, index) => {
    const rawStart = toMinutes(period.startTime);
    const rawEnd = toMinutes(period.endTime);
    if (rawStart === null || rawEnd === null || rawEnd <= rawStart) return [];
    const start = Math.max(railStartMinutes, Math.min(railEndMinutes, rawStart));
    const end = Math.max(railStartMinutes, Math.min(railEndMinutes, rawEnd));
    if (end <= start) return [];
    const lane = lanes[index] ?? { laneIndex: 0, laneCount: 1 };
    return [{
      period,
      index,
      laneCount: lane.laneCount,
      style: {
        "--rail-left": `${railInsetPercent + ((start - railStartMinutes) / railSpanMinutes) * railUsablePercent}%`,
        "--rail-width": `${Math.max(7.5, ((end - start) / railSpanMinutes) * railUsablePercent)}%`,
        "--rail-lane": String(lane.laneIndex),
      },
    }];
  });
});
const railLaneCount = computed(() =>
  Math.max(1, ...railBlocks.value.map((block) => block.laneCount)),
);
const railHeight = computed(() => `${112 + (railLaneCount.value - 1) * 48}px`);

function railHourStyle(hour: number) {
  return {
    left: `${railInsetPercent + ((hour * 60 - railStartMinutes) / railSpanMinutes) * railUsablePercent}%`,
  };
}

const timelineStartHour = computed(() => {
  const starts = props.periods
    .map((period) => toMinutes(period.startTime))
    .filter((value): value is number => value !== null);
  return Math.max(0, Math.min(8, Math.floor(Math.min(...starts, 8 * 60) / 60)));
});

const timelineEndHour = computed(() => {
  const ends = props.periods
    .map((period) => toMinutes(period.endTime))
    .filter((value): value is number => value !== null);
  return Math.min(24, Math.max(22, Math.ceil(Math.max(...ends, 22 * 60) / 60)));
});

const timelineHours = computed(() =>
  Array.from(
    { length: timelineEndHour.value - timelineStartHour.value + 1 },
    (_, index) => timelineStartHour.value + index,
  ),
);

const calendarBlocks = computed(() => {
  const startOfDay = timelineStartHour.value * 60;
  const span = (timelineEndHour.value - timelineStartHour.value) * 60;
  const lanes = layoutDutyPeriodLanes(props.periods);
  return props.periods.flatMap((period, index) => {
    const start = toMinutes(period.startTime);
    const end = toMinutes(period.endTime);
    if (start === null || end === null || end <= start || span <= 0) return [];
    const lane = lanes[index] ?? { laneIndex: 0, laneCount: 1 };
    return [
      {
        period,
        index,
        laneCount: lane.laneCount,
        style: {
          "--period-top": `${((start - startOfDay) / span) * 100}%`,
          "--period-height": `${((end - start) / span) * 100}%`,
          "--period-lane-left": `${(lane.laneIndex / lane.laneCount) * 100}%`,
          "--period-lane-width": `${100 / lane.laneCount}%`,
        },
      },
    ];
  });
});

function hourStyle(hour: number) {
  const span = timelineEndHour.value - timelineStartHour.value;
  return { top: `${((hour - timelineStartHour.value) / span) * 100}%` };
}

function formatHour(hour: number) {
  return `${String(hour).padStart(2, "0")}:00`;
}
</script>

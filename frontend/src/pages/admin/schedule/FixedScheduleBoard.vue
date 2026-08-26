<template>
  <div
    v-if="periods.length && visibleWeekdays.length"
    class="schedule-focus-board"
  >
    <nav class="schedule-focus-days" aria-label="选择排班星期">
      <button
        v-for="day in visibleWeekdays"
        :key="day.value"
        class="schedule-focus-day"
        :class="{
          active: selectedWeekday === day.value,
          disabled: !day.enabled,
        }"
        type="button"
        :aria-pressed="selectedWeekday === day.value"
        @click="selectedWeekday = day.value"
      >
        <span>
          <strong>{{ day.label }}</strong>
          <small>{{ daySlotCount(day.value) }} 个排班</small>
        </span>
        <b>{{ dayPeople(day.value) }}</b>
      </button>
    </nav>

    <div class="schedule-focus-workspace">
      <section class="schedule-focus-main">
        <header class="schedule-focus-header">
          <div>
            <h2>{{ selectedDay?.label }}固定排班</h2>
            <p>
              {{ periods.length }} 个值班时段 · 已安排
              {{ selectedDayPeople }} 人
            </p>
          </div>
          <span v-if="!selectedDay?.enabled" class="schedule-focus-day-state">
            当前星期未开放
          </span>
        </header>

        <div class="schedule-focus-timeline">
          <article
            v-for="period in periods"
            :key="periodKey(period)"
            class="schedule-focus-period"
          >
            <div class="schedule-focus-time">
              <strong>{{ shortTime(period.startTime) }}</strong>
              <span>至 {{ shortTime(period.endTime) }}</span>
            </div>

            <div class="schedule-focus-period-content">
              <div
                v-if="slotsFor(selectedWeekday, period).length"
                class="schedule-focus-slot-list"
              >
                <FixedScheduleCard
                  v-for="slot in slotsFor(selectedWeekday, period)"
                  :key="slot.id"
                  :slot="slot"
                  @edit="$emit('edit', slot)"
                  @archive="$emit('archive', slot)"
                />
              </div>
              <div v-else class="schedule-focus-empty">
                <strong>暂未安排人员</strong>
                <span>该时段尚无固定排班</span>
              </div>
            </div>

          </article>
        </div>
      </section>

      <aside
        class="schedule-focus-summary"
        :aria-label="`${selectedDay?.label}排班概览`"
      >
        <section class="schedule-focus-panel">
          <h3>{{ selectedDay?.label }}概览</h3>
          <dl class="schedule-focus-stats">
            <div>
              <dt>值班时段</dt>
              <dd>{{ periods.length }} 个</dd>
            </div>
            <div>
              <dt>固定排班</dt>
              <dd>{{ selectedDaySlotCount }} 个</dd>
            </div>
            <div>
              <dt>已安排人员</dt>
              <dd>{{ selectedDayPeople }} 人</dd>
            </div>
            <div>
              <dt>待安排时段</dt>
              <dd>{{ unfilledPeriodCount }} 个</dd>
            </div>
          </dl>
        </section>

        <section class="schedule-focus-panel">
          <h3>值班时段</h3>
          <div class="schedule-focus-periods">
            <div
              v-for="period in periods"
              :key="`summary-${periodKey(period)}`"
            >
              <time>
                {{ shortTime(period.startTime) }}–{{ shortTime(period.endTime) }}
              </time>
              <span>
                {{
                  periodAssigneeCount(period)
                    ? `${periodAssigneeCount(period)} 人`
                    : "未安排"
                }}
              </span>
            </div>
          </div>
        </section>
      </aside>
    </div>
  </div>
  <EmptyState
    v-else
    :title="
      periods.length
        ? '请先在系统设置中开放值班星期'
        : '请先在系统设置中添加值班时间段'
    "
  />
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import EmptyState from "../../../shared/ui/EmptyState.vue";
import FixedScheduleCard from "./FixedScheduleCard.vue";
import type { DutyPeriod } from "../../../features/settings/dutyPeriods";
import type { ScheduleSlot } from "../../../features/schedule/scheduleTypes";

interface WeekdayOption {
  value: number;
  label: string;
  short: string;
  enabled: boolean;
}

const props = defineProps<{
  slots: ScheduleSlot[];
  periods: DutyPeriod[];
  weekdays: WeekdayOption[];
}>();

defineEmits<{
  edit: [slot: ScheduleSlot];
  archive: [slot: ScheduleSlot];
}>();

const visibleWeekdays = computed(() => {
  const scheduledDays = new Set(
    props.slots.map((slot) => Number(slot.weekday)),
  );
  return props.weekdays.filter(
    (day) => day.enabled || scheduledDays.has(day.value),
  );
});

const selectedWeekday = ref(initialWeekday());
const selectedDay = computed(() =>
  visibleWeekdays.value.find((day) => day.value === selectedWeekday.value),
);
const selectedDaySlotCount = computed(() =>
  props.slots.filter(
    (slot) => Number(slot.weekday) === selectedWeekday.value,
  ).length,
);
const selectedDayPeople = computed(() => dayPeople(selectedWeekday.value));
const unfilledPeriodCount = computed(
  () =>
    props.periods.filter((period) => periodAssigneeCount(period) === 0).length,
);

watch(
  visibleWeekdays,
  (days) => {
    if (!days.some((day) => day.value === selectedWeekday.value)) {
      selectedWeekday.value =
        days.find((day) => day.enabled)?.value ?? days[0]?.value ?? 1;
    }
  },
  { flush: "sync" },
);

function initialWeekday() {
  return (
    visibleWeekdays.value.find((day) => day.enabled)?.value ??
    visibleWeekdays.value[0]?.value ??
    1
  );
}

function slotsFor(weekday: number, period: DutyPeriod) {
  const key = periodKey(period);
  return props.slots.filter(
    (slot) => Number(slot.weekday) === weekday && periodKey(slot) === key,
  );
}

function daySlotCount(weekday: number) {
  return props.slots.filter((slot) => Number(slot.weekday) === weekday).length;
}

function dayPeople(weekday: number) {
  const people = new Set<string>();
  props.slots
    .filter((slot) => Number(slot.weekday) === weekday)
    .forEach((slot) =>
      slot.assignees?.forEach((person) =>
        people.add(person.studentNo || person.name),
      ),
    );
  return people.size;
}

function periodAssigneeCount(period: DutyPeriod) {
  const people = new Set<string>();
  slotsFor(selectedWeekday.value, period).forEach((slot) =>
    slot.assignees?.forEach((person) =>
      people.add(person.studentNo || person.name),
    ),
  );
  return people.size;
}

function periodKey(value: Pick<DutyPeriod, "startTime" | "endTime">) {
  return `${shortTime(value.startTime)}-${shortTime(value.endTime)}`;
}

function shortTime(value?: string) {
  return value?.slice(0, 5) || "";
}
</script>

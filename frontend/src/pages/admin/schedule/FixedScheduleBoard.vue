<template>
  <div v-if="periods.length" class="schedule-matrix-shell">
    <div
      class="schedule-matrix"
      role="grid"
      aria-label="固定周排班表"
      :style="{
        gridTemplateColumns: `142px repeat(${visibleWeekdays.length}, minmax(176px, 1fr))`,
      }"
    >
      <div class="schedule-matrix-corner" role="columnheader">
        <span>固定周表</span>
        <strong>{{ periods.length }} 个时段</strong>
      </div>
      <div
        v-for="day in visibleWeekdays"
        :key="`head-${day.value}`"
        class="schedule-matrix-day"
        :class="{ disabled: !day.enabled }"
        role="columnheader"
      >
        <strong>{{ day.label }}</strong>
        <span>{{ day.enabled ? `${dayPeople(day.value)} 人` : "未开放" }}</span>
      </div>

      <template v-for="period in periods" :key="periodKey(period)">
        <div class="schedule-matrix-period" role="rowheader">
          <time>
            {{ shortTime(period.startTime) }}–{{ shortTime(period.endTime) }}
          </time>
          <span>{{ periodSlotCount(period) }} 个排班</span>
        </div>
        <section
          v-for="day in visibleWeekdays"
          :key="`${day.value}-${periodKey(period)}`"
          class="schedule-matrix-cell"
          :class="{ disabled: !day.enabled }"
          role="gridcell"
          :aria-label="`${day.label} ${shortTime(period.startTime)} 至 ${shortTime(period.endTime)}`"
        >
          <FixedScheduleCard
            v-for="slot in slotsFor(day.value, period)"
            :key="slot.id"
            :slot="slot"
            @edit="$emit('edit', slot)"
            @archive="$emit('archive', slot)"
          />
          <button
            v-if="day.enabled"
            class="schedule-add-period"
            type="button"
            @click="$emit('add', day.value, periodKey(period))"
          >
            <Plus aria-hidden="true" />添加人员
          </button>
          <span v-else class="schedule-closed-cell">未开放</span>
        </section>
      </template>
    </div>
  </div>
  <EmptyState v-else title="请先在系统设置中添加值班时间段" />
</template>

<script setup lang="ts">
import { computed } from "vue";
import { Plus } from "@lucide/vue";
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
  add: [weekday: number, period: string];
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

function slotsFor(weekday: number, period: DutyPeriod) {
  const key = periodKey(period);
  return props.slots.filter(
    (slot) => Number(slot.weekday) === weekday && periodKey(slot) === key,
  );
}

function periodSlotCount(period: DutyPeriod) {
  const key = periodKey(period);
  return props.slots.filter((slot) => periodKey(slot) === key).length;
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

function periodKey(value: Pick<DutyPeriod, "startTime" | "endTime">) {
  return `${shortTime(value.startTime)}-${shortTime(value.endTime)}`;
}

function shortTime(value?: string) {
  return value?.slice(0, 5) || "";
}
</script>

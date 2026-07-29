<template>
  <div
    v-if="periods.length"
    class="schedule-week-grid"
    :style="{
      gridTemplateColumns: `repeat(${visibleWeekdays.length}, minmax(0, 1fr))`,
    }"
  >
    <section
      v-for="day in visibleWeekdays"
      :key="day.value"
      class="schedule-day-column"
      :class="{ disabled: !day.enabled }"
    >
      <header class="schedule-day-head">
        <div>
          <span>{{ day.short }}</span>
          <strong>{{ day.label }}</strong>
        </div>
        <small>{{
          day.enabled ? `${dayPeople(day.value)} 人` : "未开放"
        }}</small>
      </header>

      <div class="schedule-day-periods">
        <section
          v-for="period in periods"
          :key="`${day.value}-${periodKey(period)}`"
          class="schedule-period-group"
        >
          <div class="schedule-period-head">
            <strong
              >{{ shortTime(period.startTime) }}-{{
                shortTime(period.endTime)
              }}</strong
            >
            <span>{{ slotsFor(day.value, period).length }} 个排班</span>
          </div>
          <article
            v-for="slot in slotsFor(day.value, period)"
            :key="slot.id"
            class="schedule-slot-card"
            :class="{ muted: !slot.enabled }"
          >
            <div class="schedule-slot-top">
              <strong>{{ slot.title }}</strong>
              <span>{{ slot.enabled ? "显示" : "隐藏" }}</span>
            </div>
            <p>
              {{
                slot.assignees.map((item) => item.name).join("、") ||
                "待安排"
              }}
            </p>
            <small>{{
              [slot.location, slot.note].filter(Boolean).join(" · ") ||
              "未填写备注"
            }}</small>
            <div class="schedule-card-actions">
              <button
                class="icon-button"
                title="编辑排班"
                @click="$emit('edit', slot)"
              >
                <Pencil />
              </button>
              <button
                class="icon-button danger-ghost"
                title="归档排班"
                @click="$emit('archive', slot)"
              >
                <Trash2 />
              </button>
            </div>
          </article>
          <button
            v-if="day.enabled"
            class="schedule-add-period"
            type="button"
            @click="$emit('add', day.value, periodKey(period))"
          >
            <Plus />添加人员
          </button>
        </section>
      </div>
    </section>
  </div>
  <EmptyState v-else title="请先在系统设置中添加值班时间段" />
</template>

<script setup lang="ts">
import { computed } from "vue";
import { Pencil, Plus, Trash2 } from "@lucide/vue";
import EmptyState from "../../../shared/ui/EmptyState.vue";
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

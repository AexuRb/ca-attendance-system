<template>
  <div
    v-if="periods.length && visibleWeekdays.length"
    class="schedule-focus-board"
  >
    <nav ref="dayNav" class="schedule-focus-days" aria-label="选择排班星期">
      <button
        v-for="day in visibleWeekdays"
        :key="day.value"
        class="schedule-focus-day"
        :data-weekday="day.value"
        :class="{
          active: selectedWeekday === day.value,
          disabled: !day.enabled,
        }"
        type="button"
        :aria-pressed="selectedWeekday === day.value"
        @click="selectWeekday(day.value)"
      >
        <span>
          <strong>{{ day.label }}</strong>
          <small>{{ daySlotCount(day.value) }} 个排班</small>
        </span>
        <b>{{ dayPeople(day.value) }}<span>人</span></b>
      </button>
    </nav>
    <p v-if="visibleWeekdays.length > 2" class="schedule-focus-scroll-hint">左右滑动可查看其他星期</p>

    <Transition :name="motionDirection > 0 ? 'schedule-day-next' : 'schedule-day-previous'" mode="out-in">
    <div :key="selectedWeekday" class="schedule-focus-workspace">
      <aside
        class="schedule-focus-summary"
        :aria-label="`${selectedDay?.label}排班概览`"
      >
        <section class="schedule-focus-panel">
          <h3>{{ selectedDay?.label }}概览</h3>
          <dl class="schedule-focus-stats">
            <div>
              <dt>固定排班</dt>
              <dd>{{ selectedDaySlotCount }} 个</dd>
            </div>
            <div>
              <dt>签到台可见</dt>
              <dd>{{ visibleSlotCount }} 个</dd>
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
          <p v-if="hiddenSlotCount" class="schedule-focus-summary-note">{{ hiddenSlotCount }} 个排班已隐藏，人员仍计入已安排。</p>
        </section>
      </aside>
      <section class="schedule-focus-main">
        <header class="schedule-focus-header">
          <div>
            <h2>{{ selectedDay?.label }}固定排班</h2>
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
                  :read-only="readOnly"
                  @edit="$emit('edit', slot)"
                  @archive="$emit('archive', slot)"
                />
              </div>
              <div v-else class="schedule-focus-empty">
                <div><strong>该时段暂无固定排班</strong><span>{{ selectedDay?.enabled ? '可以直接在此时段新增' : '当前星期未开放' }}</span></div>
                <button v-if="selectedDay?.enabled && !readOnly" class="schedule-focus-add" type="button" @click="$emit('add', selectedWeekday, periodKey(period))"><Plus aria-hidden="true" />新增此时段排班</button>
              </div>
            </div>

          </article>
        </div>
      </section>
    </div>
    </Transition>
  </div>
  <EmptyState
    v-else
    :title="
      periods.length
        ? '请先在系统设置中开放值班星期'
        : '请先在系统设置中添加值班时间段'
    "
    description="完成值班时段和开放星期设置后，即可在这里安排固定排班。"
  />
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from "vue";
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
  preferredWeekday?: number;
  readOnly?: boolean;
}>();

const emit = defineEmits<{
  edit: [slot: ScheduleSlot];
  archive: [slot: ScheduleSlot];
  add: [weekday: number, period: string];
  "weekday-change": [weekday: number];
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
const motionDirection = ref(1);
const dayNav = ref<HTMLElement | null>(null);
const selectedDay = computed(() =>
  visibleWeekdays.value.find((day) => day.value === selectedWeekday.value),
);
const selectedDaySlotCount = computed(() =>
  props.slots.filter(
    (slot) => Number(slot.weekday) === selectedWeekday.value,
  ).length,
);
const selectedDayPeople = computed(() => dayPeople(selectedWeekday.value));
const hiddenSlotCount = computed(() => props.slots.filter(slot => Number(slot.weekday) === selectedWeekday.value && slot.enabled === false).length);
const visibleSlotCount = computed(() => selectedDay.value?.enabled ? selectedDaySlotCount.value - hiddenSlotCount.value : 0);
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
watch(
  () => props.preferredWeekday,
  (weekday) => {
    if (weekday && visibleWeekdays.value.some((day) => day.value === weekday)) {
      selectedWeekday.value = weekday;
    }
  },
  { immediate: true, flush: "sync" },
);
watch(selectedWeekday, (next, previous) => {
  motionDirection.value = next >= previous ? 1 : -1;
  void nextTick(revealSelectedWeekday);
}, { flush: "post" });
watch(visibleWeekdays, () => void nextTick(revealSelectedWeekday), { flush: "post" });
onMounted(() => void nextTick(revealSelectedWeekday));

function revealSelectedWeekday() {
  const nav = dayNav.value;
  const active = nav?.querySelector<HTMLElement>(`[data-weekday="${selectedWeekday.value}"]`);
  if (!nav || !active) return;
  const navRect = nav.getBoundingClientRect();
  const activeRect = active.getBoundingClientRect();
  if (activeRect.left < navRect.left) nav.scrollLeft += activeRect.left - navRect.left - 8;
  else if (activeRect.right > navRect.right) nav.scrollLeft += activeRect.right - navRect.right + 8;
}

function initialWeekday() {
  return (
    (props.preferredWeekday &&
    visibleWeekdays.value.some((day) => day.value === props.preferredWeekday)
      ? props.preferredWeekday
      : undefined) ??
    visibleWeekdays.value.find((day) => day.enabled)?.value ??
    visibleWeekdays.value[0]?.value ??
    1
  );
}

function selectWeekday(weekday: number) {
  selectedWeekday.value = weekday;
  emit("weekday-change", weekday);
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

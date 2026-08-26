<template>
  <article class="schedule-slot-card" :class="{ muted: !slot.enabled }">
    <header class="schedule-slot-top">
      <div class="schedule-slot-heading">
        <strong>{{ slot.title }}</strong>
        <span v-if="!slot.enabled">隐藏</span>
        <small v-else>{{ slot.assignees.length }} 人</small>
      </div>
      <div class="schedule-card-actions">
        <button
          class="icon-button"
          type="button"
          :aria-label="`编辑 ${slot.title}`"
          title="编辑排班"
          @click="$emit('edit')"
        >
          <Pencil aria-hidden="true" />
        </button>
        <button
          class="icon-button danger-ghost"
          type="button"
          :aria-label="`归档 ${slot.title}`"
          title="归档排班"
          @click="$emit('archive')"
        >
          <Trash2 aria-hidden="true" />
        </button>
      </div>
    </header>
    <div
      v-if="slot.assignees.length"
      class="schedule-assignee-preview"
      :aria-label="`排班人员：${slot.assignees.map((item) => item.name).join('、')}`"
    >
      <div
        v-for="person in slot.assignees"
        :key="person.studentNo || person.id"
        class="schedule-assignee-item"
      >
        <span class="schedule-assignee-initial" aria-hidden="true">
          {{ person.name.trim().slice(0, 1) || "?" }}
        </span>
        <strong>{{ person.name }}</strong>
      </div>
    </div>
    <p v-else class="schedule-empty-assignees">待安排人员</p>
    <p v-if="meta" class="schedule-slot-meta">{{ meta }}</p>
  </article>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { Pencil, Trash2 } from "@lucide/vue";
import type { ScheduleSlot } from "../../../features/schedule/scheduleTypes";

const props = defineProps<{ slot: ScheduleSlot }>();

defineEmits<{
  edit: [];
  archive: [];
}>();

const meta = computed(() =>
  [props.slot.location, props.slot.note].filter(Boolean).join(" · "),
);
</script>

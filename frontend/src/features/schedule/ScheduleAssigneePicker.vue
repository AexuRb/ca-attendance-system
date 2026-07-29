<template>
  <div class="schedule-assignee-picker">
    <div class="schedule-assignee-search">
      <Search aria-hidden="true" />
      <input
        v-model.trim="keyword"
        aria-label="搜索排班人员"
        placeholder="搜索姓名或学号"
      />
      <span>{{ modelValue.length }} 人已选</span>
    </div>

    <div v-if="modelValue.length" class="schedule-assignee-selected">
      <button
        v-for="person in modelValue"
        :key="person.studentNo"
        type="button"
        :title="`移除 ${person.name}`"
        @click="remove(person.studentNo)"
      >
        <span>{{ person.name }}</span>
        <small>{{ person.studentNo }}</small>
        <X aria-hidden="true" />
      </button>
    </div>

    <div class="schedule-assignee-options">
      <button
        v-for="person in visibleCandidates"
        :key="person.studentNo"
        type="button"
        :class="{ selected: selectedNumbers.has(person.studentNo) }"
        @click="toggle(person)"
      >
        <span class="schedule-assignee-avatar">{{ person.name.slice(0, 1) }}</span>
        <span>
          <strong>{{ person.name }}</strong>
          <small>{{ person.studentNo }} · {{ roleLabel(person.role) }}</small>
        </span>
        <Check v-if="selectedNumbers.has(person.studentNo)" aria-hidden="true" />
        <Plus v-else aria-hidden="true" />
      </button>
      <p v-if="!visibleCandidates.length">没有匹配的可排班人员</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { Check, Plus, Search, X } from "@lucide/vue";
import {
  filterScheduleAssignees,
  mergeScheduleAssignees,
  roleLabel,
  type ScheduleAssigneeOption,
} from "./scheduleAssignees";

const props = defineProps<{
  candidates: ScheduleAssigneeOption[];
  modelValue: ScheduleAssigneeOption[];
}>();
const emit = defineEmits<{
  "update:modelValue": [value: ScheduleAssigneeOption[]];
}>();

const keyword = ref("");
const allCandidates = computed(() =>
  mergeScheduleAssignees(props.candidates, props.modelValue),
);
const visibleCandidates = computed(() =>
  filterScheduleAssignees(allCandidates.value, keyword.value),
);
const selectedNumbers = computed(
  () => new Set(props.modelValue.map((person) => person.studentNo)),
);

function toggle(person: ScheduleAssigneeOption) {
  if (selectedNumbers.value.has(person.studentNo)) {
    remove(person.studentNo);
    return;
  }
  emit("update:modelValue", [...props.modelValue, person]);
}

function remove(studentNo: string) {
  emit(
    "update:modelValue",
    props.modelValue.filter((person) => person.studentNo !== studentNo),
  );
}
</script>

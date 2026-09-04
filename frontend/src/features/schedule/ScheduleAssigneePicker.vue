<template>
  <div class="schedule-assignee-picker">
    <div class="schedule-assignee-search">
      <Search aria-hidden="true" />
      <input
        v-model.trim="keyword"
        name="scheduleAssigneeSearch"
        aria-label="搜索排班人员"
        autocomplete="off"
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

    <div
      ref="listbox"
      class="schedule-assignee-options"
      role="listbox"
      aria-label="可选排班人员"
      aria-multiselectable="true"
    >
      <button
        v-for="person in visibleCandidates"
        :key="person.studentNo"
        type="button"
        role="option"
        :data-option-id="person.studentNo"
        :aria-selected="selectedNumbers.has(person.studentNo)"
        :tabindex="optionTabIndex(person)"
        :class="{ selected: selectedNumbers.has(person.studentNo) }"
        @keydown="onOptionKeydown($event, person)"
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
import { computed, nextTick, ref, watch } from "vue";
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
  open?: boolean;
}>();
const emit = defineEmits<{
  "update:modelValue": [value: ScheduleAssigneeOption[]];
}>();

const keyword = ref("");
const activeStudentNo = ref<string | null>(null);
const listbox = ref<HTMLElement | null>(null);
let suppressModelReset = false;
const allCandidates = computed(() =>
  mergeScheduleAssignees(props.candidates, props.modelValue),
);
const visibleCandidates = computed(() =>
  filterScheduleAssignees(allCandidates.value, keyword.value),
);
const selectedNumbers = computed(
  () => new Set(props.modelValue.map((person) => person.studentNo)),
);

watch(
  visibleCandidates,
  (candidates) => {
    if (
      candidates.some(
        (candidate) => candidate.studentNo === activeStudentNo.value,
      )
    ) {
      return;
    }
    activeStudentNo.value = candidates[0]?.studentNo ?? null;
  },
  { immediate: true },
);

function resetPicker() {
  keyword.value = "";
  activeStudentNo.value = null;
}

watch(
  () => props.open,
  (open) => {
    if (open) resetPicker();
  },
);
watch(() => props.candidates, resetPicker);
watch(
  () => props.modelValue,
  () => {
    if (suppressModelReset) {
      suppressModelReset = false;
      return;
    }
    resetPicker();
  },
);

function optionTabIndex(person: ScheduleAssigneeOption) {
  const firstCandidate = visibleCandidates.value[0];
  return (activeStudentNo.value ?? firstCandidate?.studentNo) ===
    person.studentNo
    ? 0
    : -1;
}

function focusOption(person: ScheduleAssigneeOption) {
  activeStudentNo.value = person.studentNo;
  void nextTick(() => {
    const button = Array.from(
      listbox.value?.querySelectorAll<HTMLButtonElement>(
        'button[role="option"]',
      ) || [],
    ).find((item) => item.dataset.optionId === person.studentNo);
    button?.focus();
  });
}

function onOptionKeydown(
  event: KeyboardEvent,
  person: ScheduleAssigneeOption,
) {
  if (event.key === "Enter" || event.key === " " || event.key === "Spacebar") {
    event.preventDefault();
    toggle(person);
    return;
  }
  if (!["ArrowDown", "ArrowUp", "Home", "End"].includes(event.key)) return;

  const index = visibleCandidates.value.findIndex(
    (candidate) => candidate.studentNo === person.studentNo,
  );
  if (index < 0 || !visibleCandidates.value.length) return;

  event.preventDefault();
  const nextIndex =
    event.key === "Home"
      ? 0
      : event.key === "End"
        ? visibleCandidates.value.length - 1
        : event.key === "ArrowDown"
          ? (index + 1) % visibleCandidates.value.length
          : (index - 1 + visibleCandidates.value.length) %
            visibleCandidates.value.length;
  const nextCandidate = visibleCandidates.value[nextIndex];
  if (nextCandidate) focusOption(nextCandidate);
}

function toggle(person: ScheduleAssigneeOption) {
  if (selectedNumbers.value.has(person.studentNo)) {
    remove(person.studentNo);
    return;
  }
  activeStudentNo.value = person.studentNo;
  suppressModelReset = true;
  emit("update:modelValue", [...props.modelValue, person]);
  void Promise.resolve().then(() => {
    suppressModelReset = false;
  });
}

function remove(studentNo: string) {
  suppressModelReset = true;
  emit(
    "update:modelValue",
    props.modelValue.filter((person) => person.studentNo !== studentNo),
  );
  void Promise.resolve().then(() => {
    suppressModelReset = false;
  });
}
</script>

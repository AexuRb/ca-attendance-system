<template>
  <div class="account-picker">
    <div class="account-picker-search">
      <Search aria-hidden="true" />
      <input
        v-model.trim="keyword"
        :name="inputName"
        :aria-label="ariaLabel"
        :aria-invalid="invalid || undefined"
        :aria-describedby="describedBy"
        :placeholder="placeholder"
        autocomplete="off"
      />
      <button
        v-if="modelValue"
        class="icon-button"
        type="button"
        title="清除选择"
        aria-label="清除选择"
        @click="select(null)"
      >
        <X aria-hidden="true" />
      </button>
    </div>

    <div v-if="modelValue" class="account-picker-current">
      <span>{{ modelValue.name.slice(0, 1) }}</span>
      <strong>{{ modelValue.name }}</strong>
      <small>
        {{ modelValue.studentNo || "原账号不可用" }} ·
        {{ accountRoleLabel(modelValue.role) }}
      </small>
      <em v-if="modelValue.inactive">已停用</em>
    </div>

    <div
      ref="listbox"
      class="account-picker-options"
      role="listbox"
      :aria-label="`${ariaLabel}候选列表`"
    >
      <button
        v-for="candidate in visibleCandidates"
        :key="candidate.id"
        type="button"
        role="option"
        :data-option-id="candidate.id"
        :aria-selected="candidate.id === modelValue?.id"
        :tabindex="optionTabIndex(candidate)"
        :class="{
          selected: candidate.id === modelValue?.id,
          unavailable: candidate.inactive,
        }"
        :disabled="candidate.inactive"
        @keydown="onOptionKeydown($event, candidate)"
        @click="select(candidate)"
      >
        <span>{{ candidate.name.slice(0, 1) }}</span>
        <span>
          <strong>{{ candidate.name }}</strong>
          <small>
            {{ candidate.studentNo || "原账号不可用" }} ·
            {{ accountRoleLabel(candidate.role) }}
          </small>
        </span>
        <Check
          v-if="candidate.id === modelValue?.id"
          aria-hidden="true"
        />
      </button>
      <p v-if="!visibleCandidates.length">没有匹配的启用账号</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import { Check, Search, X } from "@lucide/vue";
import {
  accountRoleLabel,
  filterAccountCandidates,
  mergeAccountCandidates,
  type AccountCandidate,
} from "./accountCandidates";

const props = withDefaults(
  defineProps<{
    candidates: AccountCandidate[];
    modelValue: AccountCandidate | null;
    ariaLabel?: string;
    placeholder?: string;
    inputName?: string;
    invalid?: boolean;
    describedBy?: string;
    open?: boolean;
  }>(),
  {
    ariaLabel: "选择账号",
    placeholder: "搜索姓名或学号",
    inputName: undefined,
    invalid: false,
    describedBy: undefined,
    open: true,
  },
);
const emit = defineEmits<{
  "update:modelValue": [value: AccountCandidate | null];
}>();

const keyword = ref("");
const activeId = ref<number | null>(null);
const listbox = ref<HTMLElement | null>(null);
let suppressModelReset = false;
const visibleCandidates = computed(() =>
  filterAccountCandidates(
    mergeAccountCandidates(props.candidates, props.modelValue),
    keyword.value,
  ),
);

watch(
  visibleCandidates,
  (candidates) => {
    if (
      candidates.some(
        (candidate) =>
          !candidate.inactive && candidate.id === activeId.value,
      )
    ) {
      return;
    }
    activeId.value =
      candidates.find((candidate) => !candidate.inactive)?.id ?? null;
  },
  { immediate: true },
);

function resetPicker() {
  keyword.value = "";
  activeId.value = null;
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

function optionTabIndex(candidate: AccountCandidate) {
  if (candidate.inactive) return -1;
  const firstAvailable = visibleCandidates.value.find(
    (item) => !item.inactive,
  );
  return (activeId.value ?? firstAvailable?.id) === candidate.id ? 0 : -1;
}

function focusOption(candidate: AccountCandidate) {
  activeId.value = candidate.id;
  void nextTick(() => {
    const button = Array.from(
      listbox.value?.querySelectorAll<HTMLButtonElement>(
        'button[role="option"]:not(:disabled)',
      ) || [],
    ).find((item) => item.dataset.optionId === String(candidate.id));
    button?.focus();
  });
}

function onOptionKeydown(event: KeyboardEvent, candidate: AccountCandidate) {
  if (event.key === "Enter" || event.key === " " || event.key === "Spacebar") {
    event.preventDefault();
    select(candidate);
    return;
  }
  if (!["ArrowDown", "ArrowUp", "Home", "End"].includes(event.key)) return;

  const available = visibleCandidates.value.filter(
    (item) => !item.inactive,
  );
  const index = available.findIndex((item) => item.id === candidate.id);
  if (index < 0 || !available.length) return;

  event.preventDefault();
  const nextIndex =
    event.key === "Home"
      ? 0
      : event.key === "End"
        ? available.length - 1
        : event.key === "ArrowDown"
          ? (index + 1) % available.length
          : (index - 1 + available.length) % available.length;
  const nextCandidate = available[nextIndex];
  if (nextCandidate) focusOption(nextCandidate);
}

function select(candidate: AccountCandidate | null) {
  if (candidate?.inactive) return;
  if (candidate) activeId.value = candidate.id;
  suppressModelReset = true;
  emit("update:modelValue", candidate);
  void Promise.resolve().then(() => {
    suppressModelReset = false;
  });
}
</script>

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

    <div class="account-picker-options" role="listbox" :aria-label="ariaLabel">
      <button
        v-for="candidate in visibleCandidates"
        :key="candidate.id"
        type="button"
        role="option"
        :aria-selected="candidate.id === modelValue?.id"
        :class="{
          selected: candidate.id === modelValue?.id,
          unavailable: candidate.inactive,
        }"
        :disabled="candidate.inactive"
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
import { computed, ref } from "vue";
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
  }>(),
  {
    ariaLabel: "选择账号",
    placeholder: "搜索姓名或学号",
    inputName: undefined,
    invalid: false,
    describedBy: undefined,
  },
);
const emit = defineEmits<{
  "update:modelValue": [value: AccountCandidate | null];
}>();

const keyword = ref("");
const visibleCandidates = computed(() =>
  filterAccountCandidates(
    mergeAccountCandidates(props.candidates, props.modelValue),
    keyword.value,
  ),
);

function select(candidate: AccountCandidate | null) {
  emit("update:modelValue", candidate);
}
</script>

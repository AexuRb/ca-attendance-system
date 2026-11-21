<template>
  <div v-if="passedAssessments.length" class="member-assessment-badges">
    <span
      v-for="field in passedAssessments"
      :key="field.key"
      class="mw-badge"
      data-tone="success"
    >
      {{ field.shortLabel }}
    </span>
  </div>
  <span v-else class="member-assessment-empty">{{ allResultsKnown ? "暂无通过项" : "考核未记录" }}</span>
</template>

<script setup lang="ts">
import { computed } from "vue";
import {
  memberAssessments,
  memberAssessmentStatus,
  type MemberSummary,
} from "./memberDirectory";

const props = defineProps<{ member: MemberSummary }>();
const passedAssessments = computed(() =>
  memberAssessments.filter(({ key }) => memberAssessmentStatus(props.member, key) === "PASSED"),
);
const allResultsKnown = computed(() =>
  memberAssessments.every(({ key }) => memberAssessmentStatus(props.member, key) !== undefined),
);
</script>

<style scoped>
.member-assessment-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 6px;
}
.member-assessment-empty {
  color: var(--mw-muted);
  font: 400 12px/20px var(--mw-font);
}
</style>

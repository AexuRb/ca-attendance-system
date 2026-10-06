<template>
  <component :is="as" v-show="Boolean(message)" :data-query-state="state" role="status" aria-live="polite" aria-atomic="true">{{ message }}</component>
</template>

<script setup lang="ts">
import { computed } from "vue";

// Presentation only: callers own successful conditions, requests and retry snapshots.
const props = withDefaults(defineProps<{
  loading: boolean;
  failed: boolean;
  dirty: boolean;
  loadingText: string;
  failedText: string;
  dirtyText: string;
  idleText?: string;
  as?: "span" | "strong";
}>(), { idleText: "", as: "span" });

const state = computed(() => props.loading ? "loading" : props.failed ? "failed" : props.dirty ? "dirty" : "idle");
const message = computed(() => ({ loading: props.loadingText, failed: props.failedText, dirty: props.dirtyText, idle: props.idleText })[state.value]);
</script>

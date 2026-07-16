<template>
  <div class="toast-stack" aria-live="polite" aria-atomic="false">
    <TransitionGroup name="toast">
      <button
        v-for="toast in toasts"
        :key="toast.id"
        class="toast"
        :data-tone="toast.tone"
        @click="dismiss(toast.id)"
      >
        <CheckCircle2 v-if="toast.tone === 'success'" aria-hidden="true" />
        <TriangleAlert
          v-else-if="toast.tone === 'warning' || toast.tone === 'danger'"
          aria-hidden="true"
        />
        <Info v-else aria-hidden="true" />
        <span>{{ toast.message }}</span>
        <X aria-hidden="true" />
      </button>
    </TransitionGroup>
  </div>
</template>

<script setup lang="ts">
import { CheckCircle2, Info, TriangleAlert, X } from "@lucide/vue";
import { useToast } from "../composables/useToast";
const { toasts, dismiss } = useToast();
</script>

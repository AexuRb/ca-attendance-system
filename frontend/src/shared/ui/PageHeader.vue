<template>
  <header class="page-header">
    <div>
      <h1>{{ title }}</h1>
      <p v-if="meta" class="page-meta">{{ meta }}</p>
    </div>
    <div v-if="$slots.actions" class="page-actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup lang="ts">
defineProps<{
  title: string;
  meta?: string;
  description?: string;
  eyebrow?: string;
}>();
</script>

<template>
  <div class="empty-state">
    <component :is="icon || Inbox" aria-hidden="true" />
    <strong>{{ title }}</strong>
    <p v-if="description">{{ description }}</p>
    <slot />
  </div>
</template>

<script setup lang="ts">
import { Inbox } from "@lucide/vue";
defineProps<{ title: string; description?: string; icon?: unknown }>();
</script>

<template>
  <div class="loading-block" role="status">
    <LoaderCircle class="spin" /><span>{{ label || "正在加载" }}</span>
  </div>
</template>
<script setup lang="ts">
import { LoaderCircle } from "@lucide/vue";
defineProps<{ label?: string }>();
</script>

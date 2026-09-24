<template>
  <div class="mw-header-actions" aria-label="快捷入口">
    <RouterLink v-if="kioskAvailable" class="mw-header-action" :to="{ name: 'kiosk' }">
      <ArrowUpRight aria-hidden="true" /><span>返回签到台</span>
    </RouterLink>
    <button class="mw-header-action" type="button" aria-label="查看鸣谢" @click="$emit('openCredits')">
      <HeartHandshake aria-hidden="true" /><span>鸣谢</span>
    </button>
  </div>
</template>

<script setup lang="ts">
import { RouterLink } from "vue-router";
import { ArrowUpRight, HeartHandshake } from "@lucide/vue";

defineProps<{ kioskAvailable: boolean }>();
defineEmits<{ openCredits: [] }>();
</script>

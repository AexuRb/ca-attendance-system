<template>
  <div class="mw-header-actions" aria-label="快捷入口">
    <RouterLink data-button-kind="entry" v-if="kioskAvailable" class="mw-header-action" :to="{ name: 'kiosk' }">
      <ArrowUpRight aria-hidden="true" /><span>返回签到台</span>
    </RouterLink>
  </div>
</template>

<script setup lang="ts">
import { RouterLink } from "vue-router";
import { ArrowUpRight } from "@lucide/vue";

defineProps<{ kioskAvailable: boolean }>();
</script>

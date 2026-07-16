<template>
  <RouterView v-slot="{ Component }">
    <Transition name="route" mode="out-in">
      <component :is="Component" />
    </Transition>
  </RouterView>
  <ToastHost />
</template>

<script setup lang="ts">
import { RouterView } from "vue-router";
import ToastHost from "./shared/ui/ToastHost.vue";
</script>

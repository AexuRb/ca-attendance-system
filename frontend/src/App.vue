<template>
  <RouterView v-slot="{ Component, route }">
    <component v-if="route.meta.auth" :is="Component" />
    <Transition v-else name="route" mode="out-in">
      <component :is="Component" />
    </Transition>
  </RouterView>
  <ToastHost />
</template>

<script setup lang="ts">
import { RouterView } from "vue-router";
import ToastHost from "./shared/ui/ToastHost.vue";
</script>

<template>
  <RouterView />
</template>

<script setup lang="ts">
// Each admin page owns its workspace shell; access checks stay in the router.
import { RouterView } from "vue-router";
</script>

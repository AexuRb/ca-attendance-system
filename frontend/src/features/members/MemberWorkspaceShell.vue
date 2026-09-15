<template>
  <RefinedWorkspaceShell>
    <template #tools><slot name="tools" /></template>
    <template #filters><slot name="filters" /></template>
    <slot />
  </RefinedWorkspaceShell>
</template>
<script setup lang="ts">
import RefinedWorkspaceShell from "../../layouts/RefinedWorkspaceShell.vue";
</script>

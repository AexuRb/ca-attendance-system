<template>
  <Transition name="command-feedback">
    <div v-if="message" class="command-feedback" role="status" aria-live="polite">
      <span class="command-feedback-mark">
        <img :src="logoPath" alt="">
      </span>
      <span>
        <strong>命令未执行</strong>
        <small>{{ message }}</small>
      </span>
    </div>
  </Transition>
</template>

<script setup lang="ts">
defineProps<{ message?: string }>();
const logoPath = "/brand/ca-logo-black.png";
</script>

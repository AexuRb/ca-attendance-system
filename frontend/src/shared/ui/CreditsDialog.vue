<template>
  <ModalDialog
    :open="open"
    :title="acknowledgements.title"
    eyebrow="CA LOCAL CONSOLE"
    size="sm"
    @close="$emit('close')"
  >
    <div class="credits-mark"><Sparkles aria-hidden="true" /></div>
    <p class="credits-intro">{{ acknowledgements.intro }}</p>
    <div class="credit-list">
      <div
        v-for="person in acknowledgements.contributors"
        :key="person.name"
        class="credit-row"
      >
        <strong>{{ person.name }}</strong
        ><span>{{ person.contribution }}</span>
      </div>
    </div>
    <p class="credits-version">计算机协会本地管理系统 · 离线版</p>
  </ModalDialog>
</template>

<script setup lang="ts">
import { Sparkles } from "@lucide/vue";
import { acknowledgements } from "../config/acknowledgements";
import ModalDialog from "./ModalDialog.vue";
defineProps<{ open: boolean }>();
defineEmits<{ close: [] }>();
</script>

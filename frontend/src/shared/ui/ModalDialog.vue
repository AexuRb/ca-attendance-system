<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="open" class="modal-backdrop" @mousedown.self="$emit('close')">
        <section
          class="modal-shell"
          :class="size ? `modal-${size}` : ''"
          role="dialog"
          aria-modal="true"
          :aria-labelledby="titleId"
        >
          <header class="modal-header">
            <div>
              <p v-if="eyebrow" class="eyebrow">{{ eyebrow }}</p>
              <h2 :id="titleId">{{ title }}</h2>
            </div>
            <button
              class="icon-button"
              type="button"
              aria-label="关闭"
              title="关闭"
              @click="$emit('close')"
            >
              <X />
            </button>
          </header>
          <div class="modal-body"><slot /></div>
          <footer v-if="$slots.footer" class="modal-footer">
            <slot name="footer" />
          </footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { X } from "@lucide/vue";
const titleId = `modal-${Math.random().toString(36).slice(2)}`;
defineProps<{
  open: boolean;
  title: string;
  eyebrow?: string;
  size?: "sm" | "lg" | "xl";
}>();
defineEmits<{ close: [] }>();
</script>

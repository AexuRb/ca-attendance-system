<template>
  <Teleport to="body">
    <Transition :name="memberPresentation ? 'mw-modal' : 'modal'">
      <div v-if="open" :class="memberPresentation ? 'mw-backdrop mw-scope' : 'modal-backdrop'" @mousedown.self="$emit('close')">
        <section
          ref="dialog"
          :class="[memberPresentation ? 'mw-modal' : 'modal-shell', size ? `modal-${size}` : '']"
          role="dialog"
          aria-modal="true"
          :aria-labelledby="titleId"
          tabindex="-1"
        >
          <header :class="memberPresentation ? 'mw-modal-head' : 'modal-header'">
            <div>
              <p v-if="eyebrow && !memberPresentation" class="eyebrow">{{ eyebrow }}</p>
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
          <div :class="memberPresentation ? 'mw-modal-body' : 'modal-body'" data-dialog-content><slot /></div>
          <footer v-if="$slots.footer" :class="memberPresentation ? 'mw-modal-foot' : 'modal-footer'">
            <slot name="footer" />
          </footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { X } from "@lucide/vue";
import { inject, ref } from "vue";
import { memberPresentationKey } from "./presentation";
import { useDialogFocus } from "./useDialogFocus";

const titleId = `modal-${Math.random().toString(36).slice(2)}`;
const memberPresentation = inject(memberPresentationKey, false);
const props = defineProps<{
  open: boolean;
  title: string;
  eyebrow?: string;
  size?: "sm" | "lg" | "xl";
}>();
const emit = defineEmits<{ close: [] }>();
const dialog = ref<HTMLElement | null>(null);
useDialogFocus({
  root: dialog,
  open: () => props.open,
  close: () => emit("close"),
});
</script>

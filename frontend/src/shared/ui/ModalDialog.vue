<template>
  <Teleport to="body">
    <Transition :name="memberPresentation ? 'mw-modal' : 'modal'">
      <div v-if="open" :class="[memberPresentation ? 'mw-backdrop mw-scope' : 'modal-backdrop', backdropClass, { 'app-dialog-backdrop': !customAppearance, 'is-side-panel': sidePanel }]" @mousedown.self="close">
        <section
          ref="dialog"
          :class="[memberPresentation ? 'mw-modal' : 'modal-shell', size ? `modal-${size}` : '', { 'app-dialog': !customAppearance }]"
          role="dialog"
          aria-modal="true"
          :aria-labelledby="titleId"
          :aria-describedby="description ? `${titleId}-description` : undefined"
          :aria-busy="busy || undefined"
          tabindex="-1"
        >
          <header :class="memberPresentation ? 'mw-modal-head' : 'modal-header'">
            <div>
              <p v-if="eyebrow && !memberPresentation" class="eyebrow">{{ eyebrow }}</p>
              <h2 :id="titleId" :tabindex="titleInteractive ? 0 : undefined" @click="$emit('title-activate')" @keydown.enter.prevent="$emit('title-activate')" @keydown.space.prevent="$emit('title-activate')"><slot name="title-icon" />{{ title }}</h2>
              <p v-if="description" :id="`${titleId}-description`" class="dialog-description">{{ description }}</p>
            </div>
            <button
              class="icon-button"
              type="button"
              aria-label="关闭"
              title="关闭"
              :disabled="busy"
              @click="close"
            >
              <X />
            </button>
          </header>
          <div :class="memberPresentation ? 'mw-modal-body' : 'modal-body'" :inert="busy ? true : undefined" data-dialog-content><slot /><p v-if="attempted && !busy && error" class="dialog-submit-error" role="alert">{{ error }}</p></div>
          <footer v-if="$slots.footer" :class="memberPresentation ? 'mw-modal-foot' : 'modal-footer'" :inert="busy ? true : undefined">
            <slot name="footer" />
          </footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { X } from "@lucide/vue";
import { inject, ref, watch } from "vue";
import { memberPresentationKey } from "./presentation";
import { useDialogFocus } from "./useDialogFocus";

const titleId = `modal-${Math.random().toString(36).slice(2)}`;
const memberPresentation = inject(memberPresentationKey, false);
const props = defineProps<{
  open: boolean;
  title: string;
  description?: string;
  busy?: boolean;
  error?: string;
  eyebrow?: string;
  sidePanel?: boolean;
  size?: "sm" | "lg" | "xl";
  backdropClass?: string;
  titleInteractive?: boolean;
  customAppearance?: boolean;
}>();
const emit = defineEmits<{ close: []; 'title-activate': [] }>();
const dialog = ref<HTMLElement | null>(null);
const attempted = ref(false);
watch([() => props.open, () => props.busy], ([open, busy], previous) => {
  if (!open || !previous?.[0]) attempted.value = false;
  if (open && busy) attempted.value = true;
}, { immediate: true });
function close() { if (!props.busy) emit("close"); }
useDialogFocus({
  root: dialog,
  open: () => props.open,
  close,
});
</script>

<style scoped>
.is-side-panel { justify-content: flex-end !important; grid-template-rows: minmax(0, 1fr); padding: 12px !important; }
.is-side-panel > section { height: 100%; max-height: 100% !important; width: min(740px, 100%); margin: 0; }
@media (max-width: 680px) { .is-side-panel { padding: 4px !important; } .is-side-panel > section { width: 100%; } }
</style>

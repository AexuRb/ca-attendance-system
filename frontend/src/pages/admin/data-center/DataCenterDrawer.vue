<template>
  <Teleport to="body" :disabled="!compact">
    <div
      v-if="compact && open"
      class="data-drawer-backdrop"
      aria-hidden="true"
      @mousedown.self="$emit('close')"
    />
    <Transition name="data-center-drawer">
      <aside
        v-if="open || !compact"
        :id="resolvedPanelId"
        ref="dialog"
        :class="[panelClass, { open }]"
        :role="compact ? 'dialog' : 'complementary'"
        :aria-modal="compact ? 'true' : undefined"
        :aria-hidden="open ? undefined : 'true'"
        :aria-labelledby="titleId"
        :inert="open ? undefined : true"
        tabindex="-1"
      >
        <div class="data-drawer-inner">
          <header class="data-drawer-head">
            <div>
              <span>{{ eyebrow }}</span>
              <h3 :id="titleId">{{ title }}</h3>
            </div>
            <button
              class="icon-button"
              type="button"
              :aria-label="closeLabel"
              :title="closeLabel"
              data-dialog-initial-focus
              @click="$emit('close')"
            >
              <X aria-hidden="true" />
            </button>
          </header>
          <div class="data-drawer-content" data-dialog-content>
            <slot />
          </div>
        </div>
      </aside>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  useId,
  watch,
} from "vue";
import { X } from "@lucide/vue";
import { useDialogFocus } from "../../../shared/ui/useDialogFocus";

const props = defineProps<{
  open: boolean;
  eyebrow: string;
  title: string;
  closeLabel: string;
  panelClass: string;
  panelId?: string;
}>();

const emit = defineEmits<{ close: [] }>();
const titleId = `data-drawer-${useId()}`;
const generatedPanelId = `data-panel-${useId()}`;
const resolvedPanelId = computed(() => props.panelId || generatedPanelId);
const compact = ref(false);
const dialog = ref<HTMLElement | null>(null);
const modalOpen = computed(() => props.open && compact.value);
let mediaQuery: MediaQueryList | null = null;
let desktopReturnFocus: HTMLElement | null = null;

useDialogFocus({
  root: dialog,
  open: () => modalOpen.value,
  close: () => emit("close"),
});

function syncCompactMode(event?: MediaQueryListEvent) {
  compact.value = event?.matches ?? mediaQuery?.matches ?? false;
}

onMounted(() => {
  mediaQuery = window.matchMedia?.("(max-width: 1280px)") ?? null;
  syncCompactMode();
  mediaQuery?.addEventListener?.("change", syncCompactMode);
});

watch(
  () => props.open,
  (open, wasOpen) => {
    if (open && !compact.value) {
      desktopReturnFocus =
        document.activeElement instanceof HTMLElement
          ? document.activeElement
          : null;
    } else if (!open && wasOpen && !compact.value && desktopReturnFocus?.isConnected) {
      const target = desktopReturnFocus;
      desktopReturnFocus = null;
      void nextTick(() => target.focus());
    }
  },
);

onBeforeUnmount(() => {
  mediaQuery?.removeEventListener?.("change", syncCompactMode);
});
</script>

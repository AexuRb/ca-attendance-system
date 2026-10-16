<template>
  <section id="settings-appearance" class="sw-section sw-appearance appearance-setting-section">
    <div class="sw-heading appearance-heading">
      <div>
        <h2>主题配色</h2>
      </div>
      <button
        v-if="canEdit"
        class="button primary small"
        type="button"
        :disabled="pending || modelValue === activeAppearance"
        @click="confirmOpen = true"
      >
        <Save />{{ pending ? "正在应用" : "应用配色" }}
      </button>
    </div>

    <p id="appearance-scope" class="appearance-scope">用于后台、登录页和签到台，初始化本机保持固定配色。</p>
    <div class="appearance-choice-grid" role="radiogroup" aria-label="选择主题配色" aria-describedby="appearance-scope">
      <button
        v-for="(option, index) in options"
        :key="option.id"
        :ref="(element) => setOptionRef(element, index)"
        class="appearance-choice"
        :class="[`is-${appearanceRegistry[option.id].domValue}`, { selected: modelValue === option.id }]"
        type="button"
        role="radio"
        :aria-checked="modelValue === option.id"
        :disabled="!canEdit || pending"
        @click="$emit('update:modelValue', option.id)"
        @keydown.left.prevent="moveSelection(index, -1)"
        @keydown.right.prevent="moveSelection(index, 1)"
      >
        <SettingsAppearancePreview :appearance="appearanceRegistry[option.id].domValue" :palette="appearanceRegistry[option.id].palette" />
        <span class="appearance-choice-copy">
          <strong>{{ option.label }}</strong>
          <small>{{ option.detail }}</small>
        </span>
        <span class="appearance-current" :class="{ 'is-draft': modelValue === option.id && activeAppearance !== option.id }">
          <Check v-if="modelValue === option.id" aria-hidden="true" />
          {{ activeAppearance === option.id ? '已应用' : modelValue === option.id ? '待应用' : '\u00a0' }}
        </span>
      </button>
    </div>

    <p v-if="!canEdit" class="appearance-readonly">仅管理员可以修改主题配色。</p>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>

    <ConfirmDialog
      :open="confirmOpen"
      :backdrop-class="confirmBackdropClass"
      title="应用主题配色"
      :message="`切换为“${selectedLabel}”配色？后台、登录页和签到台将同步更新。`"
      confirm-label="确认应用"
      :pending="pending"
      pending-label="正在应用"
      @cancel="confirmOpen = false"
      @confirm="$emit('save')"
    />
  </section>
</template>

<script setup lang="ts">
import { Check, Save } from "@lucide/vue";
import { computed, ref, watch, type ComponentPublicInstance } from "vue";
import type { AppearanceId } from "../../../appearance/appearanceTypes";
import { appearanceRegistry } from "../../../appearance/appearanceRegistry";
import { appearanceOptions as options } from "../../../appearance/appearanceOptions";
import SettingsAppearancePreview from "./SettingsAppearancePreview.vue";
import ConfirmDialog from "../../../shared/ui/ConfirmDialog.vue";

const props = defineProps<{
  modelValue: AppearanceId;
  activeAppearance: AppearanceId;
  canEdit: boolean;
  pending: boolean;
  error?: string;
  confirmBackdropClass?: string;
}>();
const emit = defineEmits<{
  "update:modelValue": [appearance: AppearanceId];
  save: [];
}>();

const optionRefs = ref<(HTMLButtonElement | null)[]>([]);
const confirmOpen = ref(false);
const selectedLabel = computed(
  () => options.find((option) => option.id === props.modelValue)?.label || "所选配色",
);

watch(
  () => props.activeAppearance,
  (active) => {
    if (active === props.modelValue) confirmOpen.value = false;
  },
);

function setOptionRef(element: Element | ComponentPublicInstance | null, index: number) {
  optionRefs.value[index] = element instanceof HTMLButtonElement ? element : null;
}

function moveSelection(index: number, direction: number) {
  if (!props.canEdit || props.pending) return;
  const nextIndex = (index + direction + options.length) % options.length;
  const next = options[nextIndex];
  if (!next) return;
  emit("update:modelValue", next.id);
  optionRefs.value[nextIndex]?.focus();
}
</script>

<style scoped>
/* This component owns the seven-choice grid, independent of historical theme layouts. */
#settings-appearance .appearance-scope { margin:0; color:var(--mw-muted); font:400 13px/22px var(--mw-font); }
#settings-appearance .appearance-choice-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(min(100%,190px),1fr)); gap:12px; grid-column:auto; grid-row:auto; }
#settings-appearance .appearance-choice { display:grid; grid-template-columns:minmax(0,1fr); grid-template-rows:176px auto 20px; gap:8px; min-width:0; min-height:0; padding:10px; border:1px solid var(--mw-line); border-radius:12px; background:color-mix(in srgb,var(--mw-paper) 70%,transparent); color:var(--mw-ink); text-align:left; box-shadow:none; transform:none; transition:border-color 180ms,background-color 180ms; }
#settings-appearance .appearance-choice::before, #settings-appearance .appearance-choice::after { content:none; }
#settings-appearance .appearance-choice.selected { border-color:var(--mw-accent); background:color-mix(in srgb,var(--mw-accent) 4%,var(--mw-paper)); }
#settings-appearance .appearance-choice:not(:disabled):hover { border-color:color-mix(in srgb,var(--mw-accent) 50%,var(--mw-line)); }
#settings-appearance .appearance-choice:focus-visible { outline:2px solid var(--mw-accent); outline-offset:3px; }
#settings-appearance .appearance-choice:disabled { opacity:1; cursor:default; }
#settings-appearance .settings-appearance-preview { grid-column:1; grid-row:1; }
#settings-appearance .appearance-choice-copy { grid-column:1; grid-row:2; display:grid; gap:2px; min-width:0; padding:0; align-self:start; }
#settings-appearance .appearance-choice-copy strong { font:500 16px/24px var(--mw-font); }
#settings-appearance .appearance-choice-copy small { color:var(--mw-muted); font:400 12px/20px var(--mw-font); overflow-wrap:anywhere; }
#settings-appearance .appearance-current { position:static; grid-column:1; grid-row:3; display:flex; align-items:center; gap:5px; padding:0; border:0; background:transparent; color:var(--mw-muted); font:400 12px/20px var(--mw-font); }
#settings-appearance .appearance-current.is-draft { color:var(--mw-accent); }
#settings-appearance .appearance-current svg { width:13px; height:13px; color:var(--mw-accent); }
@media(prefers-reduced-motion:reduce) { #settings-appearance .appearance-choice { transition:none; } }
</style>

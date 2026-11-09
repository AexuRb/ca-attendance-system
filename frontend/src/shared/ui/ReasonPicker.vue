<template>
  <fieldset class="reason-picker" :disabled="disabled" :aria-invalid="invalid || undefined">
    <legend>{{ label || '操作原因' }} <span class="reason-picker-required" aria-hidden="true">*</span></legend>
    <div class="reason-picker-options">
      <label v-for="option in options" :key="option" class="reason-picker-option">
        <input type="radio" :name="name" :value="option" :disabled="disabled" :checked="selected === option" @change="choose(option)" />
        <span>{{ option }}</span>
      </label>
      <label class="reason-picker-option">
        <input type="radio" :name="name" value="other" :disabled="disabled" :checked="selected === OTHER" @change="choose(OTHER)" />
        <span>其他</span>
      </label>
    </div>
    <label v-if="selected === OTHER" class="reason-picker-other">
      <span class="reason-picker-other-label">具体原因</span>
      <textarea ref="otherInput" :value="other" :disabled="disabled" :name="`${name}Other`" rows="3" :maxlength="maxLength - PREFIX.length"
        placeholder="请填写具体原因" @input="updateOther(($event.target as HTMLTextAreaElement).value)" />
      <small class="reason-picker-count">{{ other.length }} / {{ maxLength - PREFIX.length }}</small>
    </label>
  </fieldset>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
const props = withDefaults(defineProps<{
  modelValue: string;
  options: readonly string[];
  name: string;
  label?: string;
  disabled?: boolean;
  invalid?: boolean;
  maxLength?: number;
}>(), { maxLength: 500 });
const emit = defineEmits<{ 'update:modelValue': [value: string] }>();
const OTHER = "__other__", PREFIX = "其他：";
const selected = ref(""), other = ref("");
const otherInput = ref<HTMLTextAreaElement | null>(null);
const output = computed(() => selected.value === OTHER
  ? other.value.trim() ? PREFIX + other.value.trim() : ""
  : selected.value);

watch([() => props.modelValue, () => props.options], ([value]) => {
  if (value === output.value) return;
  if (props.options.includes(value)) selected.value = value;
  else if (value) {
    selected.value = OTHER;
    other.value = value.startsWith(PREFIX) ? value.slice(PREFIX.length) : value;
  } else { selected.value = ""; other.value = ""; }
}, { immediate: true });

async function choose(value: string) {
  if (props.disabled) return;
  selected.value = value;
  emit("update:modelValue", output.value);
  if (value === OTHER) { await nextTick(); otherInput.value?.focus(); }
}
function updateOther(value: string) {
  if (props.disabled) return;
  other.value = value.slice(0, props.maxLength - PREFIX.length);
  emit("update:modelValue", output.value);
}
</script>

<style scoped>
.reason-picker { min-width:0; margin:0; padding:0; border:0; }
.reason-picker legend { margin:0 0 10px; padding:0; color:var(--mw-ink,var(--ink-800)); font:500 12px/1.6 var(--mw-font,var(--font-sans)); }
.reason-picker-options { display:flex; flex-wrap:wrap; gap:8px; }
.reason-picker-option { position:relative; display:inline-flex; align-items:center; gap:6px; min-height:38px; padding:7px 10px; border:1px solid var(--mw-line,var(--line)); border-radius:8px; background:transparent; color:var(--mw-ink,var(--ink-800)); font:400 13px/20px var(--mw-font,var(--font-sans)); cursor:pointer; }
:root[data-appearance] .reason-picker .reason-picker-option input {
  --radio-accent:var(--mw-accent,var(--theme-accent,#30669c)); --radio-fill:#fff; --radio-sweep:color-mix(in srgb,var(--radio-accent) 18%,transparent);
  appearance:none; -webkit-appearance:none; display:inline-grid; place-content:center; flex:none; width:14px; min-width:14px; min-height:14px; height:14px;
  margin:0; padding:0; border:1px solid var(--mw-line,var(--theme-line,#d6dfe7)); border-radius:50%; background-color:var(--radio-fill);
  background-image:linear-gradient(115deg,transparent 0%,transparent 43%,var(--radio-sweep) 70%,transparent 100%); background-size:250% 100%; background-position:0% 50%;
  outline:none; cursor:pointer; transition:background-color 180ms ease,background-position var(--mw-speed,220ms) ease,border-color 180ms ease,transform 140ms ease;
}
.reason-picker-option input::before { content:""; width:4px; height:4px; border-radius:50%; background:#fff; transform:scale(0); transition:transform 140ms ease; }
:root[data-appearance] .reason-picker .reason-picker-option input:checked { --radio-fill:var(--radio-accent); --radio-sweep:#ffffff45; border-color:var(--radio-accent); }
.reason-picker-option input:checked::before { transform:scale(1); }
:root[data-appearance] .reason-picker .reason-picker-option input:focus-visible { outline:2px solid var(--radio-accent); outline-offset:3px; }
:root[data-appearance] .reason-picker .reason-picker-option input:not(:disabled):active { transform:translateY(1px); }
:root[data-appearance] .reason-picker .reason-picker-option input:disabled { background:var(--ui-disabled-bg,#f1f3f5); border-color:var(--ui-disabled-line,#dde2e7); transform:none; cursor:default; }
.reason-picker-option input:disabled::before { background:var(--ui-disabled-ink,#626d79); }
.reason-picker-required { color:var(--ui-danger,#b23c45); }
.reason-picker-count { text-align:right; color:var(--mw-muted,var(--theme-muted)); font-size:11px; }
@media(hover:hover) and (pointer:fine) { :root[data-appearance] .reason-picker .reason-picker-option input:not(:disabled):hover { background-position:100% 50%; border-color:var(--radio-accent); } }
@media(prefers-reduced-motion:reduce) {
  :root[data-appearance] .reason-picker .reason-picker-option input,.reason-picker-option input::before { transition:none; }
  :root[data-appearance] .reason-picker .reason-picker-option input:not(:disabled):active { transform:none; }
}
.reason-picker-option:has(input:checked) { border-color:color-mix(in srgb,var(--mw-accent,var(--primary)) 45%,transparent); background:color-mix(in srgb,var(--mw-accent,var(--primary)) 8%,transparent); }

.reason-picker:disabled .reason-picker-option { opacity:.55; cursor:default; }
.reason-picker-other { display:grid; gap:6px; margin-top:12px; }
.reason-picker-other-label { color:var(--mw-muted,var(--ink-600)); font-size:13px; }
.reason-picker-other textarea { width:100%; min-width:0; min-height:86px; padding:8px 11px; border:1px solid var(--mw-line,var(--theme-line)); border-radius:8px; background:#fff; color:var(--mw-ink,var(--theme-ink)); font:400 13px/22px var(--mw-font,var(--font-sans)); resize:vertical; }
@media(hover:hover) and (pointer:fine) {
  .reason-picker:not(:disabled) .reason-picker-option:hover { background:color-mix(in srgb,var(--mw-accent,var(--primary)) 6%,transparent); }
}
</style>

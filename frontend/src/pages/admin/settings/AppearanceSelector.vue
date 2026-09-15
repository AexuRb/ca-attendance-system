<template>
  <section id="settings-appearance" class="sw-section sw-appearance appearance-setting-section">
    <div class="sw-heading appearance-heading">
      <div>
        <h2>界面外观</h2>
        <span>全局应用于后台、登录与签到台。</span>
      </div>
      <button
        v-if="canEdit"
        class="button primary small"
        type="button"
        :disabled="pending || modelValue === activeAppearance"
        @click="confirmOpen = true"
      >
        <Save />{{ pending ? "正在应用" : "应用界面" }}
      </button>
    </div>

    <div class="appearance-choice-grid" role="radiogroup" aria-label="选择全局界面">
      <button
        v-for="(option, index) in options"
        :key="option.id"
        :ref="(element) => setOptionRef(element, index)"
        class="appearance-choice"
        :class="[`is-${option.domValue}`, { selected: modelValue === option.id }]"
        type="button"
        role="radio"
        :aria-checked="modelValue === option.id"
        :disabled="!canEdit || pending"
        @click="$emit('update:modelValue', option.id)"
        @keydown.left.prevent="moveSelection(index, -1)"
        @keydown.right.prevent="moveSelection(index, 1)"
      >
        <span class="sw-preview" aria-hidden="true" :data-theme="option.domValue">
          <i class="sw-preview-nav"><b></b><b></b><b></b></i>
          <i class="sw-preview-side"><b></b><b></b><b></b></i>
          <i class="sw-preview-title">成员名册</i>
          <i class="sw-preview-tools"><b></b><b></b></i>
          <i class="sw-preview-lines"><b></b><b></b><b></b><b></b></i>
        </span>
        <span class="appearance-choice-copy">
          <strong>{{ option.label }}</strong>
          <small>{{ option.detail }}</small>
        </span>
        <span v-if="activeAppearance === option.id" class="appearance-current">
          <Check />当前
        </span>
      </button>
    </div>

    <p v-if="!canEdit" class="appearance-readonly">仅管理员可以修改全局界面。</p>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>

    <ConfirmDialog
      :open="confirmOpen"
      title="应用新的全局界面"
      :message="`确认切换为“${selectedLabel}”？当前页面与登录、签到台会立即使用新界面。`"
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
import ConfirmDialog from "../../../shared/ui/ConfirmDialog.vue";

const props = defineProps<{
  modelValue: AppearanceId;
  activeAppearance: AppearanceId;
  canEdit: boolean;
  pending: boolean;
  error?: string;
}>();
const emit = defineEmits<{
  "update:modelValue": [appearance: AppearanceId];
  save: [];
}>();

const options = [
  { id: "CLASSIC" as const, domValue: "classic", label: "经典", detail: "清晰 · 紧凑" },
  { id: "EDITORIAL" as const, domValue: "editorial", label: "编辑式", detail: "温暖 · 柔和" },
  { id: "SPATIAL" as const, domValue: "spatial", label: "空间式", detail: "轻盈 · 通透" },
];
const optionRefs = ref<(HTMLButtonElement | null)[]>([]);
const confirmOpen = ref(false);
const selectedLabel = computed(
  () => options.find((option) => option.id === props.modelValue)?.label || "所选界面",
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

<template>
  <div
    class="command-composer-bezel"
    :class="[`is-${state}`, { 'is-command': mode === 'command', 'panel-open': open }]"
  >
    <div class="command-composer">
      <textarea
        ref="textarea"
        name="adminCommand"
        :value="modelValue"
        rows="1"
        role="combobox"
        aria-autocomplete="list"
        autocomplete="off"
        aria-label="查找后台功能或输入命令"
        aria-controls="today-command-suggestions"
        :aria-expanded="open"
        :aria-activedescendant="activeDescendant"
        placeholder="输入 / 进入命令模式"
        @focus="$emit('focus')"
        @input="updateValue"
        @click="emitCaret"
        @keyup="emitCaret"
        @keydown="$emit('keydown', $event)"
        @compositionstart="$emit('composition', true)"
        @compositionend="finishComposition"
      ></textarea>
      <footer>
        <span
          class="command-composer-mode"
          role="status"
          aria-live="polite"
          :title="statusMessage"
        >
          <i aria-hidden="true"></i>
          {{ statusMessage }}
        </span>
        <button
          class="command-composer-send"
          type="button"
          :disabled="disabled"
          :title="submitLabel"
          :aria-label="submitLabel"
          @click="$emit('submit')"
        >
          <ArrowUp aria-hidden="true" />
        </button>
      </footer>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ArrowUp } from "@lucide/vue";
import { computed, nextTick, ref, watch } from "vue";
import type { CommandInputState } from "../../../features/command-center/commandTypes";

const props = defineProps<{
  modelValue: string;
  mode: "search" | "command";
  state: CommandInputState;
  statusMessage: string;
  open: boolean;
  disabled: boolean;
  activeDescendant?: string;
}>();
const emit = defineEmits<{
  "update:modelValue": [value: string];
  focus: [];
  caret: [value: number];
  keydown: [event: KeyboardEvent];
  submit: [];
  composition: [active: boolean];
}>();

const textarea = ref<HTMLTextAreaElement | null>(null);
const submitLabel = computed(() => {
  if (props.state === "executable" || props.state === "extensible") return "执行命令";
  if (props.state === "search") return "打开匹配功能";
  if (props.state === "invalid") return "检查命令";
  return "补全命令";
});

watch(() => props.modelValue, () => void nextTick(resize));

function updateValue(event: Event) {
  emit("update:modelValue", (event.target as HTMLTextAreaElement).value);
  emitCaret();
  resize();
}

function emitCaret() {
  emit("caret", textarea.value?.selectionStart ?? props.modelValue.length);
}

function finishComposition() {
  emit("composition", false);
  emitCaret();
}

function resize() {
  if (!textarea.value) return;
  textarea.value.style.height = "auto";
  textarea.value.style.height = Math.min(textarea.value.scrollHeight, 124) + "px";
}

function focus(value?: string, caret?: number) {
  textarea.value?.focus();
  if (typeof caret === "number") textarea.value?.setSelectionRange(caret, caret);
  if (value !== undefined) emit("update:modelValue", value);
}

function blur() {
  textarea.value?.blur();
}

defineExpose({ focus, blur });
</script>

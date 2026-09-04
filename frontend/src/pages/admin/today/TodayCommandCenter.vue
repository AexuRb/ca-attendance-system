<template>
  <section
    ref="root"
    class="command-workspace"
    :class="[`is-${view.state}`, { 'is-engaged': engaged }]"
  >
    <div class="command-workspace-messages">
      <CommandWelcome
        :date-label="dateLabel"
        :role-name="roleName"
        :actions="quickActions"
        :loading="loading"
        :compact="engaged"
        @execute="executeQuickAction"
      />
      <CommandFeedback :message="errorMessage" />
    </div>
    <div class="command-composer-zone">
      <div class="command-console-shell" :class="{ 'has-suggestions': panelOpen }">
        <CommandSuggestionPanel
          :open="panelOpen"
          :mode="view.mode"
          :path="view.path"
          :prompt="view.prompt"
          :suggestions="view.suggestions"
          :active-index="activeIndex"
          @select="selectNode"
        />
        <CommandComposer
          ref="composer"
          :model-value="modelValue"
          :mode="view.mode"
          :state="view.state"
          :status-message="view.statusMessage"
          :open="panelOpen"
          :disabled="!modelValue.trim() || executing"
          :active-descendant="activeId"
          @update:model-value="updateInput"
          @focus="focusComposer"
          @caret="updateCaret"
          @keydown="handleInputKeydown"
          @submit="submit"
          @composition="composing = $event"
        />
      </div>
      <p class="command-local-note">所有数据与操作均保留在本机</p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import type { Role } from "../../../shared/types";
import { commandInputView, completeCommandInput } from "../../../features/command-center/commandTree";
import type { CommandNodeSuggestion } from "../../../features/command-center/commandTypes";
import CommandComposer from "./CommandComposer.vue";
import CommandFeedback from "./CommandFeedback.vue";
import CommandSuggestionPanel from "./CommandSuggestionPanel.vue";
import CommandWelcome from "./CommandWelcome.vue";
import type { TodayQuickAction } from "./types";
import { useCommandSession } from "./useCommandSession";

const props = defineProps<{
  modelValue: string;
  dateLabel: string;
  roleName: string;
  role: Role;
  quickActions: TodayQuickAction[];
  errorMessage?: string;
  loading?: boolean;
}>();
const emit = defineEmits<{
  "update:modelValue": [value: string];
  execute: [value: string];
  clearError: [];
}>();

const root = ref<HTMLElement | null>(null);
const composer = ref<InstanceType<typeof CommandComposer> | null>(null);
const caret = ref(0);
const composing = ref(false);
let executionTimer = 0;
const {
  open,
  activeIndex,
  executing,
  openSuggestions,
  resetSelection,
  moveActive,
  escape,
} = useCommandSession();
const view = computed(() => commandInputView(props.modelValue, props.role, caret.value));
const engaged = computed(() => Boolean(props.modelValue.trim()));
const executable = computed(() =>
  view.value.state === "executable" || view.value.state === "extensible",
);
const panelOpen = computed(() => open.value && (Boolean(props.modelValue.trim()) || view.value.mode === "command"));
const activeId = computed(() => {
  const item = view.value.suggestions[activeIndex.value];
  return panelOpen.value && item ? "today-command-" + item.id : undefined;
});

watch(
  () => [props.modelValue, props.role, view.value.suggestions.map((item) => item.id).join("|")],
  resetSelection,
);

onMounted(() => {
  window.addEventListener("keydown", handleGlobalKeydown);
  document.addEventListener("pointerdown", handleOutsidePointer);
});
onBeforeUnmount(() => {
  window.clearTimeout(executionTimer);
  window.removeEventListener("keydown", handleGlobalKeydown);
  document.removeEventListener("pointerdown", handleOutsidePointer);
});

function updateInput(value: string) {
  emit("update:modelValue", value);
  emit("clearError");
  openSuggestions();
}

function updateCaret(value: number) {
  caret.value = value;
}

function focusComposer() {
  if (props.modelValue.trim()) openSuggestions();
}

function handleInputKeydown(event: KeyboardEvent) {
  if (event.isComposing || composing.value) return;
  if (event.key === "ArrowDown" || event.key === "ArrowUp") {
    if (!panelOpen.value) return;
    event.preventDefault();
    moveActive(event.key === "ArrowDown" ? 1 : -1, view.value.suggestions.length);
    return;
  }
  if (event.key === "Tab" && panelOpen.value && view.value.suggestions.length) {
    event.preventDefault();
    completeActive();
    return;
  }
  if (event.key === "Enter" && !event.shiftKey) {
    event.preventDefault();
    submit();
    return;
  }
  if (event.key === "Escape") {
    event.preventDefault();
    escape(clearInput, () => composer.value?.blur());
  }
}

function submit() {
  if (!props.modelValue.trim() || executing.value) return;
  if (executable.value) {
    execute(props.modelValue);
    return;
  }
  if (view.value.suggestions.length) {
    selectNode(view.value.suggestions[activeIndex.value] || view.value.suggestions[0]!);
    return;
  }
  execute(props.modelValue);
}

function selectNode(item: CommandNodeSuggestion) {
  if (item.kind === "search") {
    emit("update:modelValue", item.completion);
    execute(item.completion);
    return;
  }
  const completed = completeCommandInput(props.modelValue, item, caret.value);
  emit("update:modelValue", completed.value);
  emit("clearError");
  caret.value = completed.caret;
  openSuggestions();
  void nextTick(() => composer.value?.focus(undefined, completed.caret));
}

function completeActive() {
  const item = view.value.suggestions[activeIndex.value] || view.value.suggestions[0];
  if (item) selectNode(item);
}

function executeQuickAction(command: string) {
  emit("update:modelValue", command);
  execute(command);
}

function execute(command: string) {
  if (executing.value) return;
  executing.value = true;
  open.value = false;
  emit("execute", command);
  executionTimer = window.setTimeout(() => {
    executing.value = false;
  }, 500);
}

function clearInput() {
  emit("update:modelValue", "");
  emit("clearError");
  caret.value = 0;
}

function handleGlobalKeydown(event: KeyboardEvent) {
  if (event.key !== "/" || event.isComposing || composing.value) return;
  if (isEditable(document.activeElement)) return;
  event.preventDefault();
  emit("update:modelValue", "/");
  emit("clearError");
  caret.value = 1;
  openSuggestions();
  void nextTick(() => composer.value?.focus(undefined, 1));
}

function handleOutsidePointer(event: PointerEvent) {
  if (!root.value?.contains(event.target as Node)) open.value = false;
}

function isEditable(element: Element | null) {
  return element instanceof HTMLInputElement ||
    element instanceof HTMLTextAreaElement ||
    element instanceof HTMLSelectElement ||
    (element instanceof HTMLElement && element.isContentEditable);
}
</script>

<template>
  <Transition name="command-panel">
    <div
      v-if="open"
      id="today-command-suggestions"
      class="command-suggestion-panel"
      role="listbox"
      aria-label="命令建议"
    >
      <header class="command-suggestion-head">
        <div>
          <span v-if="mode !== 'command'" class="command-suggestion-title">{{ prompt }}</span>
          <nav v-if="mode === 'command'" aria-label="当前命令路径">
            <template v-for="(part, index) in path" :key="part + index">
              <b :class="{ current: index === path.length - 1 }">{{ part }}</b>
              <ChevronRight v-if="index < path.length - 1" aria-hidden="true" />
            </template>
          </nav>
        </div>
        <span>Esc 关闭</span>
      </header>
      <div v-if="suggestions.length" class="command-suggestion-options">
        <span
          class="command-selection-rail"
          :style="{ transform: `translateY(${activeIndex * 60}px)` }"
          aria-hidden="true"
        ></span>
        <button
          v-for="(item, index) in suggestions"
          :id="suggestionId(item.id)"
          :key="item.id"
          :ref="(element) => setOptionRef(element, index)"
          class="command-suggestion-option"
          :class="{ active: index === activeIndex }"
          type="button"
          role="option"
          :aria-selected="index === activeIndex"
          @click="$emit('select', item)"
        >
          <span>
            <strong>{{ item.label }}</strong>
            <small>{{ item.description }}</small>
          </span>
          <span class="command-option-hint">
            <kbd v-if="mode === 'command'">Tab</kbd>
            <span>{{ mode === "command" ? "补全" : executionLabel(item.execution) }}</span>
            <ChevronRight aria-hidden="true" />
          </span>
        </button>
      </div>
      <div v-else class="command-suggestion-empty">{{ prompt }}</div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { ChevronRight } from "@lucide/vue";
import { nextTick, ref, watch, type ComponentPublicInstance } from "vue";
import type { CommandExecution, CommandNodeSuggestion } from "../../../features/command-center/commandTypes";

const props = defineProps<{
  open: boolean;
  mode: "search" | "command";
  path: string[];
  prompt: string;
  suggestions: CommandNodeSuggestion[];
  activeIndex: number;
}>();
defineEmits<{ select: [item: CommandNodeSuggestion] }>();

const optionRefs = ref<Array<HTMLElement | null>>([]);

watch(() => props.activeIndex, async (index) => {
  await nextTick();
  optionRefs.value[index]?.scrollIntoView({ block: "nearest" });
});

function setOptionRef(element: Element | ComponentPublicInstance | null, index: number) {
  optionRefs.value[index] = element instanceof HTMLElement ? element : null;
}

function suggestionId(id: string) {
  return "today-command-" + id;
}

function executionLabel(execution?: CommandExecution) {
  if (execution === "prefill") return "预填";
  if (execution === "confirm") return "需确认";
  return "打开";
}
</script>

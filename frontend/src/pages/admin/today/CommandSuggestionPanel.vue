<template>
  <Transition name="command-panel">
    <div
      v-if="open"
      id="today-command-suggestions"
      ref="panel"
      class="command-suggestion-panel"
      :class="{ 'is-compact': compact }"
      :style="placement"
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
      <div v-if="suggestions.length" ref="options" class="command-suggestion-options">
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
import { nextTick, onBeforeUnmount, ref, watch, type CSSProperties, type ComponentPublicInstance } from "vue";
import type { CommandExecution, CommandNodeSuggestion } from "../../../features/command-center/commandTypes";

const props = defineProps<{
  open: boolean;
  mode: "search" | "command";
  path: string[];
  prompt: string;
  suggestions: CommandNodeSuggestion[];
  activeIndex: number;
  floating?: boolean;
}>();
defineEmits<{ select: [item: CommandNodeSuggestion] }>();

const optionRefs = ref<Array<HTMLElement | null>>([]);
const panel = ref<HTMLElement | null>(null);
const options = ref<HTMLElement | null>(null);
const placement = ref<CSSProperties>({});
const compact = ref(false);
let observer: ResizeObserver | undefined;

function placePanel() {
  if (!props.floating || !panel.value?.parentElement) return;
  const rect = panel.value.parentElement.getBoundingClientRect();
  // DOM rectangles include CSS zoom; max-height is expressed in local CSS pixels.
  const scale = rect.width / (panel.value.parentElement.offsetWidth || rect.width) || 1;
  const viewport = window.visualViewport;
  const top = viewport?.offsetTop ?? 0;
  const bottom = top + (viewport?.height ?? window.innerHeight);
  const navigationBottom = Array.from(document.querySelectorAll<HTMLElement>(
    '.today-workspace .mw-primary, .today-workspace .mw-side, .today-workspace .mw-subnav',
  )).reduce((edge, navigation) => {
    if (getComputedStyle(navigation).display === 'none') return edge;
    const bounds = navigation.getBoundingClientRect();
    return bounds.right > rect.left && bounds.left < rect.right
      ? Math.max(edge, bounds.bottom)
      : edge;
  }, top);
  const safeTop = Math.max(top, navigationBottom + 8);
  const above = Math.max(0, (rect.top - safeTop - 8) / scale - 8);
  const below = Math.max(0, (bottom - rect.bottom - 8) / scale - 8);
  const upward = above > below + 16 && above >= 80;
  const available = Math.min(320, upward ? above : below);
  compact.value = available < 128;
  placement.value = {
    top: upward ? 'auto' : 'calc(100% + 8px)',
    bottom: upward ? 'calc(100% + 8px)' : 'auto',
    maxHeight: `${available}px`,
    transformOrigin: upward ? 'bottom center' : 'top center',
  };
}

function stopPlacement() {
  observer?.disconnect();
  window.removeEventListener('resize', placePanel);
  window.removeEventListener('scroll', placePanel, true);
  window.visualViewport?.removeEventListener('resize', placePanel);
  window.visualViewport?.removeEventListener('scroll', placePanel);
}

watch(() => [props.open, props.floating], () => {
  stopPlacement();
  if (!props.open || !props.floating) return;
  placePanel();
  if (typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver(placePanel);
    if (panel.value?.parentElement) observer.observe(panel.value.parentElement);
  }
  window.addEventListener('resize', placePanel);
  window.addEventListener('scroll', placePanel, true);
  window.visualViewport?.addEventListener('resize', placePanel);
  window.visualViewport?.addEventListener('scroll', placePanel);
}, { immediate: true, flush: 'post' });
onBeforeUnmount(stopPlacement);

watch(() => props.activeIndex, async (index) => {
  await nextTick();
  const option = optionRefs.value[index];
  const list = options.value;
  if (!option || !list) return;
  const top = option.offsetTop;
  const bottom = top + option.offsetHeight;
  if (top < list.scrollTop) list.scrollTop = top;
  else if (bottom > list.scrollTop + list.clientHeight) list.scrollTop = bottom - list.clientHeight;
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

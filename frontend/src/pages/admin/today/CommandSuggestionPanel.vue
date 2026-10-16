<template>
  <Transition name="command-panel" :css="!embedded">
    <div
      v-if="open"
      id="today-command-suggestions"
      ref="panel"
      class="command-suggestion-panel"
      :class="{ 'is-compact': compact, 'is-embedded': embedded, 'is-upward': upward }"
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
        <span>{{ suggestions.length ? `${suggestions.length} 项 · ` : '' }}Esc 关闭</span>
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
          tabindex="-1"
          @click="$emit('select', item)"
        >
          <span class="command-option-copy">
            <strong>{{ item.label }}</strong>
            <small>{{ item.description }}</small>
          </span>
          <span class="command-option-hint">
            <span>{{ mode === "command" ? parameterLabel(item) : executionLabel(item.execution) }}</span>
            <ChevronRight aria-hidden="true" />
          </span>
        </button>
      </div>
      <div v-else class="command-suggestion-empty">{{ prompt }}</div>
      <footer v-if="(floating || embedded) && suggestions.length" class="command-suggestion-footer" aria-hidden="true">
        <span>↑ ↓ 选择</span><span>{{ mode === 'command' ? 'Tab 补全' : 'Enter 发送' }}</span>
      </footer>
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
  embedded?: boolean;
  upward?: boolean;
}>();
defineEmits<{ select: [item: CommandNodeSuggestion] }>();

const optionRefs = ref<Array<HTMLElement | null>>([]);
const panel = ref<HTMLElement | null>(null);
const options = ref<HTMLElement | null>(null);
const placement = ref<CSSProperties>({});
const compact = ref(false);
let observer: ResizeObserver | undefined;

function placePanel() {
  if (props.embedded) {
    const input = panel.value?.parentElement?.querySelector('.command-composer-bezel');
    const content = panel.value?.closest('.overview-content');
    if (!input || !content) return;
    const bounds = input.getBoundingClientRect();
    const anchorBounds = props.upward ? panel.value!.parentElement!.getBoundingClientRect() : bounds;
    const scale = bounds.width / ((input as HTMLElement).offsetWidth || bounds.width) || 1;
    const viewport = window.visualViewport;
    const top = Math.max(viewport?.offsetTop ?? 0, content.getBoundingClientRect().top) + 8;
    const bottom = Math.min((viewport?.offsetTop ?? 0) + (viewport?.height ?? innerHeight), content.getBoundingClientRect().bottom) - 8;
    const stage = panel.value?.closest('.today-conversation')?.querySelector('.today-command-stage')?.getBoundingClientRect();
    const stageSpace = props.upward && stage ? (anchorBounds.top - stage.top) / scale - 8 : 320;
    const available = Math.min(320, Math.max(86, Math.min(stageSpace, (bottom - top - anchorBounds.height) / scale - 8)));
    compact.value = available < 142;
    const height = available >= 142 ? 86 + Math.floor((available - 86) / 56) * 56 : available;
    placement.value = { maxHeight: `${height}px` };
    void nextTick(revealEmbeddedPanel);
    return;
  }
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
  const content = panel.value.closest('.overview-content')?.getBoundingClientRect();
  const safeTop = Math.max(top, navigationBottom, content?.top ?? top) + 8;
  const safeBottom = Math.min(bottom, content?.bottom ?? bottom) - 8;
  const above = Math.max(0, (rect.top - safeTop) / scale - 8);
  const below = Math.max(0, (safeBottom - rect.bottom) / scale - 8);
  // Prefer the usual downward list when it fits; otherwise use the larger visible side.
  const expected = Math.min(320, 86 + Math.max(1, props.suggestions.length) * 56);
  const upward = below < expected && above > below;
  const available = Math.min(320, upward ? above : below);
  compact.value = available < 142;
  // Fit whole 56px rows alongside the fixed header and keyboard hints when space permits.
  const height = available >= 142 ? 86 + Math.floor((available - 86) / 56) * 56 : available;
  placement.value = {
    top: upward ? 'auto' : 'calc(100% + 8px)',
    bottom: upward ? 'calc(100% + 8px)' : 'auto',
    maxHeight: `${height}px`,
    transformOrigin: upward ? 'bottom center' : 'top center',
  };
}

function revealEmbeddedPanel() {
  if (!props.embedded || !props.open || !panel.value) return;
  const content = panel.value.closest<HTMLElement>('.overview-content');
  const input = panel.value.parentElement?.querySelector('.command-composer-bezel');
  if (!content || !input) return;
  const viewport = window.visualViewport;
  const bounds = content.getBoundingClientRect();
  const top = Math.max(viewport?.offsetTop ?? 0, bounds.top) + 8;
  const bottom = Math.min((viewport?.offsetTop ?? 0) + (viewport?.height ?? innerHeight), bounds.bottom) - 8;
  const panelBounds = panel.value.getBoundingClientRect();
  const inputBounds = input.getBoundingClientRect();
  const delta = props.upward
    ? inputBounds.bottom > bottom ? inputBounds.bottom - bottom : Math.min(0, panelBounds.top - top)
    : Math.min(Math.max(0, panelBounds.bottom - bottom), Math.max(0, inputBounds.top - top));
  const scale = bounds.width / (content.offsetWidth || bounds.width) || 1;
  if (delta !== 0) content.scrollTop += delta / scale;
}

function stopPlacement() {
  observer?.disconnect();
  window.removeEventListener('resize', placePanel);
  window.removeEventListener('scroll', placePanel, true);
  window.visualViewport?.removeEventListener('resize', placePanel);
  window.visualViewport?.removeEventListener('scroll', placePanel);
}

watch(() => [props.open, props.floating, props.embedded, props.upward, props.suggestions.length], () => {
  stopPlacement();
  if (!props.open || (!props.floating && !props.embedded)) return;
  placePanel();
  if (typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver(placePanel);
    const anchor = props.embedded ? panel.value?.parentElement?.querySelector('.command-composer-bezel') : panel.value?.parentElement;
    if (anchor) observer.observe(anchor);
  }
  window.addEventListener('resize', placePanel);
  if (!props.embedded) window.addEventListener('scroll', placePanel, true);
  window.visualViewport?.addEventListener('resize', placePanel);
  window.visualViewport?.addEventListener('scroll', placePanel);
}, { immediate: true, flush: 'post' });
onBeforeUnmount(stopPlacement);

watch(() => props.activeIndex, async (index) => {
  await nextTick();
  const option = optionRefs.value[index];
  const list = options.value;
  if (!option || !list) return;
  const listBounds = list.getBoundingClientRect();
  const optionBounds = option.getBoundingClientRect();
  const scale = listBounds.width / (list.offsetWidth || listBounds.width) || 1;
  const top = (optionBounds.top - listBounds.top) / scale + list.scrollTop;
  const bottom = top + optionBounds.height / scale;
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
  return "选用";
}

function parameterLabel(item: CommandNodeSuggestion) {
  if (item.kind === 'action') return '动作';
  if (item.kind === 'object') return '功能';
  if (/^(今天|昨天|本周|上周|本月|上月|本年|周[一二三四五六日]|\d{4}-\d{2}-\d{2})$/.test(item.label)) return '时间';
  if (['待审核', '未签退', '进行中', '已完成', '已取消', '启用', '停用'].includes(item.label)) return '状态';
  return '参数';
}
</script>

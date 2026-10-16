<template>
  <section class="training-month-shell" aria-labelledby="training-month-title" :aria-busy="loading">
    <div class="training-month-ribbon">
      <header class="training-month-header">
        <h2 id="training-month-title" :title="label">培训场次</h2>
        <span aria-live="polite">{{ loading ? (items.length ? '正在更新…' : '正在加载…') : `${total} 场` }}</span>
      </header>
      <div v-if="error" class="training-ribbon-feedback danger" role="alert">
        <span>{{ error }}{{ items.length ? '；下方仍显示上次结果' : '' }}</span>
        <button class="button text" type="button" @click="$emit('retry')">重试</button>
      </div>
      <p v-if="loading && !items.length && !error" class="training-ribbon-feedback" aria-live="polite">正在加载培训场次…</p>
      <div v-if="orderedItems.length" ref="viewport" class="training-ribbon-viewport" @scroll="$emit('scroll')">
        <button v-for="item in orderedItems" :key="item.id" class="training-ribbon-event training-list-event" :class="{ active: item.id === selectedId }" type="button" :aria-pressed="item.id === selectedId" :disabled="loading || Boolean(error)" @click="$emit('select', item)">
          <time class="training-session-day" :datetime="item.trainingDate" :title="item.trainingDate"><strong>{{ item.trainingDate.slice(8, 10) }}</strong><small>{{ Number(item.trainingDate.slice(5, 7)) }} 月</small></time>
          <span class="training-event-copy"><strong :title="item.title">{{ item.title }}</strong><small>{{ item.startTime?.slice(0, 5) || '时间未填' }}<template v-if="item.endTime"> — {{ item.endTime.slice(0, 5) }}</template> · {{ item.participantCount || 0 }} 人</small></span>
        </button>
      </div>
      <div v-else-if="!loading && !error" class="training-ribbon-empty"><CalendarRange aria-hidden="true" /><strong>没有符合条件的培训</strong></div>
      <footer v-if="total" class="training-ribbon-pagination">
        <span>{{ page }} / {{ totalPages }}</span>
        <div>
          <button class="icon-button" type="button" aria-label="上一页培训场次" :disabled="page <= 1 || loading || Boolean(error)" @click="$emit('page', page - 1)"><ChevronLeft aria-hidden="true" /></button>
          <button class="icon-button" type="button" aria-label="下一页培训场次" :disabled="!hasMore || loading || Boolean(error)" @click="$emit('page', page + 1)"><ChevronRight aria-hidden="true" /></button>
        </div>
      </footer>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch, type ComponentPublicInstance } from "vue";
import { CalendarRange, ChevronLeft, ChevronRight } from "@lucide/vue";
import type { TrainingSession } from "./trainingTypes";
const props = defineProps<{
  label: string; items: TrainingSession[]; selectedId: number | null; total: number;
  page: number; pageSize: number; hasMore: boolean; loading: boolean; error: string;
  bindScroll?: (element: Element | ComponentPublicInstance | null) => void;
  restoringHistory?: boolean;
}>();
defineEmits<{ select: [session: TrainingSession]; page: [page: number]; retry: []; scroll: [] }>();
const viewport = ref<HTMLElement | null>(null);
watch(viewport, element => props.bindScroll?.(element), { flush: "post" });
const orderedItems = computed(() => [...props.items].sort((a, b) => `${b.trainingDate} ${b.startTime || ''}`.localeCompare(`${a.trainingDate} ${a.startTime || ''}`) || b.id - a.id));
const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)));
watch(() => [props.selectedId, props.items] as const, async () => {
  await nextTick();
  if (props.restoringHistory) return;
  viewport.value?.querySelector<HTMLElement>('.training-ribbon-event[aria-pressed="true"]')?.scrollIntoView?.({
    behavior: window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'nearest', inline: 'nearest',
  });
}, { deep: true });
</script>

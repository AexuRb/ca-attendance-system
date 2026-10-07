<template>
  <section class="training-month-shell" aria-labelledby="training-month-title" :aria-busy="loading">
    <div class="training-month-ribbon">
      <header class="training-month-header">
        <div class="training-month-switcher">
          <button
            class="icon-button"
            type="button"
            aria-label="查看上个月培训"
            :disabled="loading"
            @click="$emit('shift-month', -1)"
          >
            <ChevronLeft aria-hidden="true" />
          </button>
          <div>
            <h2 id="training-month-title">{{ label }}</h2>
            <span>{{ loading && items.length ? "正在更新场次…" : `${total} 场培训` }}</span>
          </div>
          <button
            class="icon-button"
            type="button"
            aria-label="查看下个月培训"
            :disabled="loading"
            @click="$emit('shift-month', 1)"
          >
            <ChevronRight aria-hidden="true" />
          </button>
        </div>
        <dl v-if="items.length" class="training-month-summary">
          <div>
            <dt>{{ summaryPrefix }}参与人次</dt>
            <dd>{{ participantTotal }}</dd>
          </div>
          <div>
            <dt>{{ summaryPrefix }}累计时长</dt>
            <dd>{{ hours(durationTotal) }} h</dd>
          </div>
        </dl>
      </header>

      <div v-if="error" class="training-ribbon-feedback danger" role="alert">
        <span>{{ error }}{{ items.length ? "；下方仍显示上次结果" : "" }}</span>
        <button class="button text" type="button" @click="$emit('retry')">
          重试
        </button>
      </div>
      <p
        v-if="loading && !items.length && !error"
        class="training-ribbon-feedback"
        aria-live="polite"
      >
        正在加载培训场次…
      </p>
      <div
        v-if="orderedItems.length"
        ref="viewport"
        class="training-ribbon-viewport"
        @scroll="$emit('scroll')"
      >
        <p class="training-list-scope">当前页 {{ items.length }} / {{ total }} 场 · 按日期分组；翻页查看其余场次</p>
        <section v-for="group in dateGroups" :key="group.date" class="training-date-group">
          <h3>{{ group.date }} · {{ weekday(group.date) }}</h3>
          <button v-for="item in group.items" :key="item.id" class="training-ribbon-event training-list-event"
            :class="{ active: item.id === selectedId }" type="button" :aria-pressed="item.id === selectedId"
            :disabled="loading || Boolean(error)" @click="$emit('select', item)">
            <time :datetime="item.trainingDate">{{ item.startTime?.slice(0, 5) || '时间未填' }}<template v-if="item.endTime">–{{ item.endTime.slice(0, 5) }}</template></time>
            <span><strong>{{ item.title }}</strong><small>{{ item.location || '地点未填' }}</small></span>
            <small>{{ item.participantCount || 0 }} 人</small>
          </button>
        </section>
      </div>
      <div v-else-if="!loading && !error" class="training-ribbon-empty">
        <CalendarRange aria-hidden="true" />
        <strong>没有符合条件的培训</strong>
      </div>

      <footer v-if="total > pageSize" class="training-ribbon-pagination">
        <span>第 {{ page }} / {{ totalPages }} 页</span>
        <div>
          <button
            class="icon-button"
            type="button"
            aria-label="上一页培训场次"
            :disabled="page <= 1 || loading"
            @click="$emit('page', page - 1)"
          >
            <ChevronLeft aria-hidden="true" />
          </button>
          <button
            class="icon-button"
            type="button"
            aria-label="下一页培训场次"
            :disabled="!hasMore || loading"
            @click="$emit('page', page + 1)"
          >
            <ChevronRight aria-hidden="true" />
          </button>
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
  label: string;
  items: TrainingSession[];
  selectedId: number | null;
  total: number;
  page: number;
  pageSize: number;
  hasMore: boolean;
  loading: boolean;
  error: string;
  bindScroll?: (element: Element | ComponentPublicInstance | null) => void;
  restoringHistory?: boolean;
}>();

defineEmits<{
  select: [session: TrainingSession];
  page: [page: number];
  "shift-month": [step: number];
  retry: [];
  scroll: [];
}>();

const viewport = ref<HTMLElement | null>(null);
watch(viewport, (element) => props.bindScroll?.(element), { flush: "post" });
const orderedItems = computed(() =>
  [...props.items].sort((left, right) =>
    `${left.trainingDate} ${left.startTime || ""}`.localeCompare(
      `${right.trainingDate} ${right.startTime || ""}`,
    ),
  ),
);
const dateGroups = computed(() => {
  const groups = new Map<string, TrainingSession[]>();
  for (const item of orderedItems.value) {
    const group = groups.get(item.trainingDate) || [];
    group.push(item);
    groups.set(item.trainingDate, group);
  }
  return [...groups].map(([date, items]) => ({ date, items }));
});
const participantTotal = computed(() =>
  props.items.reduce((sum, item) => sum + Number(item.participantCount || 0), 0),
);
const durationTotal = computed(() =>
  props.items.reduce((sum, item) => sum + Number(item.totalDurationHours || 0), 0),
);
const summaryPrefix = computed(() =>
  props.total > props.items.length ? "当前页" : "",
);
const totalPages = computed(() =>
  Math.max(1, Math.ceil(props.total / props.pageSize)),
);

watch(
  () => [props.selectedId, props.items] as const,
  async () => {
    await nextTick();
    if (props.restoringHistory) return;
    const selected = viewport.value?.querySelector<HTMLElement>(
      '.training-ribbon-event[aria-pressed="true"]',
    );
    selected?.scrollIntoView?.({
      behavior: reducedMotion() ? "auto" : "smooth",
      block: "nearest",
      inline: "center",
    });
  },
  { deep: true },
);


function weekday(value: string) {
  const [year = 0, month = 0, dayValue = 0] = value.split("-").map(Number);
  const parsed = new Date(year, month - 1, dayValue);
  return ["周日", "周一", "周二", "周三", "周四", "周五", "周六"][
    parsed.getDay()
  ];
}

function hours(value: number) {
  return value.toFixed(2).replace(/\.00$/, "").replace(/(\.\d)0$/, "$1");
}

function reducedMotion() {
  return typeof window !== "undefined" &&
    window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
}
</script>

<style scoped>
.training-ribbon-viewport { overflow: auto; max-height: 340px; }
.training-list-scope { font-size: 12px; margin: 0 0 8px; }
.training-date-group h3 { font-size: 13px; margin: 10px 0 4px; }
.training-list-event { display: grid; grid-template-columns: 110px minmax(0, 1fr) auto; align-items: center; width: 100%; min-width: 0; min-height: 58px; padding: 8px 12px; gap: 12px; text-align: left; }
.training-list-event time { font-size: 12px; }
.training-list-event > span { min-width: 0; }
.training-list-event > span strong { white-space: normal; overflow: visible; display: block; overflow-wrap: anywhere; }
.training-list-event small { display: block; font-size: 12px; }
@media (max-width: 680px) { .training-list-event { grid-template-columns: 86px minmax(0, 1fr) auto; gap: 8px; } }
</style>

<template>
  <section class="training-month-shell" aria-labelledby="training-month-title">
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
            <span>{{ total }} 场培训</span>
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
        <span>{{ error }}</span>
        <button class="button text" type="button" @click="$emit('retry')">
          重试
        </button>
      </div>
      <p
        v-else-if="loading && !items.length"
        class="training-ribbon-feedback"
        aria-live="polite"
      >
        正在加载培训场次…
      </p>
      <div
        v-else-if="orderedItems.length"
        ref="viewport"
        class="training-ribbon-viewport"
      >
        <div
          class="training-ribbon-track"
          :style="{ '--training-event-count': orderedItems.length }"
        >
          <button
            v-for="item in orderedItems"
            :key="item.id"
            class="training-ribbon-event"
            :class="{ active: item.id === selectedId }"
            type="button"
            :aria-pressed="item.id === selectedId"
            @click="$emit('select', item)"
          >
            <time :datetime="item.trainingDate">
              <strong>{{ day(item.trainingDate) }}</strong>
              <small>{{ weekday(item.trainingDate) }}</small>
            </time>
            <span>
              <strong>{{ item.title }}</strong>
              <small>{{ item.participantCount || 0 }} 人</small>
            </span>
          </button>
        </div>
      </div>
      <div v-else class="training-ribbon-empty">
        <CalendarRange aria-hidden="true" />
        <strong>本月暂无培训</strong>
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
import { computed, nextTick, ref, watch } from "vue";
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
}>();

defineEmits<{
  select: [session: TrainingSession];
  page: [page: number];
  "shift-month": [step: number];
  retry: [];
}>();

const viewport = ref<HTMLElement | null>(null);
const orderedItems = computed(() =>
  [...props.items].sort((left, right) =>
    `${left.trainingDate} ${left.startTime || ""}`.localeCompare(
      `${right.trainingDate} ${right.startTime || ""}`,
    ),
  ),
);
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

function day(value: string) {
  return value.slice(8, 10) || "--";
}

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

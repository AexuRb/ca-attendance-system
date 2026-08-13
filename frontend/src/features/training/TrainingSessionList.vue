<template>
  <section class="training-session-list" aria-label="培训场次目录">
    <header class="training-directory-heading">
      <div>
        <p class="eyebrow">SESSIONS</p>
        <h2>培训场次</h2>
      </div>
      <span>{{ total }} 场</span>
    </header>

    <div v-if="error" class="training-region-feedback danger" role="alert">
      <span>{{ error }}</span>
      <button class="button text" type="button" @click="$emit('retry')">
        重试
      </button>
    </div>
    <p
      v-else-if="loading && !items.length"
      class="training-region-feedback"
      aria-live="polite"
    >
      正在加载培训场次…
    </p>

    <div v-if="items.length" class="training-session-scroll">
      <button
        v-for="item in items"
        :key="item.id"
        class="training-session-item"
        :class="{ active: selectedId === item.id }"
        type="button"
        :aria-pressed="selectedId === item.id"
        @click="$emit('select', item)"
      >
        <time :datetime="item.trainingDate">
          <strong>{{ day(item.trainingDate) }}</strong>
          <span>{{ month(item.trainingDate) }}</span>
        </time>
        <span class="training-session-copy">
          <strong>{{ item.title }}</strong>
          <small>{{ item.speaker || "未填写主讲人" }}</small>
        </span>
        <span class="training-session-count">{{ item.participantCount || 0 }}人</span>
      </button>
    </div>

    <div
      v-else-if="!loading && !error"
      class="training-directory-empty"
    >
      <CalendarX aria-hidden="true" />
      <strong>暂无培训场次</strong>
      <span>调整筛选条件后再查看</span>
    </div>

    <footer v-if="total" class="training-directory-pagination">
      <button
        class="icon-button"
        type="button"
        aria-label="上一页培训场次"
        :disabled="page <= 1 || loading"
        @click="$emit('page', page - 1)"
      >
        <ChevronLeft aria-hidden="true" />
      </button>
      <span>第 {{ page }} / {{ totalPages }} 页</span>
      <button
        class="icon-button"
        type="button"
        aria-label="下一页培训场次"
        :disabled="!hasMore || loading"
        @click="$emit('page', page + 1)"
      >
        <ChevronRight aria-hidden="true" />
      </button>
    </footer>
  </section>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { CalendarX, ChevronLeft, ChevronRight } from "@lucide/vue";
import type { TrainingSession } from "./trainingTypes";

const props = defineProps<{
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
  retry: [];
}>();

const totalPages = computed(() =>
  Math.max(1, Math.ceil(props.total / props.pageSize)),
);

function day(value: string) {
  return value?.slice(8, 10) || "--";
}

function month(value: string) {
  const monthValue = Number(value?.slice(5, 7));
  return Number.isFinite(monthValue) ? `${monthValue}月` : "--";
}
</script>

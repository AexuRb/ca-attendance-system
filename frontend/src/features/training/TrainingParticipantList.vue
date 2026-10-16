<template>
  <section class="training-participant-section" aria-labelledby="participant-title" :aria-busy="loading">
    <header class="training-participant-toolbar">
      <div class="training-participant-title">
        <h2 id="participant-title">参与名单 <small>{{ total }} 人</small></h2>
        <span v-if="loading && items.length" class="training-list-refresh" role="status">正在更新…</span>
      </div>
      <form class="training-participant-search" @submit.prevent="$emit('search')">
        <label><span class="sr-only">搜索参与名单</span><Search aria-hidden="true" /><input :value="keyword" name="participant-search" type="search" placeholder="搜索姓名、学号或备注" autocomplete="off" @input="$emit('update:keyword', ($event.target as HTMLInputElement).value)" /></label>
        <button class="button" data-button-kind="action" type="submit" :disabled="loading">{{ loading ? '搜索中…' : '搜索' }}</button>
      </form>
      <div class="training-participant-actions"><button data-button-kind="soft" class="button" type="button" data-action="add-participant" @click="$emit('add')"><Plus aria-hidden="true" />新增记录</button></div>
    </header>

    <p v-if="keyword.trim() !== resultKeyword" class="training-region-feedback" aria-live="polite">
      关键词尚未查询；当前结果：{{ resultKeyword || '全部参与记录' }}。翻页沿用当前结果条件。
    </p>

    <div v-if="error" class="training-region-feedback danger" role="alert">
      <span>{{ error }}{{ items.length ? "；下方仍显示上次结果" : "" }}</span>
      <button class="button text" type="button" @click="$emit('retry')">
        重试
      </button>
    </div>
    <p
      v-else-if="loading && !items.length"
      class="training-region-feedback"
      aria-live="polite"
    >
      正在加载参与名单…
    </p>

    <div v-if="items.length" :ref="bindScroll" :data-session-id="sessionId" class="training-participant-rows" role="region" aria-label="培训参与记录" tabindex="0" @scroll="$emit('scroll')">
      <table class="training-record-table">
        <thead><tr class="training-participant-columns"><th scope="col">成员</th><th scope="col">计入时长</th><th scope="col">备注</th><th scope="col">操作</th></tr></thead>
        <tbody><tr v-for="item in items" :key="item.id" class="training-participant-row">
          <td class="training-participant-identity"><strong>{{ item.name }}</strong><span>{{ item.studentNo || '未关联账号' }}</span></td>
          <td class="training-participant-duration">{{ hours(item.durationHours) }} <small>小时</small></td>
          <td class="training-participant-remark">{{ item.remark || '—' }}</td>
          <td><div class="training-participant-row-actions">
            <button class="icon-button" type="button" :aria-label="`编辑 ${item.name} 的参与记录`" title="编辑参与记录" :disabled="loading || Boolean(error) || deletePendingId === item.id" @click="$emit('edit', item)"><Pencil aria-hidden="true" /></button>
            <button class="icon-button danger-ghost" type="button" :aria-label="`删除 ${item.name} 的参与记录`" title="删除参与记录" :disabled="loading || Boolean(error) || deletePendingId === item.id" @click="$emit('delete', item)"><Trash2 aria-hidden="true" /></button>
          </div></td>
        </tr></tbody>
      </table>
    </div>

    <div v-else-if="!loading && !error" class="training-participant-empty">
      <UserRoundSearch aria-hidden="true" />
      <strong>{{ resultKeyword ? "没有匹配的参与记录" : "暂无参与记录" }}</strong>
      <span>{{ resultKeyword ? "换个关键词再试试" : "可从上方新增参与记录" }}</span>
    </div>

    <footer v-if="total" class="training-participant-pagination">
      <span>共 {{ total }} 人</span>
      <div>
        <button
          class="button secondary small"
          type="button"
          aria-label="上一页参与名单"
          :disabled="page <= 1 || loading || Boolean(error)"
          @click="$emit('page', page - 1)"
        >
          <ChevronLeft aria-hidden="true" />上一页
        </button>
        <span>第 {{ page }} / {{ totalPages }} 页</span>
        <button
          class="button secondary small"
          type="button"
          aria-label="下一页参与名单"
          :disabled="!hasMore || loading || Boolean(error)"
          @click="$emit('page', page + 1)"
        >
          下一页<ChevronRight aria-hidden="true" />
        </button>
      </div>
    </footer>
  </section>
</template>

<script setup lang="ts">
import { computed, type ComponentPublicInstance } from "vue";
import {
  ChevronLeft,
  ChevronRight,
  Pencil,
  Plus,
  Search,
  Trash2,
  UserRoundSearch,
} from "@lucide/vue";
import type { TrainingParticipant } from "./trainingTypes";

const props = defineProps<{
  items: TrainingParticipant[];
  total: number;
  page: number;
  pageSize: number;
  hasMore: boolean;
  loading: boolean;
  error: string;
  keyword: string;
  appliedKeyword?: string;
  deletePendingId?: number | null;
  sessionId?: number;
  bindScroll?: (element: Element | ComponentPublicInstance | null) => void;
}>();

defineEmits<{
  "update:keyword": [keyword: string];
  search: [];
  page: [page: number];
  add: [];
  edit: [participant: TrainingParticipant];
  delete: [participant: TrainingParticipant];
  retry: [];
  scroll: [];
}>();

const totalPages = computed(() =>
  Math.max(1, Math.ceil(props.total / props.pageSize)),
);
const resultKeyword = computed(() => props.appliedKeyword ?? props.keyword.trim());

function hours(value: number | string | null | undefined) {
  return Number(value || 0).toFixed(2).replace(/\.00$/, "");
}
</script>

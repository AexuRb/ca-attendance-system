<template>
  <section class="training-participant-section" aria-labelledby="participant-title">
    <header class="training-participant-toolbar">
      <h2 id="participant-title">参与名单</h2>
      <div class="training-participant-actions">
        <button
          class="button secondary small"
          type="button"
          data-action="import-participants"
          @click="$emit('import')"
        >
          <Upload aria-hidden="true" />导入名单
        </button>
        <button
          class="button primary small"
          type="button"
          data-action="add-participant"
          @click="$emit('add')"
        >
          <Plus aria-hidden="true" />新增记录
        </button>
      </div>
    </header>

    <form class="training-participant-search" @submit.prevent="$emit('search')">
      <Search aria-hidden="true" />
      <label>
        <span class="sr-only">搜索参与名单</span>
        <input
          :value="keyword"
          name="participant-search"
          type="search"
          placeholder="搜索姓名、学号或备注"
          autocomplete="off"
          @input="$emit('update:keyword', ($event.target as HTMLInputElement).value)"
        />
      </label>
      <button class="button secondary small" type="submit">搜索</button>
    </form>

    <div v-if="error" class="training-region-feedback danger" role="alert">
      <span>{{ error }}</span>
      <button class="button text" type="button" @click="$emit('retry')">
        重试
      </button>
    </div>
    <p
      v-else-if="loading"
      class="training-region-feedback"
      aria-live="polite"
    >
      正在加载参与名单…
    </p>

    <template v-else-if="items.length">
      <div class="training-participant-columns" aria-hidden="true">
        <span></span>
        <span>成员</span>
        <span>培训时长</span>
        <span>备注</span>
        <span>操作</span>
      </div>

      <div class="training-participant-rows">
      <article
        v-for="item in items"
        :key="item.id"
        class="training-participant-row"
      >
        <span class="training-participant-avatar" aria-hidden="true">{{ item.name.slice(0, 1) }}</span>
        <div class="training-participant-identity">
          <strong>{{ item.name }}</strong>
          <span>{{ item.studentNo || "未关联账号" }}</span>
        </div>
        <strong class="training-participant-duration">{{ hours(item.durationHours) }} h</strong>
        <details v-if="item.remark" class="training-participant-remark">
          <summary>查看备注</summary>
          <p>{{ item.remark }}</p>
        </details>
        <span v-else class="training-participant-remark-empty">无备注</span>
        <div class="training-participant-row-actions">
          <button
            class="icon-button"
            type="button"
            :aria-label="`编辑 ${item.name} 的参与记录`"
            title="编辑参与记录"
            :disabled="deletePendingId === item.id"
            @click="$emit('edit', item)"
          >
            <Pencil aria-hidden="true" />
          </button>
          <button
            class="icon-button danger-ghost"
            type="button"
            :aria-label="`删除 ${item.name} 的参与记录`"
            title="删除参与记录"
            :disabled="deletePendingId === item.id"
            @click="$emit('delete', item)"
          >
            <Trash2 aria-hidden="true" />
          </button>
        </div>
      </article>
      </div>
    </template>

    <div v-else class="training-participant-empty">
      <UserRoundSearch aria-hidden="true" />
      <strong>{{ keyword ? "没有匹配的参与记录" : "暂无参与记录" }}</strong>
      <span>{{ keyword ? "换个关键词再试试" : "可新增记录或导入名单" }}</span>
    </div>

    <footer v-if="total" class="training-participant-pagination">
      <span>共 {{ total }} 人</span>
      <div>
        <button
          class="button secondary small"
          type="button"
          aria-label="上一页参与名单"
          :disabled="page <= 1 || loading"
          @click="$emit('page', page - 1)"
        >
          <ChevronLeft aria-hidden="true" />上一页
        </button>
        <span>第 {{ page }} / {{ totalPages }} 页</span>
        <button
          class="button secondary small"
          type="button"
          aria-label="下一页参与名单"
          :disabled="!hasMore || loading"
          @click="$emit('page', page + 1)"
        >
          下一页<ChevronRight aria-hidden="true" />
        </button>
      </div>
    </footer>
  </section>
</template>

<script setup lang="ts">
import { computed } from "vue";
import {
  ChevronLeft,
  ChevronRight,
  Pencil,
  Plus,
  Search,
  Trash2,
  Upload,
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
  deletePendingId?: number | null;
}>();

defineEmits<{
  "update:keyword": [keyword: string];
  search: [];
  page: [page: number];
  add: [];
  import: [];
  edit: [participant: TrainingParticipant];
  delete: [participant: TrainingParticipant];
  retry: [];
}>();

const totalPages = computed(() =>
  Math.max(1, Math.ceil(props.total / props.pageSize)),
);

function hours(value: number | string | null | undefined) {
  return Number(value || 0).toFixed(2).replace(/\.00$/, "");
}
</script>

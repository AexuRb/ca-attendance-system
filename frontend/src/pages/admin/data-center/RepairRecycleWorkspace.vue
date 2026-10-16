<template>
  <section class="data-workspace data-recycle-workspace">
    <div class="data-frame data-recycle-overview">
      <section class="data-recycle-lead">
        <span>当前可恢复事务</span>
        <div>
          <strong>{{ items.length }}</strong>
          <p>项维修事务</p>
        </div>
        <small>{{ recentSummary }}</small>
      </section>

      <section class="data-recycle-trend">
        <header>
          <h2>回收站内近 30 天分布</h2>
          <span>仅统计仍在回收站的记录</span>
        </header>
        <div class="data-trend-chart" aria-hidden="true">
          <i
            v-for="(bucket, index) in trendBuckets"
            :key="bucket.label"
            :class="{ hot: bucket.count === maxTrend && bucket.count > 0 }"
            :style="trendStyle(bucket.count, index)"
            :title="`${bucket.label}：${bucket.count} 项`"
          >
            <b>{{ bucket.count }}</b>
          </i>
        </div>
        <div class="data-trend-axis"><span>30 天前</span><span>今天</span></div>
        <ul class="sr-only data-trend-summary" aria-label="回收站内近 30 天删除记录分布">
          <li v-for="bucket in trendBuckets" :key="`summary-${bucket.label}`">
            {{ bucket.label }}：{{ bucket.count }} 项
          </li>
        </ul>
      </section>

      <aside class="data-recycle-status">
        <span>恢复状态</span>
        <div>
          <i aria-hidden="true"><ArchiveRestore /></i>
          <p>
            <strong>{{ items.length ? `${items.length} 项等待处理` : "无需处理" }}</strong>
            <small>{{ latestDeletedAt }}</small>
          </p>
        </div>
        <button class="button secondary" type="button" @click="$emit('refresh')">
          <RefreshCw aria-hidden="true" />刷新回收站
        </button>
      </aside>
    </div>

    <div ref="listFrame" class="data-frame data-recycle-table-frame">
      <header class="data-table-toolbar">
        <div>
          <h2>删除档案</h2>
          <span aria-live="polite">{{ query ? `匹配 ${filteredItems.length} 项 / 全部 ${items.length} 项` : `共 ${items.length} 项` }}</span>
        </div>
        <label class="data-search-field">
          <Search aria-hidden="true" />
          <input v-model.trim="query" name="repair-recycle-search" aria-label="搜索回收站维修事务"
            autocomplete="off" placeholder="搜索编号、设备或送修人…" />
        </label>
      </header>
      <LoadingBlock v-if="loading" label="正在加载回收站" />
      <EmptyState v-else-if="!filteredItems.length" :title="emptyTitle" />
      <template v-else>
        <ul class="recycle-records" aria-label="回收站维修档案">
          <li v-for="item in pageItems" :key="item.id" class="recycle-record">
            <div class="recycle-record-main">
              <button class="recycle-device" type="button" :aria-label="`查看回收站详情：${item.caseNo}`" @click="detail = item">
                {{ deviceName(item) }}
              </button>
              <div class="recycle-identity"><span>{{ item.ownerName || '未填写送修人' }}</span><span class="recycle-case-number">{{ item.caseNo }}</span></div>
              <p class="recycle-fault" :title="item.faultDescription">{{ item.faultDescription || '未填写故障描述' }}</p>
            </div>
            <div class="recycle-record-meta">
              <span class="recycle-original-status">原状态 · {{ statusLabel(item.status) }}</span>
              <time :datetime="item.deletedAt">{{ repairDateTime(item.deletedAt) }}</time>
              <span class="recycle-deleted-by">{{ item.deletedByName || '未记录删除人' }} · 删除</span>
            </div>
            <div class="recycle-record-actions">
              <button class="icon-button" title="恢复维修事务" :aria-label="`恢复维修事务：${item.caseNo}`"
                type="button" :disabled="isRestorePending(item.id)" @click="$emit('restore', item)"><ArchiveRestore aria-hidden="true" /></button>
              <button v-if="canPurge" class="icon-button danger-ghost" type="button" title="彻底删除"
                :aria-label="`彻底删除维修事务：${item.caseNo}`" @click="$emit('request-purge', item)"><Trash2 aria-hidden="true" /></button>
            </div>
          </li>
        </ul>
        <nav class="recycle-pagination" aria-label="回收站分页">
          <span>第 {{ page }} / {{ pageCount }} 页 · 每页 {{ pageSize }} 项</span>
          <div>
            <button class="button secondary" type="button" :disabled="page <= 1" @click="changePage(page - 1)">上一页</button>
            <button class="button secondary" type="button" :disabled="page >= pageCount" @click="changePage(page + 1)">下一页</button>
          </div>
        </nav>
      </template>
    </div>
    <RecycleDetailDialog :item="detail" @close="detail = null" />
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import { ArchiveRestore, RefreshCw, Search, Trash2 } from "@lucide/vue";
import EmptyState from "../../../shared/ui/EmptyState.vue";
import LoadingBlock from "../../../shared/ui/LoadingBlock.vue";
import RecycleDetailDialog from "./RecycleDetailDialog.vue";
import { repairDateTime } from "../../../features/repairs/repairDisplay";
import { statusLabel } from "./recycleDisplay";
import type { RecycledRepairCase } from "../../../features/maintenance/dataCenterTypes";

const props = defineProps<{
  items: RecycledRepairCase[];
  loading: boolean;
  canPurge: boolean;
  isRestorePending: (id: number) => boolean;
}>();

defineEmits<{
  refresh: [];
  restore: [item: RecycledRepairCase];
  "request-purge": [item: RecycledRepairCase];
}>();

const query = ref("");
const filteredItems = computed(() => {
  const term = query.value.toLowerCase();
  if (!term) return props.items;
  return props.items.filter((item) =>
    `${item.caseNo} ${deviceName(item)} ${item.ownerName}`
      .toLowerCase()
      .includes(term),
  );
});
const pageSize = 20;
const page = ref(1);
const pageCount = computed(() => Math.max(1, Math.ceil(filteredItems.value.length / pageSize)));
const pageItems = computed(() => filteredItems.value.slice((page.value - 1) * pageSize, page.value * pageSize));
const detail = ref<RecycledRepairCase | null>(null);
const listFrame = ref<HTMLElement | null>(null);
watch(query, () => { page.value = 1; });
watch(pageCount, (count) => { page.value = Math.min(page.value, count); });
watch(() => props.items, (items) => {
  if (detail.value) detail.value = items.find(item => item.id === detail.value?.id) || null;
});
async function changePage(value: number) {
  page.value = value;
  await nextTick();
  listFrame.value?.scrollIntoView?.({ block: "start" });
}
const emptyTitle = computed(() =>
  props.items.length ? "没有匹配的维修事务" : "回收站为空",
);
const deletedDates = computed(() =>
  props.items
    .map((item) => (item.deletedAt ? new Date(item.deletedAt) : null))
    .filter((value): value is Date => Boolean(value && !Number.isNaN(value.getTime()))),
);
const trendBuckets = computed(() => {
  const today = startOfDay(new Date());
  return Array.from({ length: 15 }, (_, index) => {
    const rangeEndDaysAgo = (14 - index) * 2;
    const end = new Date(today);
    end.setDate(today.getDate() - rangeEndDaysAgo);
    const start = new Date(end);
    start.setDate(end.getDate() - 1);
    const count = deletedDates.value.filter((date) => {
      const day = startOfDay(date);
      return day >= start && day <= end;
    }).length;
    return {
      count,
      label: `${start.getMonth() + 1}-${start.getDate()} 至 ${end.getMonth() + 1}-${end.getDate()}`,
    };
  });
});
const maxTrend = computed(() =>
  Math.max(1, ...trendBuckets.value.map((bucket) => bucket.count)),
);
const recentCount = computed(() => deletedDates.value.filter((date) => {
  const threshold = new Date();
  threshold.setDate(threshold.getDate() - 30);
  return date >= threshold;
}).length);
const recentSummary = computed(() =>
  recentCount.value ? `近 30 天 ${recentCount.value} 项仍在回收站` : "近 30 天无待恢复记录",
);
const latestDeletedAt = computed(() => {
  const latest = deletedDates.value.sort((a, b) => b.getTime() - a.getTime())[0];
  return latest ? `最近删除 ${dateTime(latest.toISOString())}` : "暂无删除记录";
});

function startOfDay(value: Date) {
  return new Date(value.getFullYear(), value.getMonth(), value.getDate());
}

function trendStyle(count: number, index: number) {
  return {
    "--trend-height": count ? `${Math.max(18, (count / maxTrend.value) * 100)}%` : "4%",
    "--trend-delay": `${index * 0.025}s`,
  };
}

function deviceName(item: RecycledRepairCase) {
  return [item.deviceBrand, item.deviceModel].filter(Boolean).join(" ") || item.deviceType || "未命名设备";
}

function dateTime(value: string) {
  return new Date(value).toLocaleString("zh-CN", { hour12: false });
}
</script>

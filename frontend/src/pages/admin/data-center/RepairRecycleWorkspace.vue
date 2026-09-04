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

    <div class="data-frame data-recycle-table-frame">
      <header class="data-table-toolbar">
        <div>
          <h2>删除档案</h2>
          <span>共 {{ items.length }} 项</span>
        </div>
        <label class="data-search-field">
          <Search aria-hidden="true" />
          <input
            v-model.trim="query"
            name="repair-recycle-search"
            aria-label="搜索回收站维修事务"
            autocomplete="off"
            placeholder="搜索维修编号或设备…"
          />
        </label>
      </header>

      <LoadingBlock v-if="loading" label="正在加载回收站" />
      <EmptyState v-else-if="!filteredItems.length" :title="emptyTitle" />
      <div v-else class="table-shell data-recycle-table">
        <table>
          <thead>
            <tr>
              <th>维修编号</th>
              <th>设备</th>
              <th>删除时间</th>
              <th>删除人</th>
              <th class="align-right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in filteredItems" :key="item.id">
              <td><strong>{{ item.caseNo }}</strong></td>
              <td>
                <div class="data-device-cell">
                  <Wrench aria-hidden="true" />
                  <span>
                    <strong>{{ deviceName(item) }}</strong>
                    <small>{{ item.deviceType || "未填写设备类型" }}</small>
                  </span>
                </div>
              </td>
              <td>{{ item.deletedAt ? dateTime(item.deletedAt) : "—" }}</td>
              <td>{{ item.deletedByName || "—" }}</td>
              <td class="align-right row-actions">
                <button
                  class="button secondary"
                  type="button"
                  :disabled="isRestorePending(item.id)"
                  @click="$emit('restore', item)"
                >
                  <ArchiveRestore aria-hidden="true" />恢复
                </button>
                <button
                  v-if="canPurge"
                  class="icon-button danger-ghost"
                  type="button"
                  title="彻底删除"
                  :aria-label="`彻底删除维修事务：${item.caseNo}`"
                  @click="$emit('request-purge', item)"
                >
                  <Trash2 aria-hidden="true" />
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { ArchiveRestore, RefreshCw, Search, Trash2, Wrench } from "@lucide/vue";
import EmptyState from "../../../shared/ui/EmptyState.vue";
import LoadingBlock from "../../../shared/ui/LoadingBlock.vue";
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

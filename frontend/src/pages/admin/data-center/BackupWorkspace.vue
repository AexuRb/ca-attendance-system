<template>
  <section class="data-workspace data-backup-workspace">
    <div class="data-frame data-backup-overview">
      <section class="data-backup-health">
        <div class="data-health-state">
          <i aria-hidden="true" />{{ backups.length ? "备份状态正常" : "尚无恢复点" }}
        </div>
        <div class="data-latest-backup">
          <strong>{{ latestTime }}</strong>
          <span>{{ latestDay }}</span>
        </div>
        <div class="data-backup-metrics">
          <div><span>本机备份</span><b>{{ backupCount }} 份</b></div>
          <div><span>占用空间</span><b>{{ bytes(totalSize) }}</b></div>
        </div>
      </section>

      <section class="data-backup-timeline">
        <header>
          <h2>恢复节点</h2>
          <span>最近 {{ timelineItems.length }} 个完整备份</span>
        </header>
        <div v-if="timelineItems.length" class="data-timeline-track">
          <button
            v-for="item in timelineItems"
            :key="item.filename"
            type="button"
            :class="{ active: selected?.filename === item.filename }"
            :aria-label="`查看 ${shortDate(item.createdAt)} ${shortTime(item.createdAt)} 的备份详情`"
            aria-controls="data-backup-details"
            :aria-expanded="selected?.filename === item.filename"
            @click="selectBackup(item)"
          >
            <time>{{ shortDate(item.createdAt) }}</time>
            <i aria-hidden="true" />
            <b>{{ shortTime(item.createdAt) }}</b>
            <span>{{ bytes(item.size) }}</span>
          </button>
        </div>
        <EmptyState v-else title="创建首个备份后，这里会形成恢复时间轴" />
      </section>

      <aside class="data-backup-action">
        <ShieldCheck aria-hidden="true" />
        <div>
          <span>最近完整备份</span>
          <strong>
            <time v-if="latest" :datetime="latest.createdAt">{{ latestRelative }}</time>
            <span v-else>{{ latestRelative }}</span>
          </strong>
        </div>
        <button
          class="button primary"
          type="button"
          :disabled="createPending"
          @click="$emit('request-create')"
        >
          <DatabaseBackup aria-hidden="true" />立即备份
        </button>
      </aside>
    </div>

    <div
      class="data-frame data-backup-table-frame"
      :class="{ 'drawer-open': Boolean(selected) }"
    >
      <header class="data-table-toolbar">
        <div>
          <h2>备份档案</h2>
          <span>共 {{ backups.length }} 份</span>
        </div>
        <label class="data-search-field">
          <Search aria-hidden="true" />
          <input
            v-model.trim="query"
            name="backup-search"
            aria-label="搜索备份"
            autocomplete="off"
            placeholder="搜索日期或文件名…"
          />
        </label>
        <label v-if="canRestore" class="button secondary file-button">
          <Upload aria-hidden="true" />从文件恢复
          <input
            type="file"
            name="backup-restore-file"
            aria-label="选择需要恢复的备份文件"
            accept=".zip"
            @change="$emit('pick-restore', $event)"
          />
        </label>
      </header>

      <p v-if="restoreFileError" class="form-error data-restore-error" role="alert">
        {{ restoreFileError }}
      </p>

      <div class="data-backup-body">
        <div class="data-backup-list-pane">
          <LoadingBlock v-if="loading" label="正在加载备份" />
          <EmptyState v-else-if="!filteredBackups.length" :title="emptyTitle" />
          <div v-else class="table-shell data-backup-table">
            <table>
              <thead>
                <tr>
                  <th>备份文件</th>
                  <th>创建时间</th>
                  <th>大小</th>
                  <th class="align-right">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="item in filteredBackups"
                  :key="item.filename"
                  :class="{ selected: selected?.filename === item.filename }"
                >
                  <td>
                    <button
                      class="data-backup-file"
                      type="button"
                      :aria-label="`查看备份详情：${item.filename}`"
                      aria-controls="data-backup-details"
                      :aria-expanded="selected?.filename === item.filename"
                      @click="selectBackup(item)"
                    >
                      <FileArchive aria-hidden="true" />
                      <span><strong>{{ item.filename }}</strong><small>完整业务数据</small></span>
                    </button>
                  </td>
                  <td>{{ dateTime(item.createdAt) }}</td>
                  <td>{{ bytes(item.size) }}</td>
                  <td class="align-right row-actions">
                    <button
                      class="icon-button"
                      type="button"
                      title="下载备份"
                      :aria-label="`下载备份：${item.filename}`"
                      @click="$emit('download', item)"
                    >
                      <Download aria-hidden="true" />
                    </button>
                    <button
                      v-if="canDelete"
                      class="icon-button danger-ghost"
                      type="button"
                      title="删除备份"
                      :aria-label="`删除备份：${item.filename}`"
                      @click="$emit('request-delete', item)"
                    >
                      <Trash2 aria-hidden="true" />
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <DataCenterDrawer
          :open="Boolean(selected)"
          eyebrow="备份详情"
          title="可下载恢复点"
          close-label="关闭备份详情"
          panel-class="data-backup-drawer"
          panel-id="data-backup-details"
          @close="selected = null"
        >
          <template v-if="selected">
            <span class="data-backup-available">文件完整</span>
            <h4>{{ selected.filename }}</h4>
            <dl class="data-backup-facts">
              <div><dt>创建时间</dt><dd>{{ dateTime(selected.createdAt) }}</dd></div>
              <div><dt>文件大小</dt><dd>{{ bytes(selected.size) }}</dd></div>
              <div><dt>数据范围</dt><dd>完整业务数据</dd></div>
            </dl>
            <div class="data-detail-actions">
              <button class="button primary" type="button" @click="$emit('download', selected)">
                <Download aria-hidden="true" />下载备份
              </button>
              <button
                v-if="canDelete"
                class="button danger"
                type="button"
                @click="$emit('request-delete', selected)"
              >
                <Trash2 aria-hidden="true" />删除备份
              </button>
            </div>
          </template>
        </DataCenterDrawer>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import {
  DatabaseBackup,
  Download,
  FileArchive,
  Search,
  ShieldCheck,
  Trash2,
  Upload,
} from "@lucide/vue";
import EmptyState from "../../../shared/ui/EmptyState.vue";
import LoadingBlock from "../../../shared/ui/LoadingBlock.vue";
import DataCenterDrawer from "./DataCenterDrawer.vue";
import type {
  BackupItem,
  MaintenanceSummary,
} from "../../../features/maintenance/dataCenterTypes";

const props = defineProps<{
  summary: MaintenanceSummary | null;
  backups: BackupItem[];
  loading: boolean;
  createPending: boolean;
  canRestore: boolean;
  canDelete: boolean;
  restoreFileError: string;
}>();

defineEmits<{
  "request-create": [];
  "pick-restore": [event: Event];
  download: [item: BackupItem];
  "request-delete": [item: BackupItem];
}>();

const query = ref("");
const selected = ref<BackupItem | null>(null);
const relativeTimeNow = ref(Date.now());
let relativeTimeTimer: number | undefined;
const timelineItems = computed(() => props.backups.slice(0, 6).reverse());
const filteredBackups = computed(() => {
  const term = query.value.toLowerCase();
  if (!term) return props.backups;
  return props.backups.filter((item) =>
    `${item.filename} ${dateTime(item.createdAt)}`.toLowerCase().includes(term),
  );
});
const emptyTitle = computed(() =>
  props.backups.length ? "没有匹配的备份" : "还没有本机备份",
);
const backupCount = computed(
  () => props.summary?.backups?.count ?? props.backups.length,
);
const totalSize = computed(
  () =>
    props.summary?.backups?.totalSize ??
    props.backups.reduce((sum, item) => sum + item.size, 0),
);
const latest = computed(() => props.backups[0] || null);
const latestTime = computed(() => (latest.value ? shortTime(latest.value.createdAt) : "—"));
const latestDay = computed(() =>
  latest.value ? `${shortDate(latest.value.createdAt)} 最近完整备份` : "等待创建首个备份",
);
const latestRelative = computed(() => {
  if (!latest.value) return "尚未创建";
  const elapsed = relativeTimeNow.value - new Date(latest.value.createdAt).getTime();
  if (elapsed < 60_000) return "刚刚";
  if (elapsed < 3_600_000) return `${Math.floor(elapsed / 60_000)} 分钟前`;
  if (elapsed < 86_400_000) return `${Math.floor(elapsed / 3_600_000)} 小时前`;
  return `${Math.floor(elapsed / 86_400_000)} 天前`;
});

onMounted(() => {
  relativeTimeTimer = window.setInterval(() => {
    relativeTimeNow.value = Date.now();
  }, 60_000);
});

onBeforeUnmount(() => {
  if (relativeTimeTimer !== undefined) window.clearInterval(relativeTimeTimer);
});

watch(
  () => props.backups,
  (items) => {
    if (selected.value && !items.some((item) => item.filename === selected.value?.filename)) {
      selected.value = null;
    }
  },
);

function selectBackup(item: BackupItem) {
  selected.value = item;
}

function dateTime(value: string) {
  return new Date(value).toLocaleString("zh-CN", { hour12: false });
}

function shortDate(value: string) {
  const date = new Date(value);
  const now = new Date();
  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  if (date.toDateString() === now.toDateString()) return "今天";
  if (date.toDateString() === yesterday.toDateString()) return "昨天";
  return `${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function shortTime(value: string) {
  return new Date(value).toLocaleTimeString("zh-CN", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
}

function bytes(value: number) {
  if (value < 1024) return `${value} B`;
  if (value < 1_048_576) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / 1_048_576).toFixed(1)} MB`;
}
</script>

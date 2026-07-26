<template>
  <div class="page-stack">
    <PageHeader
      eyebrow="SYSTEM / AUDIT"
      title="操作日志"
      description="查看管理员操作轨迹与数据变更前后内容。"
      ><template #actions
        ><button class="button secondary" @click="exportLogs">
          <Download />导出日志</button
        ><button class="button danger" @click="clearOpen = true">
          <Trash2 />清空日志
        </button></template
      ></PageHeader
    >
    <form class="filter-bar" @submit.prevent="load(1)">
      <label class="filter-grow"
        ><span>关键词</span
        ><input
          v-model.trim="filters.keyword"
          placeholder="操作人、对象或原因" /></label
      ><label
        ><span>操作类型</span
        ><input
          v-model.trim="filters.actionType"
          placeholder="例如 UPDATE" /></label
      ><label
        ><span>开始日期</span
        ><input v-model="filters.from" type="date" /></label
      ><label
        ><span>结束日期</span><input v-model="filters.to" type="date" /></label
      ><button class="button secondary" type="submit"><Search />查询</button>
    </form>
    <LoadingBlock v-if="busy && !items.length" /><EmptyState
      v-else-if="!items.length"
      title="暂无操作日志"
    />
    <div v-else class="timeline-list">
      <article v-for="item in items" :key="item.id">
        <span class="timeline-mark"></span>
        <div class="log-time">
          <strong>{{ time(item.createdAt) }}</strong
          ><span>{{ date(item.createdAt) }}</span>
        </div>
        <div class="log-main">
          <div>
            <StatusBadge
              :label="actionLabel(item.actionType)"
              :tone="actionTone(item.actionType)"
            /><strong
              >{{ item.operatorName || "系统" }} ·
              {{ targetLabel(item) }}</strong
            >
          </div>
          <p>{{ item.reason || "未填写操作原因" }}</p>
          <small v-if="item.operatorStudentNo">{{
            item.operatorStudentNo
          }}</small>
        </div>
        <button class="icon-button" title="查看变更详情" @click="detail = item">
          <Eye />
        </button>
      </article>
    </div>
    <div v-if="total" class="pagination">
      <span>共 {{ total }} 条日志</span>
      <div>
        <button
          class="button secondary small"
          :disabled="page <= 1"
          @click="load(page - 1)"
        >
          <ChevronLeft />上一页</button
        ><span>第 {{ page }} / {{ totalPages }} 页</span
        ><button
          class="button secondary small"
          :disabled="page >= totalPages"
          @click="load(page + 1)"
        >
          下一页<ChevronRight />
        </button>
      </div>
    </div>
    <ModalDialog
      :open="Boolean(detail)"
      title="操作详情"
      size="lg"
      @close="detail = null"
      ><div v-if="detail" class="log-detail">
        <dl>
          <div>
            <dt>操作人</dt>
            <dd>
              {{ detail.operatorName || "系统" }}（{{
                detail.operatorStudentNo || "—"
              }}）
            </dd>
          </div>
          <div>
            <dt>操作类型</dt>
            <dd>{{ actionLabel(detail.actionType) }}</dd>
          </div>
          <div>
            <dt>对象</dt>
            <dd>{{ targetLabel(detail) }}</dd>
          </div>
          <div>
            <dt>操作原因</dt>
            <dd>{{ detail.reason || "—" }}</dd>
          </div>
        </dl>
        <div class="diff-grid">
          <section>
            <h3>变更前</h3>
            <pre>{{ pretty(detail.beforeData) }}</pre>
          </section>
          <section>
            <h3>变更后</h3>
            <pre>{{ pretty(detail.afterData) }}</pre>
          </section>
        </div>
      </div>
      <template #footer
        ><button class="button primary" @click="detail = null">
          关闭
        </button></template
      ></ModalDialog
    >
    <ConfirmDialog
      :open="clearOpen"
      title="清空操作日志"
      message="清空前系统会自动创建安全备份，该操作仅影响日志记录。"
      confirm-label="备份并清空"
      danger
      @cancel="clearOpen = false"
      @confirm="clearLogs"
    />
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import {
  ChevronLeft,
  ChevronRight,
  Download,
  Eye,
  Search,
  Trash2,
} from "@lucide/vue";
import PageHeader from "../../shared/ui/PageHeader.vue";
import LoadingBlock from "../../shared/ui/LoadingBlock.vue";
import EmptyState from "../../shared/ui/EmptyState.vue";
import StatusBadge from "../../shared/ui/StatusBadge.vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
import { del, get, downloadBlob } from "../../shared/api";
import { useAsyncTask } from "../../shared/composables/useAsyncTask";
const { busy, run } = useAsyncTask();
const items = ref<any[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
const detail = ref<any>(null);
const clearOpen = ref(false);
const filters = reactive({ keyword: "", actionType: "", from: "", to: "" });
const totalPages = computed(() =>
  Math.max(1, Math.ceil(total.value / pageSize)),
);
onMounted(() => load());
async function load(target = page.value) {
  const p = params({ ...filters, page: target, pageSize });
  const value = await run(() => get<any>(`/api/logs?${p}`));
  if (value) {
    items.value = value.items;
    total.value = value.total;
    page.value = value.page;
  }
}
async function exportLogs() {
  downloadBlob(
    await get(`/api/logs/export?${params(filters)}`),
    "操作日志.xlsx",
  );
}
async function clearLogs() {
  await run(() => del("/api/logs"), "日志已清空，安全备份已创建");
  clearOpen.value = false;
  await load(1);
}
function params(value: any) {
  const p = new URLSearchParams();
  Object.entries(value).forEach(
    ([k, v]) => v !== "" && v != null && p.set(k, String(v)),
  );
  return p;
}
const date = (v: string) => v?.slice(0, 10);
const time = (v: string) => v?.slice(11, 16);
const actionLabel = (v: string) =>
  (
    ({
      CREATE: "新增",
      UPDATE: "修改",
      DELETE: "删除",
      LOGIN: "登录",
      RESTORE: "恢复",
      EXPORT: "导出",
    }) as any
  )[v] || v;
const actionTone = (v: string) =>
  v?.includes("DELETE")
    ? "danger"
    : v?.includes("CREATE")
      ? "success"
      : v?.includes("UPDATE")
        ? "info"
        : "neutral";
const targetLabel = (i: any) =>
  `${i.targetType || "系统"}${i.targetId ? ` #${i.targetId}` : ""}`;
function pretty(v?: string) {
  if (!v) return "无";
  try {
    return JSON.stringify(JSON.parse(v), null, 2);
  } catch {
    return v;
  }
}
</script>

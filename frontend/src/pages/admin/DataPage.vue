<template>
  <div class="page-stack">
    <PageHeader
      title="数据与备份"
      description="按需导出业务数据，并在同一处完成本机备份与恢复。"
    />
    <div class="segmented page-tabs" role="tablist">
      <button :class="{ active: tab === 'export' }" @click="tab = 'export'">
        <FileSpreadsheet />自定义导出</button
      ><button :class="{ active: tab === 'backups' }" @click="tab = 'backups'">
        <DatabaseBackup />本机备份</button
      ><button
        v-if="canViewRecycle"
        :class="{ active: tab === 'recycle' }"
        @click="tab = 'recycle'"
      >
        <ArchiveRestore />维修回收站
      </button>
    </div>

    <section v-if="tab === 'export'" class="export-workspace">
      <aside class="export-steps panel">
        <p class="eyebrow">EXPORT FLOW</p>
        <button
          v-for="(label, index) in stepLabels"
          :key="label"
          :class="{ active: step === index + 1, done: step > index + 1 }"
          @click="step = Math.min(step, index + 1)"
        >
          <span>{{ index + 1 }}</span
          >{{ label }}
        </button>
      </aside>
      <main class="panel export-stage">
        <template v-if="step === 1"
          ><div class="section-heading">
            <div>
              <p class="eyebrow">STEP 01</p>
              <h2>选择数据源</h2>
            </div>
          </div>
          <div class="source-grid">
            <button
              v-for="source in options.sources"
              :key="source.id"
              :class="{ active: request.source === source.id }"
              @click="selectSource(source)"
            >
              <Database /><span
                ><strong>{{ source.label }}</strong
                ><small>{{ source.fields.length }} 个可选字段</small></span
              ><Check v-if="request.source === source.id" />
            </button></div
        ></template>
        <template v-else-if="step === 2"
          ><div class="section-heading">
            <div>
              <p class="eyebrow">STEP 02</p>
              <h2>设置筛选条件</h2>
            </div>
          </div>
          <EmptyState
            v-if="!currentSource?.filters?.length"
            title="该数据源无需额外筛选" />
          <div v-else class="form-grid two">
            <label
              v-for="filter in currentSource.filters"
              :key="filter.id"
              class="field"
              ><span>{{ filter.label }}</span
              ><select
                v-if="filter.type === 'select'"
                v-model="request.filters[filter.id]"
              >
                <option
                  v-for="option in filter.options"
                  :key="option.value"
                  :value="option.value"
                >
                  {{ option.label }}
                </option></select
              ><input
                v-else
                v-model="request.filters[filter.id]"
                :type="filter.type === 'date' ? 'date' : 'text'"
            /></label></div
        ></template>
        <template v-else-if="step === 3"
          ><div class="section-heading">
            <div>
              <p class="eyebrow">STEP 03</p>
              <h2>选择导出字段</h2>
            </div>
            <button class="button text" @click="toggleAll">
              {{
                request.fields.length === currentSource?.fields.length
                  ? "取消全选"
                  : "全部选择"
              }}
            </button>
          </div>
          <div class="field-choice-grid">
            <label
              v-for="field in currentSource?.fields"
              :key="field.id"
              :class="{ selected: request.fields.includes(field.id) }"
              ><input
                v-model="request.fields"
                type="checkbox"
                :value="field.id" /><span>{{ field.label }}</span
              ><Check
            /></label></div
        ></template>
        <template v-else
          ><div class="section-heading">
            <div>
              <p class="eyebrow">STEP 04</p>
              <h2>预览与导出</h2>
            </div>
            <span v-if="preview">共 {{ preview.totalRows }} 条</span>
          </div>
          <div class="export-summary">
            <div>
              <span>数据源</span><strong>{{ currentSource?.label }}</strong>
            </div>
            <div>
              <span>字段</span><strong>{{ request.fields.length }} 项</strong>
            </div>
            <label class="field"
              ><span>文件名</span
              ><input
                v-model.trim="request.filename"
                placeholder="留空使用默认文件名"
            /></label>
          </div>
          <LoadingBlock v-if="previewing" label="正在生成预览" /><EmptyState
            v-else-if="!preview"
            title="点击下方按钮生成数据预览"
          />
          <div v-else class="table-shell preview-table">
            <table>
              <thead>
                <tr>
                  <th v-for="field in preview.fields" :key="field.id">
                    {{ field.label }}
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, index) in preview.rows" :key="index">
                  <td v-for="field in preview.fields" :key="field.id">
                    {{ row[field.id] ?? "—" }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div></template
        >
        <footer class="wizard-actions">
          <button
            class="button secondary"
            :disabled="step === 1"
            @click="step--"
          >
            <ArrowLeft />上一步</button
          ><button
            v-if="step < 4"
            class="button primary"
            :disabled="!canContinue"
            @click="step++"
          >
            下一步<ArrowRight /></button
          ><template v-else
            ><button
              class="button secondary"
              :disabled="!request.fields.length"
              @click="makePreview"
            >
              <ScanSearch />生成预览</button
            ><button
              class="button primary"
              :disabled="!preview"
              @click="exportCustom"
            >
              <Download />导出 Excel
            </button></template
          >
        </footer>
      </main>
    </section>

    <section v-else-if="tab === 'backups'" class="page-stack">
      <div v-if="summary" class="metric-grid">
        <article v-for="metric in summary.datasets" :key="metric.key">
          <span>{{ metric.label }}</span
          ><strong>{{ metric.total }}</strong
          ><small>{{ metric.detail }}</small>
        </article>
      </div>
      <div class="panel">
        <div class="section-heading">
          <div>
            <p class="eyebrow">LOCAL BACKUPS</p>
            <h2>备份文件</h2>
          </div>
          <div>
            <label v-if="canRestore" class="button secondary file-button"
              ><Upload />恢复备份<input
                type="file"
                accept=".zip"
                @change="pickRestore" /></label
            ><button class="button primary" @click="createBackup">
              <DatabaseBackup />立即备份
            </button>
          </div>
        </div>
        <EmptyState v-if="!backups.length" title="还没有本机备份" />
        <div v-else class="backup-list">
          <article v-for="item in backups" :key="item.filename">
            <FileArchive />
            <div>
              <strong>{{ item.filename }}</strong
              ><span
                >{{ dateTime(item.createdAt) }} · {{ bytes(item.size) }}</span
              >
            </div>
            <button
              class="icon-button"
              title="下载备份"
              @click="downloadBackup(item)"
            >
              <Download /></button
            ><button
              v-if="isAdmin"
              class="icon-button danger-ghost"
              title="删除备份"
              @click="deleteBackupTarget = item"
            >
              <Trash2 />
            </button>
          </article>
        </div>
      </div>
    </section>

    <section v-else class="panel">
      <div class="section-heading">
        <div>
          <p class="eyebrow">RECYCLE BIN</p>
          <h2>维修回收站</h2>
        </div>
        <button class="button secondary small" @click="loadRecycle">
          <RefreshCw />刷新
        </button>
      </div>
      <EmptyState v-if="!recycle.length" title="回收站为空" />
      <div v-else class="table-shell compact-table">
        <table>
          <thead>
            <tr>
              <th>编号</th>
              <th>设备</th>
              <th>联系人</th>
              <th>删除人</th>
              <th class="align-right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in recycle" :key="item.id">
              <td>
                <strong>{{ item.caseNo }}</strong>
              </td>
              <td>
                {{
                  [item.deviceBrand, item.deviceModel, item.deviceType]
                    .filter(Boolean)
                    .join(" ")
                }}
              </td>
              <td>{{ item.ownerName }}</td>
              <td>{{ item.deletedByName || "—" }}</td>
              <td class="align-right row-actions">
                <button class="button text" @click="restoreRepair(item)">
                  <ArchiveRestore />恢复</button
                ><button
                  v-if="isAdmin"
                  class="button text danger-text"
                  @click="purgeTarget = item"
                >
                  <Trash2 />彻底删除
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
    <ConfirmDialog
      :open="Boolean(deleteBackupTarget)"
      title="删除备份"
      :message="`永久删除备份 ${deleteBackupTarget?.filename || ''}。`"
      confirm-label="删除备份"
      danger
      @cancel="deleteBackupTarget = null"
      @confirm="deleteBackup"
    />
    <ConfirmDialog
      :open="Boolean(purgeTarget)"
      title="彻底删除维修事务"
      :message="`请输入编号 ${purgeTarget?.caseNo || ''} 以确认，系统会先自动备份。`"
      confirm-label="彻底删除"
      danger
      require-reason
      @cancel="purgeTarget = null"
      @confirm="purgeRepair"
    />
    <RestoreBackupDialog
      :open="Boolean(restoreTarget)"
      :file="restoreTarget"
      :busy="busy"
      @cancel="restoreTarget = null"
      @confirm="restoreBackup"
    />
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRouter } from "vue-router";
import {
  ArchiveRestore,
  ArrowLeft,
  ArrowRight,
  Check,
  Database,
  DatabaseBackup,
  Download,
  FileArchive,
  FileSpreadsheet,
  RefreshCw,
  ScanSearch,
  Trash2,
  Upload,
} from "@lucide/vue";
import PageHeader from "../../shared/ui/PageHeader.vue";
import EmptyState from "../../shared/ui/EmptyState.vue";
import LoadingBlock from "../../shared/ui/LoadingBlock.vue";
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
import RestoreBackupDialog from "../../features/maintenance/RestoreBackupDialog.vue";
import {
  canRestoreBackup,
  canViewRepairRecycleBin,
} from "../../features/maintenance/dataPermissions";
import { api, del, get, post, downloadBlob } from "../../shared/api";
import { useSession } from "../../app/session";
import { useAsyncTask } from "../../shared/composables/useAsyncTask";
import type {
  BackupItem,
  ExportOptions,
  ExportPreview,
  ExportRequest,
  ExportSource,
  MaintenanceSummary,
  RecycledRepairCase,
} from "../../features/maintenance/dataCenterTypes";
const { user, expireSession } = useSession();
const { busy, run } = useAsyncTask();
const router = useRouter();
const tab = ref("export");
const step = ref(1);
const options = reactive<ExportOptions>({ sources: [] });
const request = reactive<ExportRequest>({
  source: "",
  fields: [],
  filters: {},
  filename: "",
});
const preview = ref<ExportPreview | null>(null);
const previewing = ref(false);
const summary = ref<MaintenanceSummary | null>(null);
const backups = ref<BackupItem[]>([]);
const recycle = ref<RecycledRepairCase[]>([]);
const deleteBackupTarget = ref<BackupItem | null>(null);
const purgeTarget = ref<RecycledRepairCase | null>(null);
const restoreTarget = ref<File | null>(null);
const isAdmin = computed(() => user.value?.role === "ADMIN");
const canRestore = computed(() => canRestoreBackup(user.value?.role));
const canViewRecycle = computed(() =>
  canViewRepairRecycleBin(user.value?.role),
);
const stepLabels = ["选择数据源", "设置筛选", "选择字段", "预览导出"];
const currentSource = computed<ExportSource | undefined>(() =>
  options.sources.find((source) => source.id === request.source),
);
const canContinue = computed(() =>
  step.value === 1
    ? Boolean(request.source)
    : step.value === 3
      ? request.fields.length > 0
      : true,
);
onMounted(async () => {
  const value = await get<ExportOptions>("/api/exports/options");
  options.sources = value.sources || [];
  if (options.sources[0]) selectSource(options.sources[0]);
});
watch(tab, async (value) => {
  if (value === "backups") await loadBackups();
  if (value === "recycle") await loadRecycle();
});
function selectSource(source: ExportSource) {
  request.source = source.id;
  request.fields = source.fields
    .filter((field) => field.defaultSelected)
    .map((field) => field.id);
  request.filters = {};
  source.filters.forEach((filter) => {
    request.filters[filter.id] = filter.defaultValue || "";
  });
  request.filename = "";
  preview.value = null;
}
function toggleAll() {
  if (!currentSource.value) return;
  request.fields =
    request.fields.length === currentSource.value.fields.length
      ? []
      : currentSource.value.fields.map((field) => field.id);
}
async function makePreview() {
  previewing.value = true;
  try {
    preview.value = await post<ExportPreview>("/api/exports/preview", request);
  } finally {
    previewing.value = false;
  }
}
async function exportCustom() {
  downloadBlob(
    await api<Blob>("/api/exports/excel", {
      method: "POST",
      headers: {
        Accept:
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      },
      body: JSON.stringify(request),
    }),
    `${request.filename || currentSource.value?.label || "自定义导出"}.xlsx`,
  );
}
async function loadBackups() {
  [summary.value, backups.value] = await Promise.all([
    get<MaintenanceSummary>("/api/maintenance/summary"),
    get<BackupItem[]>("/api/maintenance/backups"),
  ]);
}
async function createBackup() {
  if (await run(() => post("/api/maintenance/backups"), "备份已创建"))
    await loadBackups();
}
async function downloadBackup(item: BackupItem) {
  downloadBlob(
    await get(`/api/maintenance/backups/${encodeURIComponent(item.filename)}`),
    item.filename,
  );
}
async function deleteBackup() {
  const target = deleteBackupTarget.value;
  if (!target) return;
  await run(
    () =>
      del(
        `/api/maintenance/backups/${encodeURIComponent(target.filename)}`,
      ),
    "备份已删除",
  );
  deleteBackupTarget.value = null;
  await loadBackups();
}
async function pickRestore(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0];
  (e.target as HTMLInputElement).value = "";
  if (!file || !canRestore.value) return;
  restoreTarget.value = file;
}
async function restoreBackup() {
  if (!restoreTarget.value || !canRestore.value) return;
  const body = new FormData();
  body.append("file", restoreTarget.value);
  const restored = await run(
    () => post("/api/maintenance/backups/restore", body),
    "数据已恢复，请重新登录",
  );
  if (restored === undefined) return;
  restoreTarget.value = null;
  expireSession();
  await router.replace({ name: "login", query: { reason: "restored" } });
  window.location.reload();
}
async function loadRecycle() {
  if (!canViewRecycle.value) return;
  recycle.value = await get<RecycledRepairCase[]>("/api/repairs/recycle-bin");
}
async function restoreRepair(item: RecycledRepairCase) {
  await run(() => post(`/api/repairs/${item.id}/restore`), "维修事务已恢复");
  await loadRecycle();
}
async function purgeRepair(value: string) {
  const target = purgeTarget.value;
  if (!target || value.trim() !== target.caseNo) return;
  await run(
    () => post(`/api/repairs/${target.id}/purge`, { caseNo: value }),
    "维修事务已彻底删除",
  );
  purgeTarget.value = null;
  await loadRecycle();
}
const dateTime = (v: string) =>
  new Date(v).toLocaleString("zh-CN", { hour12: false });
function bytes(v: number) {
  if (v < 1024) return `${v} B`;
  if (v < 1048576) return `${(v / 1024).toFixed(1)} KB`;
  return `${(v / 1048576).toFixed(1)} MB`;
}
</script>

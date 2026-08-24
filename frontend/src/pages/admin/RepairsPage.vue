<template>
  <div class="page-stack">
    <PageHeader
      title="维修事务"
      description="维护维修受理过程、协议与交付状态。"
      ><template #actions
        ><button
          v-if="canExport"
          class="button secondary"
          :disabled="isPending('export-repairs') || Boolean(filterError)"
          @click="exportCases"
        >
          <Download />{{ isPending('export-repairs') ? "正在导出" : "导出" }}</button
        ><button v-if="canManage" class="button primary" @click="openEditor()">
          <Plus />新建维修
        </button></template
      ></PageHeader
    >
    <form class="repair-filter-shell" @submit.prevent="load">
      <div class="repair-search-row">
        <Search aria-hidden="true" />
        <label>
          <span class="sr-only">搜索维修事务</span>
          <input
            v-model.trim="filters.keyword"
            type="search"
            name="repair-search"
            placeholder="搜索编号、联系人、设备或故障"
            autocomplete="off"
          />
        </label>
        <button class="button secondary small" type="submit">搜索</button>
        <button
          class="button text small repair-filter-toggle"
          type="button"
          :aria-expanded="filterOpen"
          aria-controls="repair-date-filters"
          @click="filterOpen = !filterOpen"
        >
          <SlidersHorizontal aria-hidden="true" />日期
        </button>
      </div>
      <Transition name="filter-expand">
        <div v-if="filterOpen" id="repair-date-filters" class="repair-date-filters">
          <label><span>开始日期</span><input v-model="filters.from" type="date" /></label>
          <label><span>结束日期</span><input v-model="filters.to" type="date" /></label>
          <button class="button secondary small" type="submit">应用筛选</button>
        </div>
      </Transition>
    </form>
    <div v-if="filterError" class="inline-alert danger" role="alert">
      {{ filterError }}
    </div>

    <RepairStatusTabs
      :active-status="activeStatus"
      :counts="statusCounts"
      @change="setStatus"
    />

    <section
      :id="`repair-panel-${activeStatus}`"
      class="repair-workspace"
      role="tabpanel"
      :aria-labelledby="`repair-tab-${activeStatus}`"
      :aria-busy="repairPage.loading"
      tabindex="0"
    >
      <header class="repair-workspace-heading">
        <div>
          <p class="eyebrow">{{ activeStatus === "REPAIRING" ? "WORK QUEUE" : "ARCHIVE" }}</p>
          <h2 :id="activeStatus === 'REPAIRING' ? 'repair-active-title' : 'repair-history-title'">
            {{ activeStatus === "REPAIRING" ? "进行中工作队列" : activeStatus === "COMPLETED" ? "已完成档案" : "已取消档案" }}
          </h2>
        </div>
        <span>共 {{ repairPage.total }} 项</span>
      </header>

      <ActiveRepairGrid
        v-if="activeStatus === 'REPAIRING'"
        :items="repairPage.items"
        :loading="repairPage.loading"
        :error="repairPage.error"
        :revealed-phones="revealedPhones"
        :can-manage="canManage"
        :can-delete="canDelete"
        @view="detailTarget = $event"
        @preview="preview"
        @edit="openEditor"
        @delete="requestDelete"
        @toggle-phone="togglePhone"
        @retry="retry"
      />
      <RepairHistoryTable
        v-else
        :items="repairPage.items"
        :status="historyStatus"
        :loading="repairPage.loading"
        :error="repairPage.error"
        :revealed-phones="revealedPhones"
        @view="detailTarget = $event"
        @preview="preview"
        @toggle-phone="togglePhone"
        @retry="retry"
      />

      <footer
        v-if="repairPage.total && !repairPage.error"
        class="repair-workspace-pagination"
      >
        <button
          class="button secondary small"
          type="button"
          :disabled="repairPage.page <= 1 || repairPage.loading"
          @click="setPage(repairPage.page - 1)"
        >
          <ArrowLeft aria-hidden="true" />上一页
        </button>
        <span>第 {{ repairPage.page }} / {{ repairTotalPages }} 页 · 共 {{ repairPage.total }} 项</span>
        <button
          class="button secondary small"
          type="button"
          :disabled="!repairPage.hasMore || repairPage.loading"
          @click="setPage(repairPage.page + 1)"
        >
          下一页<ArrowRight aria-hidden="true" />
        </button>
      </footer>
    </section>

    <RepairDetailDrawer
      :open="Boolean(detailTarget)"
      :item="detailTarget"
      :phone-visible="Boolean(detailTarget && phoneVisible(detailTarget.id))"
      :can-manage="canManage"
      :can-delete="canDelete"
      @close="detailTarget = null"
      @preview="preview"
      @edit="editFromDetail"
      @delete="requestDelete"
      @toggle-phone="togglePhone"
    />
    <RepairEditorDialog
      :open="editorOpen"
      :form="form"
      :handler="selectedHandler"
      :candidates="handlerCandidates"
      :pending="isPending('save-repair')"
      @update:handler="selectedHandler = $event"
      @close="closeEditor"
      @save="save"
    />
    <ConfirmDialog
      :open="Boolean(deleteTarget)"
      title="移入维修回收站"
      :message="`将 ${deleteTarget?.caseNo || ''} 移入回收站，管理员可在数据页面恢复。`"
      confirm-label="移入回收站"
      danger
      :pending="isPending('delete-repair')"
      @cancel="deleteTarget = null"
      @confirm="remove"
    />
    <AgreementDialog
      :open="agreementOpen"
      :case-no="agreementTarget?.caseNo"
      :html="agreementHtml"
      :loading="agreementLoading"
      :error="agreementError"
      @close="closeAgreement"
      @retry="loadAgreement"
    />
    <ConfirmDialog
      :open="unsaved.confirmOpen.value"
      title="放弃未保存修改"
      message="当前维修事务还有未保存的内容，放弃后无法恢复。"
      confirm-label="放弃修改"
      danger
      @cancel="unsaved.cancel"
      @confirm="unsaved.discard"
    />
  </div>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { onBeforeRouteLeave, useRoute, useRouter } from "vue-router";
import {
  ArrowLeft,
  ArrowRight,
  Download,
  Plus,
  Search,
  SlidersHorizontal,
} from "@lucide/vue";
import PageHeader from "../../shared/ui/PageHeader.vue";
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
import AgreementDialog from "../../shared/ui/AgreementDialog.vue";
import ActiveRepairGrid from "../../features/repairs/ActiveRepairGrid.vue";
import RepairEditorDialog from "../../features/repairs/RepairEditorDialog.vue";
import RepairDetailDrawer from "../../features/repairs/RepairDetailDrawer.vue";
import RepairHistoryTable from "../../features/repairs/RepairHistoryTable.vue";
import RepairStatusTabs from "../../features/repairs/RepairStatusTabs.vue";
import { api, del, get, post, put, downloadBlob } from "../../shared/api";
import { useSession } from "../../app/session";
import { useAsyncTask } from "../../shared/composables/useAsyncTask";
import { usePendingActions } from "../../shared/composables/usePendingActions";
import { useUnsavedChanges } from "../../shared/composables/useUnsavedChanges";
import { dateRangeError } from "../../shared/validation/dateRange";
import {
  canDeleteRepairs,
  canExportRepairs,
  canManageRepairs,
} from "../../features/repairs/repairPermissions";
import {
  repairAgreementFormType,
} from "../../features/repairs/repairDisplay";
import { fetchRepairPage } from "../../features/repairs/repairApi";
import { useRepairWorkspace } from "../../features/repairs/useRepairWorkspace";
import type { AccountCandidate } from "../../features/accounts/accountCandidates";
import type {
  RepairCase,
  RepairCaseForm,
  RepairStatus,
} from "../../features/repairs/repairTypes";
const { user } = useSession();
const task = useAsyncTask();
const actions = usePendingActions();
const { isPending } = actions;
const route = useRoute();
const router = useRouter();
const now = new Date();
const workspace = useRepairWorkspace({
  loadPage: fetchRepairPage,
  defaults: {
    from: `${now.getFullYear()}-01-01`,
    to: localDate(now),
  },
  initialQuery: route.query,
  onQueryChange: updateRouteQuery,
});
const {
  activeStatus,
  filters,
  counts: statusCounts,
  page: repairPage,
  applyFilters: applyWorkspaceFilters,
  setStatus,
  setPage,
  retry,
  refreshAfterMutation,
} = workspace;
const editorOpen = ref(false);
const filterOpen = ref(false);
const deleteTarget = ref<RepairCase | null>(null);
const detailTarget = ref<RepairCase | null>(null);
const agreementOpen = ref(false);
const agreementTarget = ref<RepairCase | null>(null);
const agreementHtml = ref("");
const agreementLoading = ref(false);
const agreementError = ref("");
let agreementRequestVersion = 0;
const handlerCandidates = ref<AccountCandidate[]>([]);
const selectedHandler = ref<AccountCandidate | null>(null);
const revealedPhones = ref(new Set<number>());
const form = reactive<RepairCaseForm>({
  id: null,
  agreementType: "REPAIR",
  ownerName: "",
  ownerPhone: "",
  deviceType: "",
  deviceBrand: "",
  deviceModel: "",
  accessories: "",
  faultDescription: "",
  serviceDescription: "",
  dataBackupConfirmed: false,
  riskAcknowledged: false,
  privacyAcknowledged: false,
  status: "REPAIRING",
  receivedAt: "",
  completedAt: "",
  handlerName: "",
  remark: "",
});
const canManage = computed(() => canManageRepairs(user.value?.role));
const canDelete = computed(() => canDeleteRepairs(user.value?.role));
const canExport = computed(() => canExportRepairs(user.value?.role));
const historyStatus = computed(
  () => activeStatus.value as Exclude<RepairStatus, "REPAIRING">,
);
const repairTotalPages = computed(() =>
  Math.max(1, Math.ceil(repairPage.total / repairPage.pageSize)),
);
const filterError = computed(() => dateRangeError(filters.from, filters.to));
const editorBaseline = ref("");
const unsaved = useUnsavedChanges(
  () => editorOpen.value && editorSnapshot() !== editorBaseline.value,
);
onMounted(async () => {
  await Promise.all([workspace.initialize(), loadHandlerCandidates()]);
});
onBeforeUnmount(workspace.dispose);
watch(
  () => route.query,
  async (query) => {
    if (sameQuery(query, workspace.currentQuery())) return;
    await workspace.restoreQuery(query);
  },
);
async function updateRouteQuery(
  query: Record<string, string>,
  mode: "push" | "replace",
) {
  if (sameQuery(route.query, query)) return;
  await router[mode]({ query });
}
async function load() {
  if (filterError.value) return;
  await applyWorkspaceFilters();
}
function openEditor(item?: RepairCase) {
  Object.assign(
    form,
    item
      ? {
          ...item,
          agreementType: repairAgreementFormType(item.agreementType),
          receivedAt: toInput(item.receivedAt),
          completedAt: toInput(item.completedAt),
        }
      : {
          id: null,
          agreementType: "REPAIR",
          ownerName: "",
          ownerPhone: "",
          deviceType: "",
          deviceBrand: "",
          deviceModel: "",
          accessories: "",
          faultDescription: "",
          serviceDescription: "",
          dataBackupConfirmed: false,
          riskAcknowledged: false,
          privacyAcknowledged: false,
          status: "REPAIRING",
          receivedAt: toInput(new Date().toISOString()),
          completedAt: "",
          handlerName: user.value?.name || "",
          remark: "",
        },
  );
  selectedHandler.value = item
    ? handlerCandidates.value.find(
        (candidate) => candidate.id === item.handlerUserId,
      ) ||
      (item.handlerUserId
        ? {
            id: item.handlerUserId,
            studentNo: "",
            name: item.handlerName || "原负责人",
            inactive: true,
          }
        : null)
    : handlerCandidates.value.find(
        (candidate) => candidate.id === user.value?.id,
      ) || null;
  editorBaseline.value = editorSnapshot();
  editorOpen.value = true;
}
function closeEditor() {
  unsaved.request(() => {
    editorOpen.value = false;
  });
}
function editFromDetail(item: RepairCase) {
  detailTarget.value = null;
  openEditor(item);
}
function requestDelete(item: RepairCase) {
  detailTarget.value = null;
  deleteTarget.value = item;
}
async function save() {
  const previousStatus = form.id ? findLoadedCase(form.id)?.status : null;
  const payload = {
    ...form,
    handlerUserId: selectedHandler.value?.id || null,
    handlerName: selectedHandler.value?.name || null,
    ownerOrg: null,
    deviceSerial: null,
    completedAt: form.completedAt || null,
  };
  const value = await actions.run("save-repair", () =>
    form.id
      ? task.run<RepairCase>(
          () => put(`/api/repairs/${form.id}`, payload),
          "维修事务已更新",
        )
      : task.run<RepairCase>(
          () => post("/api/repairs", payload),
          "维修事务已创建",
        ),
  );
  if (value) {
    editorBaseline.value = editorSnapshot();
    editorOpen.value = false;
    await refreshAfterMutation(previousStatus, value.status);
  }
}
async function loadHandlerCandidates() {
  const value = await task.run(() =>
    get<AccountCandidate[]>("/api/repairs/handler-candidates"),
  );
  if (value) handlerCandidates.value = value;
}
async function remove() {
  const target = deleteTarget.value;
  if (!target) return;
  const removed = await actions.run("delete-repair", () =>
    task.run(
      async () => {
        await del(`/api/repairs/${target.id}`);
        return true;
      },
      "已移入维修回收站",
    ),
  );
  if (removed) {
    deleteTarget.value = null;
    await refreshAfterMutation(target.status, null);
  }
}
async function preview(item: RepairCase) {
  agreementTarget.value = item;
  agreementOpen.value = true;
  await loadAgreement();
}
async function loadAgreement() {
  const target = agreementTarget.value;
  if (!target) return;
  const version = ++agreementRequestVersion;
  agreementLoading.value = true;
  agreementError.value = "";
  try {
    const blob = await api<Blob>(
      `/api/repairs/${target.id}/agreement`,
    );
    const html = await blob.text();
    if (
      version === agreementRequestVersion &&
      agreementOpen.value &&
      agreementTarget.value?.id === target.id
    ) {
      agreementHtml.value = html;
    }
  } catch (cause) {
    if (version === agreementRequestVersion && agreementOpen.value) {
      agreementError.value =
        cause instanceof Error ? cause.message : "协议暂时无法预览";
    }
  } finally {
    if (version === agreementRequestVersion) agreementLoading.value = false;
  }
}
function closeAgreement() {
  agreementRequestVersion += 1;
  agreementOpen.value = false;
  agreementTarget.value = null;
  agreementHtml.value = "";
  agreementError.value = "";
}
async function exportCases() {
  if (filterError.value) return;
  const p = new URLSearchParams();
  Object.entries(filters).forEach(([k, v]) => v && p.set(k, v));
  p.set("status", "ALL");
  await actions.run("export-repairs", async () => {
    const blob = await task.run(() => get<Blob>(`/api/repairs/export?${p}`));
    if (blob) downloadBlob(blob, `维修事务_${filters.from}_${filters.to}.xlsx`);
  });
}
onBeforeRouteLeave(
  () =>
    new Promise<boolean>((resolve) => {
      unsaved.request(() => resolve(true), () => resolve(false));
    }),
);
function findLoadedCase(id: number) {
  return repairPage.items.find((item) => item.id === id);
}
const toInput = (v?: string) => v?.slice(0, 16) || "";
const phoneVisible = (id: number) => revealedPhones.value.has(id);
function togglePhone(id: number) {
  const next = new Set(revealedPhones.value);
  if (next.has(id)) next.delete(id);
  else next.add(id);
  revealedPhones.value = next;
}
function localDate(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}
function editorSnapshot() {
  return JSON.stringify({ form, handlerId: selectedHandler.value?.id || null });
}
function sameQuery(
  current: Record<string, unknown>,
  next: Record<string, string>,
) {
  const currentEntries = Object.entries(current)
    .filter(([, value]) => typeof value === "string" && value)
    .sort(([left], [right]) => left.localeCompare(right));
  const nextEntries = Object.entries(next).sort(([left], [right]) =>
    left.localeCompare(right),
  );
  return JSON.stringify(currentEntries) === JSON.stringify(nextEntries);
}
</script>

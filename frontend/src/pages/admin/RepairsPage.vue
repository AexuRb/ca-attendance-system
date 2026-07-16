<template>
  <div class="page-stack">
    <PageHeader
      title="维修事务"
      description="维护维修受理过程、协议与交付状态。"
      ><template #actions
        ><button class="button secondary" @click="exportCases">
          <Download />导出</button
        ><button v-if="canManage" class="button primary" @click="openEditor()">
          <Plus />新建维修
        </button></template
      ></PageHeader
    >
    <form class="filter-bar" @submit.prevent="load">
      <label class="filter-grow"
        ><span>搜索事务</span
        ><input
          v-model.trim="filters.keyword"
          placeholder="编号、联系人、设备或故障" /></label
      ><label
        ><span>状态</span
        ><select v-model="filters.status">
          <option value="">全部状态</option>
          <option value="REPAIRING">进行中</option>
          <option value="COMPLETED">已完成</option>
          <option value="CANCELED">已取消</option>
        </select></label
      ><label
        ><span>开始日期</span
        ><input v-model="filters.from" type="date" /></label
      ><label
        ><span>结束日期</span><input v-model="filters.to" type="date" /></label
      ><button class="button secondary" type="submit"><Search />查询</button>
    </form>
    <div class="repair-board">
      <section
        v-for="column in columns"
        :key="column.status"
        class="repair-column"
      >
        <div class="repair-column-head">
          <span :data-tone="column.tone"></span
          ><strong>{{ column.label }}</strong
          ><b>{{ casesByStatus(column.status).length }}</b>
        </div>
        <div class="repair-card-list">
          <article
            v-for="item in casesByStatus(column.status)"
            :key="item.id"
            class="repair-card"
          >
            <div>
              <span class="case-no">{{ item.caseNo }}</span
              ><StatusBadge
                :label="agreementLabel(item.agreementType)"
                tone="info"
              />
            </div>
            <h3>{{ deviceName(item) }}</h3>
            <p>{{ item.faultDescription }}</p>
            <dl>
              <div>
                <dt>联系人</dt>
                <dd>{{ item.ownerName }} · {{ item.ownerPhone }}</dd>
              </div>
              <div>
                <dt>负责人</dt>
                <dd>{{ item.handlerName || "待分配" }}</dd>
              </div>
              <div>
                <dt>受理时间</dt>
                <dd>{{ dateTime(item.receivedAt) }}</dd>
              </div>
            </dl>
            <footer>
              <button class="button text" @click="preview(item)">
                <FileText />协议
              </button>
              <div>
                <button
                  v-if="canManage"
                  class="icon-button"
                  title="编辑"
                  @click="openEditor(item)"
                >
                  <Pencil /></button
                ><button
                  v-if="canDelete"
                  class="icon-button danger-ghost"
                  title="移入回收站"
                  @click="deleteTarget = item"
                >
                  <Trash2 />
                </button>
              </div>
            </footer>
          </article>
          <EmptyState
            v-if="!casesByStatus(column.status).length"
            title="暂无事务"
          />
        </div>
      </section>
    </div>
    <ModalDialog
      :open="editorOpen"
      :title="form.id ? '编辑维修事务' : '新建维修事务'"
      size="xl"
      @close="editorOpen = false"
      ><div class="form-sections">
        <section>
          <h3>协议与联系人</h3>
          <div class="form-grid two">
            <label class="field"
              ><span>协议类型</span
              ><select v-model="form.agreementType">
                <option value="REPAIR">维修协议</option>
                <option value="DISCLAIMER">免责协议</option>
              </select></label
            ><label class="field"
              ><span>状态</span
              ><select v-model="form.status">
                <option value="REPAIRING">进行中</option>
                <option value="COMPLETED">已完成</option>
                <option value="CANCELED">已取消</option>
              </select></label
            ><label class="field"
              ><span>联系人</span><input v-model.trim="form.ownerName" /></label
            ><label class="field"
              ><span>联系电话</span><input v-model.trim="form.ownerPhone"
            /></label>
          </div>
        </section>
        <section>
          <h3>设备与故障</h3>
          <div class="form-grid two">
            <label class="field"
              ><span>设备类型</span
              ><input
                v-model.trim="form.deviceType"
                placeholder="笔记本电脑、台式机等" /></label
            ><label class="field"
              ><span>品牌型号</span
              ><input
                v-model.trim="form.deviceBrand"
                placeholder="品牌" /></label
            ><label class="field"
              ><span>具体型号</span
              ><input v-model.trim="form.deviceModel" /></label
            ><label class="field"
              ><span>附件</span
              ><input
                v-model.trim="form.accessories"
                placeholder="电源、鼠标等" /></label
            ><label class="field span-2"
              ><span>故障描述</span
              ><textarea v-model.trim="form.faultDescription" rows="3" /></label
            ><label class="field span-2"
              ><span>维修说明</span
              ><textarea v-model.trim="form.serviceDescription" rows="3" />
            </label>
          </div>
        </section>
        <section>
          <h3>受理信息</h3>
          <div class="form-grid two">
            <label class="field"
              ><span>受理时间</span
              ><input v-model="form.receivedAt" type="datetime-local" /></label
            ><label class="field"
              ><span>完成时间</span
              ><input v-model="form.completedAt" type="datetime-local" /></label
            ><label class="field"
              ><span>负责人姓名</span
              ><input v-model.trim="form.handlerName" /></label
            ><label class="field"
              ><span>备注</span><input v-model.trim="form.remark"
            /></label>
          </div>
          <div class="check-row">
            <label
              ><input
                v-model="form.dataBackupConfirmed"
                type="checkbox"
              />已确认数据备份</label
            ><label
              ><input
                v-model="form.riskAcknowledged"
                type="checkbox"
              />已确认维修风险</label
            ><label
              ><input
                v-model="form.privacyAcknowledged"
                type="checkbox"
              />已确认隐私事项</label
            >
          </div>
        </section>
      </div>
      <template #footer
        ><button class="button secondary" @click="editorOpen = false">
          取消</button
        ><button class="button primary" :disabled="!validForm" @click="save">
          保存事务
        </button></template
      ></ModalDialog
    >
    <ConfirmDialog
      :open="Boolean(deleteTarget)"
      title="移入维修回收站"
      :message="`将 ${deleteTarget?.caseNo || ''} 移入回收站，管理员可在数据页面恢复。`"
      confirm-label="移入回收站"
      danger
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
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { Download, FileText, Pencil, Plus, Search, Trash2 } from "@lucide/vue";
import PageHeader from "../../shared/ui/PageHeader.vue";
import EmptyState from "../../shared/ui/EmptyState.vue";
import StatusBadge from "../../shared/ui/StatusBadge.vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
import AgreementDialog from "../../shared/ui/AgreementDialog.vue";
import { api, del, get, post, put, downloadBlob } from "../../shared/api";
import { useSession } from "../../app/session";
import { useAsyncTask } from "../../shared/composables/useAsyncTask";
import { useTerms } from "../../shared/composables/useTerms";
const { user } = useSession();
const { run } = useAsyncTask();
const { selectedTerm } = useTerms();
const cases = ref<any[]>([]);
const editorOpen = ref(false);
const deleteTarget = ref<any>(null);
const agreementOpen = ref(false);
const agreementTarget = ref<any>(null);
const agreementHtml = ref("");
const agreementLoading = ref(false);
const agreementError = ref("");
const now = new Date();
const filters = reactive({
  keyword: "",
  status: "",
  from: selectedTerm.value?.startDate || `${now.getFullYear()}-01-01`,
  to: localDate(now),
});
const form = reactive<any>({});
const columns = [
  { status: "REPAIRING", label: "进行中", tone: "blue" },
  { status: "COMPLETED", label: "已完成", tone: "green" },
  { status: "CANCELED", label: "已取消", tone: "gray" },
];
const canManage = computed(() =>
  ["MINISTER", "PRESIDENT", "ADMIN"].includes(user.value?.role || ""),
);
const canDelete = computed(() =>
  ["PRESIDENT", "ADMIN"].includes(user.value?.role || ""),
);
const validForm = computed(
  () =>
    form.ownerName &&
    form.ownerPhone &&
    form.deviceType &&
    form.faultDescription &&
    form.receivedAt,
);
onMounted(load);
async function load() {
  const p = new URLSearchParams();
  Object.entries(filters).forEach(([k, v]) => v && p.set(k, v));
  const value = await run(() => get<any[]>(`/api/repairs?${p}`));
  if (value) cases.value = value;
}
const casesByStatus = (status: string) =>
  cases.value.filter((i) => i.status === status);
function openEditor(item?: any) {
  Object.assign(
    form,
    item
      ? {
          ...item,
          agreementType:
            item.agreementType === "PUBLIC_DEVICE" ? "DISCLAIMER" : "REPAIR",
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
  editorOpen.value = true;
}
async function save() {
  const payload = {
    ...form,
    ownerOrg: null,
    deviceSerial: null,
    completedAt: form.completedAt || null,
  };
  const value = form.id
    ? await run(() => put(`/api/repairs/${form.id}`, payload), "维修事务已更新")
    : await run(() => post("/api/repairs", payload), "维修事务已创建");
  if (value) {
    editorOpen.value = false;
    await load();
  }
}
async function remove() {
  await run(
    () => del(`/api/repairs/${deleteTarget.value.id}`),
    "已移入维修回收站",
  );
  deleteTarget.value = null;
  await load();
}
async function preview(item: any) {
  agreementTarget.value = item;
  agreementOpen.value = true;
  await loadAgreement();
}
async function loadAgreement() {
  if (!agreementTarget.value) return;
  agreementLoading.value = true;
  agreementError.value = "";
  try {
    const blob = await api<Blob>(
      `/api/repairs/${agreementTarget.value.id}/agreement`,
    );
    agreementHtml.value = await blob.text();
  } catch (e: any) {
    agreementError.value = e.message;
  } finally {
    agreementLoading.value = false;
  }
}
function closeAgreement() {
  agreementOpen.value = false;
  agreementHtml.value = "";
  agreementError.value = "";
}
async function exportCases() {
  const p = new URLSearchParams();
  Object.entries(filters).forEach(([k, v]) => v && p.set(k, v));
  downloadBlob(
    await get(`/api/repairs/export?${p}`),
    `维修事务_${filters.from}_${filters.to}.xlsx`,
  );
}
const deviceName = (i: any) =>
  [i.deviceBrand, i.deviceModel, i.deviceType].filter(Boolean).join(" ") ||
  "未命名设备";
const agreementLabel = (v: string) =>
  v === "DISCLAIMER" ? "免责协议" : "维修协议";
const dateTime = (v?: string) => v?.replace("T", " ").slice(0, 16) || "—";
const toInput = (v?: string) => v?.slice(0, 16) || "";
function localDate(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}
</script>

<template>
  <div class="page-stack">
    <PageHeader
      title="维修事务"
      description="维护维修受理过程、协议与交付状态。"
      ><template #actions
        ><button v-if="canExport" class="button secondary" @click="exportCases">
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
          <option value="ALL">全部状态</option>
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
            :class="{ 'is-long-running': isLongRunningRepair(item) }"
          >
            <div>
              <span class="case-no">{{ item.caseNo }}</span
              ><StatusBadge
                :label="repairAgreementLabel(item.agreementType)"
                tone="info"
              />
            </div>
            <div class="repair-card-timing">
              <span :data-attention="isLongRunningRepair(item)">
                {{ repairAgeLabel(item) }}
              </span>
              <time :datetime="item.updatedAt">
                更新于 {{ dateTime(item.updatedAt) }}
              </time>
            </div>
            <h3>{{ deviceName(item) }}</h3>
            <p>{{ item.faultDescription }}</p>
            <dl>
              <div>
                <dt>联系人</dt>
                <dd class="repair-contact">
                  <span>
                    {{ item.ownerName }} ·
                    {{
                      phoneVisible(item.id)
                        ? item.ownerPhone
                        : maskRepairPhone(item.ownerPhone)
                    }}
                  </span>
                  <button
                    class="repair-phone-toggle"
                    type="button"
                    :aria-label="
                      phoneVisible(item.id) ? '隐藏完整电话' : '显示完整电话'
                    "
                    :title="
                      phoneVisible(item.id) ? '隐藏完整电话' : '显示完整电话'
                    "
                    :aria-pressed="phoneVisible(item.id)"
                    @click="togglePhone(item.id)"
                  >
                    <EyeOff v-if="phoneVisible(item.id)" aria-hidden="true" />
                    <Eye v-else aria-hidden="true" />
                  </button>
                </dd>
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
                  aria-label="编辑维修事务"
                  type="button"
                  @click="openEditor(item)"
                >
                  <Pencil aria-hidden="true" /></button
                ><button
                  v-if="canDelete"
                  class="icon-button danger-ghost"
                  title="移入回收站"
                  aria-label="将维修事务移入回收站"
                  type="button"
                  @click="deleteTarget = item"
                >
                  <Trash2 aria-hidden="true" />
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
      size="lg"
      @close="editorOpen = false"
    >
      <nav class="repair-editor-steps" aria-label="维修事务填写步骤">
        <button
          type="button"
          :class="{ active: repairStep === 1 }"
          :aria-current="repairStep === 1 ? 'step' : undefined"
          @click="repairStep = 1"
        >
          <span>1</span>
          <strong>设备与联系人</strong>
        </button>
        <button
          type="button"
          :class="{ active: repairStep === 2 }"
          :aria-current="repairStep === 2 ? 'step' : undefined"
          @click="repairStep = 2"
        >
          <span>2</span>
          <strong>受理与确认</strong>
        </button>
      </nav>
      <div class="form-sections repair-editor-body">
        <template v-if="repairStep === 1">
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
                ><textarea
                  v-model.trim="form.faultDescription"
                  rows="2" /></label
              ><label class="field span-2"
                ><span>维修说明</span
                ><textarea v-model.trim="form.serviceDescription" rows="2" />
              </label>
            </div>
          </section>
        </template>
        <section v-else>
          <h3>受理信息</h3>
          <div class="form-grid two">
            <label class="field"
              ><span>受理时间</span
              ><input v-model="form.receivedAt" type="datetime-local" /></label
            ><label class="field"
              ><span>完成时间</span
              ><input v-model="form.completedAt" type="datetime-local" /></label
            ><div class="field span-2">
              <span>负责人账号</span>
              <AccountPicker
                v-model="selectedHandler"
                :candidates="handlerCandidates"
                aria-label="选择维修负责人"
                placeholder="搜索姓名或学号"
              />
            </div>
            <label class="field"
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
      <template #footer>
        <button class="button secondary" @click="editorOpen = false">
          取消
        </button>
        <button
          v-if="repairStep === 2"
          class="button secondary"
          type="button"
          @click="repairStep = 1"
        >
          <ArrowLeft />上一步
        </button>
        <button
          v-if="repairStep === 1"
          class="button primary"
          type="button"
          @click="repairStep = 2"
        >
          下一步<ArrowRight />
        </button>
        <button
          v-else
          class="button primary"
          :disabled="!validForm"
          @click="save"
        >
          保存事务
        </button>
      </template>
    </ModalDialog>
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
import {
  ArrowLeft,
  ArrowRight,
  Download,
  Eye,
  EyeOff,
  FileText,
  Pencil,
  Plus,
  Search,
  Trash2,
} from "@lucide/vue";
import PageHeader from "../../shared/ui/PageHeader.vue";
import EmptyState from "../../shared/ui/EmptyState.vue";
import StatusBadge from "../../shared/ui/StatusBadge.vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
import AgreementDialog from "../../shared/ui/AgreementDialog.vue";
import AccountPicker from "../../features/accounts/AccountPicker.vue";
import { api, del, get, post, put, downloadBlob } from "../../shared/api";
import { useSession } from "../../app/session";
import { useAsyncTask } from "../../shared/composables/useAsyncTask";
import {
  canDeleteRepairs,
  canExportRepairs,
  canManageRepairs,
} from "../../features/repairs/repairPermissions";
import {
  isLongRunningRepair,
  maskRepairPhone,
  repairAgreementFormType,
  repairAgreementLabel,
  repairAgeLabel,
} from "../../features/repairs/repairDisplay";
import type { AccountCandidate } from "../../features/accounts/accountCandidates";
import type {
  RepairCase,
  RepairCaseForm,
  RepairStatus,
} from "../../features/repairs/repairTypes";
const { user } = useSession();
const { run } = useAsyncTask();
const cases = ref<RepairCase[]>([]);
const editorOpen = ref(false);
const repairStep = ref<1 | 2>(1);
const deleteTarget = ref<RepairCase | null>(null);
const agreementOpen = ref(false);
const agreementTarget = ref<RepairCase | null>(null);
const agreementHtml = ref("");
const agreementLoading = ref(false);
const agreementError = ref("");
const handlerCandidates = ref<AccountCandidate[]>([]);
const selectedHandler = ref<AccountCandidate | null>(null);
const revealedPhones = ref(new Set<number>());
const now = new Date();
const filters = reactive({
  keyword: "",
  status: "ALL",
  from: `${now.getFullYear()}-01-01`,
  to: localDate(now),
});
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
const columns: Array<{
  status: RepairStatus;
  label: string;
  tone: string;
}> = [
  { status: "REPAIRING", label: "进行中", tone: "blue" },
  { status: "COMPLETED", label: "已完成", tone: "green" },
  { status: "CANCELED", label: "已取消", tone: "gray" },
];
const canManage = computed(() => canManageRepairs(user.value?.role));
const canDelete = computed(() => canDeleteRepairs(user.value?.role));
const canExport = computed(() => canExportRepairs(user.value?.role));
const validForm = computed(
  () =>
    form.ownerName &&
    form.ownerPhone &&
    form.deviceType &&
    form.faultDescription &&
    form.receivedAt &&
    selectedHandler.value,
);
onMounted(async () => {
  await Promise.all([load(), loadHandlerCandidates()]);
});
async function load() {
  const p = new URLSearchParams();
  Object.entries(filters).forEach(([k, v]) => v && p.set(k, v));
  const value = await run(() => get<RepairCase[]>(`/api/repairs?${p}`));
  if (value) cases.value = value;
}
const casesByStatus = (status: RepairStatus) =>
  cases.value.filter((i) => i.status === status);
function openEditor(item?: RepairCase) {
  repairStep.value = 1;
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
  editorOpen.value = true;
}
async function save() {
  const payload = {
    ...form,
    handlerUserId: selectedHandler.value?.id || null,
    handlerName: selectedHandler.value?.name || null,
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
async function loadHandlerCandidates() {
  const value = await run(() =>
    get<AccountCandidate[]>("/api/repairs/handler-candidates"),
  );
  if (value) handlerCandidates.value = value;
}
async function remove() {
  const target = deleteTarget.value;
  if (!target) return;
  await run(
    () => del(`/api/repairs/${target.id}`),
    "已移入维修回收站",
  );
  deleteTarget.value = null;
  await load();
}
async function preview(item: RepairCase) {
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
  } catch (cause) {
    agreementError.value =
      cause instanceof Error ? cause.message : "协议暂时无法预览";
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
const deviceName = (i: RepairCase) =>
  [i.deviceBrand, i.deviceModel, i.deviceType].filter(Boolean).join(" ") ||
  "未命名设备";
const dateTime = (v?: string) => v?.replace("T", " ").slice(0, 16) || "—";
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
</script>

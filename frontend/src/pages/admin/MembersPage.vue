<template>
  <div class="page-stack members-page">
    <PageHeader
      title="成员名册"
      description="管理在册账号、角色与启用状态。"
    >
      <template #actions>
        <button class="button secondary" @click="openImport">
          <Upload />批量导入
        </button>
        <button class="button primary" @click="openCreate">
          <UserPlus />新增成员
        </button>
      </template>
    </PageHeader>

    <MemberFilters
      v-model:keyword="filters.keyword"
      v-model:role="filters.role"
      v-model:status="filters.status"
      v-model:grade="filters.grade"
      :grades="grades"
      @submit="load(1)"
    />

    <Transition name="soft-rise">
      <div v-if="selected.size" class="selection-toolbar">
        <div>
          <CheckSquare />
          <strong>已选 {{ selected.size }} 人</strong>
          <button class="text-button" type="button" @click="selected.clear()">
            清除选择
          </button>
        </div>
        <div>
          <button
            class="button secondary small"
            type="button"
            @click="openBulk('ACTIVE')"
          >
            <Power />批量启用
          </button>
          <button
            class="button secondary small"
            type="button"
            @click="openBulk('DISABLED')"
          >
            <PowerOff />批量停用
          </button>
        </div>
      </div>
    </Transition>

    <div v-if="bulkResult" class="result-note member-bulk-result">
      已更新 {{ bulkResult.updated }}，状态未变 {{ bulkResult.unchanged }}，跳过
      {{ bulkResult.skipped }}
      <ul v-if="bulkResult.errors?.length" class="result-issues">
        <li v-for="issue in bulkResult.errors" :key="issue">{{ issue }}</li>
      </ul>
    </div>

    <LoadingBlock v-if="busy && !members.length" />
    <EmptyState v-else-if="!members.length" title="没有符合条件的成员" />
    <div v-else class="table-shell member-table">
      <table>
        <thead>
          <tr>
            <th class="selection-column">
              <input
                type="checkbox"
                aria-label="选择本页可管理成员"
                :checked="pageAllSelected"
                :disabled="!selectableIds.length"
                @change="togglePage"
              />
            </th>
            <th>成员</th>
            <th>联系方式</th>
            <th>学院 / 年级</th>
            <th>角色</th>
            <th>状态</th>
            <th class="align-right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="item in members"
            :key="item.id"
            :class="{ 'is-selected': selected.has(item.id) }"
          >
            <td class="selection-column">
              <input
                type="checkbox"
                :aria-label="`选择 ${item.name}`"
                :checked="selected.has(item.id)"
                :disabled="!canEdit(item) || item.id === user?.id"
                @change="toggleMember(item.id)"
              />
            </td>
            <td>
              <div class="member-identity">
                <span class="avatar small">{{ item.name.slice(0, 1) }}</span>
                <span>
                  <strong>{{ item.name }}</strong>
                  <small>{{ item.studentNo }}</small>
                </span>
              </div>
            </td>
            <td>
              {{ item.phone || "—" }}
              <small v-if="item.qq">QQ {{ item.qq }}</small>
            </td>
            <td>
              {{ item.major || "—" }}
              <small>{{ item.grade || "未填写年级" }}</small>
            </td>
            <td>{{ roleLabel(item.role) }}</td>
            <td>
              <StatusBadge
                :label="item.status === 'ACTIVE' ? '启用' : '停用'"
                :tone="item.status === 'ACTIVE' ? 'success' : 'neutral'"
              />
            </td>
            <td class="align-right row-actions">
              <MemberRowActions
                :member="item"
                :editable="canEdit(item)"
                :self="item.id === user?.id"
                :deletable="user?.role === 'ADMIN'"
                @edit="openEdit(item)"
                @toggle-status="toggleStatus(item)"
                @reset-password="resetTarget = item"
                @delete="deleteTarget = item"
              />
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="total" class="pagination">
      <span>共 {{ total }} 人</span>
      <div>
        <button
          class="button secondary small"
          :disabled="page <= 1"
          @click="load(page - 1)"
        >
          <ChevronLeft />上一页
        </button>
        <span>第 {{ page }} / {{ totalPages }} 页</span>
        <button
          class="button secondary small"
          :disabled="page >= totalPages"
          @click="load(page + 1)"
        >
          下一页<ChevronRight />
        </button>
      </div>
    </div>

    <MemberEditorDialog
      :open="editorOpen"
      :member="editorTarget"
      :operator-role="user?.role"
      :grade-choices="gradeChoices"
      :busy="busy"
      :lock-account-controls="lockEditorAccountControls"
      @close="closeEditor"
      @save="saveMember"
    />

    <ModalDialog
      :open="importOpen"
      title="批量导入成员"
      eyebrow="EXCEL IMPORT"
      size="sm"
      @close="importOpen = false"
    >
      <div class="upload-zone">
        <Upload />
        <strong>选择成员 Excel</strong>
        <p>支持 .xlsx 与 .xls 文件</p>
        <input type="file" accept=".xlsx,.xls" @change="pickFile" />
      </div>
      <div v-if="importResult" class="result-note">
        新增 {{ importResult.created }}，更新 {{ importResult.updated }}，跳过
        {{ importResult.skipped }}
        <ul v-if="importResult.errors?.length" class="result-issues">
          <li v-for="issue in importResult.errors" :key="issue">{{ issue }}</li>
        </ul>
      </div>
      <div v-if="importError" class="form-error member-import-error" role="alert">
        {{ importError }}
      </div>
      <template #footer>
        <a
          class="button secondary"
          href="/templates/member-import-template.xlsx"
          download="成员批量导入模板.xlsx"
        >
          <Download />下载模板
        </a>
        <button
          class="button primary"
          :disabled="!importFile || busy"
          @click="importMembers"
        >
          开始导入
        </button>
      </template>
    </ModalDialog>

    <BulkMemberStatusDialog
      :open="bulkOpen"
      :count="selected.size"
      :status="bulkTargetStatus"
      :busy="busy"
      @close="bulkOpen = false"
      @confirm="applyBulkStatus"
    />

    <MemberPasswordResetDialog
      :open="Boolean(resetTarget)"
      :member="resetTarget"
      :busy="busy"
      @close="resetTarget = null"
      @confirm="resetPassword"
    />
    <ConfirmDialog
      :open="Boolean(deleteTarget)"
      title="删除成员"
      :message="`仅可永久删除从未参与业务的空白账号。将删除 ${deleteTarget?.name || ''} 的账号，系统会先自动备份；如已有历史记录，请改为停用账号。`"
      confirm-label="删除成员"
      danger
      require-reason
      @cancel="deleteTarget = null"
      @confirm="remove"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import {
  CheckSquare,
  ChevronLeft,
  ChevronRight,
  Download,
  Power,
  PowerOff,
  Upload,
  UserPlus,
} from "@lucide/vue";
import PageHeader from "../../shared/ui/PageHeader.vue";
import LoadingBlock from "../../shared/ui/LoadingBlock.vue";
import EmptyState from "../../shared/ui/EmptyState.vue";
import StatusBadge from "../../shared/ui/StatusBadge.vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
import MemberEditorDialog from "../../features/members/MemberEditorDialog.vue";
import MemberPasswordResetDialog from "../../features/members/MemberPasswordResetDialog.vue";
import BulkMemberStatusDialog from "../../features/members/BulkMemberStatusDialog.vue";
import MemberFilters from "../../features/members/MemberFilters.vue";
import MemberRowActions from "../../features/members/MemberRowActions.vue";
import {
  bulkStatusPayload,
  selectableMemberIds,
  togglePageSelection,
  type BulkStatusResult,
  type MemberImportResult,
  type MemberPage,
  type MemberStatus,
  type MemberSummary,
} from "../../features/members/memberDirectory";
import { del, get, post, put } from "../../shared/api";
import { useAsyncTask } from "../../shared/composables/useAsyncTask";
import { useSession } from "../../app/session";

const { user } = useSession();
const { busy, error: taskError, run } = useAsyncTask();
const members = ref<MemberSummary[]>([]);
const grades = ref<string[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
const selected = ref(new Set<number>());
const editorOpen = ref(false);
const editorTarget = ref<MemberSummary | null>(null);
const importOpen = ref(false);
const importFile = ref<File | null>(null);
const importResult = ref<MemberImportResult | null>(null);
const bulkOpen = ref(false);
const bulkTargetStatus = ref<MemberStatus>("ACTIVE");
const bulkResult = ref<BulkStatusResult | null>(null);
const resetTarget = ref<MemberSummary | null>(null);
const deleteTarget = ref<MemberSummary | null>(null);
const filters = reactive({ keyword: "", role: "", status: "", grade: "" });
const importError = computed(() => (importOpen.value ? taskError.value : ""));

const totalPages = computed(() =>
  Math.max(1, Math.ceil(total.value / pageSize)),
);
const gradeChoices = Array.from(
  { length: 30 },
  (_, index) => `${new Date().getFullYear() + 2 - index}级`,
);
const selectableIds = computed(() =>
  selectableMemberIds(members.value, user.value?.role, user.value?.id),
);
const pageAllSelected = computed(
  () =>
    selectableIds.value.length > 0 &&
    selectableIds.value.every((id) => selected.value.has(id)),
);
const lockEditorAccountControls = computed(
  () =>
    editorTarget.value?.id === user.value?.id &&
    editorTarget.value?.role === "ADMIN",
);

onMounted(async () => {
  await Promise.all([load(), loadGrades()]);
});

async function load(target = page.value) {
  const query = new URLSearchParams({
    page: String(target),
    pageSize: String(pageSize),
  });
  if (filters.keyword) query.set("keyword", filters.keyword);
  if (filters.role) query.set("role", filters.role);
  if (filters.status) query.set("status", filters.status);
  if (filters.grade) query.set("grade", filters.grade);
  const value = await run(() =>
    get<MemberPage>(`/api/users/page?${query}`),
  );
  if (!value) return;
  members.value = value.items;
  total.value = value.total;
  page.value = value.page;
}

async function loadGrades() {
  grades.value = await get<string[]>("/api/users/grades");
}

function canEdit(member: MemberSummary) {
  return user.value?.role === "ADMIN" || member.role !== "ADMIN";
}

function openCreate() {
  editorTarget.value = null;
  editorOpen.value = true;
}

function openEdit(member: MemberSummary) {
  editorTarget.value = member;
  editorOpen.value = true;
}

function closeEditor() {
  editorOpen.value = false;
  editorTarget.value = null;
}

async function saveMember(payload: {
  studentNo: string;
  name: string;
  role: MemberSummary["role"];
  status: MemberStatus;
  phone: string;
  major: string;
  grade: string;
  qq: string;
  reason?: string;
}) {
  const target = editorTarget.value;
  const value = target
    ? await run(
        () =>
          put<MemberSummary>(`/api/users/${target.id}`, {
            name: payload.name,
            role: payload.role,
            status: payload.status,
            phone: payload.phone,
            major: payload.major,
            grade: payload.grade,
            qq: payload.qq,
            reason: payload.reason,
          }),
        "成员资料已更新",
      )
    : await run(
        () =>
          post<MemberSummary>("/api/users", {
            studentNo: payload.studentNo,
            name: payload.name,
            role: payload.role,
            phone: payload.phone,
            major: payload.major,
            grade: payload.grade,
            qq: payload.qq,
          }),
        "成员已新增，初始密码为学号后六位",
      );
  if (!value) return;
  closeEditor();
  await Promise.all([load(target ? page.value : 1), loadGrades()]);
}

async function toggleStatus(member: MemberSummary) {
  const status: MemberStatus =
    member.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
  const value = await run(
    () =>
      put<MemberSummary>(`/api/users/${member.id}`, {
        name: member.name,
        role: member.role,
        status,
        phone: member.phone,
        major: member.major,
        grade: member.grade,
        qq: member.qq,
        reason: status === "ACTIVE" ? "启用成员账号" : "停用成员账号",
      }),
    "账号状态已更新",
  );
  if (value) await load();
}

function toggleMember(id: number) {
  const next = new Set(selected.value);
  if (next.has(id)) next.delete(id);
  else next.add(id);
  selected.value = next;
}

function togglePage(event: Event) {
  selected.value = togglePageSelection(
    selected.value,
    selectableIds.value,
    (event.target as HTMLInputElement).checked,
  );
}

function openBulk(status: MemberStatus) {
  bulkTargetStatus.value = status;
  bulkOpen.value = true;
}

async function applyBulkStatus(reason: string) {
  const result = await run(
    () =>
      put<BulkStatusResult>(
        "/api/users/bulk-status",
        bulkStatusPayload(selected.value, bulkTargetStatus.value, reason),
      ),
    "批量状态操作已完成",
  );
  if (!result) return;
  bulkResult.value = result;
  bulkOpen.value = false;
  selected.value = new Set();
  await load();
}

function openImport() {
  taskError.value = "";
  importResult.value = null;
  importFile.value = null;
  importOpen.value = true;
}

function pickFile(event: Event) {
  importFile.value = (event.target as HTMLInputElement).files?.[0] || null;
  importResult.value = null;
  taskError.value = "";
}

async function importMembers() {
  if (!importFile.value) return;
  const body = new FormData();
  body.append("file", importFile.value);
  const value = await run(
    () => post<MemberImportResult>("/api/users/import", body),
    "成员导入完成",
  );
  if (!value) return;
  importResult.value = value;
  importFile.value = null;
  await Promise.all([load(1), loadGrades()]);
}

async function resetPassword(newPassword: string) {
  if (!resetTarget.value) return;
  const result = await run(
    async () => {
      await post(`/api/users/${resetTarget.value?.id}/reset-password`, {
        newPassword: newPassword || undefined,
        reason: "后台重置密码",
      });
      return true;
    },
    "密码已重置",
  );
  if (!result) return;
  resetTarget.value = null;
}

async function remove(reason: string) {
  if (!deleteTarget.value) return;
  const removed = await run(
    () => del(`/api/users/${deleteTarget.value?.id}`, { reason }),
    "成员已删除",
  );
  if (removed === undefined) return;
  deleteTarget.value = null;
  await load();
}

const roleLabel = (role: string) =>
  (
    {
      MEMBER: "成员",
      MINISTER: "部长",
      PRESIDENT: "会长",
      ADMIN: "管理员",
    } as Record<string, string>
  )[role] || role;
</script>

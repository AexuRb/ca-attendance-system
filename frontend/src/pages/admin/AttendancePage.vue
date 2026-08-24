<template>
  <div class="page-stack">
    <PageHeader
      title="值班记录"
      description="查询、补录与修正值班记录。"
      ><template #actions
        ><button v-if="canCreate" class="button primary" @click="openCreate">
          <Plus />补录记录
        </button></template
      ></PageHeader
    >
    <form class="filter-bar" @submit.prevent="load(1)">
      <label
        ><span>开始日期</span
        ><input v-model="filters.from" type="date" /></label
      ><label
        ><span>结束日期</span><input v-model="filters.to" type="date" /></label
      ><label class="filter-grow"
        ><span>成员</span
        ><input
          v-model.trim="filters.keyword"
          placeholder="学号或姓名" /></label
      ><label
        ><span>状态</span
        ><select v-model="filters.status">
          <option value="">全部</option>
          <option value="VALID">有效</option>
          <option value="INCOMPLETE">未签退</option>
          <option value="PENDING">待审核</option>
          <option value="INVALID">无效</option>
        </select></label
      ><button class="button secondary" type="submit"><Search />查询</button>
    </form>
    <div v-if="displayError" class="inline-alert danger" role="alert">
      <span>{{ displayError }}</span>
      <button
        v-if="listError"
        class="button secondary small"
        type="button"
        data-action="retry-attendance"
        @click="load()"
      >
        重试
      </button>
    </div>
    <LoadingBlock v-if="listLoading && !records.length" />
    <EmptyState v-else-if="!records.length && !listError" title="没有符合条件的记录" />
    <div v-else class="table-shell">
      <table>
        <thead>
          <tr>
            <th>成员</th>
            <th>日期</th>
            <th>签到</th>
            <th>签退</th>
            <th>有效时长</th>
            <th>状态</th>
            <th class="align-right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in records" :key="item.id">
            <td>
              <strong>{{ item.name }}</strong
              ><small>{{ item.studentNo }}</small>
            </td>
            <td>{{ item.dutyDate }}</td>
            <td>{{ dateTime(item.checkInTime) }}</td>
            <td>{{ dateTime(item.checkOutTime) }}</td>
            <td>
              {{ item.durationMinutes ? `${item.durationMinutes} 分钟` : "—" }}
            </td>
            <td>
              <StatusBadge
                :label="statusLabel(item.effectiveStatus)"
                :tone="statusTone(item.effectiveStatus)"
              />
            </td>
            <td class="align-right row-actions">
              <button
                class="icon-button"
                :title="
                  actionAccess(item).allowed
                    ? '编辑'
                    : actionAccess(item).reason
                "
                :aria-label="
                  actionAccess(item).allowed
                    ? '编辑'
                    : `不可编辑：${actionAccess(item).reason}`
                "
                :disabled="!actionAccess(item).allowed"
                @click="openEdit(item)"
              >
                <Pencil /></button
              ><button
                class="icon-button danger-ghost"
                :title="
                  actionAccess(item).allowed
                    ? '删除'
                    : actionAccess(item).reason
                "
                :aria-label="
                  actionAccess(item).allowed
                    ? '删除'
                    : `不可删除：${actionAccess(item).reason}`
                "
                :disabled="!actionAccess(item).allowed"
                @click="askDelete(item)"
              >
                <Trash2 />
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="total" class="pagination">
      <span>共 {{ total }} 条记录</span>
      <div>
        <button
          class="button secondary small"
          :disabled="page <= 1 || listLoading"
          @click="load(page - 1)"
        >
          <ChevronLeft />上一页
        </button>
        <span>第 {{ page }} / {{ totalPages }} 页</span>
        <button
          class="button secondary small"
          :disabled="page >= totalPages || listLoading"
          @click="load(page + 1)"
        >
          下一页<ChevronRight />
        </button>
      </div>
    </div>
    <ModalDialog
      :open="editorOpen"
      :title="editing ? '修改值班记录' : '补录值班记录'"
      size="lg"
      @close="closeEditor"
    >
      <div class="form-grid two">
        <div v-if="!editing" class="field span-2">
          <span>补录成员</span>
          <AccountPicker
            v-model="selectedMember"
            :candidates="manualCandidates"
            :open="editorOpen"
            aria-label="选择补录成员"
            placeholder="搜索姓名或学号"
          />
        </div>
        <label class="field"
          ><span>签到时间</span
          ><input
            v-model="form.checkInTime"
            type="datetime-local"
            required /></label
        ><label class="field"
          ><span>签退时间</span
          ><input v-model="form.checkOutTime" type="datetime-local" /></label
        ><label v-if="editing && canReviewStatus" class="field"
          ><span>签到状态</span
          ><select v-model="form.checkInStatus">
            <option value="APPROVED">已通过</option>
            <option value="AUTO_APPROVED">自动通过</option>
            <option value="REJECTED">已驳回</option>
          </select></label
        ><label v-if="editing && canReviewStatus" class="field"
          ><span>签退状态</span
          ><select v-model="form.checkOutStatus">
            <option value="APPROVED">已通过</option>
            <option value="AUTO_APPROVED">自动通过</option>
            <option value="REJECTED">已驳回</option>
            <option value="NOT_SUBMITTED">未提交</option>
          </select></label
        ><label class="field span-2"
          ><span>操作原因</span
          ><textarea v-model="form.reason" rows="3" required />
        </label>
        <label
          v-if="editing && canReviewStatus"
          class="attendance-reevaluate span-2"
        >
          <input v-model="form.recomputeSnapshot" type="checkbox" />
          <span>
            <strong>按当前值班设置重新评估</strong>
            <small>关闭时保留该记录产生时的值班日与时段规则</small>
          </span>
        </label>
      </div>
      <template #footer
        ><button class="button secondary" :disabled="actions.isPending('save')" @click="closeEditor">
          取消</button
        ><button
          class="button primary"
          :disabled="
            actions.isPending('save') ||
            !form.reason.trim() || (!editing && !selectedMember)
          "
          @click="save"
        >
          保存
        </button></template
      >
    </ModalDialog>
    <ConfirmDialog
      :open="Boolean(deleteTarget)"
      title="删除值班记录"
      :message="`将删除 ${deleteTarget?.name || ''} 的这条记录，系统会先自动备份。`"
      confirm-label="删除记录"
      danger
      require-reason
      :pending="actions.isPending('delete')"
      @cancel="deleteTarget = null"
      @confirm="remove"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import {
  ChevronLeft,
  ChevronRight,
  Pencil,
  Plus,
  Search,
  Trash2,
} from "@lucide/vue";
import PageHeader from "../../shared/ui/PageHeader.vue";
import EmptyState from "../../shared/ui/EmptyState.vue";
import LoadingBlock from "../../shared/ui/LoadingBlock.vue";
import StatusBadge from "../../shared/ui/StatusBadge.vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
import AccountPicker from "../../features/accounts/AccountPicker.vue";
import { del, get, post, put } from "../../shared/api";
import { useAsyncTask } from "../../shared/composables/useAsyncTask";
import { useLatestRequest } from "../../shared/composables/useLatestRequest";
import { usePendingActions } from "../../shared/composables/usePendingActions";
import { dateRangeError } from "../../shared/validation/dateRange";
import { useSession } from "../../app/session";
import {
  attendancePageQuery,
  attendanceActionAccess,
  localDateTimeInput,
  manualCheckoutStatus,
  totalAttendancePages,
  type AttendanceActionAccess,
  type AttendanceRecordItem,
  type AttendanceRecordPage,
} from "../../features/attendance/attendanceRecords";
import type { AccountCandidate } from "../../features/accounts/accountCandidates";
const { user } = useSession();
const route = useRoute();
const records = ref<AttendanceRecordItem[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
const task = useAsyncTask();
const listRequest = useLatestRequest();
const actions = usePendingActions();
const { loading: listLoading, error: listError } = listRequest;
const editorOpen = ref(false);
const editing = ref<AttendanceRecordItem | null>(null);
const deleteTarget = ref<AttendanceRecordItem | null>(null);
const manualCandidates = ref<AccountCandidate[]>([]);
const selectedMember = ref<AccountCandidate | null>(null);
const filters = reactive({ from: "", to: "", keyword: "", status: "" });
const filterError = computed(() => dateRangeError(filters.from, filters.to));
const displayError = computed(() => filterError.value || listError.value);
const form = reactive({
  studentNo: "",
  checkInTime: "",
  checkOutTime: "",
  checkInStatus: "APPROVED",
  checkOutStatus: "APPROVED",
  recomputeSnapshot: false,
  reason: "",
});
const canCreate = computed(() =>
  ["PRESIDENT", "ADMIN"].includes(user.value?.role || ""),
);
const canReviewStatus = canCreate;
watch(
  () => form.checkOutTime,
  (value) => {
    form.checkOutStatus = manualCheckoutStatus(form.checkOutStatus, value);
  },
);
const totalPages = computed(() =>
  totalAttendancePages(total.value, pageSize),
);
onMounted(async () => {
  const now = new Date();
  filters.to = localDate(now);
  const start = new Date(now);
  start.setDate(1);
  filters.from = localDate(start);
  filters.status =
    typeof route.query.status === "string" ? route.query.status : "";
  await Promise.all([load(), canCreate.value ? loadManualCandidates() : undefined]);
});
async function load(target = page.value) {
  if (filterError.value) return;
  const query = attendancePageQuery(filters, target, pageSize);
  const value = await listRequest.run(
    (signal) =>
      get<AttendanceRecordPage>(`/api/attendance/page?${query}`, { signal }),
    "值班记录加载失败",
  );
  if (!value) return;
  records.value = value.items;
  total.value = value.total;
  page.value = value.page;
}
function openCreate() {
  editing.value = null;
  selectedMember.value = null;
  Object.assign(form, {
    studentNo: "",
    checkInTime: localDateTimeInput(new Date()),
    checkOutTime: "",
    checkInStatus: "APPROVED",
    checkOutStatus: "APPROVED",
    recomputeSnapshot: false,
    reason: "",
  });
  editorOpen.value = true;
}
function openEdit(item: AttendanceRecordItem) {
  if (!actionAccess(item).allowed) return;
  editing.value = item;
  Object.assign(form, {
    studentNo: item.studentNo,
    checkInTime: toInput(item.checkInTime),
    checkOutTime: toInput(item.checkOutTime),
    checkInStatus: item.checkInStatus,
    checkOutStatus: item.checkOutStatus,
    recomputeSnapshot: false,
    reason: "",
  });
  editorOpen.value = true;
}
async function save() {
  await actions.run("save", async () => {
    const current = editing.value;
    const payload = {
      ...form,
      studentNo: current
        ? form.studentNo
        : selectedMember.value?.studentNo || "",
      checkOutTime: form.checkOutTime || null,
    };
    const result = current
      ? await task.run(
          () => put(`/api/attendance/${current.id}/manual`, payload),
          "记录已更新",
        )
      : await task.run(
          () => post("/api/attendance/manual", payload),
          "记录已补录",
        );
    if (result === undefined) return;
    editorOpen.value = false;
    await load();
  });
}
function askDelete(item: AttendanceRecordItem) {
  if (!actionAccess(item).allowed) return;
  deleteTarget.value = item;
}
async function remove(reason: string) {
  const target = deleteTarget.value;
  if (!target) return;
  await actions.run("delete", async () => {
    const removed = await task.run(
      () =>
        del(
          `/api/attendance/${target.id}?reason=${encodeURIComponent(reason)}`,
        ),
      "记录已删除",
    );
    if (removed === undefined) return;
    deleteTarget.value = null;
    await load();
  });
}
const statusLabels: Record<string, string> = {
  VALID: "有效",
  INCOMPLETE: "未签退",
  PENDING: "待审核",
  INVALID: "无效",
};
const statusLabel = (v: string) => statusLabels[v] || v;
const statusTone = (v: string) =>
  v === "VALID"
    ? "success"
    : v === "INVALID"
      ? "danger"
      : v === "PENDING"
        ? "warning"
        : "info";
const dateTime = (v?: string) => v?.replace("T", " ").slice(0, 16) || "—";
const toInput = (v?: string) => v?.slice(0, 16) || "";
function actionAccess(item: AttendanceRecordItem): AttendanceActionAccess {
  return attendanceActionAccess(
    user.value?.role,
    item.userRole,
    item.dutyDate,
  );
}
async function loadManualCandidates() {
  const value = await task.run(() =>
    get<AccountCandidate[]>("/api/attendance/manual-candidates"),
  );
  if (value) manualCandidates.value = value;
}
function closeEditor() {
  if (!actions.isPending("save")) editorOpen.value = false;
}
function localDate(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}
</script>

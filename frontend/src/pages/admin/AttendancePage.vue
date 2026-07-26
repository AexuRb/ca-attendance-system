<template>
  <div class="page-stack">
    <PageHeader
      eyebrow="DUTY / RECORDS"
      title="值班记录"
      description="查询、补录与修正值班记录。"
      ><template #actions
        ><button v-if="canCreate" class="button primary" @click="openCreate">
          <Plus />补录记录
        </button></template
      ></PageHeader
    >
    <form class="filter-bar" @submit.prevent="load">
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
    <LoadingBlock v-if="busy && !records.length" />
    <EmptyState v-else-if="!records.length" title="没有符合条件的记录" />
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
                title="编辑"
                aria-label="编辑"
                @click="openEdit(item)"
              >
                <Pencil /></button
              ><button
                class="icon-button danger-ghost"
                title="删除"
                aria-label="删除"
                @click="askDelete(item)"
              >
                <Trash2 />
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <ModalDialog
      :open="editorOpen"
      :title="editing ? '修改值班记录' : '补录值班记录'"
      size="lg"
      @close="editorOpen = false"
    >
      <div class="form-grid two">
        <label v-if="!editing" class="field"
          ><span>成员学号</span
          ><input v-model.trim="form.studentNo" required /></label
        ><label class="field"
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
      </div>
      <template #footer
        ><button class="button secondary" @click="editorOpen = false">
          取消</button
        ><button
          class="button primary"
          :disabled="!form.reason.trim()"
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
      @cancel="deleteTarget = null"
      @confirm="remove"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import { Pencil, Plus, Search, Trash2 } from "@lucide/vue";
import PageHeader from "../../shared/ui/PageHeader.vue";
import EmptyState from "../../shared/ui/EmptyState.vue";
import LoadingBlock from "../../shared/ui/LoadingBlock.vue";
import StatusBadge from "../../shared/ui/StatusBadge.vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
import { del, get, post, put } from "../../shared/api";
import { useAsyncTask } from "../../shared/composables/useAsyncTask";
import { useSession } from "../../app/session";
const { user } = useSession();
const route = useRoute();
const records = ref<any[]>([]);
const { busy, run } = useAsyncTask();
const editorOpen = ref(false);
const editing = ref<any>(null);
const deleteTarget = ref<any>(null);
const filters = reactive({ from: "", to: "", keyword: "", status: "" });
const form = reactive({
  studentNo: "",
  checkInTime: "",
  checkOutTime: "",
  checkInStatus: "APPROVED",
  checkOutStatus: "APPROVED",
  reason: "",
});
const canCreate = computed(() =>
  ["PRESIDENT", "ADMIN"].includes(user.value?.role || ""),
);
const canReviewStatus = canCreate;
onMounted(() => {
  const now = new Date();
  filters.to = localDate(now);
  const start = new Date(now);
  start.setDate(1);
  filters.from = localDate(start);
  filters.status =
    typeof route.query.status === "string" ? route.query.status : "";
  load();
});
async function load() {
  const p = new URLSearchParams({ from: filters.from, to: filters.to });
  if (filters.keyword) p.set("studentNo", filters.keyword);
  if (filters.status) p.set("status", filters.status);
  const value = await run(() => get<any[]>(`/api/attendance?${p}`));
  if (value) records.value = value;
}
function openCreate() {
  editing.value = null;
  Object.assign(form, {
    studentNo: "",
    checkInTime: `${localDate(new Date())}T14:00`,
    checkOutTime: "",
    checkInStatus: "APPROVED",
    checkOutStatus: "APPROVED",
    reason: "",
  });
  editorOpen.value = true;
}
function openEdit(item: any) {
  editing.value = item;
  Object.assign(form, {
    studentNo: item.studentNo,
    checkInTime: toInput(item.checkInTime),
    checkOutTime: toInput(item.checkOutTime),
    checkInStatus: item.checkInStatus,
    checkOutStatus: item.checkOutStatus,
    reason: "",
  });
  editorOpen.value = true;
}
async function save() {
  const payload = { ...form, checkOutTime: form.checkOutTime || null };
  const ok = editing.value
    ? await run(
        () => put(`/api/attendance/${editing.value.id}/manual`, payload),
        "记录已更新",
      )
    : await run(() => post("/api/attendance/manual", payload), "记录已补录");
  if (ok) {
    editorOpen.value = false;
    await load();
  }
}
function askDelete(item: any) {
  deleteTarget.value = item;
}
async function remove(reason: string) {
  await run(
    () =>
      del(
        `/api/attendance/${deleteTarget.value.id}?reason=${encodeURIComponent(reason)}`,
      ),
    "记录已删除",
  );
  deleteTarget.value = null;
  await load();
}
const statusLabel = (v: string) =>
  (
    ({
      VALID: "有效",
      INCOMPLETE: "未签退",
      PENDING: "待审核",
      INVALID: "无效",
    }) as any
  )[v] || v;
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
function localDate(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}
</script>

<template>
  <div class="page-stack">
    <PageHeader
      eyebrow="PEOPLE / DIRECTORY"
      title="成员名册"
      description="管理在册账号、角色与启用状态。"
    >
      <template #actions>
        <button class="button secondary" @click="importOpen = true">
          <Upload />批量导入
        </button>
        <button class="button primary" @click="openCreate">
          <UserPlus />新增成员
        </button>
      </template>
    </PageHeader>

    <form class="filter-bar" @submit.prevent="load(1)">
      <label class="filter-grow"
        ><span>搜索成员</span
        ><input
          v-model.trim="filters.keyword"
          placeholder="姓名、学号、手机号或学院"
      /></label>
      <label
        ><span>角色</span
        ><select v-model="filters.role">
          <option value="">全部角色</option>
          <option value="MEMBER">成员</option>
          <option value="MINISTER">部长</option>
          <option value="PRESIDENT">会长</option>
          <option value="ADMIN">管理员</option>
        </select></label
      >
      <label
        ><span>年级</span
        ><select v-model="filters.grade">
          <option value="">全部年级</option>
          <option v-for="grade in grades" :key="grade" :value="grade">
            {{ grade }}
          </option>
        </select></label
      >
      <button class="button secondary" type="submit"><Search />查询</button>
    </form>

    <LoadingBlock v-if="busy && !users.length" />
    <EmptyState v-else-if="!users.length" title="没有符合条件的成员" />
    <div v-else class="table-shell">
      <table>
        <thead>
          <tr>
            <th>成员</th>
            <th>联系方式</th>
            <th>学院 / 年级</th>
            <th>角色</th>
            <th>状态</th>
            <th class="align-right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in users" :key="item.id">
            <td>
              <strong>{{ item.name }}</strong
              ><small>{{ item.studentNo }}</small>
            </td>
            <td>
              {{ item.phone || "—"
              }}<small v-if="item.qq">QQ {{ item.qq }}</small>
            </td>
            <td>
              {{ item.major || "—"
              }}<small>{{ item.grade || "未填写年级" }}</small>
            </td>
            <td>
              <select
                class="table-select"
                :value="item.role"
                :disabled="!canEdit(item)"
                :aria-label="`修改 ${item.name} 的角色`"
                @change="onRoleChange(item, $event)"
              >
                <option value="MEMBER">成员</option>
                <option value="MINISTER">部长</option>
                <option value="PRESIDENT">会长</option>
                <option value="ADMIN" :disabled="user?.role !== 'ADMIN'">
                  管理员
                </option>
              </select>
            </td>
            <td>
              <StatusBadge
                :label="item.status === 'ACTIVE' ? '启用' : '停用'"
                :tone="item.status === 'ACTIVE' ? 'success' : 'neutral'"
              />
            </td>
            <td class="align-right row-actions">
              <button
                class="icon-button"
                :title="item.status === 'ACTIVE' ? '停用账号' : '启用账号'"
                :aria-label="item.status === 'ACTIVE' ? '停用账号' : '启用账号'"
                :disabled="!canEdit(item)"
                @click="toggle(item)"
              >
                <Power v-if="item.status !== 'ACTIVE'" /><PowerOff
                  v-else
                /></button
              ><button
                class="icon-button"
                title="重置密码"
                aria-label="重置密码"
                :disabled="!canEdit(item)"
                @click="confirmReset(item)"
              >
                <KeyRound /></button
              ><button
                v-if="user?.role === 'ADMIN'"
                class="icon-button danger-ghost"
                title="删除成员"
                aria-label="删除成员"
                :disabled="item.id === user?.id"
                @click="deleteTarget = item"
              >
                <Trash2 />
              </button>
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
      :open="editorOpen"
      title="新增成员"
      size="lg"
      @close="editorOpen = false"
    >
      <div class="form-grid two">
        <label class="field"
          ><span>学号</span
          ><input v-model.trim="form.studentNo" autocomplete="off" /></label
        ><label class="field"
          ><span>姓名</span
          ><input v-model.trim="form.name" autocomplete="name" /></label
        ><label class="field"
          ><span>手机号</span
          ><input v-model.trim="form.phone" autocomplete="tel" /></label
        ><label class="field"
          ><span>学院</span><input v-model.trim="form.major" /></label
        ><label class="field"
          ><span>年级</span
          ><select v-model="form.grade">
            <option value="">暂不填写</option>
            <option v-for="grade in gradeChoices" :key="grade" :value="grade">
              {{ grade }}
            </option>
          </select></label
        ><label class="field"
          ><span>QQ</span><input v-model.trim="form.qq" inputmode="numeric"
        /></label>
      </div>
      <template #footer
        ><button class="button secondary" @click="editorOpen = false">
          取消</button
        ><button
          class="button primary"
          :disabled="!form.studentNo || !form.name"
          @click="create"
        >
          新增成员
        </button></template
      >
    </ModalDialog>

    <ModalDialog
      :open="importOpen"
      title="批量导入成员"
      size="sm"
      @close="importOpen = false"
    >
      <div class="upload-zone">
        <Upload /><strong>选择成员 Excel</strong>
        <p>支持 .xlsx 与 .xls 文件</p>
        <input type="file" accept=".xlsx,.xls" @change="pickFile" />
      </div>
      <div v-if="importResult" class="result-note">
        新增 {{ importResult.created }}，更新 {{ importResult.updated }}，跳过
        {{ importResult.skipped }}
      </div>
      <template #footer
        ><a
          class="button secondary"
          href="/templates/member-import-template.xlsx"
          download="成员批量导入模板.xlsx"
          ><Download />下载模板</a
        ><button
          class="button primary"
          :disabled="!importFile"
          @click="importMembers"
        >
          开始导入
        </button></template
      >
    </ModalDialog>

    <ConfirmDialog
      :open="Boolean(resetTarget)"
      title="重置成员密码"
      :message="`将 ${resetTarget?.name || ''} 的密码恢复为学号后六位。`"
      confirm-label="确认重置"
      @cancel="resetTarget = null"
      @confirm="resetPassword"
    />
    <ConfirmDialog
      :open="Boolean(deleteTarget)"
      title="删除成员"
      :message="`将删除 ${deleteTarget?.name || ''} 的账号。系统会先自动备份，删除后只能通过整库备份恢复；已有业务记录时请改为停用。`"
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
  ChevronLeft,
  ChevronRight,
  Download,
  KeyRound,
  Power,
  PowerOff,
  Search,
  Trash2,
  Upload,
  UserPlus,
} from "@lucide/vue";
import PageHeader from "../../shared/ui/PageHeader.vue";
import LoadingBlock from "../../shared/ui/LoadingBlock.vue";
import EmptyState from "../../shared/ui/EmptyState.vue";
import StatusBadge from "../../shared/ui/StatusBadge.vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
import { del, get, post, put } from "../../shared/api";
import { useAsyncTask } from "../../shared/composables/useAsyncTask";
import { useSession } from "../../app/session";
const { user } = useSession();
const { busy, run } = useAsyncTask();
const users = ref<any[]>([]);
const grades = ref<string[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
const editorOpen = ref(false);
const importOpen = ref(false);
const importFile = ref<File | null>(null);
const importResult = ref<any>(null);
const resetTarget = ref<any>(null);
const deleteTarget = ref<any>(null);
const filters = reactive({ keyword: "", role: "", grade: "" });
const form = reactive({
  studentNo: "",
  name: "",
  phone: "",
  major: "",
  grade: "",
  qq: "",
});
const totalPages = computed(() =>
  Math.max(1, Math.ceil(total.value / pageSize)),
);
const gradeChoices = Array.from(
  { length: 30 },
  (_, i) => `${new Date().getFullYear() + 2 - i}级`,
);
onMounted(async () => {
  await Promise.all([load(), loadGrades()]);
});
async function load(target = page.value) {
  const p = new URLSearchParams({
    page: String(target),
    pageSize: String(pageSize),
  });
  if (filters.keyword) p.set("keyword", filters.keyword);
  if (filters.role) p.set("role", filters.role);
  if (filters.grade) p.set("grade", filters.grade);
  const value = await run(() => get<any>(`/api/users/page?${p}`));
  if (value) {
    users.value = value.items;
    total.value = value.total;
    page.value = value.page;
  }
}
async function loadGrades() {
  grades.value = await get("/api/users/grades");
}
function openCreate() {
  Object.assign(form, {
    studentNo: "",
    name: "",
    phone: "",
    major: "",
    grade: "",
    qq: "",
  });
  editorOpen.value = true;
}
async function create() {
  const value = await run(
    () => post("/api/users", { ...form, role: "MEMBER" }),
    "成员已新增，初始密码为学号后六位",
  );
  if (value) {
    editorOpen.value = false;
    await load(1);
  }
}
function canEdit(item: any) {
  return user.value?.role === "ADMIN" || item.role !== "ADMIN";
}
async function changeRole(item: any, role: string) {
  if (
    await run(
      () =>
        put(`/api/users/${item.id}`, { ...item, role, reason: "调整成员角色" }),
      "角色已更新",
    )
  )
    await load();
}
function onRoleChange(item: any, event: Event) {
  changeRole(item, (event.target as HTMLSelectElement).value);
}
async function toggle(item: any) {
  const status = item.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
  if (
    await run(
      () =>
        put(`/api/users/${item.id}`, {
          ...item,
          status,
          reason: status === "ACTIVE" ? "启用成员账号" : "停用成员账号",
        }),
      "账号状态已更新",
    )
  )
    await load();
}
function confirmReset(item: any) {
  resetTarget.value = item;
}
async function resetPassword() {
  await run(
    () =>
      post(`/api/users/${resetTarget.value.id}/reset-password`, {
        reason: "后台重置密码",
      }),
    "密码已重置",
  );
  resetTarget.value = null;
}
async function remove(reason: string) {
  await run(
    () => del(`/api/users/${deleteTarget.value.id}`, { reason }),
    "成员已删除",
  );
  deleteTarget.value = null;
  await load();
}
function pickFile(event: Event) {
  importFile.value = (event.target as HTMLInputElement).files?.[0] || null;
  importResult.value = null;
}
async function importMembers() {
  if (!importFile.value) return;
  const body = new FormData();
  body.append("file", importFile.value);
  const value = await run(
    () => post<any>("/api/users/import", body),
    "成员导入完成",
  );
  if (value) {
    importResult.value = value;
    importFile.value = null;
    await load(1);
    await loadGrades();
  }
}
</script>

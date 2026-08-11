<template>
  <div class="page-stack">
    <PageHeader
      title="培训记录"
      description="记录培训场次、参与名单与计入值班的培训时长。"
      ><template #actions
        ><button class="button secondary" @click="exportSummary">
          <Download />导出统计</button
        ><button class="button primary" @click="openSession()">
          <Plus />新建培训
        </button></template
      ></PageHeader
    >
    <form class="filter-bar" @submit.prevent="loadSessions">
      <label class="filter-grow"
        ><span>关键词</span
        ><input
          v-model.trim="filters.keyword"
          placeholder="标题、地点或主讲人" /></label
      ><label
        ><span>开始日期</span
        ><input v-model="filters.from" type="date" /></label
      ><label
        ><span>结束日期</span><input v-model="filters.to" type="date" /></label
      ><button class="button secondary" type="submit"><Search />查询</button>
    </form>
    <div class="split-workspace">
      <aside class="record-list panel">
        <div class="section-heading">
          <div>
            <p class="eyebrow">SESSIONS</p>
            <h2>培训场次</h2>
          </div>
          <span>{{ sessions.length }}</span>
        </div>
        <button
          v-for="item in sessions"
         :key="item.id"
          class="record-list-item"
          :class="{ active: selected?.id === item.id }"
          type="button"
          :aria-pressed="selected?.id === item.id"
          @click="select(item)"
        >
          <span class="record-date">{{ shortDate(item.trainingDate) }}</span
          ><span class="record-list-copy"
            ><strong>{{ item.title }}</strong
            ><small>{{
              [item.speaker, item.location].filter(Boolean).join(" · ") ||
              "未填写主讲人与地点"
            }}</small></span
          ><b>{{ item.participantCount || 0 }} 人</b>
        </button>
        <EmptyState v-if="!sessions.length" title="暂无培训记录" />
      </aside>
      <main class="detail-panel panel">
        <EmptyState v-if="!selected" title="请选择培训场次" />
        <template v-else>
          <div class="detail-heading training-detail-heading">
            <div>
              <p class="eyebrow">{{ selected.trainingDate }}</p>
              <h2>{{ selected.title }}</h2>
              <span>{{ sessionMeta(selected) }}</span>
            </div>
            <div class="row-actions">
              <button
                class="icon-button"
                title="导出名单"
                aria-label="导出名单"
                type="button"
                @click="downloadSession"
              >
                <Download aria-hidden="true" /></button
              ><button
                class="icon-button"
                title="导入名单"
                aria-label="导入名单"
                type="button"
                @click="importOpen = true"
              >
                <Upload aria-hidden="true" /></button
              ><button
                class="icon-button"
                title="编辑培训"
                aria-label="编辑培训"
                type="button"
                @click="openSession(selected)"
              >
                <Pencil aria-hidden="true" /></button
              ><button
                class="icon-button danger-ghost"
                title="归档培训"
                aria-label="归档培训"
                type="button"
                @click="deleteTarget = selected"
              >
                <Trash2 aria-hidden="true" />
              </button>
            </div>
          </div>
          <div class="compact-metrics">
            <div>
              <span>参与人数</span
              ><strong>{{ selected.participantCount || 0 }}</strong>
            </div>
            <div>
              <span>累计时长</span
              ><strong>{{ hours(selected.totalDurationHours) }} h</strong>
            </div>
            <div>
              <span>培训时间</span><strong>{{ timeRange(selected) }}</strong>
            </div>
          </div>
          <div class="section-heading list-heading">
            <div>
              <p class="eyebrow">PARTICIPANTS</p>
              <h2>参与名单</h2>
            </div>
            <button class="button secondary small" @click="openParticipant()">
              <Plus />新增记录
            </button>
          </div>
          <EmptyState v-if="!participants.length" title="暂无参与记录" />
          <div v-else class="table-shell compact-table training-participant-table">
            <table>
              <thead>
                <tr>
                  <th>参与人</th>
                  <th>计入时长</th>
                  <th>备注</th>
                  <th class="align-right">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in participants" :key="item.id">
                  <td data-label="参与人">
                    <strong>{{ item.name }}</strong
                    ><small>{{ item.studentNo || "未关联账号" }}</small>
                  </td>
                  <td data-label="计入时长">
                    {{ hours(item.durationHours) }} 小时
                  </td>
                  <td data-label="备注">{{ item.remark || "—" }}</td>
                  <td data-label="操作" class="align-right row-actions">
                    <button
                      class="icon-button"
                      title="编辑"
                      aria-label="编辑参与记录"
                      type="button"
                      @click="openParticipant(item)"
                    >
                      <Pencil aria-hidden="true" /></button
                    ><button
                      class="icon-button danger-ghost"
                      title="删除"
                      aria-label="删除参与记录"
                      type="button"
                      @click="participantDeleteTarget = item"
                    >
                      <Trash2 aria-hidden="true" />
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
      </main>
    </div>

    <ModalDialog
      :open="sessionOpen"
      :title="sessionForm.id ? '编辑培训' : '新建培训'"
      size="lg"
      @close="sessionOpen = false"
      ><div class="form-grid two">
        <label class="field span-2"
          ><span>培训标题</span
          ><input
            v-model.trim="sessionForm.title"
            name="training-title"
            autocomplete="off"
          /></label
        ><label class="field"
          ><span>培训日期</span
          ><input
            v-model="sessionForm.trainingDate"
            name="training-date"
            type="date"
          /></label
        ><label class="field"
          ><span>地点</span
          ><input
            v-model.trim="sessionForm.location"
            name="training-location"
            autocomplete="off"
          /></label
        ><label class="field"
          ><span>开始时间</span
          ><input
            v-model="sessionForm.startTime"
            name="training-start-time"
            type="time"
          /></label
        ><label class="field"
          ><span>结束时间</span
          ><input
            v-model="sessionForm.endTime"
            name="training-end-time"
            type="time"
          /></label
        ><label class="field"
          ><span>主讲人</span
          ><input
            v-model.trim="sessionForm.speaker"
            name="training-speaker"
            autocomplete="off"
          /></label
        ><label class="field"
          ><span>说明</span
          ><input
            v-model.trim="sessionForm.description"
            name="training-description"
            autocomplete="off"
        /></label>
      </div>
      <template #footer
        ><button class="button secondary" @click="sessionOpen = false">
          取消</button
        ><button
          class="button primary"
          :disabled="!sessionForm.title || !sessionForm.trainingDate"
          @click="saveSession"
        >
          保存培训
        </button></template
      ></ModalDialog
    >
    <ModalDialog
      :open="participantOpen"
      :title="participantForm.id ? '编辑参与记录' : '新增参与记录'"
      size="sm"
      @close="participantOpen = false"
      ><div class="form-grid">
        <label class="field"
          ><span>学号</span
          ><input
            v-model.trim="participantForm.studentNo"
            name="participant-student-no"
            autocomplete="off"
          /></label
        ><label class="field"
          ><span>姓名</span
          ><input
            v-model.trim="participantForm.name"
            name="participant-name"
            autocomplete="off"
          /></label
        ><label class="field"
          ><span>计入时长（小时）</span
          ><input
            v-model.number="participantForm.durationHours"
            name="participant-duration"
            type="number"
            min="0"
            step="0.25" /></label
        ><label class="field"
          ><span>备注</span
          ><textarea
            v-model.trim="participantForm.remark"
            name="participant-remark"
            rows="3"
          />
        </label>
      </div>
      <template #footer
        ><button class="button secondary" @click="participantOpen = false">
          取消</button
        ><button
          class="button primary"
          :disabled="!participantForm.name"
          @click="saveParticipant"
        >
          保存记录
        </button></template
      ></ModalDialog
    >
    <ModalDialog
      :open="importOpen"
      title="导入参与名单"
      size="sm"
      @close="importOpen = false"
      ><div class="upload-zone">
        <Upload /><strong>选择培训名单 Excel</strong>
        <p>第一行默认作为主讲人记录</p>
        <input
          type="file"
          name="training-roster-file"
          aria-label="选择培训名单 Excel"
          accept=".xlsx,.xls"
          @change="pickImport"
        />
      </div>
      <template #footer
        ><button class="button secondary" @click="downloadTemplate">
          <Download />下载模板</button
        ><button
          class="button primary"
          :disabled="!importFile"
          @click="importParticipants"
        >
          开始导入
        </button></template
      ></ModalDialog
    >
    <ConfirmDialog
      :open="Boolean(deleteTarget)"
      title="归档培训"
      :message="`归档培训“${deleteTarget?.title || ''}”，该场次将不再出现在列表中。`"
      confirm-label="确认归档"
      @cancel="deleteTarget = null"
      @confirm="archiveSession"
    />
    <ConfirmDialog
      :open="Boolean(participantDeleteTarget)"
      title="删除参与记录"
      :message="`删除 ${participantDeleteTarget?.name || ''} 的培训记录。`"
      confirm-label="删除记录"
      danger
      @cancel="participantDeleteTarget = null"
      @confirm="deleteParticipant"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { Download, Pencil, Plus, Search, Trash2, Upload } from "@lucide/vue";
import PageHeader from "../../shared/ui/PageHeader.vue";
import EmptyState from "../../shared/ui/EmptyState.vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
import { del, get, post, put, downloadBlob } from "../../shared/api";
import { useAsyncTask } from "../../shared/composables/useAsyncTask";
import type {
  TrainingParticipant,
  TrainingParticipantForm,
  TrainingSession,
  TrainingSessionForm,
} from "../../features/training/trainingTypes";
const { run } = useAsyncTask();
const sessions = ref<TrainingSession[]>([]);
const participants = ref<TrainingParticipant[]>([]);
const selected = ref<TrainingSession | null>(null);
const sessionOpen = ref(false);
const participantOpen = ref(false);
const importOpen = ref(false);
const importFile = ref<File | null>(null);
const deleteTarget = ref<TrainingSession | null>(null);
const participantDeleteTarget = ref<TrainingParticipant | null>(null);
const today = localDate(new Date());
const filters = reactive({
  keyword: "",
  from: `${new Date().getFullYear()}-01-01`,
  to: today,
});
const sessionForm = reactive<TrainingSessionForm>({
  id: null,
  title: "",
  trainingDate: today,
  startTime: "",
  endTime: "",
  location: "",
  speaker: "",
  description: "",
});
const participantForm = reactive<TrainingParticipantForm>({
  id: null,
  studentNo: "",
  name: "",
  durationHours: 0,
  remark: "",
});
onMounted(loadSessions);
async function loadSessions() {
  const p = new URLSearchParams();
  Object.entries(filters).forEach(([k, v]) => v && p.set(k, v));
  const value = await run(() => get<TrainingSession[]>(`/api/trainings?${p}`));
  if (value) {
    sessions.value = value;
    const next =
      value.find((i) => i.id === selected.value?.id) || value[0] || null;
    await select(next);
  }
}
async function select(item: TrainingSession | null) {
  selected.value = item;
  participants.value = item
    ? await get<TrainingParticipant[]>(
        `/api/trainings/${item.id}/participants`,
      )
    : [];
}
function openSession(item?: TrainingSession) {
  Object.assign(
    sessionForm,
    item
      ? { ...item }
      : {
          id: null,
          title: "",
          trainingDate: today,
          startTime: "",
          endTime: "",
          location: "",
          speaker: "",
          description: "",
        },
  );
  sessionOpen.value = true;
}
async function saveSession() {
  const payload = {
    ...sessionForm,
    startTime: sessionForm.startTime || null,
    endTime: sessionForm.endTime || null,
  };
  const result = sessionForm.id
    ? await run(
        () => put(`/api/trainings/${sessionForm.id}`, payload),
        "培训已更新",
      )
    : await run(() => post("/api/trainings", payload), "培训已创建");
  if (result) {
    sessionOpen.value = false;
    await loadSessions();
  }
}
async function archiveSession() {
  const target = deleteTarget.value;
  if (!target) return;
  await run(() => del(`/api/trainings/${target.id}`), "培训已归档");
  deleteTarget.value = null;
  selected.value = null;
  await loadSessions();
}
function openParticipant(item?: TrainingParticipant) {
  Object.assign(
    participantForm,
    item
      ? { ...item }
      : {
          id: null,
          studentNo: "",
          name: participants.value.length ? "" : selected.value?.speaker || "",
          durationHours: defaultDuration(selected.value),
          remark: "",
        },
  );
  participantOpen.value = true;
}
async function saveParticipant() {
  const session = selected.value;
  if (!session) return;
  const path = `/api/trainings/${session.id}/participants`;
  const result = participantForm.id
    ? await run(
        () => put(`${path}/${participantForm.id}`, participantForm),
        "参与记录已更新",
      )
    : await run(() => post(path, participantForm), "参与记录已添加");
  if (result) {
    participantOpen.value = false;
    await loadSessions();
  }
}
async function deleteParticipant() {
  const session = selected.value;
  const target = participantDeleteTarget.value;
  if (!session || !target) return;
  await run(
    () =>
      del(`/api/trainings/${session.id}/participants/${target.id}`),
    "参与记录已删除",
  );
  participantDeleteTarget.value = null;
  await loadSessions();
}
function pickImport(e: Event) {
  importFile.value = (e.target as HTMLInputElement).files?.[0] || null;
}
async function importParticipants() {
  const session = selected.value;
  if (!importFile.value || !session) return;
  const body = new FormData();
  body.append("file", importFile.value);
  if (
    await run(
      () =>
        post(`/api/trainings/${session.id}/participants/import`, body),
      "名单导入完成",
    )
  ) {
    importOpen.value = false;
    importFile.value = null;
    await loadSessions();
  }
}
async function downloadTemplate() {
  const session = selected.value;
  if (!session) return;
  downloadBlob(
    await get<Blob>(
      `/api/trainings/${session.id}/participants/import-template`,
    ),
    `培训名单导入模板_${session.title}.xlsx`,
  );
}
async function downloadSession() {
  const session = selected.value;
  if (!session) return;
  downloadBlob(
    await get<Blob>(`/api/trainings/${session.id}/export`),
    `培训名单_${session.title}.xlsx`,
  );
}
async function exportSummary() {
  const p = new URLSearchParams(filters);
  downloadBlob(
    await get(`/api/trainings/export?${p}`),
    `培训统计_${filters.from}_${filters.to}.xlsx`,
  );
}
const hours = (v: number | string | null | undefined) =>
  Number(v || 0)
    .toFixed(2)
    .replace(/\.00$/, "");
const shortDate = (v: string) => v?.slice(5).replace("-", "/");
const timeRange = (v: Pick<TrainingSession, "startTime" | "endTime">) =>
  v.startTime && v.endTime
    ? `${v.startTime.slice(0, 5)}–${v.endTime.slice(0, 5)}`
    : "未设置";
const sessionMeta = (v: TrainingSession) =>
  [v.speaker, v.location, timeRange(v)].filter(Boolean).join(" · ");
function defaultDuration(v: TrainingSession | null) {
  if (!v?.startTime || !v?.endTime) return 0;
  const [sh, sm] = v.startTime.split(":").map(Number),
    [eh, em] = v.endTime.split(":").map(Number);
  return Math.max(0, Number(((eh * 60 + em - sh * 60 - sm) / 60).toFixed(2)));
}
function localDate(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}
</script>

<template>
  <div class="page-stack terms-page">
    <PageHeader
      title="学期与结算"
      description="按学期组织业务数据，并在交接前生成不可变结算快照。"
      ><template #actions
        ><button class="button primary" @click="openCreate">
          <Plus />新建学期
        </button></template
      ></PageHeader
    >
    <div class="term-layout">
      <aside class="term-list panel">
        <div class="section-heading">
          <div>
            <p class="eyebrow">TERMS</p>
            <h2>学期</h2>
          </div>
        </div>
        <button
          v-for="term in state.terms"
          :key="term.id"
          class="term-list-item"
          :class="{ active: selected?.id === term.id }"
          @click="select(term)"
        >
          <span
            ><strong>{{ term.name }}</strong
            ><small>{{ term.startDate }} 至 {{ term.endDate }}</small></span
          ><StatusBadge
            :label="statusLabel(term.status)"
            :tone="statusTone(term.status)"
          /></button
        ><EmptyState v-if="!state.terms.length" title="还没有学期" />
      </aside>
      <main v-if="selected" class="term-detail">
        <section class="term-hero" :data-status="selected.status">
          <div>
            <p>{{ selected.code }}</p>
            <h2>{{ selected.name }}</h2>
            <span>{{ selected.startDate }} 至 {{ selected.endDate }}</span>
          </div>
          <div class="term-hero-actions">
            <button
              v-if="selected.status === 'DRAFT'"
              class="button secondary"
              @click="editSelected"
            >
              <Pencil />编辑</button
            ><button
              v-if="selected.status === 'DRAFT'"
              class="button primary"
              @click="activate"
            >
              <Play />激活学期</button
            ><button
              v-if="selected.status === 'ACTIVE'"
              class="button warning"
              @click="beginSettlement"
            >
              <Calculator />开始结算</button
            ><button
              v-if="selected.status === 'SEALED' && isAdmin"
              class="button secondary"
              @click="reopenOpen = true"
            >
              <LockOpen />重新打开
            </button>
          </div>
        </section>
        <section
          v-if="selected.status === 'SETTLING'"
          class="settlement-workspace"
        >
          <div class="settlement-steps">
            <div class="done">
              <span>1</span>
              <p>处理阻塞项</p>
            </div>
            <i></i>
            <div :class="{ done: preview }">
              <span>2</span>
              <p>核对结算预览</p>
            </div>
            <i></i>
            <div :class="{ done: selected.status === 'SEALED' }">
              <span>3</span>
              <p>管理员封存</p>
            </div>
          </div>
          <section class="panel">
            <div class="section-heading">
              <div>
                <p class="eyebrow">PREFLIGHT</p>
                <h2>封存预检</h2>
              </div>
              <button class="button secondary small" @click="loadPreflight">
                <RefreshCw />重新检查
              </button>
            </div>
            <div v-if="preflight?.blocked" class="issue-list">
              <article v-for="issue in preflight.issues" :key="issue.code">
                <TriangleAlert />
                <div>
                  <strong>{{ issue.message }}</strong
                  ><span>{{ issue.count }} 项</span>
                </div>
              </article>
            </div>
            <div v-else class="preflight-clear">
              <CheckCircle2 />
              <div>
                <strong>没有阻塞项</strong>
                <p>可以生成结算预览并准备封存。</p>
              </div>
            </div>
          </section>
          <section class="panel">
            <div class="section-heading">
              <div>
                <p class="eyebrow">SETTLEMENT PREVIEW</p>
                <h2>结算预览</h2>
              </div>
              <button
                class="button secondary small"
                :disabled="preflight?.blocked"
                @click="generatePreview"
              >
                <FileSearch />生成预览
              </button>
            </div>
            <EmptyState v-if="!preview" title="尚未生成结算预览" /><template
              v-else
              ><div class="settlement-summary">
                <div v-for="metric in summaryMetrics" :key="metric.label">
                  <span>{{ metric.label }}</span
                  ><strong>{{ metric.value }}</strong>
                </div>
              </div>
              <div class="table-shell compact-table">
                <table>
                  <thead>
                    <tr>
                      <th>成员</th>
                      <th>角色</th>
                      <th>值班</th>
                      <th>培训</th>
                      <th>合计</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr
                      v-for="member in preview.members"
                      :key="member.studentNo"
                    >
                      <td>
                        <strong>{{ member.name }}</strong
                        ><small>{{ member.studentNo }}</small>
                      </td>
                      <td>{{ roleLabel(member.role) }}</td>
                      <td>{{ minutes(member.attendanceMinutes) }}</td>
                      <td>{{ minutes(member.trainingMinutes) }}</td>
                      <td>
                        <strong>{{ minutes(member.totalMinutes) }}</strong>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div></template
            >
          </section>
          <div class="seal-bar">
            <div>
              <LockKeyhole /><span
                ><strong>封存后本学期数据只读</strong
                ><small>重新打开需要管理员填写原因并自动备份。</small></span
              >
            </div>
            <button
              v-if="isAdmin"
              class="button primary"
              :disabled="!preview || preflight?.blocked"
              @click="sealOpen = true"
            >
              <ShieldCheck />确认封存</button
            ><span v-else>仅管理员可以执行最终封存</span>
          </div>
        </section>
        <section v-else-if="selected.status === 'SEALED'" class="panel">
          <div class="section-heading">
            <div>
              <p class="eyebrow">SEALED VERSIONS</p>
              <h2>结算版本</h2>
            </div>
          </div>
          <div class="version-list">
            <article v-for="version in versions" :key="version.id">
              <span class="version-number">V{{ version.version }}</span>
              <div>
                <strong>{{
                  version.status === "SEALED" ? "当前封存版本" : "历史版本"
                }}</strong>
                <p>
                  {{
                    version.sealedAt?.replace("T", " ") || version.preparedAt
                  }}
                  · {{ version.memberCount }} 位成员
                </p>
                <small>校验值 {{ version.sourceDigest.slice(0, 12) }}</small>
              </div>
              <StatusBadge
                :label="version.status === 'SEALED' ? '有效' : '已被替代'"
                :tone="version.status === 'SEALED' ? 'success' : 'neutral'"
              />
            </article>
          </div>
        </section>
      </main>
      <EmptyState v-else title="请选择一个学期" />
    </div>
    <ModalDialog
      :open="editorOpen"
      :title="form.id ? '编辑学期' : '新建学期'"
      size="sm"
      @close="editorOpen = false"
      ><div class="form-grid">
        <label class="field"
          ><span>学期编码</span
          ><input
            v-model.trim="form.code"
            placeholder="例如 2026-autumn" /></label
        ><label class="field"
          ><span>学期名称</span
          ><input
            v-model.trim="form.name"
            placeholder="例如 2026-2027 第一学期" /></label
        ><label class="field"
          ><span>开始日期</span
          ><input v-model="form.startDate" type="date" /></label
        ><label class="field"
          ><span>结束日期</span><input v-model="form.endDate" type="date"
        /></label>
      </div>
      <template #footer
        ><button class="button secondary" @click="editorOpen = false">
          取消</button
        ><button class="button primary" @click="saveTerm">
          保存
        </button></template
      ></ModalDialog
    >
    <ConfirmDialog
      :open="sealOpen"
      title="封存学期"
      message="系统将先自动备份，再生成不可变结算快照并锁定本学期数据。"
      confirm-label="备份并封存"
      @cancel="sealOpen = false"
      @confirm="seal"
    />
    <ConfirmDialog
      :open="reopenOpen"
      title="重新打开学期"
      message="旧结算版本会保留，新封存将生成下一个版本。"
      confirm-label="备份并重新打开"
      require-reason
      @cancel="reopenOpen = false"
      @confirm="reopen"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import {
  Calculator,
  CheckCircle2,
  FileSearch,
  LockKeyhole,
  LockOpen,
  Pencil,
  Play,
  Plus,
  RefreshCw,
  ShieldCheck,
  TriangleAlert,
} from "@lucide/vue";
import PageHeader from "../../shared/ui/PageHeader.vue";
import StatusBadge from "../../shared/ui/StatusBadge.vue";
import EmptyState from "../../shared/ui/EmptyState.vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
import { get, post, put } from "../../shared/api";
import { useTerms } from "../../shared/composables/useTerms";
import { useSession } from "../../app/session";
import { useAsyncTask } from "../../shared/composables/useAsyncTask";
import type { AcademicTerm } from "../../shared/types";
const { state, selectedTerm, loadTerms } = useTerms();
const { user } = useSession();
const { run } = useAsyncTask();
const selected = ref<AcademicTerm | null>(null);
const preflight = ref<any>(null);
const preview = ref<any>(null);
const versions = ref<any[]>([]);
const editorOpen = ref(false);
const sealOpen = ref(false);
const reopenOpen = ref(false);
const form = reactive<any>({
  id: null,
  code: "",
  name: "",
  startDate: "",
  endDate: "",
});
const isAdmin = computed(() => user.value?.role === "ADMIN");
const summaryMetrics = computed(() =>
  preview.value
    ? [
        { label: "值班记录", value: preview.value.summary.attendanceRecords },
        {
          label: "有效记录",
          value: preview.value.summary.validAttendanceRecords,
        },
        { label: "培训场次", value: preview.value.summary.trainingSessions },
        { label: "固定排班", value: preview.value.summary.scheduleSlots },
        {
          label: "排班调整",
          value:
            Number(preview.value.summary.scheduleExceptions) +
            Number(preview.value.summary.shiftReassignments),
        },
        { label: "维修事务", value: preview.value.summary.repairs },
      ]
    : [],
);
onMounted(async () => {
  await loadTerms(true);
  select(selectedTerm.value || state.terms[0] || null);
});
async function select(term: AcademicTerm | null) {
  selected.value = term;
  preflight.value = null;
  preview.value = null;
  versions.value = [];
  if (!term) return;
  if (term.status === "SETTLING") await loadPreflight();
  if (term.status === "SEALED")
    versions.value = await get(`/api/terms/${term.id}/settlements`);
}
function openCreate() {
  Object.assign(form, {
    id: null,
    code: "",
    name: "",
    startDate: "",
    endDate: "",
  });
  editorOpen.value = true;
}
function editSelected() {
  Object.assign(form, selected.value);
  editorOpen.value = true;
}
async function saveTerm() {
  const action = form.id
    ? () => put(`/api/terms/${form.id}`, form)
    : () => post("/api/terms", form);
  const saved = await run(action, "学期已保存");
  if (saved) {
    editorOpen.value = false;
    await loadTerms(true);
    select(state.terms.find((i) => i.id === (saved as any).id) || null);
  }
}
async function activate() {
  if (!selected.value) return;
  const saved = await run(
    () =>
      post(`/api/terms/${selected.value!.id}/activate`, {
        copyPreviousSchedule: false,
      }),
    "学期已激活",
  );
  if (saved) {
    await loadTerms(true);
    select(saved as AcademicTerm);
  }
}
async function beginSettlement() {
  if (!selected.value) return;
  const saved = await run(
    () => post(`/api/terms/${selected.value!.id}/settling`),
    "已进入学期结算",
  );
  if (saved) {
    await loadTerms(true);
    select(saved as AcademicTerm);
  }
}
async function loadPreflight() {
  if (selected.value)
    preflight.value = await get(
      `/api/terms/${selected.value.id}/settlement/preflight`,
    );
}
async function generatePreview() {
  if (selected.value)
    preview.value = await run(() =>
      post(`/api/terms/${selected.value!.id}/settlement/preview`),
    );
}
async function seal() {
  if (!selected.value) return;
  const result = await run(
    () => post(`/api/terms/${selected.value!.id}/seal`),
    "学期已封存",
  );
  sealOpen.value = false;
  if (result) {
    await loadTerms(true);
    select((result as any).term);
  }
}
async function reopen(reason: string) {
  if (!selected.value) return;
  const term = await run(
    () => post(`/api/terms/${selected.value!.id}/reopen`, { reason }),
    "学期已重新打开",
  );
  reopenOpen.value = false;
  if (term) {
    await loadTerms(true);
    select(term as AcademicTerm);
  }
}
const statusLabel = (v: string) =>
  (
    ({
      DRAFT: "草稿",
      ACTIVE: "进行中",
      SETTLING: "结算中",
      SEALED: "已封存",
    }) as any
  )[v] || v;
const statusTone = (v: string) =>
  v === "ACTIVE"
    ? "success"
    : v === "SETTLING"
      ? "warning"
      : v === "SEALED"
        ? "neutral"
        : "info";
const roleLabel = (v: string) =>
  (
    ({
      MEMBER: "成员",
      MINISTER: "部长",
      PRESIDENT: "会长",
      ADMIN: "管理员",
    }) as any
  )[v] || v;
const minutes = (v: number) =>
  `${(Number(v || 0) / 60).toFixed(v % 60 ? 1 : 0)} 小时`;
</script>

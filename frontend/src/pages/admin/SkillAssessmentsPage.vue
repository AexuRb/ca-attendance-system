<template>
  <ExpandedWorkspaceShell class="skill-assessments" current-page="skill-assessments">
    <template #heading>
      <h1>技能考核</h1>
    </template>

    <section class="skill-assessment-members" aria-labelledby="skill-assessment-heading">
      <header class="skill-assessment-heading">
        <h2 id="skill-assessment-heading">今日签到成员</h2>
        <span v-if="roster"><time :datetime="roster.date">{{ roster.date }}</time> · 共 {{ roster.items.length }} 人</span>
      </header>
      <div v-if="loadError" class="inline-alert danger" role="alert">
        <span>{{ loadError }}</span>
        <button class="button secondary small" type="button" @click="load">重试</button>
      </div>
      <LoadingBlock v-else-if="loading && !roster" />
      <EmptyState v-else-if="roster && !roster.items.length" title="今天暂无可考核成员"
        description="普通成员在签到台签到后，可在这里登记考核结果。" />
      <template v-if="roster?.items.length">
        <div class="skill-assessment-columns" aria-hidden="true">
          <span>姓名 / 学号</span>
          <span class="skill-assessment-result-columns">
            <span v-for="field in memberAssessments" :key="field.key">{{ field.label }}</span>
          </span>
          <span class="skill-assessment-action-column">操作</span>
        </div>
        <ul class="skill-assessment-list">
          <li v-for="member in roster.items" :key="member.id" class="skill-assessment-row">
            <div class="skill-assessment-person">
              <span class="skill-assessment-avatar" aria-hidden="true">{{ member.name.slice(0, 1) }}</span>
              <div>
                <strong>{{ member.name }}</strong>
                <small>{{ member.studentNo }}</small>
              </div>
            </div>
            <dl class="skill-assessment-results">
              <div v-for="field in memberAssessments" :key="field.key">
                <dt>{{ field.label }}</dt>
                <dd class="mw-badge" :data-tone="member[field.key] === 'PASSED' ? 'success' : 'neutral'">
                  {{ member[field.key] === "PASSED" ? "通过" : "未通过" }}
                </dd>
              </div>
            </dl>
            <button data-button-kind="soft" class="button secondary skill-assessment-edit" type="button"
              :aria-label="`登记 ${member.name} 的考核结果`" :disabled="loading || Boolean(loadError)"
              @click="openEditor(member)">登记结果</button>
          </li>
        </ul>
      </template>
    </section>
  </ExpandedWorkspaceShell>

  <ModalDialog :open="Boolean(editor)" title="登记考核结果" :busy="saveTask.busy.value"
    :error="saveTask.error.value" @close="requestClose">
    <dl v-if="editor" class="dialog-summary">
      <div><dt>成员</dt><dd>{{ editor.member.name }}</dd></div>
      <div><dt>学号</dt><dd>{{ editor.member.studentNo }}</dd></div>
    </dl>
    <form id="skill-assessment-form" class="form-grid two" @submit.prevent="save">
      <label v-for="field in memberAssessments" :key="field.key" class="field">
        <span>{{ field.label }}</span>
        <select v-model="draft[field.key]" :name="field.key">
          <option value="NOT_PASSED">未通过</option>
          <option value="PASSED">通过</option>
        </select>
      </label>
    </form>
    <template #footer>
      <button class="button secondary" type="button" :disabled="saveTask.busy.value" @click="requestClose">取消</button>
      <button data-button-kind="action" class="button primary" type="submit" form="skill-assessment-form"
        :disabled="!changed || saveTask.busy.value">保存结果</button>
    </template>
  </ModalDialog>
  <ConfirmDialog :open="unsaved.confirmOpen.value" title="放弃未保存修改" message="考核结果尚未保存。"
    confirm-label="放弃修改" @cancel="unsaved.cancel" @confirm="unsaved.discard" />
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import ExpandedWorkspaceShell from "../../layouts/ExpandedWorkspaceShell.vue";
import EmptyState from "../../shared/ui/EmptyState.vue";
import LoadingBlock from "../../shared/ui/LoadingBlock.vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
import { get, patch } from "../../shared/api";
import { useAsyncTask } from "../../shared/composables/useAsyncTask";
import { useLatestRequest } from "../../shared/composables/useLatestRequest";
import { useUnsavedChanges } from "../../shared/composables/useUnsavedChanges";
import { memberAssessments, type AssessmentStatus } from "../../features/members/memberDirectory";
import "../../styles/workspace.css";
import "../../features/today/overview-presentation.css";

type AssessmentResults = Record<(typeof memberAssessments)[number]["key"], AssessmentStatus>;
type AssessmentMember = AssessmentResults & { id: number; name: string; studentNo: string };
type AssessmentRoster = { date: string; items: AssessmentMember[] };

const roster = ref<AssessmentRoster | null>(null);
const editor = ref<{ member: AssessmentMember; date: string } | null>(null);
const draft = reactive<AssessmentResults>({ disassemblyAssessment: "NOT_PASSED", osReinstallAssessment: "NOT_PASSED" });
const request = useLatestRequest();
const { loading, error: loadError } = request;
const saveTask = useAsyncTask();
const rosterRefreshInterval = 30_000;
let refreshTimer: number | undefined;
const changed = computed(() => Boolean(editor.value && memberAssessments.some(({ key }) => draft[key] !== editor.value?.member[key])));
const unsaved = useUnsavedChanges(() => changed.value);

async function load() {
  if (editor.value || loading.value) return;
  const result = await request.run(signal => get<AssessmentRoster>("/api/skill-assessments/today", { signal }), "今日名单读取失败");
  if (result) roster.value = result;
}

function openEditor(member: AssessmentMember) {
  if (!roster.value) return;
  saveTask.error.value = "";
  editor.value = { member, date: roster.value.date };
  draft.disassemblyAssessment = member.disassemblyAssessment;
  draft.osReinstallAssessment = member.osReinstallAssessment;
}

function requestClose() {
  if (saveTask.busy.value) return;
  unsaved.request(() => {
    const refreshAfterError = Boolean(saveTask.error.value);
    editor.value = null;
    if (refreshAfterError) void load();
  });
}

async function save() {
  const target = editor.value;
  if (!target || !changed.value || saveTask.busy.value) return;
  const result = await saveTask.run(() => patch<AssessmentMember>(`/api/skill-assessments/${target.member.id}`, {
    date: target.date,
    ...draft,
    previousDisassemblyAssessment: target.member.disassemblyAssessment,
    previousOsReinstallAssessment: target.member.osReinstallAssessment,
  }), "考核结果已保存");
  if (!result) return;
  editor.value = null;
  if (roster.value?.date === target.date) {
    roster.value.items = roster.value.items.map(member => member.id === result.id ? result : member);
  }
}

function refreshVisibleRoster() {
  if (document.visibilityState === "visible") void load();
}

onMounted(() => {
  void load();
  refreshTimer = window.setInterval(refreshVisibleRoster, rosterRefreshInterval);
  document.addEventListener("visibilitychange", refreshVisibleRoster);
});
onBeforeUnmount(() => {
  window.clearInterval(refreshTimer);
  document.removeEventListener("visibilitychange", refreshVisibleRoster);
});
</script>

<style scoped>
html:root[data-appearance] .skill-assessments :deep(.overview-content) {
  padding: 24px 0 20px;
}
html:root[data-appearance] .skill-assessments :deep(:is(.overview-page-heading, .overview-workspace)) {
  width: calc(100% - 48px);
  margin-inline: auto;
}
html:root[data-appearance] .skill-assessments :deep(.overview-page-heading) {
  margin-bottom: 16px;
}
html:root[data-appearance] .skill-assessments :deep(.overview-page-heading h1) {
  font: 500 28px/38px var(--mw-font);
}
.skill-assessment-edit {
  white-space: nowrap;
}
.skill-assessment-members {
  container: skill-assessment / inline-size;
  min-width: 0;
}
.skill-assessment-heading {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--mw-line);
}
.skill-assessment-heading h2 {
  margin: 0;
  font: 500 16px/24px var(--mw-font);
}
.skill-assessment-heading span,
.skill-assessment-person small {
  color: var(--mw-muted);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}
.skill-assessment-list {
  margin: 0;
  padding: 0;
  list-style: none;
}
.skill-assessment-columns,
.skill-assessment-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(240px, 1fr) 110px;
  align-items: center;
  gap: 12px 16px;
  padding-inline: 12px;
  border-bottom: 1px solid var(--mw-line);
}
.skill-assessment-columns {
  min-height: 48px;
  background: var(--mw-paper);
  color: var(--mw-muted);
  font: 400 14px/22px var(--mw-font);
}
.skill-assessment-row {
  min-height: 84px;
  padding-block: 12px;
}
.skill-assessment-row:hover {
  background: var(--overview-hover);
}
.skill-assessment-person {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  overflow-wrap: anywhere;
}
.skill-assessment-person strong,
.skill-assessment-person small {
  display: block;
}
.skill-assessment-person strong {
  font: 500 18px/26px var(--mw-font);
}
.skill-assessment-avatar {
  display: grid;
  place-items: center;
  flex: 0 0 30px;
  height: 30px;
  border: 1px solid var(--mw-line);
  border-radius: 8px;
  background: var(--mw-paper);
  font-size: 13px;
}
.skill-assessment-result-columns,
.skill-assessment-results {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
  margin: 0;
}
.skill-assessment-results dt {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
  font-size: 13px;
  color: var(--mw-muted);
}
.skill-assessment-results dd {
  margin: 0;
}
.skill-assessment-action-column,
.skill-assessment-edit {
  justify-self: end;
}
@container skill-assessment (max-width: 650px) {
  .skill-assessment-columns {
    display: none;
  }
  .skill-assessment-row {
    grid-template-columns: minmax(0, 1fr) auto;
    min-height: 0;
    padding-inline: 0;
    gap: 10px;
  }
  .skill-assessment-results {
    grid-column: 1 / -1;
    grid-row: 2;
    padding-inline-start: 40px;
  }
  .skill-assessment-results dt {
    position: static;
    width: auto;
    height: auto;
    overflow: visible;
    clip-path: none;
    white-space: normal;
    margin-bottom: 4px;
    font-size: 12px;
  }
  .skill-assessment-edit {
    grid-column: 2;
    grid-row: 1;
  }
}
@media (max-width: 550px) {
  html:root[data-appearance] .skill-assessments :deep(:is(.overview-page-heading, .overview-workspace)) {
    width: calc(100% - 32px);
  }
  html:root[data-appearance] .skill-assessments :deep(.overview-page-heading h1) {
    font-size: 24px;
  }
}
</style>

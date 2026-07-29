<template>
  <div class="page-stack">
    <PageHeader
      eyebrow="DUTY / SCHEDULE"
      title="排班管理"
      description="按已设置的值班星期和时间段维护部长固定周表。"
    >
      <template #actions>
        <button
          class="button secondary"
          :disabled="busy"
          @click="downloadImportTemplate"
        >
          <Download />导入模板
        </button>
        <button
          class="button secondary"
          :disabled="busy"
          @click="importOpen = true"
        >
          <Upload />批量导入
        </button>
        <button class="button secondary" :disabled="busy" @click="loadBase">
          <RefreshCw />刷新
        </button>
        <button
          class="button primary"
          :disabled="busy || !periods.length"
          @click="openFixed(null)"
        >
          <Plus />新增排班
        </button>
      </template>
    </PageHeader>

    <LoadingBlock v-if="busy && !slots.length" />
    <FixedScheduleBoard
      v-else
      :slots="slots"
      :periods="periods"
      :weekdays="weekdays"
      @add="openFixedForPeriod"
      @edit="openFixed"
      @archive="deleteFixed"
    />

    <ModalDialog
      :open="editorOpen"
      :title="fixedForm.id ? '编辑固定排班' : '新增固定排班'"
      size="lg"
      @close="editorOpen = false"
    >
      <div class="form-grid two">
        <label class="field">
          <span>星期</span>
          <select v-model.number="fixedForm.weekday">
            <option
              v-for="day in weekdays"
              :key="day.value"
              :value="day.value"
              :disabled="!day.enabled"
            >
              {{ day.label }}{{ day.enabled ? "" : "（未开放）" }}
            </option>
          </select>
        </label>
        <label class="field">
          <span>值班时段</span>
          <select v-model="fixedForm.period">
            <option
              v-for="period in periods"
              :key="periodKey(period)"
              :value="periodKey(period)"
            >
              {{ shortTime(period.startTime) }}–{{ shortTime(period.endTime) }}
            </option>
          </select>
        </label>
        <label class="field">
          <span>标题</span>
          <input v-model="fixedForm.title" />
        </label>
        <label class="field">
          <span>地点</span>
          <input v-model="fixedForm.location" />
        </label>
        <div class="field span-2">
          <span>排班人员</span>
          <ScheduleAssigneePicker
            v-model="fixedForm.assignees"
            :candidates="assigneeCandidates"
          />
        </div>
        <div class="field span-2 schedule-visibility-field">
          <span>签到台展示</span>
          <label class="period-enabled-toggle schedule-visibility-toggle">
            <input v-model="fixedForm.enabled" type="checkbox" />
            <span>{{ fixedForm.enabled ? "显示" : "隐藏" }}</span>
          </label>
          <small>
            隐藏后保留排班内容，但不会出现在签到台今日和本周排班中。
          </small>
        </div>
        <label class="field span-2">
          <span>备注</span>
          <textarea v-model="fixedForm.note" rows="2" />
        </label>
      </div>
      <template #footer>
        <button class="button secondary" @click="editorOpen = false">
          取消
        </button>
        <button
          class="button primary"
          :disabled="busy || !fixedForm.period || !fixedForm.title.trim()"
          @click="saveFixed"
        >
          保存
        </button>
      </template>
    </ModalDialog>

    <ScheduleImportDialog
      :open="importOpen"
      @close="importOpen = false"
      @imported="loadBase"
    />

    <ConfirmDialog
      :open="Boolean(deleteTarget)"
      title="归档固定排班"
      :message="`归档 ${deleteTarget?.weekdayName || ''} ${shortTime(deleteTarget?.startTime)} 的固定排班。`"
      confirm-label="确认归档"
      @cancel="deleteTarget = null"
      @confirm="confirmDeleteFixed"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { Download, Plus, RefreshCw, Upload } from "@lucide/vue";
import PageHeader from "../../shared/ui/PageHeader.vue";
import LoadingBlock from "../../shared/ui/LoadingBlock.vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
import FixedScheduleBoard from "./schedule/FixedScheduleBoard.vue";
import ScheduleImportDialog from "./schedule/ScheduleImportDialog.vue";
import ScheduleAssigneePicker from "../../features/schedule/ScheduleAssigneePicker.vue";
import { del, downloadBlob, get, post, put } from "../../shared/api";
import { useAsyncTask } from "../../shared/composables/useAsyncTask";
import {
  normalizeDutyWeekdays,
  type DutyWeekdaySetting,
} from "../../features/settings/dutyWeekdays";
import type { DutyPeriod } from "../../features/settings/dutyPeriods";
import type { ScheduleAssigneeOption } from "../../features/schedule/scheduleAssignees";
import { schedulePayload } from "../../features/schedule/scheduleEditor";
import type {
  ScheduleEditorForm,
  ScheduleSlot,
} from "../../features/schedule/scheduleTypes";

const { busy, run } = useAsyncTask();
const editorOpen = ref(false);
const importOpen = ref(false);
const deleteTarget = ref<ScheduleSlot | null>(null);
const slots = ref<ScheduleSlot[]>([]);
const periods = ref<DutyPeriod[]>([]);
const dutyWeekdays = ref<DutyWeekdaySetting[]>([]);
const assigneeCandidates = ref<ScheduleAssigneeOption[]>([]);

const fallbackWeekdays = [1, 2, 3, 4, 5, 6, 7].map((value, index) => ({
  value,
  label: `星期${"一二三四五六日"[index]}`,
  short: `周${"一二三四五六日"[index]}`,
  enabled: true,
}));

const weekdays = computed(() =>
  dutyWeekdays.value.length
    ? dutyWeekdays.value.map((day) => ({
        value: day.weekday,
        label: day.weekday_name,
        short: `周${"一二三四五六日"[day.weekday - 1]}`,
        enabled: day.enabled,
      }))
    : fallbackWeekdays,
);

const fixedForm = reactive<ScheduleEditorForm>({
  id: null,
  weekday: 1,
  period: "",
  title: "部长值班",
  location: "协会办公室",
  assignees: [] as ScheduleAssigneeOption[],
  enabled: true,
  note: "",
});

onMounted(loadBase);

async function loadBase() {
  const result = await run(() =>
    Promise.all([
      get<ScheduleSlot[]>("/api/schedules"),
      get<DutyPeriod[]>("/api/settings/duty-periods"),
      get<DutyWeekdaySetting[]>("/api/settings/weekdays"),
      get<ScheduleAssigneeOption[]>("/api/schedules/assignee-candidates"),
    ]),
  );
  if (!result) return;
  const [scheduleSlots, dutyPeriods, weekdaySettings, managerCandidates] =
    result;
  slots.value = scheduleSlots;
  periods.value = dutyPeriods.filter((period) => period.enabled !== false);
  dutyWeekdays.value = normalizeDutyWeekdays(weekdaySettings);
  assigneeCandidates.value = managerCandidates;
  if (
    !periods.value.some(
      (period) => periodKey(period) === fixedForm.period,
    )
  ) {
    fixedForm.period = periods.value[0] ? periodKey(periods.value[0]) : "";
  }
}

function openFixed(
  item: ScheduleSlot | null,
  weekday = weekdays.value.find((day) => day.enabled)?.value || 1,
  period?: string,
) {
  Object.assign(
    fixedForm,
    item
      ? {
          id: item.id,
          weekday: item.weekday,
          period: periodKey(item),
          title: item.title,
          location: item.location || "",
          assignees: item.assignees
            .filter(
              (
                assignee,
              ): assignee is typeof assignee & { studentNo: string } =>
                Boolean(assignee.studentNo),
            )
            .map(
              (assignee) =>
                assigneeCandidates.value.find(
                  (candidate) => candidate.studentNo === assignee.studentNo,
                ) || {
                  studentNo: assignee.studentNo,
                  name: assignee.name,
                },
            ),
          enabled: item.enabled !== false,
          note: item.note || "",
        }
      : {
          id: null,
          weekday,
          period: period || (periods.value[0] ? periodKey(periods.value[0]) : ""),
          title: "部长值班",
          location: "协会办公室",
          assignees: [],
          enabled: true,
          note: "",
        },
  );
  editorOpen.value = true;
}

function openFixedForPeriod(weekday: number, period: string) {
  openFixed(null, weekday, period);
}

async function downloadImportTemplate() {
  const blob = await run(
    () => get<Blob>("/api/schedules/import-template"),
    "排班导入模板已下载",
  );
  if (blob) downloadBlob(blob, "部长排班导入模板.xlsx");
}

async function saveFixed() {
  const payload = schedulePayload(fixedForm);
  const saved = fixedForm.id
    ? await run(
        () => put(`/api/schedules/${fixedForm.id}`, payload),
        "排班已更新",
      )
    : await run(() => post("/api/schedules", payload), "排班已新增");
  if (!saved) return;
  editorOpen.value = false;
  await loadBase();
}

function deleteFixed(item: ScheduleSlot) {
  deleteTarget.value = item;
}

async function confirmDeleteFixed() {
  const target = deleteTarget.value;
  if (!target) return;
  const archived = await run(
    () => del(`/api/schedules/${target.id}`),
    "排班已归档",
  );
  if (archived === undefined) return;
  deleteTarget.value = null;
  await loadBase();
}

function periodKey(value: Pick<DutyPeriod, "startTime" | "endTime">) {
  return `${shortTime(value.startTime)}-${shortTime(value.endTime)}`;
}

function shortTime(value?: string) {
  return value?.slice(0, 5) || "";
}
</script>

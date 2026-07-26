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
        <label class="field span-2">
          <span>排班人员学号</span>
          <input
            v-model="fixedForm.assignees"
            placeholder="多个学号用逗号分隔"
          />
          <small>仅可填写部长、会长或管理员账号</small>
        </label>
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
import { del, downloadBlob, get, post, put } from "../../shared/api";
import { useAsyncTask } from "../../shared/composables/useAsyncTask";
import { normalizeDutyWeekdays } from "../../features/settings/dutyWeekdays";

const { busy, run } = useAsyncTask();
const editorOpen = ref(false);
const importOpen = ref(false);
const deleteTarget = ref<any>(null);
const slots = ref<any[]>([]);
const periods = ref<any[]>([]);
const dutyWeekdays = ref<any[]>([]);

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

const fixedForm = reactive({
  id: null as number | null,
  weekday: 1,
  period: "",
  title: "部长值班",
  location: "协会办公室",
  assignees: "",
  note: "",
});

onMounted(loadBase);

async function loadBase() {
  const result = await run(() =>
    Promise.all([
      get<any[]>("/api/schedules"),
      get<any[]>("/api/settings/duty-periods"),
      get<any[]>("/api/settings/weekdays"),
    ]),
  );
  if (!result) return;
  const [scheduleSlots, dutyPeriods, weekdaySettings] = result;
  slots.value = scheduleSlots;
  periods.value = dutyPeriods;
  dutyWeekdays.value = normalizeDutyWeekdays(weekdaySettings);
  if (!fixedForm.period && dutyPeriods[0]) {
    fixedForm.period = periodKey(dutyPeriods[0]);
  }
}

function openFixed(
  item: any,
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
            .map((assignee: any) => assignee.studentNo)
            .filter(Boolean)
            .join(","),
          note: item.note || "",
        }
      : {
          id: null,
          weekday,
          period: period || (periods.value[0] ? periodKey(periods.value[0]) : ""),
          title: "部长值班",
          location: "协会办公室",
          assignees: "",
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
  const [startTime, endTime] = fixedForm.period.split("-");
  const payload = {
    weekday: fixedForm.weekday,
    startTime,
    endTime,
    title: fixedForm.title,
    location: fixedForm.location,
    note: fixedForm.note,
    enabled: true,
    assignees: fixedForm.assignees
      .split(/[,，\s]+/)
      .filter(Boolean)
      .map((studentNo) => ({ studentNo, name: null })),
  };
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

function deleteFixed(item: any) {
  deleteTarget.value = item;
}

async function confirmDeleteFixed() {
  if (!deleteTarget.value) return;
  const archived = await run(
    () => del(`/api/schedules/${deleteTarget.value.id}`),
    "排班已归档",
  );
  if (archived === undefined) return;
  deleteTarget.value = null;
  await loadBase();
}

function periodKey(value: any) {
  return `${shortTime(value.startTime)}-${shortTime(value.endTime)}`;
}

function shortTime(value?: string) {
  return value?.slice(0, 5) || "";
}
</script>

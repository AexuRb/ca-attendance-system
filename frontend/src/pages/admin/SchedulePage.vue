<template>
  <div class="page-stack">
    <PageHeader
      title="排班"
      description="固定周排班、当日例外和直接调班统一生成最终排班。"
    >
      <template #actions
        ><button
          class="button primary"
          :disabled="!selectedTerm"
          @click="openCreate"
        >
          <Plus />{{ activeView === "fixed" ? "新增固定排班" : "新增调整" }}
        </button></template
      >
    </PageHeader>
    <div class="subnav-row">
      <div class="segmented">
        <button
          :class="{ active: activeView === 'effective' }"
          @click="
            activeView = 'effective';
            loadEffective();
          "
        >
          生效排班</button
        ><button
          :class="{ active: activeView === 'fixed' }"
          @click="activeView = 'fixed'"
        >
          固定周表</button
        ><button
          :class="{ active: activeView === 'adjustments' }"
          @click="
            activeView = 'adjustments';
            loadAdjustments();
          "
        >
          例外与调班
        </button>
      </div>
      <label v-if="activeView !== 'fixed'" class="inline-date"
        ><span>查看日期</span
        ><input
          v-model="selectedDate"
          type="date"
          @change="
            activeView === 'effective' ? loadEffective() : loadAdjustments()
          "
      /></label>
    </div>

    <LoadingBlock v-if="busy && !effectiveDay && !slots.length" />
    <template v-else-if="activeView === 'effective'">
      <section v-if="effectiveDay" class="effective-day panel">
        <div class="effective-date">
          <p class="eyebrow">{{ effectiveDay.weekdayName }}</p>
          <h2>{{ effectiveDay.date }}</h2>
          <StatusBadge
            v-if="effectiveDay.cancelled"
            label="全天取消"
            tone="warning"
          />
        </div>
        <EmptyState
          v-if="!effectiveDay.slots.length"
          title="该日没有生效排班"
        />
        <div v-else class="schedule-lanes">
          <article v-for="slot in effectiveDay.slots" :key="slot.key">
            <time
              >{{ hm(slot.startTime) }}<span>{{ hm(slot.endTime) }}</span></time
            >
            <div class="lane-main">
              <div>
                <strong>{{ slot.title }}</strong
                ><StatusBadge
                  v-if="slot.origin === 'TEMPORARY_ADDITION'"
                  label="临时"
                  tone="info"
                />
              </div>
              <p>{{ slot.location || "未设置地点" }}</p>
              <div class="person-chips">
                <span
                  v-for="person in slot.assignees"
                  :key="person.studentNo"
                  :class="{ reassigned: person.reassigned }"
                  :title="
                    person.originalName ? `原值班：${person.originalName}` : ''
                  "
                  >{{ person.name }}</span
                ><em v-if="!slot.assignees.length">待安排</em>
              </div>
            </div>
            <button
              class="icon-button"
              title="直接调班"
              aria-label="直接调班"
              @click="openReassignment(slot)"
            >
              <Repeat2 />
            </button>
          </article>
        </div>
      </section>
      <EmptyState v-else title="请选择学期和日期" />
    </template>

    <template v-else-if="activeView === 'fixed'">
      <div class="week-board">
        <section v-for="day in weekdays" :key="day.value">
          <header>
            <span>{{ day.short }}</span
            ><strong>{{ day.label }}</strong>
          </header>
          <div class="week-day-slots">
            <article
              v-for="slot in slots.filter((i) => i.weekday === day.value)"
              :key="slot.id"
            >
              <time>{{ hm(slot.startTime) }}–{{ hm(slot.endTime) }}</time
              ><strong>{{ slot.title }}</strong>
              <p>
                {{
                  slot.assignees.map((i: any) => i.name).join("、") || "待安排"
                }}
              </p>
              <div class="row-actions">
                <button
                  class="icon-button"
                  title="编辑"
                  @click="openFixed(slot)"
                >
                  <Pencil /></button
                ><button
                  class="icon-button danger-ghost"
                  title="归档"
                  @click="deleteFixed(slot)"
                >
                  <Trash2 />
                </button>
              </div>
            </article>
            <button
              class="add-slot"
              type="button"
              @click="openFixed(null, day.value)"
            >
              <Plus />添加时段
            </button>
          </div>
        </section>
      </div>
    </template>

    <template v-else>
      <div class="adjustment-columns">
        <section class="panel">
          <div class="section-heading">
            <div>
              <p class="eyebrow">EXCEPTIONS</p>
              <h2>排班例外</h2>
            </div>
            <button class="button small secondary" @click="openException">
              <Plus />新增
            </button>
          </div>
          <EmptyState v-if="!exceptions.length" title="当前范围没有排班例外" />
          <div v-else class="adjustment-list">
            <article v-for="item in exceptions" :key="item.id">
              <div>
                <StatusBadge
                  :label="exceptionLabel(item.type)"
                  :tone="item.type === 'DAY_CANCELLED' ? 'warning' : 'info'"
                /><strong
                  >{{ item.date }} {{ hm(item.startTime)
                  }}<template v-if="item.endTime"
                    >–{{ hm(item.endTime) }}</template
                  ></strong
                >
                <p>{{ item.reason }}</p>
                <small v-if="item.assignees?.length">{{
                  item.assignees.map((i: any) => i.name).join("、")
                }}</small>
              </div>
              <div class="row-actions">
                <button class="icon-button" @click="editException(item)">
                  <Pencil /></button
                ><button
                  class="icon-button danger-ghost"
                  @click="removeException(item)"
                >
                  <Trash2 />
                </button>
              </div>
            </article>
          </div>
        </section>
        <section class="panel">
          <div class="section-heading">
            <div>
              <p class="eyebrow">REASSIGNMENTS</p>
              <h2>直接调班</h2>
            </div>
          </div>
          <EmptyState
            v-if="!reassignments.length"
            title="当前范围没有调班记录"
          />
          <div v-else class="adjustment-list">
            <article v-for="item in reassignments" :key="item.id">
              <div>
                <strong
                  >{{ item.date }} · {{ hm(item.startTime) }}–{{
                    hm(item.endTime)
                  }}</strong
                >
                <p>
                  {{ item.original.name }} <ArrowRight />
                  {{ item.replacement.name }}
                </p>
                <small>{{ item.reason }}</small>
              </div>
              <div class="row-actions">
                <button
                  class="icon-button danger-ghost"
                  @click="removeReassignment(item)"
                >
                  <Trash2 />
                </button>
              </div>
            </article>
          </div>
        </section>
      </div>
    </template>

    <ModalDialog
      :open="editor === 'fixed'"
      :title="fixedForm.id ? '编辑固定排班' : '新增固定排班'"
      size="lg"
      @close="editor = ''"
    >
      <div class="form-grid two">
        <label class="field"
          ><span>星期</span
          ><select v-model.number="fixedForm.weekday">
            <option v-for="day in weekdays" :value="day.value">
              {{ day.label }}
            </option>
          </select></label
        ><label class="field"
          ><span>值班时段</span
          ><select v-model="fixedForm.period">
            <option
              v-for="period in periods"
              :value="`${period.startTime}-${period.endTime}`"
            >
              {{ period.startTime }}–{{ period.endTime }}
            </option>
          </select></label
        ><label class="field"
          ><span>标题</span><input v-model="fixedForm.title" /></label
        ><label class="field"
          ><span>地点</span><input v-model="fixedForm.location" /></label
        ><label class="field span-2"
          ><span>排班人员学号</span
          ><input
            v-model="fixedForm.assignees"
            placeholder="多个学号用逗号分隔"
          /><small>仅可填写部长、会长或管理员账号</small></label
        ><label class="field span-2"
          ><span>备注</span><textarea v-model="fixedForm.note" rows="2" />
        </label>
      </div>
      <template #footer
        ><button class="button secondary" @click="editor = ''">取消</button
        ><button class="button primary" @click="saveFixed">
          保存
        </button></template
      >
    </ModalDialog>

    <ModalDialog
      :open="editor === 'exception'"
      :title="exceptionForm.id ? '编辑排班例外' : '新增排班例外'"
      size="lg"
      @close="editor = ''"
    >
      <div class="form-grid two">
        <label class="field"
          ><span>日期</span
          ><input v-model="exceptionForm.date" type="date" /></label
        ><label class="field"
          ><span>例外类型</span
          ><select v-model="exceptionForm.type">
            <option value="DAY_CANCELLED">全天取消</option>
            <option value="PERIOD_CANCELLED">取消时段</option>
            <option value="TEMPORARY_ADDITION">临时增加</option>
            <option value="ASSIGNEE_OVERRIDE">人员覆盖</option>
          </select></label
        ><label v-if="exceptionForm.type !== 'DAY_CANCELLED'" class="field"
          ><span>固定时段</span
          ><select v-model.number="exceptionForm.sourceSlotId">
            <option :value="null">按时间选择</option>
            <option v-for="slot in slotsForDate" :value="slot.id">
              {{ hm(slot.startTime) }}–{{ hm(slot.endTime) }} · {{ slot.title }}
            </option>
          </select></label
        ><label
          v-if="
            exceptionForm.type !== 'DAY_CANCELLED' &&
            !exceptionForm.sourceSlotId
          "
          class="field"
          ><span>值班时段</span
          ><select v-model="exceptionForm.period">
            <option
              v-for="period in periods"
              :value="`${period.startTime}-${period.endTime}`"
            >
              {{ period.startTime }}–{{ period.endTime }}
            </option>
          </select></label
        ><label v-if="exceptionForm.type === 'TEMPORARY_ADDITION'" class="field"
          ><span>标题</span><input v-model="exceptionForm.title" /></label
        ><label
          v-if="
            ['TEMPORARY_ADDITION', 'ASSIGNEE_OVERRIDE'].includes(
              exceptionForm.type,
            )
          "
          class="field span-2"
          ><span>排班人员学号</span
          ><input
            v-model="exceptionForm.assignees"
            placeholder="多个学号用逗号分隔" /></label
        ><label class="field span-2"
          ><span>原因</span
          ><textarea v-model="exceptionForm.reason" rows="3" required />
        </label>
      </div>
      <template #footer
        ><button class="button secondary" @click="editor = ''">取消</button
        ><button
          class="button primary"
          :disabled="!exceptionForm.reason.trim()"
          @click="saveException"
        >
          保存例外
        </button></template
      >
    </ModalDialog>

    <ModalDialog
      :open="editor === 'reassign'"
      title="直接调班"
      size="sm"
      @close="editor = ''"
    >
      <div v-if="reassignForm.slot" class="selected-slot">
        {{ selectedDate }} · {{ hm(reassignForm.slot.startTime) }}–{{
          hm(reassignForm.slot.endTime)
        }}
      </div>
      <label class="field"
        ><span>原值班人员</span
        ><select v-model="reassignForm.originalStudentNo">
          <option
            v-for="person in reassignForm.slot?.assignees || []"
            :value="person.studentNo"
          >
            {{ person.name }}（{{ person.studentNo }}）
          </option>
        </select></label
      ><label class="field"
        ><span>替班人员学号</span
        ><input v-model.trim="reassignForm.replacementStudentNo" /></label
      ><label class="field"
        ><span>调班原因</span
        ><textarea v-model="reassignForm.reason" rows="3" />
      </label>
      <template #footer
        ><button class="button secondary" @click="editor = ''">取消</button
        ><button
          class="button primary"
          :disabled="!reassignForm.reason.trim()"
          @click="saveReassignment"
        >
          确认调班
        </button></template
      >
    </ModalDialog>
    <ConfirmDialog
      :open="Boolean(fixedDeleteTarget)"
      title="归档固定排班"
      :message="`归档 ${fixedDeleteTarget?.weekdayName || ''} ${hm(fixedDeleteTarget?.startTime)} 的固定排班。`"
      confirm-label="确认归档"
      @cancel="fixedDeleteTarget = null"
      @confirm="confirmDeleteFixed"
    />
    <ConfirmDialog
      :open="Boolean(exceptionDeleteTarget)"
      title="删除排班例外"
      :message="`删除 ${exceptionDeleteTarget?.date || ''} 的排班例外，生效排班会立即重新计算。`"
      confirm-label="删除例外"
      danger
      require-reason
      @cancel="exceptionDeleteTarget = null"
      @confirm="confirmRemoveException"
    />
    <ConfirmDialog
      :open="Boolean(reassignmentDeleteTarget)"
      title="删除调班记录"
      :message="`删除 ${reassignmentDeleteTarget?.date || ''} 的调班记录，原排班人员将恢复。`"
      confirm-label="删除调班"
      danger
      require-reason
      @cancel="reassignmentDeleteTarget = null"
      @confirm="confirmRemoveReassignment"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { ArrowRight, Pencil, Plus, Repeat2, Trash2 } from "@lucide/vue";
import PageHeader from "../../shared/ui/PageHeader.vue";
import EmptyState from "../../shared/ui/EmptyState.vue";
import LoadingBlock from "../../shared/ui/LoadingBlock.vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
import StatusBadge from "../../shared/ui/StatusBadge.vue";
import { del, get, post, put } from "../../shared/api";
import { useAsyncTask } from "../../shared/composables/useAsyncTask";
import { useTerms } from "../../shared/composables/useTerms";
import { notify } from "../../shared/composables/useToast";
const { selectedTerm, loadTerms } = useTerms();
const { busy, run } = useAsyncTask();
const activeView = ref("effective");
const editor = ref("");
const selectedDate = ref(date(new Date()));
const slots = ref<any[]>([]);
const periods = ref<any[]>([]);
const effectiveDay = ref<any>(null);
const exceptions = ref<any[]>([]);
const reassignments = ref<any[]>([]);
const weekdays = [1, 2, 3, 4, 5, 6, 7].map((value, i) => ({
  value,
  label: `星期${"一二三四五六日"[i]}`,
  short: `周${"一二三四五六日"[i]}`,
}));
const fixedForm = reactive<any>({
  id: null,
  weekday: 1,
  period: "",
  title: "部长值班",
  location: "协会办公室",
  assignees: "",
  note: "",
});
const exceptionForm = reactive<any>({
  id: null,
  date: selectedDate.value,
  type: "PERIOD_CANCELLED",
  sourceSlotId: null,
  period: "",
  title: "临时值班",
  assignees: "",
  reason: "",
});
const reassignForm = reactive<any>({
  slot: null,
  originalStudentNo: "",
  replacementStudentNo: "",
  reason: "",
});
const slotsForDate = computed(() =>
  slots.value.filter(
    (i) =>
      i.weekday === new Date(`${exceptionForm.date}T12:00:00`).getDay() ||
      i.weekday === (new Date(`${exceptionForm.date}T12:00:00`).getDay() || 7),
  ),
);
const fixedDeleteTarget = ref<any>(null);
const exceptionDeleteTarget = ref<any>(null);
const reassignmentDeleteTarget = ref<any>(null);
onMounted(async () => {
  await loadTerms();
  syncSelectedDate();
  await Promise.all([loadBase(), loadEffective()]);
});
watch(
  () => selectedTerm.value?.id,
  () => {
    syncSelectedDate();
    loadEffective();
    loadAdjustments();
  },
);
async function loadBase() {
  const [s, p] = await Promise.all([
    get<any[]>("/api/schedules"),
    get<any[]>("/api/settings/duty-periods"),
  ]);
  slots.value = s;
  periods.value = p;
  if (!fixedForm.period && p[0])
    fixedForm.period = `${p[0].startTime}-${p[0].endTime}`;
}
async function loadEffective() {
  if (!selectedTerm.value) return;
  effectiveDay.value = await run(() =>
    get(
      `/api/schedules/effective?date=${selectedDate.value}&termId=${selectedTerm.value!.id}`,
    ),
  );
}
async function loadAdjustments() {
  if (!selectedTerm.value) return;
  const from = selectedDate.value;
  const d = new Date(`${from}T12:00:00`);
  d.setDate(d.getDate() + 30);
  const to = date(d);
  const [e, r] = await Promise.all([
    get<any[]>(
      `/api/schedules/exceptions?termId=${selectedTerm.value.id}&from=${from}&to=${to}`,
    ),
    get<any[]>(
      `/api/schedules/reassignments?termId=${selectedTerm.value.id}&from=${from}&to=${to}`,
    ),
  ]);
  exceptions.value = e;
  reassignments.value = r;
}
function openCreate() {
  activeView.value === "fixed" ? openFixed(null) : openException();
}
function openFixed(item: any, weekday = 1) {
  Object.assign(
    fixedForm,
    item
      ? {
          id: item.id,
          weekday: item.weekday,
          period: `${hm(item.startTime)}-${hm(item.endTime)}`,
          title: item.title,
          location: item.location || "",
          assignees: item.assignees
            .map((i: any) => i.studentNo)
            .filter(Boolean)
            .join(","),
          note: item.note || "",
        }
      : {
          id: null,
          weekday,
          period: periods.value[0]
            ? `${periods.value[0].startTime}-${periods.value[0].endTime}`
            : "",
          title: "部长值班",
          location: "协会办公室",
          assignees: "",
          note: "",
        },
  );
  editor.value = "fixed";
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
      .map((studentNo: string) => ({ studentNo, name: null })),
  };
  const ok = fixedForm.id
    ? await run(
        () => put(`/api/schedules/${fixedForm.id}`, payload),
        "排班已更新",
      )
    : await run(() => post("/api/schedules", payload), "排班已新增");
  if (ok) {
    editor.value = "";
    await loadBase();
    await loadEffective();
  }
}
function deleteFixed(item: any) {
  fixedDeleteTarget.value = item;
}
async function confirmDeleteFixed() {
  await run(
    () => del(`/api/schedules/${fixedDeleteTarget.value.id}`),
    "排班已归档",
  );
  fixedDeleteTarget.value = null;
  await loadBase();
  await loadEffective();
}
function openException() {
  Object.assign(exceptionForm, {
    id: null,
    date: selectedDate.value,
    type: "PERIOD_CANCELLED",
    sourceSlotId: null,
    period: periods.value[0]
      ? `${periods.value[0].startTime}-${periods.value[0].endTime}`
      : "",
    title: "临时值班",
    assignees: "",
    reason: "",
  });
  editor.value = "exception";
}
function editException(item: any) {
  Object.assign(exceptionForm, {
    id: item.id,
    date: item.date,
    type: item.type,
    sourceSlotId: item.sourceSlotId,
    period: item.startTime ? `${hm(item.startTime)}-${hm(item.endTime)}` : "",
    title: item.title || "",
    assignees: (item.assignees || []).map((i: any) => i.studentNo).join(","),
    reason: item.reason,
  });
  editor.value = "exception";
}
async function saveException() {
  const [startTime, endTime] = exceptionForm.period.split("-");
  const payload = {
    termId: selectedTerm.value?.id,
    date: exceptionForm.date,
    type: exceptionForm.type,
    sourceSlotId: exceptionForm.sourceSlotId || null,
    startTime: exceptionForm.sourceSlotId ? null : startTime || null,
    endTime: exceptionForm.sourceSlotId ? null : endTime || null,
    title: exceptionForm.title,
    location: null,
    reason: exceptionForm.reason,
    assignees: exceptionForm.assignees
      .split(/[,，\s]+/)
      .filter(Boolean)
      .map((studentNo: string) => ({ studentNo })),
  };
  const ok = exceptionForm.id
    ? await run(
        () => put(`/api/schedules/exceptions/${exceptionForm.id}`, payload),
        "例外已更新",
      )
    : await run(() => post("/api/schedules/exceptions", payload), "例外已新增");
  if (ok) {
    editor.value = "";
    await Promise.all([loadEffective(), loadAdjustments()]);
  }
}
function removeException(item: any) {
  exceptionDeleteTarget.value = item;
}
async function confirmRemoveException(reason: string) {
  await run(
    () =>
      del(`/api/schedules/exceptions/${exceptionDeleteTarget.value.id}`, {
        reason,
      }),
    "例外已删除",
  );
  exceptionDeleteTarget.value = null;
  await Promise.all([loadEffective(), loadAdjustments()]);
}
function openReassignment(slot: any) {
  Object.assign(reassignForm, {
    slot,
    originalStudentNo: slot.assignees[0]?.studentNo || "",
    replacementStudentNo: "",
    reason: "",
  });
  editor.value = "reassign";
}
async function saveReassignment() {
  const slot = reassignForm.slot;
  const ok = await run(
    () =>
      post("/api/schedules/reassignments", {
        termId: selectedTerm.value?.id,
        date: selectedDate.value,
        sourceSlotId: slot.sourceSlotId,
        startTime: slot.startTime,
        endTime: slot.endTime,
        originalStudentNo: reassignForm.originalStudentNo,
        replacementStudentNo: reassignForm.replacementStudentNo,
        reason: reassignForm.reason,
      }),
    "调班已保存",
  );
  if (ok) {
    editor.value = "";
    await Promise.all([loadEffective(), loadAdjustments()]);
  }
}
function removeReassignment(item: any) {
  reassignmentDeleteTarget.value = item;
}
async function confirmRemoveReassignment(reason: string) {
  await run(
    () =>
      del(`/api/schedules/reassignments/${reassignmentDeleteTarget.value.id}`, {
        reason,
      }),
    "调班已删除",
  );
  reassignmentDeleteTarget.value = null;
  await Promise.all([loadEffective(), loadAdjustments()]);
}
const hm = (v?: string) => v?.slice(0, 5) || "—";
const exceptionLabel = (v: string) =>
  (
    ({
      DAY_CANCELLED: "全天取消",
      PERIOD_CANCELLED: "取消时段",
      TEMPORARY_ADDITION: "临时增加",
      ASSIGNEE_OVERRIDE: "人员覆盖",
    }) as any
  )[v] || v;
function date(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}
function syncSelectedDate() {
  const term = selectedTerm.value;
  if (!term) return;
  if (selectedDate.value < term.startDate) selectedDate.value = term.startDate;
  else if (selectedDate.value > term.endDate) selectedDate.value = term.endDate;
}
</script>

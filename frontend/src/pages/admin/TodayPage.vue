<template>
  <ExpandedWorkspaceShell class="overview-today">
    <template #heading><h1>今日概览</h1></template>
    <div class="today-workbench">
    <TodayCommandCenter
      ref="commandCenter"
      :key="`${user?.id || 'anonymous'}:${currentRole}`"
      refined
      v-model="commandInput"
      :date-label="todayLabel"
      :role-name="currentRoleLabel"
      :role="currentRole"
      :quick-actions="quickActions"
      :inline-shortcuts="false"
      :opening="opening"
      :busy="replying"
      :error-message="commandError"
      :load-error="error"
      :loading="loading && !dashboard"
      @execute="executeCommand"
      @clear-error="commandError = ''"
      @retry-load="refresh"
    >
      <template #conversation>
        <TodayConversation :entry="entryText" :turns="turns"
          :opening="opening" :replying="replying" @open="openReply" @retry="retryTurn" />
      </template>
    </TodayCommandCenter>
    <TodaySidebar :dashboard="dashboard" :actions="entryActions" :quick-actions="quickActions"
      :schedule-notice="scheduleNotice" :loading="loading && !dashboard" :load-error="error"
      :opening="opening" :busy="replying" @open="openReply" @shortcut="runShortcut" @retry="refresh" />
    </div>
  </ExpandedWorkspaceShell>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, provide, ref, watch } from "vue";
import ExpandedWorkspaceShell from "../../layouts/ExpandedWorkspaceShell.vue";
import { memberPresentationKey } from "../../shared/ui/presentation";
import "../../styles/workspace.css";
import "../../features/attendance/presentation.css";
import "../../features/today/overview-presentation.css";
import "../../features/today/conversation-presentation.css";
provide(memberPresentationKey, true);
import { useRouter } from "vue-router";
import TodayCommandCenter from "./today/TodayCommandCenter.vue";
import TodayConversation, { type ConversationTurn } from "./today/TodayConversation.vue";
import TodaySidebar from "./today/TodaySidebar.vue";
import { createReplyPicker, describeOperation, summaryReply } from "./today/conversationReplies";
import { querySummary } from "./today/conversationQuery";
import type { ResolvedCommand } from "../../features/command-center/commandTypes";
import { get } from "../../shared/api";
import { roleLabel } from "../../app/adminNavigation";
import { useSession } from "../../app/session";
import { resolveCommand } from "../../features/command-center/commandParser";
import { useLatestRequest } from "../../shared/composables/useLatestRequest";
import { notify } from "../../shared/composables/useToast";
import type { Role } from "../../shared/types";
import type { TodayDashboardData, TodayQuickAction, TodayScheduleData } from "./today/types";

const { user } = useSession();
const router = useRouter();
const request = useLatestRequest();
const { loading, error } = request;
const dashboard = ref<TodayDashboardData | null>(null);
const dashboardDate = ref("");
const schedule = ref<TodayScheduleData | null>(null);
const commandInput = ref("");
const commandError = ref("");
const opening = ref(false);
const replying = ref(false);
const turns = ref<ConversationTurn[]>([]);
const commandCenter = ref<InstanceType<typeof TodayCommandCenter> | null>(null);
const entryText = computed(() => user.value?.name?.trim() ? `欢迎回来，${user.value.name.trim()}。` : "欢迎回来。");
let pickReply = createReplyPicker();
let turnId = 0;
let epoch = 0;
let queryController: AbortController | undefined;
const currentDate = ref(new Date());
let timer = 0;

const today = computed(() => localDate(currentDate.value));
const todayLabel = computed(() =>
  new Intl.DateTimeFormat("zh-CN", { month: "long", day: "numeric" }).format(currentDate.value),
);
const currentRole = computed<Role>(() => user.value?.role || "MINISTER");
const currentRoleLabel = computed(() => roleLabel(currentRole.value));
const canSchedule = computed(() =>
  user.value?.role === "PRESIDENT" || user.value?.role === "ADMIN",
);
const missingScheduleCount = computed(() =>
  canSchedule.value
    ? schedule.value?.slots?.filter((slot) => !slot.assignees.length).length || 0
    : 0,
);
const quickActions = computed<TodayQuickAction[]>(() => currentRole.value === "MEMBER" ? [] : [
  { id: "reviews", command: "/ 打开 签到审核", label: "签到审核", detail: "", tone: "blue" },
  { id: "today-records", command: "/ 查看 值班记录 今天", label: "今日值班记录", detail: "", tone: "blue" },
  { id: "stats-week", command: "/ 查看 数据统计 本周", label: "本周统计", detail: "", tone: "blue" },
  { id: "repairs-new", command: "/ 新建 维修事务", label: "新建维修", detail: "", tone: "blue" },
  ...(canSchedule.value ? [{ id: "backups", command: "/ 打开 备份管理", label: "备份与恢复", detail: "", tone: "blue" as const }] : []),
]);
const scheduleNotice = computed(() => missingScheduleCount.value ? `今日排班还有 ${missingScheduleCount.value} 个空缺时段。` : "");
const entryActions = computed<TodayQuickAction[]>(() => {
  if (!dashboard.value || error.value || currentRole.value === "MEMBER") return [];
  const date = dashboardDate.value || today.value;
  const label = date === today.value ? "今日" : date;
  const items: TodayQuickAction[] = [];
  const hasPending = (dashboard.value.todayPendingCount || 0) > 0;
  const hasRepairs = (dashboard.value.ongoingRepairCount || 0) > 0;
  items.push({ id: "reviews", command: "/ 查看 值班记录 待审核 " + date, label: "查看" + label + "待审核", detail: "", tone: "amber", primary: hasPending });
  if (missingScheduleCount.value) items.push({ id: "schedules", command: "/ 打开 排班管理", label: label + "排班空缺", detail: missingScheduleCount.value + " 个时段", tone: "amber" });
  items.push({ id: "repairs", command: "/ 查看 维修事务 进行中", label: "查看进行中维修", detail: "", tone: "blue", primary: !hasPending && hasRepairs });
  return items;
});

onMounted(() => {
  refresh();
  timer = window.setInterval(refresh, 60_000);
  window.addEventListener("focus", refresh);
});
onBeforeUnmount(() => {
  epoch++;
  queryController?.abort();
  window.clearInterval(timer);
  window.removeEventListener("focus", refresh);
});
watch(() => [user.value?.id, currentRole.value], () => {
  epoch++;
  queryController?.abort();
  turns.value = [];
  replying.value = false;
  opening.value = false;
  dashboard.value = null;
  pickReply = createReplyPicker();
  commandInput.value = "";
  commandError.value = "";
  refresh();
});

function refresh() {
  void load();
}

async function load() {
  currentDate.value = new Date();
  const queryDate = today.value;
  const result = await request.run(
    (signal) =>
      Promise.all([
        get<TodayDashboardData>("/api/stats/dashboard?date=" + queryDate, { signal }),
        get<TodayScheduleData>("/api/public/schedules/today", { signal }),
      ]),
    "今日数据加载失败",
  );
  if (!result) return;
  [dashboard.value, schedule.value] = result;
  dashboardDate.value = queryDate;
}

function runShortcut(item: TodayQuickAction) {
  commandCenter.value?.executeQuickAction(item.command, item.label);
}

async function executeCommand(value: string, displayName?: string) {
  if (replying.value || opening.value) return;
  const now = new Date();
  const resolution = resolveCommand(value, currentRole.value, now);
  if (resolution.kind !== "resolved") {
    commandError.value = resolution.message;
    turns.value = [...turns.value.slice(-7), { id: ++turnId, input: value, text: resolution.message, button: "", failed: true }];
    return;
  }
  commandInput.value = "";
  commandError.value = "";
  const reply = describeOperation(resolution, pickReply);
  const turn: ConversationTurn = { id: ++turnId, input: displayName || value, shortcut: Boolean(displayName), text: reply.text, button: reply.button, command: resolution };
  turns.value = [...turns.value.slice(-7), turn];
  await fillSummary(turn.id);
}

async function fillSummary(id: number) {
  const turn = turns.value.find(item => item.id === id);
  if (!turn?.command || !/^\/ (查看|查找) /.test(turn.command.canonical)) return;
  const ownEpoch = epoch;
  const fallback = turn.failed ? describeOperation(turn.command, pickReply).text : turn.text;
  queryController = new AbortController();
  replying.value = true;
  turn.pending = true;
  turn.failed = false;
  turn.summary = undefined;
  turn.text = "正在查询…";
  try {
    const result = await querySummary(turn.command, queryController.signal);
    if (epoch !== ownEpoch) return;
    turn.summary = result || undefined;
    turn.text = result ? summaryReply(result, pickReply) : fallback;
  } catch {
    if (epoch !== ownEpoch) return;
    turn.failed = true;
    turn.text = "查询没能完成，请重试，也可以打开详情查看。";
  } finally {
    if (epoch === ownEpoch) { turn.pending = false; replying.value = false; }
  }
}
function retryTurn(id: number) { if (!replying.value) void fillSummary(id); }

async function openReply(value: string | ResolvedCommand) {
  if (opening.value) return;
  const resolved = typeof value === 'string' ? resolveCommand(value, currentRole.value, new Date()) : value;
  if (resolved.kind !== 'resolved') { commandError.value = resolved.message; return; }
  const allowed = resolveCommand(resolved.canonical, currentRole.value, new Date());
  if (allowed.kind !== 'resolved') { commandError.value = allowed.message; return; }
  const turn = turns.value.find(item => item.command === value);
  const ownEpoch = epoch;
  opening.value = true;
  try {
    const failure = await router.push(resolved.target);
    if (epoch !== ownEpoch) return;
    if (failure) {
      if (turn) turn.navigationError = true;
      else commandError.value = "页面没能打开，请再试一次。";
      return;
    }
    if (turn) turn.navigationError = false;
    notify(resolved.feedback, "success");
  } catch {
    if (epoch !== ownEpoch) return;
    if (turn) turn.navigationError = true;
    else commandError.value = "页面没能打开，请再试一次。";
  } finally {
    if (epoch === ownEpoch) opening.value = false;
  }
}

function localDate(date: Date) {
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, "0"),
    String(date.getDate()).padStart(2, "0"),
  ].join("-");
}
</script>

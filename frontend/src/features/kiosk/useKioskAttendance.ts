import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { get, post } from "../../shared/api";
import {
  canConfirmAttendance,
  type AttendanceLookupResult,
  type AttendanceMemberOption,
} from "./attendanceLookup";
import {
  ensureSubmissionAttempt,
  type SubmissionAttempt,
} from "./attendanceSubmission";
import type {
  AttendanceSubmitResult,
  KioskStep,
  ScheduleDay,
} from "./types";

const RESET_DELAY = 4500;
const RETRY_DELAY = 2500;

export function useKioskAttendance() {
  const step = ref<KioskStep>("input");
  const query = ref("");
  const lookupResult = ref<AttendanceLookupResult | null>(null);
  const matches = ref<AttendanceMemberOption[]>([]);
  const busy = ref(false);
  const error = ref("");
  const online = ref(true);
  const todaySchedule = ref<ScheduleDay | null>(null);
  const weekSchedule = ref<ScheduleDay[]>([]);
  const scheduleError = ref("");
  const successName = ref("");
  const successAction = ref("");
  const successTime = ref("");

  let resetTimer: number | undefined;
  let lookupRetryTimer: number | undefined;
  let scheduleRetryTimer: number | undefined;
  let submissionAttempt: SubmissionAttempt | null = null;

  const scheduleCount = computed(
    () =>
      todaySchedule.value?.slots?.reduce(
        (total, slot) => total + (slot.assignees?.length || 0),
        0,
      ) || 0,
  );

  onMounted(loadSchedule);
  onBeforeUnmount(clearTimers);

  async function loadSchedule() {
    window.clearTimeout(scheduleRetryTimer);
    try {
      const [today, week] = await Promise.all([
        get<ScheduleDay>("/api/public/schedules/today"),
        get<ScheduleDay[]>("/api/public/schedules/week"),
      ]);
      todaySchedule.value = today;
      weekSchedule.value = week;
      scheduleError.value = "";
      online.value = true;
    } catch (cause) {
      scheduleError.value =
        cause instanceof Error ? cause.message : "排班暂时不可用";
      online.value = false;
      scheduleRetryTimer = window.setTimeout(loadSchedule, 3000);
    }
  }

  async function lookup(selectionToken?: string) {
    const lookupQuery = selectionToken || query.value;
    if (!lookupQuery || busy.value) return;
    window.clearTimeout(lookupRetryTimer);
    busy.value = true;
    error.value = "";
    try {
      const result = await get<AttendanceLookupResult>(
        `/api/public/attendance/lookup?query=${encodeURIComponent(lookupQuery)}`,
      );
      online.value = true;
      if (result.matches?.length) {
        matches.value = result.matches;
        step.value = "choose";
      } else if (canConfirmAttendance(result)) {
        lookupResult.value = result;
        submissionAttempt = null;
        step.value = "confirm";
      } else {
        const message = result.message || "未找到可签到的成员";
        error.value = `${message}。请检查学号，或联系管理员确认账号是否停用。`;
      }
    } catch (cause) {
      online.value = false;
      const message = cause instanceof Error ? cause.message : "查询失败";
      error.value = `${message}。已保留输入，连接恢复后将自动重试。`;
      lookupRetryTimer = window.setTimeout(() => {
        if (step.value === "input" && query.value) lookup();
      }, RETRY_DELAY);
    } finally {
      busy.value = false;
    }
  }

  async function selectMember(memberToken: string) {
    await lookup(memberToken);
  }

  async function submitAttendance() {
    if (!lookupResult.value?.memberToken || busy.value) return;
    busy.value = true;
    error.value = "";
    submissionAttempt = ensureSubmissionAttempt(
      submissionAttempt,
      lookupResult.value.memberToken,
    );
    try {
      const result = await post<AttendanceSubmitResult>(
        "/api/public/attendance/submit",
        {
          memberToken: submissionAttempt.memberToken,
          requestId: submissionAttempt.requestId,
        },
      );
      submissionAttempt = null;
      online.value = true;
      successName.value = result.name;
      successAction.value =
        result.action === "CHECK_IN" ? "签到成功" : "签退成功";
      successTime.value = new Intl.DateTimeFormat("zh-CN", {
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        hour12: false,
      }).format(new Date(result.submittedAt));
      step.value = "success";
      resetTimer = window.setTimeout(reset, RESET_DELAY);
    } catch (cause) {
      online.value = false;
      error.value = cause instanceof Error ? cause.message : "提交失败";
      step.value = "confirm";
    } finally {
      busy.value = false;
    }
  }

  function clearError() {
    error.value = "";
  }

  function reset() {
    window.clearTimeout(resetTimer);
    window.clearTimeout(lookupRetryTimer);
    query.value = "";
    lookupResult.value = null;
    matches.value = [];
    error.value = "";
    successName.value = "";
    successAction.value = "";
    successTime.value = "";
    submissionAttempt = null;
    step.value = "input";
  }

  function clearTimers() {
    window.clearTimeout(resetTimer);
    window.clearTimeout(lookupRetryTimer);
    window.clearTimeout(scheduleRetryTimer);
  }

  return {
    step,
    query,
    lookupResult,
    matches,
    busy,
    error,
    online,
    todaySchedule,
    weekSchedule,
    scheduleError,
    scheduleCount,
    successName,
    successAction,
    successTime,
    lookup,
    selectMember,
    submitAttendance,
    clearError,
    reset,
  };
}

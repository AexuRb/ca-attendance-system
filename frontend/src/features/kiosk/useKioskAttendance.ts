import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { ApiError, get, post } from "../../shared/api";
import { createKioskRequests, KioskRequestTimeout, KIOSK_WRITE_TIMEOUT } from "./kioskRequests";
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
const CLOCK_REFRESH_DELAY = 30_000;
const SCHEDULE_REFRESH_DELAY = 5 * 60_000;

export function useKioskAttendance() {
  const step = ref<KioskStep>("input");
  const query = ref("");
  const lookupResult = ref<AttendanceLookupResult | null>(null);
  const matches = ref<AttendanceMemberOption[]>([]);
  const busy = ref(false);
  const error = ref("");
  const scheduleAvailable = ref(true);
  const attendanceAvailable = ref(true);
  const online = computed(() => scheduleAvailable.value && attendanceAvailable.value);
  const todaySchedule = ref<ScheduleDay | null>(null);
  const weekSchedule = ref<ScheduleDay[]>([]);
  const scheduleError = ref("");
  const currentDate = ref(new Date());
  const successName = ref("");
  const successAction = ref("");
  const successTime = ref("");
  const selectingMemberToken = ref("");

  let resetTimer: number | undefined;
  let lookupRetryTimer: number | undefined;
  let scheduleRetryTimer: number | undefined;
  let refreshTimer: number | undefined;
  let scheduleRequest: Promise<void> | undefined;
  let lastScheduleRefreshAt = 0;
  let submissionAttempt: SubmissionAttempt | null = null;
  let pendingLookupQuery: string | null = null;
  let disposed = false;
  let interactionVersion = 0;
  const scheduleRequests = createKioskRequests();
  const interactionRequests = createKioskRequests();

  const scheduleCount = computed(
    () =>
      todaySchedule.value?.slots?.reduce(
        (total, slot) => total + (slot.assignees?.length || 0),
        0,
      ) || 0,
  );

  onMounted(() => {
    disposed = false;
    currentDate.value = new Date();
    void loadSchedule();
    refreshTimer = window.setInterval(
      () => refreshKioskState(false),
      CLOCK_REFRESH_DELAY,
    );
    window.addEventListener("focus", handleFocus);
    document.addEventListener("visibilitychange", handleVisibilityChange);
  });
  onBeforeUnmount(() => {
    disposed = true;
    interactionVersion += 1;
    interactionRequests.cancel();
    scheduleRequests.cancel();
    clearTimers();
  });

  function loadSchedule(): Promise<void> {
    if (disposed) return Promise.resolve();
    if (scheduleRequest) return scheduleRequest;
    window.clearTimeout(scheduleRetryTimer);
    scheduleRequest = (async () => {
      try {
        const [today, week] = await scheduleRequests.run(signal => Promise.all([
          get<ScheduleDay>("/api/public/schedules/today", { signal }),
          get<ScheduleDay[]>("/api/public/schedules/week", { signal }),
        ]));
        if (disposed) return;
        todaySchedule.value = today;
        weekSchedule.value = week;
        scheduleError.value = "";
        scheduleAvailable.value = true;
        lastScheduleRefreshAt = Date.now();
      } catch (cause) {
        if (disposed) return;
        scheduleError.value = cause instanceof KioskRequestTimeout
          ? "排班读取超时，正在重试。"
          : isRetryableError(cause)
          ? "排班服务暂时不可用，正在重试。"
          : cause instanceof Error ? cause.message : "排班暂时不可用";
        scheduleAvailable.value = !isRetryableError(cause);
        if (isRetryableError(cause)) {
          scheduleRetryTimer = window.setTimeout(() => void loadSchedule(), 3000);
        }
      }
    })().finally(() => {
      scheduleRequest = undefined;
    });
    return scheduleRequest;
  }

  function refreshKioskState(forceSchedule: boolean) {
    const previousDay = localDateKey(currentDate.value);
    const nextDate = new Date();
    currentDate.value = nextDate;
    const dayChanged = previousDay !== localDateKey(nextDate);
    const scheduleStale =
      lastScheduleRefreshAt === 0 ||
      nextDate.getTime() - lastScheduleRefreshAt >= SCHEDULE_REFRESH_DELAY;
    if (forceSchedule || dayChanged || scheduleStale) {
      void loadSchedule();
    }
  }

  function handleFocus() {
    refreshKioskState(true);
  }

  function handleVisibilityChange() {
    if (document.visibilityState === "visible") {
      refreshKioskState(true);
    }
  }

  async function lookup() {
    await performLookup(query.value, "");
  }

  async function performLookup(lookupQuery: string, selectedToken: string) {
    if (!lookupQuery || busy.value) return;
    const version = interactionVersion;
    window.clearTimeout(lookupRetryTimer);
    pendingLookupQuery = lookupQuery;
    selectingMemberToken.value = selectedToken;
    busy.value = true;
    error.value = "";
    try {
      const result = await interactionRequests.run(signal => get<AttendanceLookupResult>(
        `/api/public/attendance/lookup?query=${encodeURIComponent(lookupQuery)}`,
        { signal },
      ));
      if (disposed || version !== interactionVersion) return;
      attendanceAvailable.value = true;
      pendingLookupQuery = null;
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
      if (disposed || version !== interactionVersion) return;
      const message = cause instanceof Error ? cause.message : "查询失败";
      if (isRetryableError(cause)) {
        attendanceAvailable.value = false;
        error.value = cause instanceof KioskRequestTimeout
          ? "查询等待超时，已保留当前输入，稍后将自动重试。"
          : "本机服务暂时无法连接。已保留当前输入，连接恢复后将自动重试。";
        lookupRetryTimer = window.setTimeout(() => {
          if (!disposed && pendingLookupQuery === lookupQuery) {
            void performLookup(lookupQuery, selectedToken);
          }
        }, RETRY_DELAY);
      } else {
        attendanceAvailable.value = true;
        pendingLookupQuery = null;
        error.value = message;
      }
    } finally {
      if (!disposed && version === interactionVersion) {
        busy.value = false;
        selectingMemberToken.value = "";
      }
    }
  }

  async function selectMember(memberToken: string) {
    await performLookup(memberToken, memberToken);
  }

  async function submitAttendance() {
    if (!lookupResult.value?.memberToken || busy.value) return;
    const version = interactionVersion;
    busy.value = true;
    error.value = "";
    submissionAttempt = ensureSubmissionAttempt(
      submissionAttempt,
      lookupResult.value.memberToken,
    );
    const attempt = submissionAttempt;
    try {
      const result = await interactionRequests.run(signal => post<AttendanceSubmitResult>(
        "/api/public/attendance/submit",
        {
          memberToken: attempt.memberToken,
          requestId: attempt.requestId,
        },
        { signal },
      ), KIOSK_WRITE_TIMEOUT);
      if (disposed || version !== interactionVersion) return;
      submissionAttempt = null;
      attendanceAvailable.value = true;
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
      if (disposed || version !== interactionVersion) return;
      const serviceFailure = isRetryableError(cause);
      attendanceAvailable.value = !serviceFailure;
      const message = cause instanceof Error ? cause.message : "提交失败";
      error.value = cause instanceof KioskRequestTimeout
        ? "提交等待超时，结果暂未确认。当前确认已保留，请保留此页面并再次确认，避免重新输入。"
        : serviceFailure
        ? "提交结果暂未确认。当前确认已保留，请保留此页面并再次确认，避免重新输入。"
        : message;
      step.value = "confirm";
    } finally {
      if (!disposed && version === interactionVersion) busy.value = false;
    }
  }

  function clearError() {
    if (step.value === "input" && busy.value) {
      interactionVersion += 1;
      interactionRequests.cancel();
      busy.value = false;
      selectingMemberToken.value = "";
    }
    window.clearTimeout(lookupRetryTimer);
    pendingLookupQuery = null;
    error.value = "";
  }

  function reset() {
    // Reset the interaction, not a write that may already have reached the server.
    interactionVersion += 1;
    interactionRequests.cancel();
    busy.value = false;
    window.clearTimeout(resetTimer);
    window.clearTimeout(lookupRetryTimer);
    pendingLookupQuery = null;
    query.value = "";
    lookupResult.value = null;
    matches.value = [];
    error.value = "";
    successName.value = "";
    successAction.value = "";
    successTime.value = "";
    selectingMemberToken.value = "";
    submissionAttempt = null;
    step.value = "input";
  }

  function clearTimers() {
    window.clearTimeout(resetTimer);
    window.clearTimeout(lookupRetryTimer);
    window.clearTimeout(scheduleRetryTimer);
    window.clearInterval(refreshTimer);
    window.removeEventListener("focus", handleFocus);
    document.removeEventListener("visibilitychange", handleVisibilityChange);
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
    currentDate,
    successName,
    successAction,
    successTime,
    selectingMemberToken,
    lookup,
    selectMember,
    submitAttendance,
    clearError,
    reset,
  };
}

function isNetworkError(cause: unknown) {
  return cause instanceof ApiError && cause.network;
}

function isRetryableError(cause: unknown) {
  return isNetworkError(cause) || (cause instanceof ApiError && cause.status >= 500);
}

function localDateKey(date: Date) {
  return `${date.getFullYear()}-${date.getMonth() + 1}-${date.getDate()}`;
}

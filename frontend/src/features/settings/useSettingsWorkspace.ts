import { computed, nextTick, onMounted, ref } from "vue";
import { onBeforeRouteLeave, useRoute } from "vue-router";
import { useSession } from "../../app/session";
import {
  changeGlobalAppearance,
  useAppearance,
} from "../../appearance/appearanceStore";
import type { AppearanceId } from "../../appearance/appearanceTypes";
import { get, put } from "../../shared/api";
import { useAsyncTask } from "../../shared/composables/useAsyncTask";
import { useLatestRequest } from "../../shared/composables/useLatestRequest";
import { usePendingActions } from "../../shared/composables/usePendingActions";
import { useUnsavedChanges } from "../../shared/composables/useUnsavedChanges";
import {
  canManageAttendancePolicy,
  normalizeAttendancePolicy,
  type AttendancePolicy,
} from "./attendancePolicy";
import { validateDutyPeriods, type DutyPeriod } from "./dutyPeriods";
import {
  normalizeDutyWeekdays,
  type DutyWeekdaySetting,
} from "./dutyWeekdays";

export function useSettingsWorkspace() {
  const task = useAsyncTask();
  const route = useRoute();
  const loadRequest = useLatestRequest();
  const actions = usePendingActions();
  const { loading, error: loadError } = loadRequest;
  const { user } = useSession();
  const { state: appearanceState } = useAppearance();
  const weekdays = ref<DutyWeekdaySetting[]>([]);
  const periods = ref<DutyPeriod[]>([]);
  const attendancePolicy = ref<AttendancePolicy>(normalizeAttendancePolicy());
  const appearanceDraft = ref<AppearanceId>(appearanceState.active);
  const appearanceBaseline = ref<AppearanceId>(appearanceState.active);
  const appearanceError = ref("");
  const canEditAppearance = computed(() => user.value?.role === "ADMIN");
  const appearanceDirty = computed(
    () => appearanceDraft.value !== appearanceBaseline.value,
  );
  const canEditAttendancePolicy = computed(() =>
    canManageAttendancePolicy(user.value?.role),
  );
  const periodError = computed(() => validateDutyPeriods(periods.value));
  const weekdayBaseline = ref("");
  const periodBaseline = ref("");
  const policyBaseline = ref("");
  const periodsDirty = computed(
    () => Boolean(periodBaseline.value) && periodSnapshot() !== periodBaseline.value,
  );
  const policyDirty = computed(
    () => Boolean(policyBaseline.value) && policySnapshot() !== policyBaseline.value,
  );
  const unsaved = useUnsavedChanges(() =>
    (Boolean(weekdayBaseline.value) && weekdaySnapshot() !== weekdayBaseline.value) ||
    periodsDirty.value ||
    policyDirty.value ||
    appearanceDirty.value,
  );

  onMounted(async () => {
    await loadSettings();
    const section = typeof route.query.section === "string" ? route.query.section : "";
    if (!["appearance", "weekdays", "policy", "periods"].includes(section)) return;
    await nextTick();
    const target = document.getElementById(`settings-${section}`);
    target?.scrollIntoView({ behavior: "smooth", block: "start" });
    const heading = target?.querySelector<HTMLElement>("h2");
    heading?.setAttribute("tabindex", "-1");
    heading?.focus({ preventScroll: true });
  });

  async function loadSettings() {
    appearanceDraft.value = appearanceState.active;
    appearanceBaseline.value = appearanceState.active;
    appearanceError.value = "";
    const result = await loadRequest.run(
      (signal) =>
        Promise.all([
          get<DutyWeekdaySetting[]>("/api/settings/weekdays", { signal }),
          get<DutyPeriod[]>("/api/settings/duty-periods", { signal }),
          get<AttendancePolicy>("/api/settings/attendance-policy", { signal }),
        ]),
      "系统设置加载失败",
    );
    if (!result) return;
    const [weekdayRows, dutyPeriods, policy] = result;
    weekdays.value = normalizeDutyWeekdays(weekdayRows);
    periods.value = dutyPeriods.map((period) => ({
      ...period,
      enabled: period.enabled !== false,
    }));
    attendancePolicy.value = normalizeAttendancePolicy(policy);
    weekdayBaseline.value = weekdaySnapshot();
    periodBaseline.value = periodSnapshot();
    policyBaseline.value = policySnapshot();
  }

  async function saveWeekdays() {
    await actions.run("weekdays", async () => {
      const saved = await task.run(
        () =>
          put("/api/settings/weekdays", {
            enabledWeekdays: weekdays.value
              .filter((item) => item.enabled)
              .map((item) => item.weekday),
          }),
        "值班星期已保存",
      );
      if (saved !== undefined) weekdayBaseline.value = weekdaySnapshot();
    });
  }

  async function savePeriods() {
    if (periodError.value) return;
    await actions.run("periods", async () => {
      const saved = await task.run(
        () =>
          put<DutyPeriod[]>("/api/settings/duty-periods", {
            periods: periods.value.map((item) => ({
              startTime: item.startTime.slice(0, 5),
              endTime: item.endTime.slice(0, 5),
              enabled: item.enabled,
            })),
          }),
        "值班时间段已保存",
      );
      if (saved) {
        periods.value = saved;
        periodBaseline.value = periodSnapshot();
      }
    });
  }

  function toggleWeekday(weekday: number) {
    const day = weekdays.value.find((item) => item.weekday === weekday);
    if (day) day.enabled = !day.enabled;
  }

  async function saveAttendancePolicy() {
    if (!canEditAttendancePolicy.value) return;
    await actions.run("policy", async () => {
      const saved = await task.run(
        () =>
          put<AttendancePolicy>(
            "/api/settings/attendance-policy",
            attendancePolicy.value,
          ),
        "有效时长规则已保存",
      );
      if (saved) {
        attendancePolicy.value = normalizeAttendancePolicy(saved);
        policyBaseline.value = policySnapshot();
      }
    });
  }

  async function saveAppearance() {
    if (!canEditAppearance.value || !appearanceDirty.value) return;
    appearanceError.value = "";
    await actions.run("appearance", async () => {
      const saved = await task.run(
        () => changeGlobalAppearance(appearanceDraft.value),
        "全局界面已更新",
      );
      if (saved) {
        appearanceDraft.value = saved.id;
        appearanceBaseline.value = saved.id;
      } else {
        appearanceError.value = task.error.value;
      }
    });
  }

  function weekdaySnapshot() {
    return JSON.stringify(
      weekdays.value.map(({ weekday, enabled }) => ({ weekday, enabled })),
    );
  }

  function periodSnapshot() {
    return JSON.stringify(periods.value);
  }

  function policySnapshot() {
    return JSON.stringify(attendancePolicy.value);
  }

  onBeforeRouteLeave(
    () =>
      new Promise((resolve) => {
        unsaved.request(() => resolve(true), () => resolve(false));
      }),
  );

  return {
    actions,
    appearanceDraft,
    appearanceDirty,
    appearanceError,
    appearanceState,
    attendancePolicy,
    canEditAppearance,
    canEditAttendancePolicy,
    loadError,
    loading,
    loadSettings,
    periodError,
    periods,
    periodsDirty,
    policyDirty,
    saveAttendancePolicy,
    saveAppearance,
    savePeriods,
    saveWeekdays,
    toggleWeekday,
    unsaved,
    weekdays,
  };
}

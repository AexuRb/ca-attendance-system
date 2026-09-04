import { computed, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { useSession } from "../../app/session";
import { get, put, setToken } from "../../shared/api";
import { useAsyncTask } from "../../shared/composables/useAsyncTask";
import { useLatestRequest } from "../../shared/composables/useLatestRequest";
import { usePendingActions } from "../../shared/composables/usePendingActions";
import { notify } from "../../shared/composables/useToast";
import { dateRangeError } from "../../shared/validation/dateRange";
import { focusFirstInvalid, validateProfileInput, type InputErrors } from "../../shared/validation/userInput";
import { attendanceStatusMeta, totalAttendanceHours, type AttendanceProfileRecord, type TrainingProfileRecord } from "./profileRecords";

export function useProfileWorkspace() {
  const router = useRouter();
  const { state, user, refreshUser } = useSession();
  const task = useAsyncTask();
  const actions = usePendingActions();
  const busy = computed(() => actions.isPending("save-profile"));
  const profileRequest = useLatestRequest();
  const recordsRequest = useLatestRequest();
  const { loading: recordsLoading, error: recordsError } = recordsRequest;
  const attendanceRecords = ref<AttendanceProfileRecord[]>([]);
  const trainingRecords = ref<TrainingProfileRecord[]>([]);
  const activeRecordTab = ref<"attendance" | "training">("attendance");
  const passwordOpen = ref(false);
  const from = ref(`${new Date().getFullYear()}-01-01`);
  const to = ref(localDate(new Date()));
  const profile = reactive({ phone: "", qq: "", major: "", grade: "" });
  const profileForm = ref<HTMLFormElement | null>(null);
  const profileErrors = reactive<InputErrors>({});
  const filterError = computed(() => dateRangeError(from.value, to.value));
  const pageError = computed(() => profileRequest.error.value || recordsError.value);
  const activeRecords = computed(() => activeRecordTab.value === "attendance" ? attendanceRecords.value : trainingRecords.value);
  const attendanceHours = computed(() => totalAttendanceHours(attendanceRecords.value));
  const trainingHours = computed(() => trainingRecords.value.reduce((sum, record) => sum + Number(record.durationHours || 0), 0));
  const totalHours = computed(() => attendanceHours.value + trainingHours.value);

  onMounted(() => void Promise.all([loadProfile(), loadRecords()]));

  async function loadProfile() {
    const me = await profileRequest.run(
      (signal) => get<{ phone?: string; qq?: string; major?: string; grade?: string }>("/api/auth/me", { signal }),
      "个人资料加载失败",
    );
    if (!me) return;
    Object.assign(profile, { phone: me.phone || "", qq: me.qq || "", major: me.major || "", grade: me.grade || "" });
  }

  async function loadRecords() {
    if (filterError.value) return;
    const query = new URLSearchParams({ from: from.value, to: to.value });
    const value = await recordsRequest.run(
      (signal) => Promise.all([
        get<AttendanceProfileRecord[]>(`/api/attendance/me?${query}`, { signal }),
        get<TrainingProfileRecord[]>(`/api/trainings/me?${query}`, { signal }),
      ]),
      "个人记录加载失败",
    );
    if (value) [attendanceRecords.value, trainingRecords.value] = value;
  }

  async function save() {
    const nextErrors = validateProfileInput({ phone: profile.phone, qq: profile.qq, college: profile.major });
    Object.keys(profileErrors).forEach((key) => delete profileErrors[key]);
    Object.assign(profileErrors, nextErrors);
    if (Object.keys(profileErrors).length) {
      focusFirstInvalid(profileForm.value, profileErrors);
      return;
    }
    await actions.run("save-profile", async () => {
      const result = await task.run(
        () => put("/api/me/profile", { phone: profile.phone, major: profile.major, qq: profile.qq }),
        "个人资料已保存",
      );
      if (result !== undefined) await refreshUser();
    });
  }

  async function passwordChanged() {
    passwordOpen.value = false;
    notify("密码修改成功，请使用新密码登录", "success");
    setToken("");
    state.user = null;
    await router.replace({ name: "login", query: { reason: "password-changed" } });
  }

  function retryFailedLoad() {
    if (profileRequest.error.value) void loadProfile();
    if (recordsError.value) void loadRecords();
  }

  function captureProfileForm(element: unknown) {
    profileForm.value = element instanceof HTMLFormElement ? element : null;
  }

  function attendanceNote(record: AttendanceProfileRecord) {
    return record.checkInRejectReason || record.checkOutRejectReason || record.manualReason || "";
  }

  const clock = (value?: string) => value?.slice(11, 16) || "—";
  const shortClock = (value?: string) => value?.slice(0, 5) || "—";
  const number = (value: unknown) => {
    const numeric = Number(value || 0);
    return numeric.toFixed(numeric % 1 ? 1 : 0);
  };
  const sourceLabel = (source?: string) => source === "ADMIN_MANUAL" ? "后台补录" : "签到台";
  const roleLabels: Record<string, string> = { MEMBER: "成员", MINISTER: "部长", PRESIDENT: "会长", ADMIN: "管理员" };
  const roleLabel = (role?: string) => roleLabels[role || ""] || "";
  function localDate(value: Date) {
    return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, "0")}-${String(value.getDate()).padStart(2, "0")}`;
  }

  return {
    activeRecordTab, activeRecords, attendanceHours, attendanceNote, attendanceRecords,
    attendanceStatusMeta, busy, captureProfileForm, clock, filterError, from, loadRecords,
    number, pageError, passwordChanged, passwordOpen, profile, profileErrors, recordsError,
    recordsLoading, retryFailedLoad, roleLabel, save, shortClock, sourceLabel, to, totalHours,
    trainingHours, trainingRecords, user,
  };
}

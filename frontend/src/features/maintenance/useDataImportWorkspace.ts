import { computed, onBeforeUnmount, ref } from "vue";
import { get, post, downloadBlob } from "../../shared/api";
import { excelFileError } from "../../shared/validation/fileValidation";
import { importKinds, reviewMembers, reviewSchedules, reviewTraining } from "./dataImport";
import type { ImportKind, ImportReview, MemberPreview, TrainingPreview, ImportOutcome } from "./dataImport";
import type { ScheduleImportPreview, ScheduleImportResult } from "../schedule/scheduleTypes";
import type { TrainingSession, TrainingPage } from "../training/trainingTypes";

export function useDataImportWorkspace() {
  const active = ref<ImportKind | null>(null);
  const file = ref<File | null>(null);
  const review = ref<ImportReview | null>(null);
  const error = ref("");
  const busy = ref<"" | "validate" | "import" | "template">("");
  const complete = ref("");
  const completionWarnings = ref<string[]>([]);
  const confirmOpen = ref(false);
  const onlyErrors = ref(false);
  const session = ref<TrainingSession | null>(null);
  const sessions = ref<TrainingSession[]>([]);
  const sessionKeyword = ref("");
  const sessionQuery = ref("");
  const sessionPage = ref(1);
  const sessionHasMore = ref(false);
  const sessionLoading = ref(false);
  const sessionError = ref("");
  let live = true;
  let searchVersion = 0;
  onBeforeUnmount(() => { live = false; searchVersion++; });
  const config = computed(() => importKinds.find(k => k.id === active.value));
  const hasTarget = computed(() => active.value !== "training" || Boolean(session.value));
  const canValidate = computed(() => Boolean(file.value && hasTarget.value && !busy.value));
  const canImport = computed(() => canValidate.value && Boolean(review.value?.valid));
  const targetLabel = computed(() => active.value === "training" && session.value ? `${session.value.trainingDate} · ${session.value.title}` : config.value?.name || "");

  function clearFile() {
    if (busy.value) return;
    file.value = null; review.value = null; error.value = ""; complete.value = "";
    completionWarnings.value = []; onlyErrors.value = false; confirmOpen.value = false;
  }
  function openTask(kind: ImportKind) {
    if (busy.value) return;
    if (active.value === kind) { active.value = null; clearFile(); return; }
    clearFile(); active.value = kind; session.value = null;
    if (kind === "training" && !sessions.value.length) void searchSessions(1);
  }
  function selectSession(id: string) {
    if (busy.value) return;
    const next = sessions.value.find(s => String(s.id) === id) || null;
    if (next?.id === session.value?.id) return;
    clearFile(); session.value = next;
  }
  function chooseFile(selected: File | null) {
    if (busy.value || !selected) return;
    clearFile();
    error.value = excelFileError(selected, "导入文件");
    if (active.value === "schedules" && !selected.name.toLowerCase().endsWith(".xlsx")) error.value = "排班导入仅支持 .xlsx 文件";
    if (!error.value) file.value = selected;
  }
  function fileBody() {
    const body = new FormData();
    body.append("file", file.value!);
    return body;
  }
  function endpoint() {
    if (active.value === "members") return "/api/users/import";
    if (active.value === "schedules") return "/api/schedules/import";
    return `/api/trainings/${session.value!.id}/participants/import`;
  }
  async function validate() {
    if (!canValidate.value) return;
    busy.value = "validate"; error.value = ""; review.value = null; onlyErrors.value = false;
    try {
      const value = await post<MemberPreview | ScheduleImportPreview | TrainingPreview>(`${endpoint()}/preview`, fileBody());
      if (!live) return;
      review.value = active.value === "members" ? reviewMembers(value as MemberPreview)
        : active.value === "schedules" ? reviewSchedules(value as ScheduleImportPreview) : reviewTraining(value as TrainingPreview);
    } catch (cause) { if (live) error.value = message(cause, "文件校验失败，请重试"); }
    finally { if (live) busy.value = ""; }
  }
  async function confirmImport() {
    if (!canImport.value || !confirmOpen.value) return;
    busy.value = "import"; error.value = "";
    try {
      const body = fileBody();
      if (review.value?.token) body.append("previewToken", review.value.token);
      const result = await post<ImportOutcome | ScheduleImportResult>(endpoint(), body);
      if (!live) return;
      if ("errors" in result && result.errors?.length) { error.value = result.errors.join("；"); review.value = null; return; }
      if ("replacedGroups" in result) complete.value = `已更新 ${result.replacedGroups} 个排班分组，安排 ${result.assignedMembers} 名值班人员。`;
      else { const value = result as ImportOutcome; complete.value = `新增 ${value.created}，更新 ${value.updated}${value.skipped ? `，跳过 ${value.skipped}` : ""}。`; completionWarnings.value = value.warnings || []; }
      file.value = null; review.value = null;
    } catch (cause) {
      if (live) { error.value = message(cause, "导入未能完成，请重试"); review.value = null; }
    } finally { if (live) { busy.value = ""; confirmOpen.value = false; } }
  }
  async function template(kind: ImportKind) {
    if (busy.value) return;
    if (kind === "training" && (!session.value || active.value !== "training")) {
      if (active.value !== "training") openTask("training");
      error.value = "请先选择培训，再下载对应模板。"; return;
    }
    const path = kind === "members" ? "/api/users/import-template" : kind === "schedules" ? "/api/schedules/import-template" : `/api/trainings/${session.value!.id}/participants/import-template`;
    busy.value = "template"; error.value = "";
    try { const blob = await get<Blob>(path); if (live) downloadBlob(blob, `${importKinds.find(k => k.id === kind)!.name}导入模板.xlsx`); }
    catch (cause) { if (live) error.value = message(cause, "模板下载失败，请重试"); }
    finally { if (live) busy.value = ""; }
  }
  async function searchSessions(page = 1) {
    if (busy.value) return;
    const version = ++searchVersion;
    if (page === 1) sessionQuery.value = sessionKeyword.value.trim();
    sessionLoading.value = true; sessionError.value = "";
    const params = new URLSearchParams({ page: String(page), pageSize: "20", keyword: sessionQuery.value, from: "0001-01-01", to: "9999-12-31" });
    try {
      const value = await get<TrainingPage<TrainingSession>>(`/api/trainings/page?${params}`);
      if (!live || version !== searchVersion) return;
      sessions.value = value.items; sessionPage.value = value.page; sessionHasMore.value = value.hasMore;
    } catch (cause) { if (live && version === searchVersion) sessionError.value = message(cause, "培训列表加载失败"); }
    finally { if (live && version === searchVersion) sessionLoading.value = false; }
  }
  return { active, config, file, review, error, busy, complete, completionWarnings, confirmOpen, onlyErrors, session, sessions,
    sessionKeyword, sessionPage, sessionHasMore, sessionLoading, sessionError, hasTarget, targetLabel, canValidate, canImport,
    clearFile, openTask, chooseFile, selectSession, validate, confirmImport, template, searchSessions };
}
function message(cause: unknown, fallback: string) { return cause instanceof Error ? cause.message : fallback; }

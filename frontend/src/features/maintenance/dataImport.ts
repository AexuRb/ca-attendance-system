import type { ScheduleImportPreview } from "../schedule/scheduleTypes";

export type ImportKind = "members" | "schedules" | "training";
export const importKinds = [
  { id: "members", name: "成员名册", detail: "新增成员与资料更新", rule: "按学号识别成员，空白可选字段保留原资料。", limit: "最多 3,000 条", effect: "按学号新增或更新成员资料" },
  { id: "schedules", name: "固定排班", detail: "按星期与时段安排值班", rule: "仅使用已开放的星期与时段。", limit: "最多 1,000 条", effect: "只替换文件涉及的星期和时段分组，其他排班保留" },
  { id: "training", name: "培训参与名单", detail: "导入指定培训的参与记录", rule: "先选择培训，再下载对应模板。", limit: "最多 3,000 条", effect: "按学号新增或更新所选培训的参与记录" },
] as const;
export interface MemberPreview {
  valid: boolean; created: number; updated: number; errorCount: number; errors: string[];
  warnings: string[]; token: string;
  changes: { studentNo: string; name: string; action: string; fields: { field: string; before: string; after: string }[] }[];
}
export interface TrainingPreview {
  valid: boolean; sourceRows: number; errorCount: number; errors: string[];
  rows: { row: number; studentNo: string; name: string; durationHours: number; remark?: string }[];
}
export interface ImportReview {
  valid: boolean; total: number; issueCount: number; errors: string[]; warnings: string[]; token?: string;
  columns: string[]; rows: string[][]; summary: string;
}
export interface ImportOutcome { created: number; updated: number; skipped: number; errors?: string[]; warnings?: string[] }
export function reviewMembers(value: MemberPreview): ImportReview {
  return { valid: value.valid && value.created + value.updated > 0, total: value.created + value.updated + value.errorCount,
    issueCount: value.errorCount, errors: value.errors, warnings: value.warnings || [], token: value.token,
    columns: ["学号", "姓名", "操作", "资料变化"],
    rows: value.changes.map(c => [c.studentNo, c.name, c.action, c.fields.map(f => `${f.field}：${f.before || "未填写"} → ${f.after}`).join("；") || "与现有资料一致"]),
    summary: `预计新增 ${value.created}，更新 ${value.updated}` };
}
export function reviewSchedules(value: ScheduleImportPreview): ImportReview {
  return { valid: value.valid && value.memberCount > 0, total: value.sourceRows, issueCount: value.issues.length,
    errors: value.issues.map(i => `${i.row ? `第 ${i.row} 行：` : ""}${i.message}`), warnings: [],
    columns: ["星期", "时段", "人数", "值班成员"],
    rows: value.groups.map(g => [g.weekdayName, `${g.startTime}–${g.endTime}`, String(g.members.length), g.members.map(m => `${m.name}（${m.studentNo}）`).join("、")]),
    summary: `${value.groupCount} 个排班分组，${value.memberCount} 名值班人员` };
}
export function reviewTraining(value: TrainingPreview): ImportReview {
  return { valid: value.valid && value.rows.length > 0, total: value.sourceRows, issueCount: value.errorCount,
    errors: value.errors, warnings: [], columns: ["行号", "学号", "姓名", "时长（小时）", "备注"],
    rows: value.rows.map(r => [String(r.row), r.studentNo, r.name, String(r.durationHours), r.remark || "—"]),
    summary: `${value.rows.length} 条参与记录通过校验` };
}

export type MemberRole = "MEMBER" | "MINISTER" | "PRESIDENT" | "ADMIN";
export type MemberStatus = "ACTIVE" | "DISABLED";
export type AssessmentStatus = "PASSED" | "NOT_PASSED";

export const memberAssessments = [
  { key: "disassemblyAssessment", label: "拆机考核", shortLabel: "拆机" },
  { key: "osReinstallAssessment", label: "重装系统考核", shortLabel: "重装系统" },
] as const;

type AssessmentField = typeof memberAssessments[number]["key"];
export type MemberAssessmentChanges = Partial<Record<AssessmentField, AssessmentStatus>>;

export interface MemberSummary extends MemberAssessmentChanges {
  id: number;
  studentNo: string;
  name: string;
  role: MemberRole;
  status: MemberStatus;
  phone?: string;
  major?: string;
  grade?: string;
  qq?: string;
  mustChangePassword?: boolean;
}

export interface MemberEditorPayload {
  studentNo: string;
  name: string;
  role: MemberRole;
  status: MemberStatus;
  phone: string;
  major: string;
  grade: string;
  qq: string;
  reason?: string;
  assessmentChanges: MemberAssessmentChanges;
  assessmentOnly: boolean;
}

export function memberAssessmentStatus(member: MemberSummary, key: AssessmentField) {
  return member.role === "MEMBER" ? member[key] : "PASSED";
}

export interface MemberPage {
  items: MemberSummary[];
  total: number;
  page: number;
  pageSize: number;
}

export interface MemberImportResult {
  created: number;
  updated: number;
  skipped: number;
  errors: string[];
}

export interface BulkStatusResult {
  updated: number;
  unchanged: number;
  skipped: number;
  errors: string[];
}

interface SelectableMember {
  id: number;
  role: string;
}

export function selectableMemberIds(
  members: SelectableMember[],
  operatorRole?: string,
  operatorId?: number,
): number[] {
  return members
    .filter(
      (member) =>
        member.id !== operatorId &&
        (operatorRole === "ADMIN" || member.role !== "ADMIN"),
    )
    .map((member) => member.id);
}

export function bulkStatusPayload(
  selected: Set<number>,
  status: MemberStatus,
  reason: string,
) {
  return {
    ids: [...selected].sort((left, right) => left - right),
    status,
    reason: reason.trim(),
  };
}

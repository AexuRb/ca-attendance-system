export type MemberRole = "MEMBER" | "MINISTER" | "PRESIDENT" | "ADMIN";
export type MemberStatus = "ACTIVE" | "DISABLED";

export interface MemberSummary {
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

export function togglePageSelection(
  selected: Set<number>,
  pageIds: number[],
  checked: boolean,
): Set<number> {
  const next = new Set(selected);
  pageIds.forEach((id) => {
    if (checked) next.add(id);
    else next.delete(id);
  });
  return next;
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

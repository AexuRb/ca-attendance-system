export type AccountRole = "MEMBER" | "MINISTER" | "PRESIDENT" | "ADMIN";

export interface AccountCandidate {
  id: number;
  studentNo: string;
  name: string;
  role?: AccountRole;
  inactive?: boolean;
}

export function filterAccountCandidates(
  candidates: AccountCandidate[],
  keyword: string,
): AccountCandidate[] {
  const normalized = keyword.trim().toLocaleLowerCase();
  if (!normalized) return candidates;
  return candidates.filter(
    (candidate) =>
      candidate.studentNo.toLocaleLowerCase().includes(normalized) ||
      candidate.name.toLocaleLowerCase().includes(normalized),
  );
}

export function mergeAccountCandidates(
  candidates: AccountCandidate[],
  selected: AccountCandidate | null,
): AccountCandidate[] {
  if (!selected || candidates.some((candidate) => candidate.id === selected.id)) {
    return candidates;
  }
  return [selected, ...candidates];
}

export function accountRoleLabel(role?: AccountRole): string {
  if (!role) return "账号";
  return {
    MEMBER: "成员",
    MINISTER: "部长",
    PRESIDENT: "会长",
    ADMIN: "管理员",
  }[role];
}

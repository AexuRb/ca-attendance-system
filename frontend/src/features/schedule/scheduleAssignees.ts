export type ScheduleManagerRole = "MINISTER" | "PRESIDENT" | "ADMIN";

export interface ScheduleAssigneeOption {
  studentNo: string;
  name: string;
  role?: ScheduleManagerRole;
}

export function filterScheduleAssignees(
  candidates: ScheduleAssigneeOption[],
  keyword: string,
): ScheduleAssigneeOption[] {
  const normalized = keyword.trim().toLocaleLowerCase();
  if (!normalized) return candidates;
  return candidates.filter(
    (candidate) =>
      candidate.studentNo.toLocaleLowerCase().includes(normalized) ||
      candidate.name.toLocaleLowerCase().includes(normalized),
  );
}

export function mergeScheduleAssignees(
  candidates: ScheduleAssigneeOption[],
  selected: ScheduleAssigneeOption[],
): ScheduleAssigneeOption[] {
  const merged = new Map<string, ScheduleAssigneeOption>();
  candidates.forEach((candidate) => merged.set(candidate.studentNo, candidate));
  selected.forEach((candidate) => {
    if (!merged.has(candidate.studentNo)) {
      merged.set(candidate.studentNo, candidate);
    }
  });
  return [...merged.values()];
}

export function roleLabel(role?: ScheduleManagerRole): string {
  return (
    {
      MINISTER: "部长",
      PRESIDENT: "会长",
      ADMIN: "管理员",
    }[role || "MINISTER"] || "负责人"
  );
}

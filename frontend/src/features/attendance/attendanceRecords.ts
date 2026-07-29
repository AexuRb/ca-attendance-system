export interface AttendanceRecordItem {
  id: number;
  userId: number;
  userRole: AttendanceRole;
  studentNo: string;
  name: string;
  dutyDate: string;
  checkInTime?: string;
  checkOutTime?: string;
  checkInStatus: string;
  checkOutStatus: string;
  durationMinutes: number;
  effectiveStatus: string;
}

export type AttendanceRole = "MEMBER" | "MINISTER" | "PRESIDENT" | "ADMIN";

export interface AttendanceActionAccess {
  allowed: boolean;
  reason: string;
}

export interface AttendanceRecordPage {
  items: AttendanceRecordItem[];
  total: number;
  page: number;
  pageSize: number;
}

export interface AttendanceRecordFilters {
  from: string;
  to: string;
  keyword: string;
  status: string;
}

export function attendancePageQuery(
  filters: AttendanceRecordFilters,
  page: number,
  pageSize: number,
): URLSearchParams {
  const query = new URLSearchParams({
    from: filters.from,
    to: filters.to,
    page: String(page),
    pageSize: String(pageSize),
  });
  const keyword = filters.keyword.trim();
  if (keyword) query.set("studentNo", keyword);
  if (filters.status) query.set("status", filters.status);
  return query;
}

export function totalAttendancePages(total: number, pageSize: number): number {
  return Math.max(1, Math.ceil(total / pageSize));
}

export function localDateTimeInput(value: Date): string {
  return [
    value.getFullYear(),
    "-",
    String(value.getMonth() + 1).padStart(2, "0"),
    "-",
    String(value.getDate()).padStart(2, "0"),
    "T",
    String(value.getHours()).padStart(2, "0"),
    ":",
    String(value.getMinutes()).padStart(2, "0"),
  ].join("");
}

export function attendanceActionAccess(
  actorRole: AttendanceRole | undefined,
  targetRole: AttendanceRole | undefined,
  dutyDate: string,
  now = new Date(),
): AttendanceActionAccess {
  if (actorRole === "ADMIN" || actorRole === "PRESIDENT") {
    return { allowed: true, reason: "" };
  }
  if (actorRole !== "MINISTER") {
    return { allowed: false, reason: "当前账号无权修改值班记录" };
  }
  if (targetRole === "PRESIDENT" || targetRole === "ADMIN") {
    return {
      allowed: false,
      reason: "部长不能修改或删除会长、管理员的记录",
    };
  }

  const weekStart = new Date(
    now.getFullYear(),
    now.getMonth(),
    now.getDate() - ((now.getDay() + 6) % 7),
  );
  const weekEnd = new Date(
    weekStart.getFullYear(),
    weekStart.getMonth(),
    weekStart.getDate() + 6,
    23,
    59,
    59,
    999,
  );
  const recordDate = new Date(`${dutyDate}T00:00:00`);
  if (
    Number.isNaN(recordDate.getTime()) ||
    recordDate < weekStart ||
    recordDate > weekEnd
  ) {
    return {
      allowed: false,
      reason: "部长只能修改或删除本周记录",
    };
  }
  return { allowed: true, reason: "" };
}

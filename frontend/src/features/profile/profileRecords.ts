export type StatusTone = "neutral" | "info" | "success" | "warning" | "danger";

export interface AttendanceProfileRecord {
  id: number;
  dutyDate: string;
  checkInTime?: string;
  checkOutTime?: string;
  checkInStatus: string;
  checkOutStatus: string;
  durationMinutes: number;
  validHours: number;
  effectiveStatus: string;
  source?: string;
  manualReason?: string;
  checkInRejectReason?: string;
  checkOutRejectReason?: string;
}

export interface TrainingProfileRecord {
  participantId: number;
  sessionId: number;
  title: string;
  trainingDate: string;
  startTime?: string;
  endTime?: string;
  location?: string;
  speaker?: string;
  attendanceStatus: string;
  durationHours: number;
  remark?: string;
}

export interface StatusMeta {
  label: string;
  tone: StatusTone;
}

export function attendanceStatusMeta(status?: string): StatusMeta {
  const states: Record<string, StatusMeta> = {
    VALID: { label: "有效", tone: "success" },
    PENDING: { label: "待审核", tone: "warning" },
    INVALID: { label: "无效", tone: "danger" },
    INCOMPLETE: { label: "未完成", tone: "neutral" },
  };
  return states[status || ""] || { label: status || "未知", tone: "neutral" };
}

export function trainingStatusMeta(status?: string): StatusMeta {
  const states: Record<string, StatusMeta> = {
    PRESENT: { label: "出席", tone: "success" },
    LEAVE: { label: "请假", tone: "warning" },
    ABSENT: { label: "缺席", tone: "danger" },
  };
  return states[status || ""] || { label: status || "未知", tone: "neutral" };
}

export function totalAttendanceHours(
  records: Pick<AttendanceProfileRecord, "effectiveStatus" | "validHours">[],
): number {
  return records
    .filter((record) => record.effectiveStatus === "VALID")
    .reduce((sum, record) => sum + Number(record.validHours || 0), 0);
}

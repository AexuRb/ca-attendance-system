export type KioskStep = "input" | "choose" | "confirm" | "success";

export interface ScheduleAssignee {
  studentNo: string;
  name: string;
}

export interface ScheduleSlot {
  key?: string;
  startTime: string;
  endTime: string;
  assignees: ScheduleAssignee[];
}

export interface ScheduleDay {
  date: string;
  weekdayName: string;
  slots: ScheduleSlot[];
}

export interface AttendanceSubmitResult {
  name: string;
  action: "CHECK_IN" | "CHECK_OUT";
  submittedAt: string;
}

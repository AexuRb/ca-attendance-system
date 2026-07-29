import type { ScheduleAssigneeOption } from "./scheduleAssignees";

export interface ScheduleAssignee {
  id: number;
  userId?: number;
  studentNo?: string;
  name: string;
  sortOrder: number;
}

export interface ScheduleSlot {
  id: number;
  weekday: number;
  weekdayName: string;
  dutyDate?: string;
  startTime: string;
  endTime: string;
  title: string;
  location?: string;
  note?: string;
  enabled: boolean;
  assignees: ScheduleAssignee[];
}

export interface ScheduleEditorForm {
  id: number | null;
  weekday: number;
  period: string;
  title: string;
  location: string;
  assignees: ScheduleAssigneeOption[];
  enabled: boolean;
  note: string;
}

export interface ScheduleImportMember {
  studentNo: string;
  name: string;
}

export interface ScheduleImportGroup {
  weekday: number;
  weekdayName: string;
  startTime: string;
  endTime: string;
  members: ScheduleImportMember[];
}

export interface ScheduleImportIssue {
  row: number;
  field: string;
  message: string;
}

export interface ScheduleImportPreview {
  valid: boolean;
  sourceRows: number;
  groupCount: number;
  memberCount: number;
  groups: ScheduleImportGroup[];
  issues: ScheduleImportIssue[];
}

export interface ScheduleImportResult {
  replacedGroups: number;
  assignedMembers: number;
}

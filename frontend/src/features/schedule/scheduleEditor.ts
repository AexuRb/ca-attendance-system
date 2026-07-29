import type { ScheduleAssigneeOption } from "./scheduleAssignees";

export interface ScheduleEditorValue {
  weekday: number;
  period: string;
  title: string;
  location: string;
  note: string;
  enabled: boolean;
  assignees: ScheduleAssigneeOption[];
}

export function schedulePayload(form: ScheduleEditorValue) {
  const [startTime, endTime] = form.period.split("-");
  return {
    weekday: form.weekday,
    startTime,
    endTime,
    title: form.title,
    location: form.location,
    note: form.note,
    enabled: form.enabled,
    assignees: form.assignees.map((person) => ({
      studentNo: person.studentNo,
      name: person.name,
    })),
  };
}

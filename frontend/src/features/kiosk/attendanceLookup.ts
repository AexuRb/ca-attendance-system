export interface AttendanceLookupResult {
  exists: boolean;
  dutyDay?: boolean;
  withinDutyPeriod?: boolean;
  studentNo?: string;
  name?: string;
  action?: "CHECK_IN" | "CHECK_OUT";
  message: string;
  matches?: AttendanceMemberOption[];
}

export interface AttendanceMemberOption {
  studentNo: string;
  name: string;
}

export function canConfirmAttendance(result: AttendanceLookupResult): boolean {
  return (
    result.exists && Boolean(result.studentNo && result.name && result.action)
  );
}

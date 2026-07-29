export interface AttendanceLookupResult {
  exists: boolean;
  dutyDay?: boolean;
  withinDutyPeriod?: boolean;
  memberToken?: string;
  maskedStudentNo?: string;
  name?: string;
  action?: "CHECK_IN" | "CHECK_OUT";
  message: string;
  matches?: AttendanceMemberOption[];
}

export interface AttendanceMemberOption {
  memberToken: string;
  maskedStudentNo: string;
  name: string;
  grade?: string;
}

export function canConfirmAttendance(result: AttendanceLookupResult): boolean {
  return (
    result.exists && Boolean(result.memberToken && result.name && result.action)
  );
}

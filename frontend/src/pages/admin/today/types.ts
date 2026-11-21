import type { AssessmentStatus } from "../../../features/members/memberDirectory";

export interface TodayDashboardData {
  todayRecordCount?: number;
  todayValidHours?: number;
  todayPendingCount?: number;
  todayOpenCount?: number;
  ongoingRepairCount?: number;
}


export interface TodayRoster {
  date: string;
  items: TodayRosterMember[];
}

export interface TodayRosterMember {
  id: number;
  name: string;
  disassemblyAssessment: AssessmentStatus;
  osReinstallAssessment: AssessmentStatus;
}

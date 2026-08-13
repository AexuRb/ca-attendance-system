export interface TrainingSession {
  id: number;
  title: string;
  trainingDate: string;
  startTime?: string;
  endTime?: string;
  location?: string;
  speaker?: string;
  description?: string;
  status: string;
  participantCount: number;
  totalDurationHours: number;
  createdByName?: string;
  updatedByName?: string;
  createdAt: string;
  updatedAt: string;
}

export interface TrainingParticipant {
  id: number;
  sessionId: number;
  userId?: number;
  studentNo?: string;
  name: string;
  durationHours: number;
  remark?: string;
}

export interface TrainingSessionForm {
  id: number | null;
  title: string;
  trainingDate: string;
  startTime: string;
  endTime: string;
  location: string;
  speaker: string;
  description: string;
}

export interface TrainingParticipantForm {
  id: number | null;
  studentNo: string;
  name: string;
  durationHours: number;
  remark: string;
}

export interface TrainingPage<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
  hasMore: boolean;
}

export interface TrainingFilters {
  keyword: string;
  from: string;
  to: string;
}

export interface TrainingWorkspaceRouteState extends TrainingFilters {
  sessionId: number | null;
  sessionPage: number;
  participantPage: number;
  participantKeyword: string;
}

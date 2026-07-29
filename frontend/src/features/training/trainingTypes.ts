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
  presentCount: number;
  absentCount: number;
  leaveCount: number;
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
  attendanceStatus: string;
  durationHours: number;
  remark?: string;
  source?: string;
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

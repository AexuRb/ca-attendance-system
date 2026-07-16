export type Role = "MEMBER" | "MINISTER" | "PRESIDENT" | "ADMIN";

export interface UserSession {
  id: number;
  studentNo: string;
  name: string;
  role: Role;
  mustChangePassword?: boolean;
  phone?: string;
  major?: string;
  grade?: string;
  qq?: string;
}

export interface AccessContext {
  mode: "LOCAL" | "REMOTE_ADMIN";
  kioskAvailable: boolean;
  allowedRemoteRoles: Role[];
}

export interface AcademicTerm {
  id: number;
  code: string;
  name: string;
  startDate: string;
  endDate: string;
  status: "DRAFT" | "ACTIVE" | "SETTLING" | "SEALED";
  legacy: boolean;
  settlingStartedAt?: string;
  sealedAt?: string;
  reopenedAt?: string;
  reopenReason?: string;
}

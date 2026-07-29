export interface OperationLog {
  id: number;
  operatorUserId?: number;
  operatorStudentNo?: string;
  operatorName?: string;
  actionType: string;
  targetType?: string;
  targetId?: number;
  beforeData?: string;
  afterData?: string;
  reason?: string;
  createdAt: string;
}

export interface OperationLogPage {
  items: OperationLog[];
  total: number;
  page: number;
  pageSize: number;
}

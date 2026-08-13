export type RepairStatus = "REPAIRING" | "COMPLETED" | "CANCELED";
export type AgreementType = "REPAIR" | "DISCLAIMER";
export type StoredAgreementType =
  | AgreementType
  | "PERSONAL_DEVICE"
  | "PUBLIC_DEVICE";

export interface RepairCase {
  id: number;
  caseNo: string;
  agreementType: StoredAgreementType;
  ownerName: string;
  ownerPhone: string;
  deviceType: string;
  deviceBrand?: string;
  deviceModel?: string;
  accessories?: string;
  faultDescription: string;
  serviceDescription?: string;
  dataBackupConfirmed: boolean;
  riskAcknowledged: boolean;
  privacyAcknowledged: boolean;
  status: RepairStatus;
  receivedAt: string;
  completedAt?: string;
  handlerUserId?: number;
  handlerName?: string;
  remark?: string;
  deletedByName?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface RepairCaseForm {
  id: number | null;
  agreementType: AgreementType;
  ownerName: string;
  ownerPhone: string;
  deviceType: string;
  deviceBrand: string;
  deviceModel: string;
  accessories: string;
  faultDescription: string;
  serviceDescription: string;
  dataBackupConfirmed: boolean;
  riskAcknowledged: boolean;
  privacyAcknowledged: boolean;
  status: RepairStatus;
  receivedAt: string;
  completedAt: string;
  handlerName: string;
  remark: string;
}

export interface RepairPage {
  items: RepairCase[];
  total: number;
  page: number;
  pageSize: number;
  hasMore: boolean;
  statusCounts: RepairStatusCounts;
}

export type RepairStatusCounts = Record<RepairStatus, number>;

export interface RepairWorkspaceRouteState extends RepairFilters {
  status: RepairStatus;
  page: number;
}

export interface RepairFilters {
  keyword: string;
  from: string;
  to: string;
}

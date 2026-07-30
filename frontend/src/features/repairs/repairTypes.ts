export type RepairStatus = "REPAIRING" | "COMPLETED" | "CANCELED";
export type AgreementType = "REPAIR" | "DISCLAIMER";

export interface RepairCase {
  id: number;
  caseNo: string;
  agreementType: AgreementType | "PUBLIC_DEVICE";
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

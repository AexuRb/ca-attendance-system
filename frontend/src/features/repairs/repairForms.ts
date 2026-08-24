import type { AccountCandidate } from "../accounts/accountCandidates";
import type { RepairCaseForm } from "./repairTypes";

export type RepairField =
  | "ownerName"
  | "ownerPhone"
  | "deviceType"
  | "faultDescription"
  | "receivedAt"
  | "completedAt"
  | "handler";
export type RepairErrors = Partial<Record<RepairField, string>>;

export function validateRepairForm(
  form: RepairCaseForm,
  handler: AccountCandidate | null,
): { errors: RepairErrors; step: 1 | 2 } {
  const errors: RepairErrors = {};
  if (!form.ownerName.trim()) errors.ownerName = "请填写联系人";
  if (form.ownerPhone && !/^[0-9+()\s-]{6,40}$/.test(form.ownerPhone)) {
    errors.ownerPhone = "联系电话格式不正确";
  }
  if (!form.deviceType.trim()) errors.deviceType = "请填写设备类型";
  if (!form.faultDescription.trim()) {
    errors.faultDescription = "请填写故障描述";
  }
  if (!form.receivedAt) errors.receivedAt = "请选择受理时间";
  if (
    form.status === "COMPLETED" &&
    form.completedAt &&
    form.receivedAt &&
    form.completedAt <= form.receivedAt
  ) {
    errors.completedAt = "完成时间必须晚于受理时间";
  }
  if (!handler) errors.handler = "请选择负责人";

  const stepOneFields: RepairField[] = [
    "ownerName",
    "ownerPhone",
    "deviceType",
    "faultDescription",
  ];
  return {
    errors,
    step: stepOneFields.some((field) => errors[field]) ? 1 : 2,
  };
}

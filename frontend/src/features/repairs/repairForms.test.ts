import { describe, expect, it } from "vitest";
import { validateRepairForm } from "./repairForms";

describe("repair form validation", () => {
  it("returns errors with the editor step that owns each field", () => {
    const result = validateRepairForm(
      {
        id: null,
        agreementType: "REPAIR",
        ownerName: "",
        ownerPhone: "abc",
        deviceType: "",
        deviceBrand: "",
        deviceModel: "",
        accessories: "",
        faultDescription: "",
        serviceDescription: "",
        dataBackupConfirmed: false,
        riskAcknowledged: false,
        privacyAcknowledged: false,
        status: "REPAIRING",
        receivedAt: "",
        completedAt: "",
        handlerName: "",
        remark: "",
      },
      null,
    );

    expect(result.step).toBe(1);
    expect(result.errors).toEqual({
      ownerName: "请填写联系人",
      ownerPhone: "联系电话格式不正确",
      deviceType: "请填写设备类型",
      faultDescription: "请填写故障描述",
      receivedAt: "请选择受理时间",
      handler: "请选择负责人",
    });
  });
});

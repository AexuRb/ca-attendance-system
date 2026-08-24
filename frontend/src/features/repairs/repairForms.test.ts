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

  it("rejects a completion time that is not after intake", () => {
    const result = validateRepairForm(
      {
        id: null,
        agreementType: "REPAIR",
        ownerName: "测试联系人",
        ownerPhone: "13800000000",
        deviceType: "笔记本电脑",
        deviceBrand: "",
        deviceModel: "",
        accessories: "",
        faultDescription: "无法开机",
        serviceDescription: "",
        dataBackupConfirmed: false,
        riskAcknowledged: false,
        privacyAcknowledged: false,
        status: "COMPLETED",
        receivedAt: "2026-08-21T14:00",
        completedAt: "2026-08-21T13:59",
        handlerName: "负责人",
        remark: "",
      },
      { id: 1, studentNo: "1000000001", name: "负责人", role: "ADMIN" },
    );

    expect(result.step).toBe(2);
    expect(result.errors.completedAt).toBe("完成时间必须晚于受理时间");
  });
});

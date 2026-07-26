import { describe, expect, it } from "vitest";
import { canConfirmAttendance } from "./attendanceLookup";

describe("attendance lookup", () => {
  it("accepts the exists field returned by the public attendance API", () => {
    expect(
      canConfirmAttendance({
        exists: true,
        studentNo: "20230001",
        name: "张三",
        action: "CHECK_IN",
        message: "请确认姓名后提交",
      }),
    ).toBe(true);
  });

  it("does not enter confirmation when the account was not found", () => {
    expect(
      canConfirmAttendance({
        exists: false,
        message: "学号不存在或账号已停用",
      }),
    ).toBe(false);
  });
});

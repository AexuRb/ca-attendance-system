import { describe, expect, it } from "vitest";
import {
  validateMemberInput,
  validatePassword,
  validateProfileInput,
} from "./userInput";

describe("user input validation", () => {
  it("shows only the relevant contact error", () => {
    expect(validateProfileInput({phone:"中文",qq:"中文"})).toEqual({phone:"仅支持数字",qq:"仅支持数字"});
    expect(validateProfileInput({phone:"1380000000",qq:"1234"})).toEqual({phone:"手机号应为11位",qq:"QQ号应为5–20位"});
    expect(validateProfileInput({phone:"12800000000",qq:"012345"})).toEqual({phone:"手机号格式不正确",qq:"QQ号不能以0开头"});
  });
  it("validates optional phones and 5 to 20 digit QQ as text", () => {
    expect(validateProfileInput({phone:" 13800000000 ",qq:"12345678901234567890"})).toEqual({});
    expect(validateProfileInput({phone:" ",qq:""})).toEqual({});
    for (const phone of ["中文", "12800000000", "1380000000", "138000000001", "1380000000a", "１３８００００００００"]) {
      expect(validateProfileInput({phone})).toHaveProperty("phone");
      expect(validateMemberInput({studentNo:"9900000001",name:"合成成员",phone})).toHaveProperty("phone");
    }
    for (const qq of ["中文","1234","012345","12345a","1".repeat(21),"１２３４５"]) {
      expect(validateProfileInput({qq})).toHaveProperty("qq");
    }
  });
  it("uses the same account and name boundaries as the backend", () => {
    expect(validateMemberInput({ studentNo: "12345", name: "张三" })).toEqual(
      expect.objectContaining({ studentNo: expect.stringContaining("6 至 32") }),
    );
    expect(
      validateMemberInput({ studentNo: "123456", name: "名".repeat(64) }),
    ).toEqual({});
    expect(
      validateMemberInput({ studentNo: "1".repeat(33), name: "名".repeat(65) }),
    ).toEqual(
      expect.objectContaining({
        studentNo: expect.any(String),
        name: expect.stringContaining("64"),
      }),
    );
  });

  it("does not reject an unchanged historical account while editing", () => {
    expect(
      validateMemberInput(
        { studentNo: "old-admin", name: "历史管理员" },
        { validateStudentNo: false },
      ),
    ).toEqual({});
  });

  it("validates profile and password field lengths", () => {
    expect(validatePassword("12345")).toContain("6 至 64");
    expect(validatePassword("      ")).toContain("6 至 64");
    expect(validatePassword("1".repeat(64))).toBe("");
    expect(validateProfileInput({ college: "院".repeat(129) })).toEqual(
      expect.objectContaining({ college: expect.stringContaining("128") }),
    );
  });

  it("uses the backend dynamic grade range for member editing", () => {
    const currentYear = new Date().getFullYear();

    expect(
      validateMemberInput({
        studentNo: "123456",
        name: "张三",
        grade: `${currentYear - 30}级`,
      }),
    ).toEqual({});
    expect(
      validateMemberInput({
        studentNo: "123456",
        name: "张三",
        grade: `${currentYear + 3}级`,
      }),
    ).toEqual(expect.objectContaining({ grade: expect.stringContaining("年级范围") }));
  });
});

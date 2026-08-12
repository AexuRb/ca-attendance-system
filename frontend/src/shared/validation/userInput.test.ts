import { describe, expect, it } from "vitest";
import {
  validateMemberInput,
  validatePassword,
  validateProfileInput,
} from "./userInput";

describe("user input validation", () => {
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
});

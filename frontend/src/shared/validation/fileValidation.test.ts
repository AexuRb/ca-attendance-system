import { describe, expect, it } from "vitest";
import {
  BACKUP_MAX_BYTES,
  EXCEL_MAX_BYTES,
  backupFileError,
  excelFileError,
} from "./fileValidation";

describe("file validation", () => {
  it("accepts supported files at the configured size boundary", () => {
    expect(excelFileError({ name: "成员.XLSX", size: EXCEL_MAX_BYTES }, "成员 Excel 文件")).toBe("");
    expect(backupFileError({ name: "backup.zip", size: BACKUP_MAX_BYTES })).toBe("");
  });

  it("rejects unsupported extensions and oversized files", () => {
    expect(excelFileError({ name: "成员.csv", size: 10 }, "成员 Excel 文件")).toContain(".xlsx");
    expect(excelFileError({ name: "成员.xlsx", size: EXCEL_MAX_BYTES + 1 }, "成员 Excel 文件")).toContain("5 MB");
    expect(backupFileError({ name: "backup.zip", size: BACKUP_MAX_BYTES + 1 })).toContain("128 MB");
  });
});

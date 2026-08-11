import { describe, expect, it } from "vitest";
import {
  canDeleteBackup,
  canRestoreBackup,
  canViewRepairRecycleBin,
} from "./dataPermissions";

describe("data page permissions", () => {
  it("reserves restore and recycle operations for administrators", () => {
    for (const role of ["MEMBER", "MINISTER", "PRESIDENT"] as const) {
      expect(canDeleteBackup(role)).toBe(false);
      expect(canRestoreBackup(role)).toBe(false);
      expect(canViewRepairRecycleBin(role)).toBe(false);
    }
    expect(canDeleteBackup("ADMIN")).toBe(true);
    expect(canRestoreBackup("ADMIN")).toBe(true);
    expect(canViewRepairRecycleBin("ADMIN")).toBe(true);
  });
});

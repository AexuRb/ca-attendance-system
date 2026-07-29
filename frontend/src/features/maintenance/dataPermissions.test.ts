import { describe, expect, it } from "vitest";
import {
  canRestoreBackup,
  canViewRepairRecycleBin,
} from "./dataPermissions";

describe("data page permissions", () => {
  it("reserves restore and recycle operations for administrators", () => {
    expect(canRestoreBackup("ADMIN")).toBe(true);
    expect(canViewRepairRecycleBin("ADMIN")).toBe(true);
    expect(canRestoreBackup("PRESIDENT")).toBe(false);
    expect(canViewRepairRecycleBin("PRESIDENT")).toBe(false);
  });
});

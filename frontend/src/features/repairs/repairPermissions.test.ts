import { describe, expect, it } from "vitest";
import { canExportRepairs } from "./repairPermissions";

describe("repair permissions", () => {
  it("allows only presidents and administrators to export repairs", () => {
    expect(canExportRepairs("ADMIN")).toBe(true);
    expect(canExportRepairs("PRESIDENT")).toBe(true);
    expect(canExportRepairs("MINISTER")).toBe(false);
    expect(canExportRepairs("MEMBER")).toBe(false);
  });
});

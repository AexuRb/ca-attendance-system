import { describe, expect, it } from "vitest";
import {
  canDeleteRepairs,
  canExportRepairs,
  canManageRepairs,
} from "./repairPermissions";

describe("repair permissions", () => {
  it("allows ministers, presidents and administrators to manage repairs", () => {
    expect(canManageRepairs("ADMIN")).toBe(true);
    expect(canManageRepairs("PRESIDENT")).toBe(true);
    expect(canManageRepairs("MINISTER")).toBe(true);
    expect(canManageRepairs("MEMBER")).toBe(false);
  });

  it("allows only presidents and administrators to export repairs", () => {
    expect(canExportRepairs("ADMIN")).toBe(true);
    expect(canExportRepairs("PRESIDENT")).toBe(true);
    expect(canExportRepairs("MINISTER")).toBe(false);
    expect(canExportRepairs("MEMBER")).toBe(false);
  });

  it("allows only presidents and administrators to move repairs to recycle bin", () => {
    expect(canDeleteRepairs("ADMIN")).toBe(true);
    expect(canDeleteRepairs("PRESIDENT")).toBe(true);
    expect(canDeleteRepairs("MINISTER")).toBe(false);
    expect(canDeleteRepairs("MEMBER")).toBe(false);
  });
});

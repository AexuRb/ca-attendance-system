import { describe, expect, it } from "vitest";
import { safeStorageGet, safeStorageSet } from "./storage";

describe("safe storage", () => {
  it("falls back to memory when browser storage throws", () => {
    const blocked = {
      getItem: () => {
        throw new Error("blocked");
      },
      setItem: () => {
        throw new Error("blocked");
      },
    };

    safeStorageSet("sidebar", "true", blocked);
    expect(safeStorageGet("sidebar", blocked)).toBe("true");
  });
});

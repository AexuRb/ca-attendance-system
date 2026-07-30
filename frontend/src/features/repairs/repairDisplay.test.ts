import { describe, expect, it } from "vitest";
import {
  isLongRunningRepair,
  maskRepairPhone,
  repairAgeDays,
  repairAgeLabel,
} from "./repairDisplay";

describe("repair display helpers", () => {
  it("masks all but the final four phone digits", () => {
    expect(maskRepairPhone("13800001234")).toBe("**** **** 1234");
    expect(maskRepairPhone("")).toBe("未填写");
  });

  it("uses calendar days for current and completed repairs", () => {
    const repairing = {
      status: "REPAIRING" as const,
      receivedAt: "2026-07-20T18:00:00",
    };
    const completed = {
      status: "COMPLETED" as const,
      receivedAt: "2026-07-20T18:00:00",
      completedAt: "2026-07-23T09:00:00",
    };

    expect(repairAgeDays(repairing, new Date("2026-07-27T08:00:00"))).toBe(7);
    expect(repairAgeLabel(completed)).toBe("处理历时 3 天");
    expect(
      isLongRunningRepair(repairing, new Date("2026-07-27T08:00:00")),
    ).toBe(true);
  });
});

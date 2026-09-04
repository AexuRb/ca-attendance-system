import { describe, expect, it } from "vitest";
import { buildTodayQuickActions } from "./todayQuickActions";

describe("today quick actions", () => {
  it("prioritizes live attention items", () => {
    const items = buildTodayQuickActions({
      todayPendingCount: 4,
      todayOpenCount: 2,
      ongoingRepairCount: 3,
    }, 0, true, "ADMIN");

    expect(items.map((item) => item.id)).toEqual(["reviews", "attendance-open", "repairs"]);
    expect(items[0]?.label).toContain("4");
  });

  it("does not expose schedule attention to ministers", () => {
    const items = buildTodayQuickActions({}, 5, false, "MINISTER");

    expect(items.some((item) => item.id === "schedules")).toBe(false);
    expect(items).toHaveLength(3);
  });
});

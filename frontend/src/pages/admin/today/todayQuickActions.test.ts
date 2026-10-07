import { describe, expect, it } from "vitest";
import { buildTodayQuickActions } from "./todayQuickActions";
import { resolveCommand } from "../../../features/command-center/commandParser";

describe("today quick actions", () => {
  it("retains the fourth known reminder for the overflow entry", () => {
    const actions = buildTodayQuickActions({ todayPendingCount: 1, todayOpenCount: 2, ongoingRepairCount: 3 }, 1, true, "ADMIN");
    expect(actions.map(item => item.id)).toEqual(["reviews", "attendance-open", "schedules", "repairs"]);
  });

  it("prioritizes live attention items", () => {
    const items = buildTodayQuickActions({
      todayPendingCount: 4,
      todayOpenCount: 2,
      ongoingRepairCount: 3,
    }, 0, true, "ADMIN");

    expect(items.map((item) => item.id)).toEqual(["reviews", "attendance-open", "repairs"]);
    expect(items[0]?.detail).toContain("4");
  });

  it("does not expose schedule attention to ministers", () => {
    const items = buildTodayQuickActions({}, 5, false, "MINISTER");

    expect(items.some((item) => item.id === "schedules")).toBe(false);
    expect(items).toHaveLength(3);
  });

  it("carries the dashboard date and status into the open-record task", () => {
    const action = buildTodayQuickActions({ todayOpenCount: 2 }, 0, false, "MINISTER")
      .find(item => item.id === "attendance-open")!;
    expect(resolveCommand(action.command, "MINISTER", new Date(2026, 8, 30, 10))).toMatchObject({
      kind: "resolved",
      target: { name: "attendance", query: { status: "INCOMPLETE", from: "2026-09-30", to: "2026-09-30" } },
    });
    expect(action.detail).toContain("今日");
  });

  it("keeps the successfully loaded date when a reminder is opened on a later day", () => {
    const action = buildTodayQuickActions({ todayOpenCount: 2 }, 0, false, "MINISTER", "2026-09-29")
      .find(item => item.id === "attendance-open")!;
    expect(resolveCommand(action.command, "MINISTER", new Date(2026, 8, 30, 10))).toMatchObject({
      kind: "resolved", target: { query: { status: "INCOMPLETE", from: "2026-09-29", to: "2026-09-29" } },
    });
    expect(action.detail).toContain("2026-09-29");
  });

  it("distinguishes today's reminder count from the complete review queue", () => {
    const action = buildTodayQuickActions({ todayPendingCount: 4 }, 0, false, "MINISTER")
      .find(item => item.id === "reviews")!;
    expect(action.label).toBe("处理待审核记录");
    expect(action.detail).toContain("今日 4 条");
    expect(action.detail).toContain("完整队列");
    expect(resolveCommand(action.command, "MINISTER")).toMatchObject({
      kind: "resolved", target: { name: "reviews" },
    });
  });
});

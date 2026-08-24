import { describe, expect, it } from "vitest";
import { dateRangeError } from "./dateRange";

describe("dateRangeError", () => {
  it("rejects a start date later than the end date", () => {
    expect(dateRangeError("2026-08-22", "2026-08-21")).toBe(
      "开始日期不能晚于结束日期",
    );
  });

  it("allows equal, ordered, or incomplete date ranges", () => {
    expect(dateRangeError("2026-08-21", "2026-08-21")).toBe("");
    expect(dateRangeError("2026-08-20", "2026-08-21")).toBe("");
    expect(dateRangeError("", "2026-08-21")).toBe("");
  });
});

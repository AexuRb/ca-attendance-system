import { describe, expect, it } from "vitest";
import {
  currentTrainingMonth,
  shiftTrainingMonth,
  trainingRangeLabel,
} from "./trainingCalendar";

describe("trainingCalendar", () => {
  it("builds complete month ranges including leap years", () => {
    expect(currentTrainingMonth(new Date(2024, 1, 12))).toEqual({
      from: "2024-02-01",
      to: "2024-02-29",
    });
    expect(shiftTrainingMonth("2026-01-18", -1)).toEqual({
      from: "2025-12-01",
      to: "2025-12-31",
    });
  });

  it("labels monthly and custom date ranges", () => {
    expect(trainingRangeLabel("2026-08-01", "2026-08-31")).toBe(
      "2026年8月",
    );
    expect(trainingRangeLabel("2026-07-20", "2026-08-26")).toBe(
      "7月20日 至 8月26日",
    );
  });
});

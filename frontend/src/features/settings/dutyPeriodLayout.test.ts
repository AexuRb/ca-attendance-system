import { describe, expect, it } from "vitest";
import { layoutDutyPeriodLanes } from "./dutyPeriodLayout";

describe("layoutDutyPeriodLanes", () => {
  it("keeps touching periods on a single full-width lane", () => {
    expect(
      layoutDutyPeriodLanes([
        { startTime: "14:00", endTime: "16:00", enabled: true },
        { startTime: "16:00", endTime: "18:00", enabled: true },
      ]),
    ).toEqual([
      { laneIndex: 0, laneCount: 1 },
      { laneIndex: 0, laneCount: 1 },
    ]);
  });

  it("places overlapping and duplicate periods in separate lanes", () => {
    expect(
      layoutDutyPeriodLanes([
        { startTime: "14:00", endTime: "16:00", enabled: true },
        { startTime: "15:00", endTime: "17:00", enabled: true },
      ]),
    ).toEqual([
      { laneIndex: 0, laneCount: 2 },
      { laneIndex: 1, laneCount: 2 },
    ]);

    expect(
      layoutDutyPeriodLanes([
        { startTime: "14:00", endTime: "16:00", enabled: true },
        { startTime: "14:00", endTime: "16:00", enabled: false },
      ]),
    ).toEqual([
      { laneIndex: 0, laneCount: 2 },
      { laneIndex: 1, laneCount: 2 },
    ]);
  });

  it("reuses lanes across a connected overlap group", () => {
    expect(
      layoutDutyPeriodLanes([
        { startTime: "14:00", endTime: "16:00", enabled: true },
        { startTime: "15:00", endTime: "17:00", enabled: true },
        { startTime: "16:00", endTime: "18:00", enabled: true },
      ]),
    ).toEqual([
      { laneIndex: 0, laneCount: 2 },
      { laneIndex: 1, laneCount: 2 },
      { laneIndex: 0, laneCount: 2 },
    ]);
  });

  it("uses three lanes when three periods overlap at the same time", () => {
    expect(
      layoutDutyPeriodLanes([
        { startTime: "14:00", endTime: "18:00", enabled: true },
        { startTime: "15:00", endTime: "17:00", enabled: true },
        { startTime: "16:00", endTime: "19:00", enabled: true },
      ]),
    ).toEqual([
      { laneIndex: 0, laneCount: 3 },
      { laneIndex: 1, laneCount: 3 },
      { laneIndex: 2, laneCount: 3 },
    ]);
  });

  it("leaves invalid periods at the default layout", () => {
    expect(
      layoutDutyPeriodLanes([
        { startTime: "", endTime: "16:00", enabled: true },
        { startTime: "18:00", endTime: "17:00", enabled: true },
      ]),
    ).toEqual([
      { laneIndex: 0, laneCount: 1 },
      { laneIndex: 0, laneCount: 1 },
    ]);
  });
});

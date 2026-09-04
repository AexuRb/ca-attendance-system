import type { DutyPeriod } from "./dutyPeriods";

export interface DutyPeriodLane {
  laneIndex: number;
  laneCount: number;
}

interface DrawablePeriod {
  index: number;
  start: number;
  end: number;
}

export function layoutDutyPeriodLanes(periods: DutyPeriod[]): DutyPeriodLane[] {
  const lanes = periods.map(() => ({ laneIndex: 0, laneCount: 1 }));
  const drawable = periods
    .flatMap<DrawablePeriod>((period, index) => {
      const start = toMinutes(period.startTime);
      const end = toMinutes(period.endTime);
      if (start === null || end === null || end <= start) return [];
      return [{ index, start, end }];
    })
    .sort(
      (left, right) =>
        left.start - right.start || left.end - right.end || left.index - right.index,
    );

  let overlapGroup: DrawablePeriod[] = [];
  let overlapGroupEnd = -1;

  const assignGroup = () => {
    if (!overlapGroup.length) return;

    const laneEnds: number[] = [];
    const assignments = overlapGroup.map((period) => {
      let laneIndex = laneEnds.findIndex((end) => end <= period.start);
      if (laneIndex === -1) laneIndex = laneEnds.length;
      laneEnds[laneIndex] = period.end;
      return { index: period.index, laneIndex };
    });

    const laneCount = laneEnds.length;
    assignments.forEach(({ index, laneIndex }) => {
      lanes[index] = { laneIndex, laneCount };
    });
  };

  drawable.forEach((period) => {
    if (overlapGroup.length && period.start >= overlapGroupEnd) {
      assignGroup();
      overlapGroup = [];
      overlapGroupEnd = -1;
    }

    overlapGroup.push(period);
    overlapGroupEnd = Math.max(overlapGroupEnd, period.end);
  });
  assignGroup();

  return lanes;
}

function toMinutes(value: string) {
  const [hour, minute] = value.slice(0, 5).split(":").map(Number);
  if (!Number.isFinite(hour) || !Number.isFinite(minute)) return null;
  return (hour ?? 0) * 60 + (minute ?? 0);
}

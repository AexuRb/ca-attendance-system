import { describe, expect, it } from "vitest";
import { schedulePayload } from "./scheduleEditor";

describe("schedule editor", () => {
  it("preserves an explicitly hidden schedule", () => {
    expect(
      schedulePayload({
        weekday: 2,
        period: "14:00-16:00",
        title: "部长值班",
        location: "协会办公室",
        note: "",
        enabled: false,
        assignees: [
          { studentNo: "1002", name: "李四", role: "MINISTER" },
        ],
      }),
    ).toMatchObject({
      startTime: "14:00",
      endTime: "16:00",
      enabled: false,
    });
  });
});

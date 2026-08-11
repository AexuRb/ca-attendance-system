import { describe, expect, it } from "vitest";
import {
  attendanceActionAccess,
  attendancePageQuery,
  localDateTimeInput,
  manualCheckoutStatus,
  totalAttendancePages,
} from "./attendanceRecords";

describe("attendance records", () => {
  it("builds a trimmed page query without empty filters", () => {
    expect(
      attendancePageQuery(
        {
          from: "2026-07-01",
          to: "2026-07-29",
          keyword: "  1004  ",
          status: "",
        },
        2,
        20,
      ).toString(),
    ).toBe(
      "from=2026-07-01&to=2026-07-29&page=2&pageSize=20&studentNo=1004",
    );
  });

  it("keeps the page count usable when the result is empty", () => {
    expect(totalAttendancePages(0, 20)).toBe(1);
    expect(totalAttendancePages(41, 20)).toBe(3);
  });

  it("uses local time for a new manual record", () => {
    expect(localDateTimeInput(new Date(2026, 6, 29, 9, 7))).toBe(
      "2026-07-29T09:07",
    );
  });

  it("keeps checkout time and submission status consistent", () => {
    expect(manualCheckoutStatus("NOT_SUBMITTED", "2026-08-10T16:00")).toBe(
      "APPROVED",
    );
    expect(manualCheckoutStatus("REJECTED", "2026-08-10T16:00")).toBe(
      "REJECTED",
    );
    expect(manualCheckoutStatus("APPROVED", "")).toBe("NOT_SUBMITTED");
  });

  it("limits minister actions to this week's member and minister records", () => {
    const now = new Date(2026, 6, 29, 12, 0);

    expect(attendanceActionAccess("MINISTER", "MEMBER", "2026-07-27", now)).toEqual(
      { allowed: true, reason: "" },
    );
    expect(
      attendanceActionAccess("MINISTER", "PRESIDENT", "2026-07-29", now),
    ).toEqual({
      allowed: false,
      reason: "部长不能修改或删除会长、管理员的记录",
    });
    expect(
      attendanceActionAccess("MINISTER", "MEMBER", "2026-07-20", now),
    ).toEqual({
      allowed: false,
      reason: "部长只能修改或删除本周记录",
    });
    expect(
      attendanceActionAccess("PRESIDENT", "ADMIN", "2026-07-01", now),
    ).toEqual({ allowed: true, reason: "" });
  });
});

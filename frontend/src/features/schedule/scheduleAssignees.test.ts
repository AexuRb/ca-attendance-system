import { describe, expect, it } from "vitest";
import {
  filterScheduleAssignees,
  mergeScheduleAssignees,
  type ScheduleAssigneeOption,
} from "./scheduleAssignees";

const candidates: ScheduleAssigneeOption[] = [
  { studentNo: "1001", name: "张部长", role: "MINISTER" },
  { studentNo: "1002", name: "李会长", role: "PRESIDENT" },
];

describe("schedule assignees", () => {
  it("matches either name or student number", () => {
    expect(filterScheduleAssignees(candidates, "张")).toEqual([candidates[0]]);
    expect(filterScheduleAssignees(candidates, "1002")).toEqual([
      candidates[1],
    ]);
  });

  it("preserves existing assignees while removing duplicates", () => {
    expect(
      mergeScheduleAssignees(candidates, [
        { studentNo: "1001", name: "旧姓名" },
        { studentNo: "1003", name: "已停用成员" },
      ]),
    ).toEqual([
      candidates[0],
      candidates[1],
      { studentNo: "1003", name: "已停用成员" },
    ]);
  });
});

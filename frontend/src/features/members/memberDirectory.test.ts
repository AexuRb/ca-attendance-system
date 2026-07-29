import { describe, expect, it } from "vitest";
import {
  bulkStatusPayload,
  selectableMemberIds,
  togglePageSelection,
} from "./memberDirectory";

describe("member directory selection", () => {
  const members = [
    { id: 1, role: "MEMBER" },
    { id: 2, role: "ADMIN" },
    { id: 3, role: "MINISTER" },
  ];

  it("only selects members the current operator can manage", () => {
    expect(selectableMemberIds(members, "PRESIDENT", 9)).toEqual([1, 3]);
    expect(selectableMemberIds(members, "ADMIN", 2)).toEqual([1, 3]);
  });

  it("selects and clears the current page without dropping other pages", () => {
    expect(togglePageSelection(new Set([8]), [1, 3], true)).toEqual(
      new Set([8, 1, 3]),
    );
    expect(togglePageSelection(new Set([8, 1, 3]), [1, 3], false)).toEqual(
      new Set([8]),
    );
  });

  it("builds an explicit bulk status request", () => {
    expect(
      bulkStatusPayload(new Set([3, 1]), "DISABLED", "学期账号整理"),
    ).toEqual({
      ids: [1, 3],
      status: "DISABLED",
      reason: "学期账号整理",
    });
  });
});

import { describe, expect, it } from "vitest";
import { buildBulkApprovalRequest } from "./reviewBulkApproval";

describe("bulk attendance review", () => {
  it("never expands an explicit selection into the whole queue", () => {
    expect(buildBulkApprovalRequest([3, 3, 7])).toEqual({ ids: [3, 7], part: "ALL", scope: "SELECTED" });
    expect(buildBulkApprovalRequest([])).toEqual({ ids: [], part: "ALL", scope: "SELECTED" });
  });
  it("uses the backend contract for the complete pending queue", () => {
    expect(buildBulkApprovalRequest()).toEqual({
      ids: [],
      part: "ALL",
      scope: "ALL_PENDING",
    });
  });
});

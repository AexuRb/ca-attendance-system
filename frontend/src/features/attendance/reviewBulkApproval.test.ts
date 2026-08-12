import { describe, expect, it } from "vitest";
import { buildBulkApprovalRequest } from "./reviewBulkApproval";

describe("bulk attendance review", () => {
  it("uses the backend contract for the complete pending queue", () => {
    expect(buildBulkApprovalRequest()).toEqual({
      ids: [],
      part: "ALL",
      scope: "ALL_PENDING",
    });
  });
});

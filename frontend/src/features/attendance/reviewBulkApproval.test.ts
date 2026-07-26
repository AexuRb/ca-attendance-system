import { describe, expect, it } from "vitest";
import { buildBulkApprovalRequest } from "./reviewBulkApproval";

describe("bulk attendance review", () => {
  it("uses the backend ALL contract and removes duplicate record ids", () => {
    expect(
      buildBulkApprovalRequest([{ id: 12 }, { id: 18 }, { id: 12 }]),
    ).toEqual({
      ids: [12, 18],
      part: "ALL",
    });
  });
});

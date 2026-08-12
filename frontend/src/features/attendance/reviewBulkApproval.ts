export function buildBulkApprovalRequest() {
  return {
    ids: [],
    part: "ALL" as const,
    scope: "ALL_PENDING" as const,
  };
}

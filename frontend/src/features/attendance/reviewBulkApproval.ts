export function buildBulkApprovalRequest(ids?: number[]) {
  return {
    ids: ids === undefined ? [] : [...new Set(ids)],
    part: "ALL" as const,
    scope: ids === undefined ? "ALL_PENDING" as const : "SELECTED" as const,
  };
}

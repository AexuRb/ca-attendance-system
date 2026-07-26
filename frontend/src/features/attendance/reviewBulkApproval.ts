export interface PendingReviewRecord {
  id: number;
}

export function buildBulkApprovalRequest(
  records: readonly PendingReviewRecord[],
) {
  return {
    ids: [...new Set(records.map((record) => record.id))],
    part: "ALL" as const,
  };
}

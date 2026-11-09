export interface RestoreContactReview {
  fileHash: string;
  phoneCount: number;
  qqCount: number;
  issues: { rowNumber: number; field: string; reason: string }[];
  truncated: boolean;
}

export interface RestoreConfirmation {
  clearInvalidContacts: boolean;
  reviewHash: string;
}

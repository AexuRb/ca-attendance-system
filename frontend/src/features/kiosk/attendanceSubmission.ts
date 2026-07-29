export interface SubmissionAttempt {
  memberToken: string;
  requestId: string;
}

export function ensureSubmissionAttempt(
  current: SubmissionAttempt | null,
  memberToken: string,
  createId: () => string = createRequestId,
): SubmissionAttempt {
  if (current?.memberToken === memberToken) return current;
  return { memberToken, requestId: createId() };
}

function createRequestId(): string {
  return (
    crypto.randomUUID?.() ||
    `${Date.now()}-${Math.random().toString(36).slice(2)}`
  );
}

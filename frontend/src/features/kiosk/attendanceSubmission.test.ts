import { describe, expect, it, vi } from "vitest";
import { ensureSubmissionAttempt } from "./attendanceSubmission";

describe("attendance submission attempt", () => {
  it("reuses the same request id when a failed submission is retried", () => {
    const createId = vi.fn(() => "request-001");
    const first = ensureSubmissionAttempt(null, "sel_member_1", createId);
    const retry = ensureSubmissionAttempt(first, "sel_member_1", createId);

    expect(retry).toBe(first);
    expect(retry.requestId).toBe("request-001");
    expect(createId).toHaveBeenCalledOnce();
  });

  it("creates a new request id after the selected member changes", () => {
    const createId = vi
      .fn()
      .mockReturnValueOnce("request-001")
      .mockReturnValueOnce("request-002");
    const first = ensureSubmissionAttempt(null, "sel_member_1", createId);
    const next = ensureSubmissionAttempt(first, "sel_member_2", createId);

    expect(next).toEqual({
      memberToken: "sel_member_2",
      requestId: "request-002",
    });
  });
});

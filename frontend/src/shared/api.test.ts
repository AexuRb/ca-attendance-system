import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, api, getToken, setToken } from "./api";

afterEach(() => {
  localStorage.clear();
  vi.unstubAllGlobals();
});

describe("api client", () => {
  it("persists and clears the local session token", () => {
    setToken("local-token");
    expect(getToken()).toBe("local-token");
    setToken("");
    expect(getToken()).toBe("");
  });

  it("sends the bearer token to authenticated endpoints", async () => {
    setToken("local-token");
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    await api("/api/example");

    const headers = fetchMock.mock.calls[0][1].headers as Headers;
    expect(headers.get("Authorization")).toBe("Bearer local-token");
  });

  it("turns connection failures into a retryable network error", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("offline")));
    await expect(api("/api/example")).rejects.toMatchObject<ApiError>({
      network: true,
      status: 0,
    });
  });
});

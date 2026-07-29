import { afterEach, describe, expect, it, vi } from "vitest";
import {
  ApiError,
  api,
  getToken,
  setToken,
  setUnauthorizedHandler,
} from "./api";

afterEach(() => {
  setUnauthorizedHandler(null);
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
    await expect(api("/api/example")).rejects.toMatchObject({
      network: true,
      status: 0,
    });
  });

  it("clears an expired authenticated session and notifies once", async () => {
    setToken("expired-token");
    const onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ message: "登录状态已失效" }), {
          status: 401,
          headers: { "content-type": "application/json" },
        }),
      ),
    );

    await expect(api("/api/protected")).rejects.toMatchObject({
      status: 401,
    });
    await expect(api("/api/protected")).rejects.toMatchObject({
      status: 401,
    });

    expect(getToken()).toBe("");
    expect(onUnauthorized).toHaveBeenCalledOnce();
  });

  it("does not treat a failed login as an expired session", async () => {
    const onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ message: "账号或密码错误" }), {
          status: 401,
          headers: { "content-type": "application/json" },
        }),
      ),
    );

    await expect(api("/api/auth/login")).rejects.toMatchObject({
      status: 401,
    });
    expect(onUnauthorized).not.toHaveBeenCalled();
  });
});

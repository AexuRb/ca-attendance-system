// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  ApiError,
  api,
  configureTokenStorage,
  getToken,
  safeDownloadFilename,
  setToken,
  setUnauthorizedHandler,
} from "./api";

beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
  configureTokenStorage("REMOTE_ADMIN");
});

afterEach(() => {
  setUnauthorizedHandler(null);
  localStorage.clear();
  sessionStorage.clear();
  vi.unstubAllGlobals();
});

describe("api client", () => {
  it("normalizes download names before assigning them to the browser", () => {
    expect(safeDownloadFilename(' 培训名单_测试/场次:*?"<>|.xlsx ')).toBe(
      "培训名单_测试_场次_.xlsx",
    );
    expect(safeDownloadFilename("控制\u0000字符.xlsx")).toBe("控制_字符.xlsx");
    expect(safeDownloadFilename("超".repeat(100) + ".xlsx")).toBe(
      "超".repeat(80) + ".xlsx",
    );
  });

  it("persists and clears the local session token", () => {
    configureTokenStorage("LOCAL");
    setToken("local-token");
    expect(getToken()).toBe("local-token");
    expect(localStorage.getItem("ca_attendance_token")).toBe("local-token");
    expect(sessionStorage.getItem("ca_attendance_token")).toBeNull();
    setToken("");
    expect(getToken()).toBe("");
  });

  it("keeps remote tokens only for the current browser session", () => {
    localStorage.setItem("ca_attendance_token", "legacy-remote-token");

    configureTokenStorage("REMOTE_ADMIN");

    expect(getToken()).toBe("");
    expect(localStorage.getItem("ca_attendance_token")).toBeNull();

    setToken("remote-token");
    expect(getToken()).toBe("remote-token");
    expect(sessionStorage.getItem("ca_attendance_token")).toBe(
      "remote-token",
    );
    expect(localStorage.getItem("ca_attendance_token")).toBeNull();

    sessionStorage.clear();
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

  it("uses the backend error message from the shared error contract", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ message: "请求参数格式不正确" }), {
          status: 400,
          headers: { "content-type": "application/json" },
        }),
      ),
    );

    await expect(api("/api/example")).rejects.toMatchObject({
      message: "请求参数格式不正确",
      status: 400,
    });
  });

  it("falls back to the status when an error response is not JSON", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response("Bad Gateway", { status: 502 })),
    );

    await expect(api("/api/example")).rejects.toMatchObject({
      message: "请求失败（502）",
      status: 502,
    });
  });
});

// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest";

afterEach(() => {
  localStorage.clear();
  sessionStorage.clear();
  vi.unstubAllGlobals();
  vi.resetModules();
});

describe("session token persistence", () => {
  it("keeps initialization unknown when the status request fails, then recovers", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(localAccess()))
      .mockRejectedValueOnce(new TypeError("temporary connection failure"));
    vi.stubGlobal("fetch", fetchMock);
    const { useSession } = await import("./session");
    const session = useSession();
    session.state.setup.initialized = true;

    await session.bootstrap();

    expect(session.state.ready).toBe(false);
    expect(session.state.startupError).toBe(true);
    expect(session.state.setup.initialized).toBe(true);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    fetchMock.mockResolvedValueOnce(jsonResponse(localAccess())).mockResolvedValueOnce(jsonResponse({ initialized: true }));
    await session.bootstrap();
    expect(session.state.ready).toBe(true);
    expect(session.state.startupError).toBe(false);
  });

  it("does not assume a local entry or touch a stored token after an access failure", async () => {
    localStorage.setItem("ca_attendance_token", "synthetic-token");
    const fetchMock = vi.fn().mockRejectedValue(new TypeError("offline"));
    vi.stubGlobal("fetch", fetchMock);
    const session = (await import("./session")).useSession();
    await Promise.all([session.bootstrap(), session.bootstrap()]);
    expect(fetchMock).toHaveBeenCalledOnce();
    expect(session.state.ready).toBe(false);
    expect(session.state.startupPending).toBe(false);
    expect(localStorage.getItem("ca_attendance_token")).toBe("synthetic-token");
    fetchMock.mockResolvedValueOnce(jsonResponse(remoteAccess()));
    await session.bootstrap();
    expect(session.state.ready).toBe(true);
    expect(session.state.access.kioskAvailable).toBe(false);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(localStorage.getItem("ca_attendance_token")).toBeNull();
  });

  it("retains a local token after temporary user lookup failure and retries verification", async () => {
    localStorage.setItem("ca_attendance_token", "synthetic-token");
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(localAccess()))
      .mockResolvedValueOnce(jsonResponse({ initialized: true }))
      .mockResolvedValueOnce(new Response("unavailable", { status: 503 }));
    vi.stubGlobal("fetch", fetchMock);
    const session = (await import("./session")).useSession();
    await session.bootstrap();
    expect(session.state.ready).toBe(false);
    expect(session.state.user).toBeNull();
    expect(localStorage.getItem("ca_attendance_token")).toBe("synthetic-token");
    fetchMock.mockResolvedValueOnce(jsonResponse(localAccess()))
      .mockResolvedValueOnce(jsonResponse({ initialized: true }))
      .mockResolvedValueOnce(jsonResponse({ id: 1, role: "ADMIN" }));
    await session.bootstrap();
    expect(session.state.ready).toBe(true);
    expect(session.state.user?.id).toBe(1);
  });

  it("bounds a stalled startup request without leaving startup busy", async () => {
    vi.useFakeTimers();
    try {
      const fetchMock = vi.fn((_url, options) => new Promise((_resolve, reject) => {
        options.signal.addEventListener("abort", () => reject(new DOMException("Aborted", "AbortError")));
      }));
      vi.stubGlobal("fetch", fetchMock);
      const session = (await import("./session")).useSession();
      const boot = session.bootstrap();
      expect(session.state.startupPending).toBe(true);
      await vi.advanceTimersByTimeAsync(5000);
      await boot;
      expect(session.state.ready).toBe(false);
      expect(session.state.startupPending).toBe(false);
      expect(session.state.startupError).toBe(true);
    } finally { vi.useRealTimers(); }
  });

  it("clears a definitively rejected token and permits login after startup", async () => {
    localStorage.setItem("ca_attendance_token", "synthetic-token");
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(jsonResponse(localAccess()))
      .mockResolvedValueOnce(jsonResponse({ initialized: true }))
      .mockResolvedValueOnce(new Response("unauthorized", { status: 401 })));
    const session = (await import("./session")).useSession();
    await session.bootstrap();
    expect(session.state.ready).toBe(true);
    expect(session.state.user).toBeNull();
    expect(localStorage.getItem("ca_attendance_token")).toBeNull();
  });

  it("discards legacy persistent tokens at the remote entry", async () => {
    localStorage.setItem("ca_attendance_token", "legacy-remote-token");
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(remoteAccess()))
      .mockResolvedValueOnce(
        jsonResponse({
          id: 1,
          studentNo: "remote-admin",
          name: "远程管理员",
          role: "ADMIN",
          mustChangePassword: false,
          token: "remote-session-token",
        }),
      );
    vi.stubGlobal("fetch", fetchMock);
    const { useSession } = await import("./session");
    const session = useSession();

    await session.bootstrap();

    expect(localStorage.getItem("ca_attendance_token")).toBeNull();
    expect(sessionStorage.getItem("ca_attendance_token")).toBeNull();
    expect(fetchMock).toHaveBeenCalledOnce();

    await session.login("remote-admin", "password");

    expect(localStorage.getItem("ca_attendance_token")).toBeNull();
    expect(sessionStorage.getItem("ca_attendance_token")).toBe(
      "remote-session-token",
    );
  });

  it("restores a persistent token after confirming the local entry", async () => {
    localStorage.setItem("ca_attendance_token", "local-token");
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(localAccess()))
      .mockResolvedValueOnce(
        jsonResponse({ initialized: true }),
      )
      .mockResolvedValueOnce(
        jsonResponse({
          id: 2,
          studentNo: "local-admin",
          name: "本机管理员",
          role: "ADMIN",
          mustChangePassword: false,
        }),
      );
    vi.stubGlobal("fetch", fetchMock);
    const { useSession } = await import("./session");
    const session = useSession();

    await session.bootstrap();

    expect(session.user.value?.studentNo).toBe("local-admin");
    expect(localStorage.getItem("ca_attendance_token")).toBe("local-token");
    expect(sessionStorage.getItem("ca_attendance_token")).toBeNull();
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });
});

describe("private navigation session boundary", () => {
  it.each(["login", "initialize", "logout", "expire", "roleChange"])("clears private queries on %s, including late writes", async (action) => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 1, role: "ADMIN", token: "synthetic-token" }));
    vi.stubGlobal("fetch", fetchMock);
    const { useSession } = await import("./session");
    const { createPrivateNavigationState } = await import("../shared/navigation/privateNavigationState");
    const memory = createPrivateNavigationState<string>();
    const previous = memory.scope();
    previous.set("visit", "合成关键词");
    const session = useSession();
    session.state.user = { id: 1, role: "MEMBER", name: "合成成员", studentNo: "demo" };
    if (action === "login") await session.login("demo", "synthetic");
    if (action === "initialize") await session.initialize("demo", "合成成员", "synthetic");
    if (action === "logout") {
      fetchMock.mockRejectedValueOnce(new TypeError("offline"));
      await session.logout();
    }
    if (action === "expire") session.expireSession();
    if (action === "roleChange") await session.refreshUser();
    expect(previous.get("visit")).toBeUndefined();
    previous.set("late", "迟到结果");
    expect(memory.scope().get("late")).toBeUndefined();
  });

  it("keeps private query context when refreshing the same account and role", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse({ id: 1, role: "ADMIN", name: "更新资料", studentNo: "demo" })));
    const { useSession } = await import("./session");
    const { createPrivateNavigationState } = await import("../shared/navigation/privateNavigationState");
    const scope = createPrivateNavigationState<string>().scope();
    scope.set("visit", "合成关键词");
    const session = useSession();
    session.state.user = { id: 1, role: "ADMIN", name: "合成成员", studentNo: "demo" };
    await session.refreshUser();
    expect(scope.get("visit")).toBe("合成关键词");
  });
});

function jsonResponse(payload: unknown) {
  return new Response(JSON.stringify(payload), {
    status: 200,
    headers: { "content-type": "application/json" },
  });
}

function remoteAccess() {
  return {
    mode: "REMOTE_ADMIN",
    kioskAvailable: false,
    allowedRemoteRoles: ["PRESIDENT", "ADMIN"],
  };
}

function localAccess() {
  return {
    mode: "LOCAL",
    kioskAvailable: true,
    allowedRemoteRoles: [],
  };
}

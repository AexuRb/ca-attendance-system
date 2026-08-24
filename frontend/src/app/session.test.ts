// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest";

afterEach(() => {
  localStorage.clear();
  sessionStorage.clear();
  vi.unstubAllGlobals();
  vi.resetModules();
});

describe("session token persistence", () => {
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

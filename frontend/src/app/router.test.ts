import { describe, expect, it } from "vitest";
import { resolveRouteAccess, router } from "./router";
import type { Role } from "../shared/types";

describe("admin route permissions", () => {
  const route = (name: string) =>
    router.getRoutes().find((item) => item.name === name);

  it("keeps private member data away from ministers", () => {
    expect(route("members")?.meta.roles).toEqual(["PRESIDENT", "ADMIN"]);
  });

  it("keeps schedule management away from ministers", () => {
    expect(route("schedules")?.meta.roles).toEqual(["PRESIDENT", "ADMIN"]);
  });

  it("allows members to open only their profile in the admin shell", () => {
    const memberRoutes = router
      .getRoutes()
      .filter(
        (item) =>
          Array.isArray(item.meta.roles) &&
          (item.meta.roles as string[]).includes("MEMBER"),
      )
      .map((item) => item.name);
    expect(memberRoutes).toEqual(["profile"]);
  });

  it("limits logs to administrators", () => {
    expect(route("logs")?.meta.roles).toEqual(["ADMIN"]);
  });

  it.each([
    ["MINISTER", "members", "today"],
    ["PRESIDENT", "logs", "today"],
    ["MEMBER", "repairs", "profile"],
  ] as const)(
    "redirects %s away from the %s route",
    (role, target, fallback) => {
      const destination = resolveRouteAccess(
        router.resolve({ name: target }),
        localState(role),
      );

      expect(destination).toEqual({ name: fallback });
    },
  );

  it("redirects the remote entry away from the kiosk", () => {
    const destination = resolveRouteAccess(router.resolve({ name: "kiosk" }), {
      setup: { initialized: true },
      access: {
        mode: "REMOTE_ADMIN",
        kioskAvailable: false,
        allowedRemoteRoles: ["PRESIDENT", "ADMIN"],
      },
      user: null,
    });

    expect(destination).toEqual({ name: "login" });
  });

  function localState(role: Role) {
    return {
      setup: { initialized: true },
      access: {
        mode: "LOCAL" as const,
        kioskAvailable: true,
        allowedRemoteRoles: [],
      },
      user: {
        id: 1,
        studentNo: `test-${role.toLowerCase()}`,
        name: `${role} 测试账号`,
        role,
        mustChangePassword: false,
      },
    };
  }
});

// @vitest-environment jsdom
import { describe, expect, it } from "vitest";
import { resolveRouteAccess, safeLoginNext, router } from "./router";
import type { Role } from "../shared/types";

type RouteAccessState = Parameters<typeof resolveRouteAccess>[1];
type RouteGuardCase = {
  case: string;
  target: string;
  state: RouteAccessState;
  expected: ReturnType<typeof resolveRouteAccess>;
};

const routeGuardMatrix: RouteGuardCase[] = [
  {
    case: "redirects an uninitialized local kiosk to setup",
    target: "kiosk",
    state: routeState({ initialized: false }),
    expected: { name: "setup" },
  },
  {
    case: "keeps setup reachable before local initialization",
    target: "setup",
    state: routeState({ initialized: false }),
    expected: true,
  },
  {
    case: "redirects an unauthenticated local admin route to login with next",
    target: "members",
    state: routeState(),
    expected: { name: "login", query: { next: "/admin/members" } },
  },
  {
    case: "redirects an unauthenticated remote admin route to login with next",
    target: "today",
    state: routeState({ mode: "REMOTE_ADMIN" }),
    expected: { name: "login", query: { next: "/admin/today" } },
  },
  {
    case: "redirects a forced-password-change user to password",
    target: "today",
    state: routeState({ role: "MEMBER", mustChangePassword: true }),
    expected: { name: "password" },
  },
  {
    case: "allows a forced-password-change user to open password",
    target: "password",
    state: routeState({ role: "MEMBER", mustChangePassword: true }),
    expected: true,
  },
  {
    case: "allows a local member to open profile",
    target: "profile",
    state: routeState({ role: "MEMBER" }),
    expected: true,
  },
  {
    case: "allows a local minister to open repairs",
    target: "repairs",
    state: routeState({ role: "MINISTER" }),
    expected: true,
  },
  {
    case: "allows a local president to open members",
    target: "members",
    state: routeState({ role: "PRESIDENT" }),
    expected: true,
  },
  {
    case: "allows a local administrator to open logs",
    target: "logs",
    state: routeState({ role: "ADMIN" }),
    expected: true,
  },
  {
    case: "redirects a local member from repairs to profile",
    target: "repairs",
    state: routeState({ role: "MEMBER" }),
    expected: { name: "profile" },
  },
  {
    case: "redirects a local minister from members to today",
    target: "members",
    state: routeState({ role: "MINISTER" }),
    expected: { name: "today" },
  },
  {
    case: "redirects a local president from logs to today",
    target: "logs",
    state: routeState({ role: "PRESIDENT" }),
    expected: { name: "today" },
  },
  {
    case: "allows a remote president to open president-level members",
    target: "members",
    state: routeState({ mode: "REMOTE_ADMIN", role: "PRESIDENT" }),
    expected: true,
  },
  {
    case: "allows a remote administrator to open administrator-only logs",
    target: "logs",
    state: routeState({ mode: "REMOTE_ADMIN", role: "ADMIN" }),
    expected: true,
  },
  {
    case: "keeps a remote president out of administrator-only logs",
    target: "logs",
    state: routeState({ mode: "REMOTE_ADMIN", role: "PRESIDENT" }),
    expected: { name: "today" },
  },
  {
    case: "redirects the remote entry away from the kiosk",
    target: "kiosk",
    state: routeState({ mode: "REMOTE_ADMIN" }),
    expected: { name: "login" },
  },
];

type RouteStateOptions = {
  initialized?: boolean;
  mode?: RouteAccessState["access"]["mode"];
  kioskAvailable?: boolean;
  allowedRemoteRoles?: Role[];
  role?: Role | null;
  mustChangePassword?: boolean;
};

function routeState({
  initialized = true,
  mode = "LOCAL",
  kioskAvailable = mode === "LOCAL",
  allowedRemoteRoles = mode === "REMOTE_ADMIN" ? ["PRESIDENT", "ADMIN"] : [],
  role = null,
  mustChangePassword = false,
}: RouteStateOptions = {}): RouteAccessState {
  return {
    setup: { initialized },
    access: {
      mode,
      kioskAvailable,
      allowedRemoteRoles,
    },
    user: role
      ? {
          id: 1,
          studentNo: `test-${role.toLowerCase()}`,
          name: `${role} 测试账号`,
          role,
          mustChangePassword,
        }
      : null,
  };
}

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

});

describe("route guard behavior matrix", () => {
  it.each(routeGuardMatrix)("$case", ({ target, state, expected }) => {
    expect(resolveRouteAccess(router.resolve({ name: target }), state)).toEqual(
      expected,
    );
  });
});

describe("login redirects", () => {
  it("accepts only internal absolute paths", () => {
    expect(safeLoginNext("/admin/data?tab=backups")).toBe(
      "/admin/data?tab=backups",
    );
    expect(safeLoginNext("//example.com/steal")).toBeNull();
    expect(safeLoginNext("https://example.com/steal")).toBeNull();
    expect(safeLoginNext(["/admin/today"])).toBeNull();
  });
});

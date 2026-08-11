import { describe, expect, it } from "vitest";
import type { Role } from "../shared/types";
import { adminNavigation, navigationForRole } from "./adminNavigation";
import { router } from "./router";

const expectedRoutes: Record<Role, string[]> = {
  MEMBER: ["profile"],
  MINISTER: ["today", "reviews", "attendance", "stats", "profile", "repairs"],
  PRESIDENT: [
    "today",
    "reviews",
    "attendance",
    "stats",
    "schedules",
    "members",
    "profile",
    "repairs",
    "trainings",
    "data",
    "settings",
  ],
  ADMIN: [
    "today",
    "reviews",
    "attendance",
    "stats",
    "schedules",
    "members",
    "profile",
    "repairs",
    "trainings",
    "data",
    "settings",
    "logs",
  ],
};

describe("admin navigation permissions", () => {
  it.each(Object.entries(expectedRoutes))(
    "shows the exact %s navigation entries",
    (role, expected) => {
      const names = navigationForRole(role as Role)
        .flatMap((section) => section.items)
        .map((item) => item.name);
      expect(names).toEqual(expected);
    },
  );

  it("keeps navigation roles synchronized with route guards", () => {
    const guardedRoutes = new Map(
      router
        .getRoutes()
        .filter((route) => Array.isArray(route.meta.roles))
        .map((route) => [route.name, route.meta.roles]),
    );

    for (const item of adminNavigation.flatMap((section) => section.items)) {
      expect(guardedRoutes.get(item.name), item.name).toEqual(item.roles);
    }
  });
});

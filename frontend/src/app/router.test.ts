import { describe, expect, it } from "vitest";
import { router } from "./router";

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

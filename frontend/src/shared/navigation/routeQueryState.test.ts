import { describe, expect, it, vi } from "vitest";
import type { Router } from "vue-router";
import {
  positiveRoutePage,
  routeQuerySignature,
  stringRouteQuery,
  updateOwnedRouteQuery,
} from "./routeQueryState";

describe("routeQueryState", () => {
  it("normalizes scalar values and positive pages", () => {
    expect(stringRouteQuery(["ACTIVE", "DISABLED"])).toBe("ACTIVE");
    expect(positiveRoutePage("3")).toBe(3);
    expect(positiveRoutePage("0")).toBe(1);
    expect(positiveRoutePage("invalid")).toBe(1);
  });

  it("only replaces owned keys and preserves route intents", async () => {
    const push = vi.fn().mockResolvedValue(undefined);
    const router = { push, replace: vi.fn() } as unknown as Router;

    await updateOwnedRouteQuery(
      router,
      { intent: "new", keyword: "敏感关键词", status: "PENDING" },
      ["keyword", "status", "page"],
      { status: "VALID", page: 2 },
      "push",
    );

    expect(push).toHaveBeenCalledWith({
      query: { intent: "new", status: "VALID", page: "2" },
    });
  });

  it("does not navigate when the owned state is unchanged", async () => {
    const replace = vi.fn().mockResolvedValue(undefined);
    const router = { push: vi.fn(), replace } as unknown as Router;

    const changed = await updateOwnedRouteQuery(
      router,
      { status: "VALID" },
      ["status", "page"],
      { status: "VALID" },
      "replace",
    );

    expect(changed).toBe(false);
    expect(replace).not.toHaveBeenCalled();
    expect(routeQuerySignature({ status: "VALID" }, ["status", "page"])).toBe(
      "status=VALID&page=",
    );
  });
});

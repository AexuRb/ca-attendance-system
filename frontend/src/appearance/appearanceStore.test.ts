// @vitest-environment jsdom
import { afterEach, describe, expect, it } from "vitest";
import {
  changeGlobalAppearance,
  initializeAppearance,
  useAppearance,
} from "./appearanceStore";
import type { AppearanceId } from "./appearanceTypes";

afterEach(() => {
  delete document.documentElement.dataset.appearance;
});

describe("appearance startup", () => {
  it("loads the selected appearance before marking startup ready", async () => {
    const loaded: AppearanceId[] = [];

    await initializeAppearance(
      async () => ({ appearance: "CLASSIC", version: 1 }),
      async (appearance) => {
        loaded.push(appearance.id);
        return appearance;
      },
    );

    const { state } = useAppearance();
    expect(loaded).toEqual(["CLASSIC"]);
    expect(state.ready).toBe(true);
    expect(state.requested).toBe("CLASSIC");
    expect(state.active).toBe("CLASSIC");
    expect(state.fallback).toBe(false);
  });

  it("falls back to classic when the public request fails", async () => {
    await initializeAppearance(
      async () => {
        throw new TypeError("offline");
      },
      async (appearance) => appearance,
    );

    const { state } = useAppearance();
    expect(state.ready).toBe(true);
    expect(state.requested).toBe("CLASSIC");
    expect(state.active).toBe("CLASSIC");
    expect(state.fallback).toBe(true);
  });

  it("loads an implemented editorial appearance without fallback", async () => {
    await initializeAppearance(
      async () => ({ appearance: "EDITORIAL", version: 1 }),
      async (appearance) => appearance,
    );

    const { state } = useAppearance();
    expect(state.requested).toBe("EDITORIAL");
    expect(state.active).toBe("EDITORIAL");
    expect(state.fallback).toBe(false);
  });

  it("loads an implemented spatial appearance without fallback", async () => {
    await initializeAppearance(
      async () => ({ appearance: "SPATIAL", version: 1 }),
      async (appearance) => appearance,
    );

    const { state } = useAppearance();
    expect(state.requested).toBe("SPATIAL");
    expect(state.active).toBe("SPATIAL");
    expect(state.fallback).toBe(false);
  });

  it("normalizes an unknown appearance to classic", async () => {
    await initializeAppearance(
      async () => ({ appearance: "UNKNOWN" as AppearanceId, version: 1 }),
      async (appearance) => appearance,
    );

    const { state } = useAppearance();
    expect(state.requested).toBe("CLASSIC");
    expect(state.active).toBe("CLASSIC");
    expect(state.fallback).toBe(false);
  });

  it("falls back to classic when the selected module cannot load", async () => {
    const activations: AppearanceId[] = [];

    await initializeAppearance(
      async () => ({ appearance: "SPATIAL", version: 1 }),
      async (appearance) => {
        activations.push(appearance.id);
        if (appearance.id === "SPATIAL") throw new Error("chunk unavailable");
        return appearance;
      },
    );

    const { state } = useAppearance();
    expect(activations).toEqual(["SPATIAL", "CLASSIC"]);
    expect(state.requested).toBe("SPATIAL");
    expect(state.active).toBe("CLASSIC");
    expect(state.fallback).toBe(true);
    expect(state.error).toContain("界面资源加载失败");
  });

  it("preloads and persists a new appearance before applying it", async () => {
    await initializeAppearance(
      async () => ({ appearance: "CLASSIC", version: 1 }),
      async (appearance) => appearance,
    );
    const steps: string[] = [];

    await changeGlobalAppearance(
      "SPATIAL",
      async (appearance) => {
        steps.push(`save:${appearance}`);
        return { appearance, version: 1 };
      },
      async (appearance) => {
        steps.push(`preload:${appearance.id}`);
      },
    );

    const { state } = useAppearance();
    expect(steps).toEqual(["preload:SPATIAL", "save:SPATIAL"]);
    expect(state.active).toBe("SPATIAL");
    expect(document.documentElement.dataset.appearance).toBe("spatial");
  });

  it("keeps the current appearance when persistence fails", async () => {
    await initializeAppearance(
      async () => ({ appearance: "CLASSIC", version: 1 }),
      async (appearance) => appearance,
    );
    document.documentElement.dataset.appearance = "classic";

    await expect(
      changeGlobalAppearance(
        "EDITORIAL",
        async () => {
          throw new Error("保存失败");
        },
        async () => undefined,
      ),
    ).rejects.toThrow("保存失败");

    const { state } = useAppearance();
    expect(state.active).toBe("CLASSIC");
    expect(document.documentElement.dataset.appearance).toBe("classic");
  });
});

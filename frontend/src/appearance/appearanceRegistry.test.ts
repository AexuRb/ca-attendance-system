// @vitest-environment jsdom
import { afterEach, describe, expect, it } from "vitest";
import {
  applyAppearanceAttributes,
  normalizeAppearanceId,
  resolveAppearance,
} from "./appearanceRegistry";

afterEach(() => {
  delete document.documentElement.dataset.appearance;
  document.querySelector('meta[name="theme-color"]')?.remove();
});

describe("appearance registry", () => {
  it("normalizes unknown values to the stable classic appearance", () => {
    expect(normalizeAppearanceId("EDITORIAL")).toBe("EDITORIAL");
    expect(normalizeAppearanceId("unknown")).toBe("CLASSIC");
    expect(normalizeAppearanceId(null)).toBe("CLASSIC");
  });

  it("resolves all implemented appearances", () => {
    expect(resolveAppearance("EDITORIAL").id).toBe("EDITORIAL");
    expect(resolveAppearance("SPATIAL").id).toBe("SPATIAL");
    expect(resolveAppearance("CLASSIC").id).toBe("CLASSIC");
  });

  it("applies the active appearance and browser theme color", () => {
    const meta = document.createElement("meta");
    meta.name = "theme-color";
    document.head.append(meta);

    applyAppearanceAttributes(resolveAppearance("CLASSIC"));

    expect(document.documentElement.dataset.appearance).toBe("classic");
    expect(meta.content).toBe("#eef6fa");
  });
});

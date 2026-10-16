// @vitest-environment jsdom
import { afterEach, describe, expect, it } from "vitest";
import {
  applyAppearanceAttributes,
  normalizeAppearanceId,
  resolveAppearance,
} from "./appearanceRegistry";

afterEach(() => {
  delete document.documentElement.dataset.appearance;
  delete document.documentElement.dataset.palette;
  document.querySelector('meta[name="theme-color"]')?.remove();
});

describe("appearance registry", () => {
  it.each([
    ["CLASSIC","classic","mist"], ["EDITORIAL","editorial","sand"], ["SPATIAL","spatial","sky"],
    ["PINE","classic","pine"], ["VIOLET","spatial","violet"], ["GRAPHITE","classic","graphite"], ["ROSE","editorial","rose"],
  ])("applies %s without changing the reviewed layout family", (id, family, palette) => {
    const definition=resolveAppearance(id);
    expect(definition.id).toBe(id);
    applyAppearanceAttributes(definition);
    expect(document.documentElement.dataset.appearance).toBe(family);
    expect(document.documentElement.dataset.palette).toBe(palette);
  });
  it("normalizes unknown values to the stable classic appearance", () => {
    expect(normalizeAppearanceId("EDITORIAL")).toBe("EDITORIAL");
    expect(normalizeAppearanceId("unknown")).toBe("CLASSIC");
    expect(normalizeAppearanceId(null)).toBe("CLASSIC");
    expect(normalizeAppearanceId("__proto__")).toBe("CLASSIC");
    expect(normalizeAppearanceId("constructor")).toBe("CLASSIC");
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
    expect(meta.content).toBe("#EEF2F6");
    expect(document.documentElement.dataset.palette).toBe("mist");
  });
});

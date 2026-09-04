import type { AppearanceId } from "./appearanceTypes";

export interface AppearanceDefinition {
  id: AppearanceId;
  domValue: string;
  themeColor: string;
  implemented: boolean;
  load?: () => Promise<unknown>;
}

export const appearanceRegistry: Record<AppearanceId, AppearanceDefinition> = {
  CLASSIC: {
    id: "CLASSIC",
    domValue: "classic",
    themeColor: "#eef6fa",
    implemented: true,
    load: () => import("../appearances/classic/index"),
  },
  EDITORIAL: {
    id: "EDITORIAL",
    domValue: "editorial",
    themeColor: "#faf9f5",
    implemented: true,
    load: () => import("../appearances/editorial/index"),
  },
  SPATIAL: {
    id: "SPATIAL",
    domValue: "spatial",
    themeColor: "#f5f5f7",
    implemented: true,
    load: () => import("../appearances/spatial/index"),
  },
};

export function normalizeAppearanceId(value: unknown): AppearanceId {
  return typeof value === "string" && value in appearanceRegistry
    ? (value as AppearanceId)
    : "CLASSIC";
}

export function resolveAppearance(value: unknown): AppearanceDefinition {
  const requested = appearanceRegistry[normalizeAppearanceId(value)];
  return requested.implemented ? requested : appearanceRegistry.CLASSIC;
}

export async function activateAppearance(
  definition: AppearanceDefinition,
): Promise<AppearanceDefinition> {
  await definition.load?.();
  applyAppearanceAttributes(definition);
  return definition;
}

export function applyAppearanceAttributes(definition: AppearanceDefinition) {
  document.documentElement.dataset.appearance = definition.domValue;
  const themeColor = document.querySelector<HTMLMetaElement>(
    'meta[name="theme-color"]',
  );
  if (themeColor) themeColor.content = definition.themeColor;
}

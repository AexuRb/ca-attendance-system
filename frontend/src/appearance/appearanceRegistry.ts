import type { AppearanceId } from "./appearanceTypes";

export interface AppearanceDefinition {
  id: AppearanceId;
  domValue: string;
  palette: string;
  themeColor: string;
  implemented: boolean;
  load?: () => Promise<unknown>;
}

export const appearanceRegistry: Record<AppearanceId, AppearanceDefinition> = {
  CLASSIC: {
    id: "CLASSIC",
    domValue: "classic",
    palette: "mist",
    themeColor: "#EEF2F6",
    implemented: true,
    load: () => import("../appearances/classic/index"),
  },
  EDITORIAL: {
    id: "EDITORIAL",
    domValue: "editorial",
    palette: "sand",
    themeColor: "#F5F1E9",
    implemented: true,
    load: () => import("../appearances/editorial/index"),
  },
  SPATIAL: {
    id: "SPATIAL",
    domValue: "spatial",
    palette: "sky",
    themeColor: "#EDF3F9",
    implemented: true,
    load: () => import("../appearances/spatial/index"),
  },
  PINE: {
    id: "PINE", domValue: "classic", palette: "pine",
    themeColor: "#EEF3EF", implemented: true,
    load: () => import("../appearances/classic/index"),
  },
  VIOLET: {
    id: "VIOLET", domValue: "spatial", palette: "violet",
    themeColor: "#F2EFF6", implemented: true,
    load: () => import("../appearances/spatial/index"),
  },
  GRAPHITE: {
    id: "GRAPHITE", domValue: "classic", palette: "graphite",
    themeColor: "#EFF1F3", implemented: true,
    load: () => import("../appearances/classic/index"),
  },
  ROSE: {
    id: "ROSE", domValue: "editorial", palette: "rose",
    themeColor: "#F6F0F2", implemented: true,
    load: () => import("../appearances/editorial/index"),
  },
};

export function normalizeAppearanceId(value: unknown): AppearanceId {
  return typeof value === "string" && Object.hasOwn(appearanceRegistry, value)
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
  document.documentElement.dataset.palette = definition.palette;
  const themeColor = document.querySelector<HTMLMetaElement>(
    'meta[name="theme-color"]',
  );
  if (themeColor) themeColor.content = definition.themeColor;
}

export const appearanceIds = ["CLASSIC", "EDITORIAL", "SPATIAL", "PINE", "VIOLET", "GRAPHITE", "ROSE"] as const;

export type AppearanceId = (typeof appearanceIds)[number];

export interface AppearanceResponse {
  appearance: AppearanceId;
  version: number;
}

import type { AppearanceId } from "./appearanceTypes";

// Display metadata is independent of persisted IDs and the shared layout family.
export const appearanceOptions = [
  { id: "CLASSIC", label: "雾蓝", detail: "灰蓝清爽，沉稳耐看" },
  { id: "EDITORIAL", label: "暖砂", detail: "米白暖棕，温润柔和" },
  { id: "SPATIAL", label: "晴空", detail: "浅蓝明亮，轻盈通透" },
  { id: "PINE", label: "松绿", detail: "灰绿低饱和，自然沉静" },
  { id: "VIOLET", label: "雾紫", detail: "淡紫轻染，安静柔和" },
  { id: "GRAPHITE", label: "石墨", detail: "银灰蓝调，简洁克制" },
  { id: "ROSE", label: "蔷薇", detail: "灰粉玫瑰，柔和雅致" },
] as const satisfies ReadonlyArray<{ id: AppearanceId; label: string; detail: string }>;

import { reactive, readonly } from "vue";
import { get, put } from "../shared/api";
import {
  activateAppearance,
  applyAppearanceAttributes,
  appearanceRegistry,
  normalizeAppearanceId,
  resolveAppearance,
  type AppearanceDefinition,
} from "./appearanceRegistry";
import type { AppearanceId, AppearanceResponse } from "./appearanceTypes";

type AppearanceRequest = () => Promise<AppearanceResponse>;
type AppearanceActivator = (
  definition: AppearanceDefinition,
) => Promise<AppearanceDefinition>;
type AppearanceUpdater = (appearance: AppearanceId) => Promise<AppearanceResponse>;
type AppearancePreloader = (definition: AppearanceDefinition) => Promise<void>;

const state = reactive({
  ready: false,
  requested: "CLASSIC" as AppearanceId,
  active: "CLASSIC" as AppearanceId,
  fallback: false,
  error: "" as string,
});

export async function initializeAppearance(
  request: AppearanceRequest = requestGlobalAppearance,
  activate: AppearanceActivator = activateAppearance,
) {
  state.ready = false;
  state.error = "";
  let requested: AppearanceId = "CLASSIC";
  let requestFailed = false;

  try {
    const response = await request();
    requested = normalizeAppearanceId(response.appearance);
  } catch {
    requestFailed = true;
  }

  const resolved = resolveAppearance(requested);
  let active = resolved;
  try {
    active = await activate(resolved);
  } catch {
    state.error = "界面资源加载失败，已使用经典界面";
    active = appearanceRegistry.CLASSIC;
    try {
      active = await activate(appearanceRegistry.CLASSIC);
    } catch {
      applyAppearanceAttributes(appearanceRegistry.CLASSIC);
    }
  }

  state.requested = requested;
  state.active = active.id;
  state.fallback = requestFailed || requested !== active.id;
  state.ready = true;
  return readonly(state);
}

async function requestGlobalAppearance(): Promise<AppearanceResponse> {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), 2000);
  try {
    return await get<AppearanceResponse>("/api/public/appearance", {
      signal: controller.signal,
    });
  } finally {
    window.clearTimeout(timeout);
  }
}

export async function changeGlobalAppearance(
  appearance: AppearanceId,
  update: AppearanceUpdater = updateGlobalAppearance,
  preload: AppearancePreloader = preloadAppearance,
) {
  const target = resolveAppearance(appearance);
  if (target.id !== appearance) {
    throw new Error("所选界面当前不可用");
  }

  await preload(target);
  const saved = await update(target.id);
  const resolved = resolveAppearance(saved.appearance);
  if (resolved.id !== target.id) {
    throw new Error("界面设置未正确保存，请刷新后重试");
  }

  applyAppearanceAttributes(resolved);
  state.requested = resolved.id;
  state.active = resolved.id;
  state.fallback = false;
  state.error = "";
  return resolved;
}

async function updateGlobalAppearance(
  appearance: AppearanceId,
): Promise<AppearanceResponse> {
  return put<AppearanceResponse>("/api/settings/appearance", { appearance });
}

async function preloadAppearance(definition: AppearanceDefinition) {
  await definition.load?.();
}

export function useAppearance() {
  return { state: readonly(state) };
}

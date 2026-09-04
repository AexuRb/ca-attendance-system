import type {
  LocationQuery,
  LocationQueryRaw,
  Router,
} from "vue-router";

export type RouteQueryValue = string | number | null | undefined;

export function stringRouteQuery(value: unknown) {
  const candidate = Array.isArray(value) ? value[0] : value;
  return typeof candidate === "string" ? candidate : "";
}

export function positiveRoutePage(value: unknown) {
  const candidate = stringRouteQuery(value);
  if (!/^\d+$/.test(candidate)) return 1;
  const page = Number(candidate);
  return Number.isSafeInteger(page) && page > 0 ? page : 1;
}

export function routeQuerySignature(
  query: LocationQuery,
  keys: readonly string[],
) {
  return keys
    .map((key) => `${key}=${stringRouteQuery(query[key])}`)
    .join("&");
}

export async function updateOwnedRouteQuery(
  router: Router,
  current: LocationQuery,
  ownedKeys: readonly string[],
  values: Record<string, RouteQueryValue>,
  mode: "push" | "replace" = "push",
) {
  const next: LocationQueryRaw = { ...current };
  ownedKeys.forEach((key) => delete next[key]);
  Object.entries(values).forEach(([key, value]) => {
    if (value !== "" && value != null) next[key] = String(value);
  });
  if (sameRouteQuery(current, next)) return false;
  await router[mode]({ query: next });
  return true;
}

function sameRouteQuery(left: LocationQuery, right: LocationQueryRaw) {
  const keys = [...new Set([...Object.keys(left), ...Object.keys(right)])].sort();
  return keys.every(
    (key) => stringRouteQuery(left[key]) === stringRouteQuery(right[key]),
  );
}

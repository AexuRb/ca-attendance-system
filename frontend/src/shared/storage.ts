interface StorageAccess {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
}

const fallback = new Map<string, string>();

export function safeStorageGet(
  key: string,
  storage?: StorageAccess,
): string | null {
  try {
    return (storage || window.localStorage).getItem(key);
  } catch {
    return fallback.get(key) ?? null;
  }
}

export function safeStorageSet(
  key: string,
  value: string,
  storage?: StorageAccess,
) {
  try {
    (storage || window.localStorage).setItem(key, value);
    fallback.delete(key);
  } catch {
    fallback.set(key, value);
  }
}

// Private query context stays in memory. History contains only an opaque visit id.
const resetters = new Set<() => void>();

export function clearPrivateNavigationState() {
  resetters.forEach((reset) => reset());
}

export function createPrivateNavigationState<T>(limit = 30) {
  const entries = new Map<string, T>();
  let generation = 0;
  resetters.add(() => {
    generation += 1;
    entries.clear();
  });
  return {
    scope() {
      const owner = generation;
      return {
        get(key: string) {
          return owner === generation ? entries.get(key) : undefined;
        },
        set(key: string, value: T) {
          if (owner !== generation) return;
          entries.delete(key);
          entries.set(key, value);
          if (entries.size > limit) entries.delete(entries.keys().next().value!);
        },
      };
    },
  };
}

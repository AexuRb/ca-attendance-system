import { reactive } from "vue";

export function usePendingActions() {
  const pending = reactive(new Set<string>());

  function isPending(key: string) {
    return pending.has(key);
  }

  async function run<T>(key: string, task: () => Promise<T>) {
    if (pending.has(key)) return undefined;
    pending.add(key);
    try {
      return await task();
    } finally {
      pending.delete(key);
    }
  }

  return { pending, isPending, run };
}

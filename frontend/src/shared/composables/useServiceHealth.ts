import { onBeforeUnmount, onMounted, ref } from "vue";
import { get } from "../api";

const DEFAULT_INTERVAL = 30_000;

export function useServiceHealth(interval = DEFAULT_INTERVAL) {
  const online = ref(false);
  const checking = ref(true);
  let disposed = false;
  let timer: number | undefined;
  let pending: Promise<void> | null = null;

  function refresh() {
    if (disposed) return Promise.resolve();
    if (pending) return pending;
    pending = (async () => {
      try {
        await get("/api/health");
        if (!disposed) online.value = true;
      } catch {
        if (!disposed) online.value = false;
      } finally {
        if (!disposed) checking.value = false;
      }
    })().finally(() => {
      pending = null;
    });
    return pending;
  }

  onMounted(() => {
    disposed = false;
    void refresh();
    timer = window.setInterval(() => void refresh(), interval);
    window.addEventListener("focus", refresh);
  });

  onBeforeUnmount(() => {
    disposed = true;
    window.clearInterval(timer);
    window.removeEventListener("focus", refresh);
  });

  return { online, checking, refresh };
}

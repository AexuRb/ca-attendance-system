import { onBeforeUnmount, ref } from "vue";

export function useLatestRequest() {
  const loading = ref(false);
  const error = ref("");
  let version = 0;
  let active = true;
  let controller: AbortController | null = null;

  async function run<T>(
    task: (signal: AbortSignal) => Promise<T>,
    fallbackMessage = "加载失败",
  ): Promise<T | undefined> {
    controller?.abort();
    controller = new AbortController();
    const currentController = controller;
    const currentVersion = ++version;
    loading.value = true;
    error.value = "";
    try {
      const value = await task(currentController.signal);
      return isCurrent(currentVersion, currentController) ? value : undefined;
    } catch (cause) {
      if (isCurrent(currentVersion, currentController)) {
        error.value = cause instanceof Error ? cause.message : fallbackMessage;
      }
      return undefined;
    } finally {
      if (isCurrent(currentVersion, currentController)) loading.value = false;
    }
  }

  function dispose() {
    active = false;
    version += 1;
    controller?.abort();
    controller = null;
  }

  function isCurrent(
    requestVersion: number,
    requestController: AbortController,
  ) {
    return (
      active &&
      version === requestVersion &&
      controller === requestController &&
      !requestController.signal.aborted
    );
  }

  onBeforeUnmount(dispose);
  return { loading, error, run, dispose };
}

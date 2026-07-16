import { ref } from "vue";
import { notify } from "./useToast";

export function useAsyncTask() {
  const busy = ref(false);
  const error = ref("");

  async function run<T>(
    task: () => Promise<T>,
    successMessage = "",
  ): Promise<T | undefined> {
    busy.value = true;
    error.value = "";
    try {
      const value = await task();
      if (successMessage) notify(successMessage, "success");
      return value;
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : "操作失败";
      notify(error.value, "danger");
      return undefined;
    } finally {
      busy.value = false;
    }
  }
  return { busy, error, run };
}

import { ref } from "vue";

export function useCommandSession() {
  const open = ref(false);
  const activeIndex = ref(0);
  const dismissedOnce = ref(false);
  const executing = ref(false);

  function openSuggestions() {
    open.value = true;
    dismissedOnce.value = false;
  }

  function resetSelection() {
    activeIndex.value = 0;
  }

  function moveActive(step: number, count: number) {
    if (!count) return;
    activeIndex.value = (activeIndex.value + step + count) % count;
  }

  function escape(clear: () => void, blur: () => void) {
    if (open.value) {
      open.value = false;
      dismissedOnce.value = true;
      return;
    }
    clear();
    blur();
    dismissedOnce.value = false;
  }

  async function runOnce(action: () => void | Promise<void>) {
    if (executing.value) return;
    executing.value = true;
    try {
      await action();
    } finally {
      executing.value = false;
    }
  }

  return {
    open,
    activeIndex,
    dismissedOnce,
    executing,
    openSuggestions,
    resetSelection,
    moveActive,
    escape,
    runOnce,
  };
}

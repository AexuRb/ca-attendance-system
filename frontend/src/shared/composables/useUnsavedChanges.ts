import { onBeforeUnmount, onMounted, ref } from "vue";

export function useUnsavedChanges(isDirty: () => boolean) {
  const confirmOpen = ref(false);
  let pendingAction: (() => void) | null = null;
  let pendingCancel: (() => void) | null = null;

  function request(action: () => void, cancelAction?: () => void) {
    if (!isDirty()) {
      action();
      return true;
    }
    pendingAction = action;
    pendingCancel = cancelAction || null;
    confirmOpen.value = true;
    return false;
  }

  function cancel() {
    const action = pendingCancel;
    pendingAction = null;
    pendingCancel = null;
    confirmOpen.value = false;
    action?.();
  }

  function discard() {
    const action = pendingAction;
    pendingAction = null;
    pendingCancel = null;
    confirmOpen.value = false;
    action?.();
  }

  function onBeforeUnload(event: BeforeUnloadEvent | Event) {
    if (!isDirty()) return;
    event.preventDefault();
    if ("returnValue" in event) event.returnValue = "";
  }

  onMounted(() => window.addEventListener("beforeunload", onBeforeUnload));
  onBeforeUnmount(() =>
    window.removeEventListener("beforeunload", onBeforeUnload),
  );

  return { confirmOpen, request, cancel, discard };
}

import { nextTick, onBeforeUnmount, type Ref, watch } from "vue";

const dialogStack: HTMLElement[] = [];
const focusableSelector = [
  "a[href]",
  "button:not([disabled])",
  "input:not([disabled]):not([type='hidden'])",
  "select:not([disabled])",
  "textarea:not([disabled])",
  "iframe",
  "[tabindex]:not([tabindex='-1'])",
].join(",");

interface DialogFocusOptions {
  root: Ref<HTMLElement | null>;
  open: () => boolean;
  close: () => void;
}

export function useDialogFocus(options: DialogFocusOptions) {
  let active = false;
  let activeDialog: HTMLElement | null = null;
  let returnFocus: HTMLElement | null = null;

  function focusableElements() {
    if (!activeDialog) return [];
    return Array.from(
      activeDialog.querySelectorAll<HTMLElement>(focusableSelector),
    ).filter(
      (element) =>
        !element.hidden &&
        element.getAttribute("aria-hidden") !== "true" &&
        !element.closest("[inert]"),
    );
  }

  function isTopDialog() {
    return dialogStack.at(-1) === activeDialog;
  }

  function initialFocusTarget() {
    if (!activeDialog) return null;
    const content = activeDialog.querySelector<HTMLElement>(
      "[data-dialog-content]",
    );
    return (
      activeDialog.querySelector<HTMLElement>(
        "[data-dialog-initial-focus]",
      ) ??
      activeDialog.querySelector<HTMLElement>("[autofocus]") ??
      content?.querySelector<HTMLElement>(focusableSelector) ??
      focusableElements()[0] ??
      activeDialog
    );
  }

  function onKeydown(event: KeyboardEvent) {
    if (!isTopDialog()) return;
    if (event.key === "Escape") {
      event.preventDefault();
      options.close();
      return;
    }
    if (event.key !== "Tab") return;

    const focusable = focusableElements();
    if (!focusable.length) {
      event.preventDefault();
      activeDialog?.focus();
      return;
    }

    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (!activeDialog?.contains(document.activeElement)) {
      event.preventDefault();
      (event.shiftKey ? last : initialFocusTarget())?.focus();
      return;
    }
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  async function activate() {
    if (active) return;
    active = true;
    returnFocus =
      document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null;
    await nextTick();
    if (!options.open() || !options.root.value) {
      active = false;
      return;
    }

    activeDialog = options.root.value;
    dialogStack.push(activeDialog);
    document.addEventListener("keydown", onKeydown);
    initialFocusTarget()?.focus();
  }

  function deactivate(restoreFocus = true) {
    if (!active) return;
    const wasTop = isTopDialog();
    active = false;
    document.removeEventListener("keydown", onKeydown);
    const index = activeDialog ? dialogStack.lastIndexOf(activeDialog) : -1;
    if (index >= 0) dialogStack.splice(index, 1);
    if (restoreFocus && wasTop && returnFocus?.isConnected) {
      const target = returnFocus;
      void nextTick(() => target.focus());
    }
    activeDialog = null;
    returnFocus = null;
  }

  watch(
    options.open,
    (open) => {
      if (open) void activate();
      else deactivate();
    },
    { immediate: true, flush: "post" },
  );

  onBeforeUnmount(() => deactivate(false));
}

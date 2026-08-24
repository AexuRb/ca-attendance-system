import { nextTick, onBeforeUnmount, type Ref, watch } from "vue";

const dialogStack: HTMLElement[] = [];
let pageScrollLockCount = 0;
let previousHtmlOverflow: string | null = null;
let previousBodyOverflow: string | null = null;
const focusableSelector = [
  "a[href]",
  "button:not([disabled])",
  "input:not([disabled]):not([type='hidden'])",
  "select:not([disabled])",
  "textarea:not([disabled])",
  "iframe",
  "[tabindex]:not([tabindex='-1'])",
].join(",");

function lockPageScroll() {
  if (pageScrollLockCount === 0) {
    previousHtmlOverflow = document.documentElement.style.overflow;
    previousBodyOverflow = document.body.style.overflow;
    document.documentElement.style.overflow = "hidden";
    document.body.style.overflow = "hidden";
  }
  pageScrollLockCount += 1;
}

function unlockPageScroll() {
  if (pageScrollLockCount === 0) return;
  pageScrollLockCount -= 1;
  if (pageScrollLockCount > 0) return;

  document.documentElement.style.overflow = previousHtmlOverflow ?? "";
  document.body.style.overflow = previousBodyOverflow ?? "";
  previousHtmlOverflow = null;
  previousBodyOverflow = null;
}

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
    if (!first || !last) return;
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
    lockPageScroll();
    returnFocus =
      document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null;
    await nextTick();
    if (!active || !options.open() || !options.root.value) {
      if (active) unlockPageScroll();
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
    unlockPageScroll();
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

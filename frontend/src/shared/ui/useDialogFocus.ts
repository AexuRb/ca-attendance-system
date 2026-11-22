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

interface DialogActivation {
  opener: HTMLElement | null;
  dialog: HTMLElement | null;
}

function canReceiveFocus(element: HTMLElement) {
  return !element.matches(":disabled") && !element.closest('[hidden], [inert], [aria-hidden="true"]');
}

export function makeDialogInert(element: Element) {
  element.setAttribute("inert", "");
}

export function useDialogFocus(options: DialogFocusOptions) {
  let activation: DialogActivation | undefined;

  function focusableElements(dialog: HTMLElement) {
    return [...dialog.querySelectorAll<HTMLElement>(focusableSelector)].filter(canReceiveFocus);
  }

  function initialFocusTarget(dialog: HTMLElement, focusable = focusableElements(dialog)) {
    const preferred = (selector: string) => [...dialog.querySelectorAll<HTMLElement>(selector)].find(canReceiveFocus);
    const content = dialog.querySelector("[data-dialog-content]");
    return (
      preferred("[data-dialog-initial-focus]") ??
      preferred("[autofocus]") ??
      focusable.find(element => content?.contains(element)) ??
      focusable[0] ??
      dialog
    );
  }

  function onKeydown(event: KeyboardEvent) {
    const dialog = activation?.dialog;
    if (event.defaultPrevented || !dialog || dialogStack.at(-1) !== dialog) return;
    if (event.key === "Escape") {
      event.preventDefault();
      options.close();
      return;
    }
    if (event.key !== "Tab") return;

    const focusable = focusableElements(dialog);
    if (!focusable.length) {
      event.preventDefault();
      dialog.focus();
      return;
    }

    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (!first || !last) return;
    if (!dialog.contains(document.activeElement)) {
      event.preventDefault();
      (event.shiftKey ? last : initialFocusTarget(dialog, focusable)).focus();
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
    if (activation) return;
    const current: DialogActivation = {
      opener: document.activeElement instanceof HTMLElement ? document.activeElement : null,
      dialog: null,
    };
    activation = current;
    lockPageScroll();
    await nextTick();
    if (activation !== current) return;
    const dialog = options.root.value;
    if (!options.open() || !dialog) {
      deactivate(false);
      return;
    }

    current.dialog = dialog;
    dialogStack.push(dialog);
    document.addEventListener("keydown", onKeydown);
    initialFocusTarget(dialog).focus();
  }

  function deactivate(restoreFocus = true) {
    if (!activation) return;
    const { dialog, opener } = activation;
    const wasTop = dialogStack.at(-1) === dialog;
    activation = undefined;
    document.removeEventListener("keydown", onKeydown);
    unlockPageScroll();
    const index = dialog ? dialogStack.lastIndexOf(dialog) : -1;
    if (index >= 0) dialogStack.splice(index, 1);
    if (restoreFocus && wasTop && opener) {
      const remainingDialog = dialogStack.at(-1);
      void nextTick(() => {
        if (activation || dialogStack.at(-1) !== remainingDialog || !opener.isConnected) return;
        if ((!remainingDialog || remainingDialog.contains(opener)) && canReceiveFocus(opener)) opener.focus();
      });
    }
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

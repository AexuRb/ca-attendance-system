import { reactive } from "vue";

export type ToastTone = "success" | "warning" | "danger" | "info";
export interface Toast {
  id: number;
  message: string;
  tone: ToastTone;
}

const toasts = reactive<Toast[]>([]);
let nextId = 1;
const MAX_TOASTS = 3;
const TOAST_DURATION = 6_000;
interface ToastTimer {
  handle: number;
  remaining: number;
  startedAt: number;
}
const timers = new Map<number, ToastTimer>();

export function notify(message: string, tone: ToastTone = "info") {
  while (toasts.length >= MAX_TOASTS) {
    const oldest = toasts[0];
    if (!oldest) break;
    dismiss(oldest.id);
  }
  const id = nextId++;
  toasts.push({ id, message, tone });
  startTimer(id, TOAST_DURATION);
}

export function dismiss(id: number) {
  clearTimer(id);
  const index = toasts.findIndex((item) => item.id === id);
  if (index >= 0) toasts.splice(index, 1);
}

export function pause(id: number) {
  const timer = timers.get(id);
  if (!timer) return;
  window.clearTimeout(timer.handle);
  timer.remaining = Math.max(0, timer.remaining - (Date.now() - timer.startedAt));
  timers.delete(id);
  timers.set(id, { ...timer, handle: 0 });
}

export function resume(id: number) {
  const timer = timers.get(id);
  if (!timer || timer.handle) return;
  startTimer(id, timer.remaining);
}

function startTimer(id: number, remaining: number) {
  clearTimer(id);
  const handle = window.setTimeout(() => dismiss(id), remaining);
  timers.set(id, { handle, remaining, startedAt: Date.now() });
}

function clearTimer(id: number) {
  const timer = timers.get(id);
  if (timer?.handle) window.clearTimeout(timer.handle);
  timers.delete(id);
}

export function useToast() {
  return { toasts, notify, dismiss, pause, resume };
}

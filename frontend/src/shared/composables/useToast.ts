import { reactive } from "vue";

export type ToastTone = "success" | "warning" | "danger" | "info";
export interface Toast {
  id: number;
  message: string;
  tone: ToastTone;
}

const toasts = reactive<Toast[]>([]);
let nextId = 1;

export function notify(message: string, tone: ToastTone = "info") {
  const id = nextId++;
  toasts.push({ id, message, tone });
  setTimeout(() => dismiss(id), 4200);
}

export function dismiss(id: number) {
  const index = toasts.findIndex((item) => item.id === id);
  if (index >= 0) toasts.splice(index, 1);
}

export function useToast() {
  return { toasts, notify, dismiss };
}

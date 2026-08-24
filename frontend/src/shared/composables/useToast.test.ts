// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest";
import { dismiss, notify, pause, resume, useToast } from "./useToast";

const { toasts } = useToast();

afterEach(() => {
  for (const toast of [...toasts]) dismiss(toast.id);
  vi.useRealTimers();
});

describe("toast lifecycle", () => {
  it("keeps only the three newest messages", () => {
    notify("一");
    notify("二");
    notify("三");
    notify("四");

    expect(toasts.map((toast) => toast.message)).toEqual(["二", "三", "四"]);
  });

  it("pauses and resumes automatic dismissal", () => {
    vi.useFakeTimers();
    notify("需要阅读");
    const id = toasts[0].id;
    vi.advanceTimersByTime(2_000);
    pause(id);
    vi.advanceTimersByTime(10_000);
    expect(toasts).toHaveLength(1);

    resume(id);
    vi.advanceTimersByTime(3_999);
    expect(toasts).toHaveLength(1);
    vi.advanceTimersByTime(1);
    expect(toasts).toHaveLength(0);
  });
});

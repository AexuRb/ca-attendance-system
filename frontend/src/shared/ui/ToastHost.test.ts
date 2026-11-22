// @vitest-environment jsdom
import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import { dismiss, notify, useToast } from "../composables/useToast";
import ToastHost from "./ToastHost.vue";

const { toasts } = useToast();

afterEach(() => {
  for (const toast of [...toasts]) dismiss(toast.id);
  vi.useRealTimers();
});

describe("ToastHost", () => {
  it("uses a separate labelled dismiss button", async () => {
    const wrapper = mount(ToastHost);
    notify("保存成功", "success");
    await wrapper.vm.$nextTick();

    expect(wrapper.get(".toast").element.tagName).toBe("ARTICLE");
    await wrapper.get('button[aria-label="关闭通知：保存成功"]').trigger("click");
    expect(wrapper.find(".toast").exists()).toBe(false);
  });

  it("keeps a notification visible while the keyboard focus is inside it", async () => {
    vi.useFakeTimers();
    const wrapper = mount(ToastHost, { attachTo: document.body });
    try {
      notify("保存成功", "success");
      await wrapper.vm.$nextTick();
      const close = wrapper.get<HTMLButtonElement>("button").element;
      vi.advanceTimersByTime(2_000);
      close.focus();
      expect(document.activeElement).toBe(close);
      vi.advanceTimersByTime(10_000);
      expect(toasts).toHaveLength(1);
      close.blur();
      vi.advanceTimersByTime(3_999);
      expect(toasts).toHaveLength(1);
      vi.advanceTimersByTime(1);
      expect(toasts).toHaveLength(0);
    } finally {
      wrapper.unmount();
    }
  });
});

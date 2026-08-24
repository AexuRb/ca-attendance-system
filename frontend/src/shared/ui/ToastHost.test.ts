// @vitest-environment jsdom
import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it } from "vitest";
import { dismiss, notify, useToast } from "../composables/useToast";
import ToastHost from "./ToastHost.vue";

const { toasts } = useToast();

afterEach(() => {
  for (const toast of [...toasts]) dismiss(toast.id);
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
});

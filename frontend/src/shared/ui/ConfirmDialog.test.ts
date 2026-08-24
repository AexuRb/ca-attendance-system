// @vitest-environment jsdom
import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it } from "vitest";
import ConfirmDialog from "./ConfirmDialog.vue";

afterEach(() => {
  document.body.innerHTML = "";
});

describe("ConfirmDialog", () => {
  it("locks both actions while the confirmation request is pending", async () => {
    const wrapper = mount(ConfirmDialog, {
      attachTo: document.body,
      props: {
        open: true,
        title: "删除记录",
        message: "确认删除",
        confirmLabel: "删除",
        pending: true,
      },
    });

    const buttons = Array.from(
      document.body.querySelectorAll<HTMLButtonElement>(".modal-footer button"),
    );
    expect(buttons).toHaveLength(2);
    expect(buttons.every((button) => button.disabled)).toBe(true);
    expect(document.body.textContent).toContain("处理中...");

    await buttons[0].click();
    expect(wrapper.emitted("cancel")).toBeUndefined();
    wrapper.unmount();
  });
});

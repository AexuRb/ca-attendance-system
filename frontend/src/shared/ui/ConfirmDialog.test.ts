// @vitest-environment jsdom
import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it } from "vitest";
import ConfirmDialog from "./ConfirmDialog.vue";

afterEach(() => {
  document.body.innerHTML = "";
});

describe("ConfirmDialog", () => {
  it("requires a reason, retains it after failure, and clears it when reopened", async () => {
    const wrapper = mount(ConfirmDialog, {
      props: { open: true, title: "删除记录", message: "确认删除", requireReason: true,
        reasonOptions: ["重复记录", "误补录"] },
      global: { stubs: { Teleport: true } },
    });
    const confirm = () => wrapper.get(".modal-footer .button.primary");
    expect(confirm().attributes("disabled")).toBeDefined();
    await wrapper.get('input[value="重复记录"]').setValue();
    await confirm().trigger("click");
    expect(wrapper.emitted("confirm")?.at(-1)).toEqual(["重复记录"]);
    await wrapper.setProps({ pending: true });
    expect(confirm().attributes("disabled")).toBeDefined();
    await wrapper.setProps({ pending: false });
    expect((wrapper.get('input[value="重复记录"]').element as HTMLInputElement).checked).toBe(true);
    await wrapper.get('input[value="other"]').setValue();
    expect(confirm().attributes("disabled")).toBeDefined();
    await wrapper.get("textarea").setValue("  核对归属后删除  ");
    await confirm().trigger("click");
    expect(wrapper.emitted("confirm")?.at(-1)).toEqual(["其他：核对归属后删除"]);
    await wrapper.setProps({ open: false });
    await wrapper.setProps({ open: true });
    expect(wrapper.find("input:checked").exists()).toBe(false);
    expect(wrapper.find("textarea").exists()).toBe(false);
    expect(confirm().attributes("disabled")).toBeDefined();
    wrapper.unmount();
  });

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

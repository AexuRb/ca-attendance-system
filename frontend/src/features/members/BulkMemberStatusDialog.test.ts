// @vitest-environment jsdom
import { DOMWrapper, mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import BulkMemberStatusDialog from "./BulkMemberStatusDialog.vue";

describe("BulkMemberStatusDialog reasons", () => {
  it("requires an explicit choice, submits a complete explanation, and resets for the next status", async () => {
    const wrapper = mount(BulkMemberStatusDialog, {
      props: { open: true, count: 2, status: "DISABLED" },
      attachTo: document.body,
      global: { stubs: { Teleport: false } },
    });
    const ui = new DOMWrapper(document.body);
    expect(ui.get(".button.danger").attributes("disabled")).toBeDefined();
    await ui.get('input[value="毕业离会"]').setValue();
    await ui.get(".button.danger").trigger("click");
    expect(wrapper.emitted("confirm")?.at(-1)).toEqual(["毕业离会"]);
    await wrapper.setProps({ open: false });
    await wrapper.setProps({ open: true, status: "ACTIVE" });
    expect(ui.find("input:checked").exists()).toBe(false);
    expect(ui.get(".button.primary").attributes("disabled")).toBeDefined();
    await ui.get('input[value="other"]').setValue();
    expect(ui.get(".button.primary").attributes("disabled")).toBeDefined();
    await ui.get("textarea").setValue("  确认恢复权限  ");
    await wrapper.setProps({ busy: true });
    expect(ui.get(".button.primary").attributes("disabled")).toBeDefined();
    expect((ui.get("textarea").element as HTMLTextAreaElement).value).toBe("  确认恢复权限  ");
    await wrapper.setProps({ busy: false });
    await ui.get(".button.primary").trigger("click");
    expect(wrapper.emitted("confirm")?.at(-1)).toEqual(["其他：确认恢复权限"]);
    wrapper.unmount();
  });
});

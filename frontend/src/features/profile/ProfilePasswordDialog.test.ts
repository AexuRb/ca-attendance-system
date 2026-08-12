import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it } from "vitest";
import ProfilePasswordDialog from "./ProfilePasswordDialog.vue";

afterEach(() => {
  document.body.innerHTML = "";
});

describe("ProfilePasswordDialog", () => {
  it("shows field errors before sending an invalid password change", async () => {
    const wrapper = mount(ProfilePasswordDialog, {
      attachTo: document.body,
      props: { open: true },
      global: { stubs: { Teleport: true } },
    });

    await wrapper.get('input[name="oldPassword"]').setValue("old-password");
    await wrapper.get('input[name="newPassword"]').setValue("12345");
    await wrapper.get('input[name="confirmation"]').setValue("54321");
    await wrapper.get("form").trigger("submit");

    expect(document.body.textContent).toContain("6 至 64 个字符");
    expect(document.body.textContent).toContain("两次输入的新密码不一致");
    expect(wrapper.emitted("changed")).toBeUndefined();
  });
});

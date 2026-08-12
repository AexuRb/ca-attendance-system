import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it } from "vitest";
import MemberPasswordResetDialog from "./MemberPasswordResetDialog.vue";

afterEach(() => {
  document.body.innerHTML = "";
});

const baseMember = {
  id: 2,
  studentNo: "1004231224",
  name: "测试成员",
  role: "MEMBER" as const,
  status: "ACTIVE" as const,
};

describe("MemberPasswordResetDialog", () => {
  it("allows a normal account to reset to the last six digits", async () => {
    const wrapper = mount(MemberPasswordResetDialog, {
      attachTo: document.body,
      props: { open: true, member: baseMember },
    });

    expect(document.body.textContent).toContain("231224");
    await document
      .querySelector<HTMLButtonElement>('button[type="submit"]')!
      .click();
    expect(wrapper.emitted("confirm")?.[0]).toEqual([""]);
  });

  it("requires a compliant explicit password for a historical account", async () => {
    const wrapper = mount(MemberPasswordResetDialog, {
      attachTo: document.body,
      props: {
        open: true,
        member: { ...baseMember, studentNo: "old-admin" },
      },
    });

    expect(document.body.textContent).toContain("历史账号");
    const form = document.querySelector<HTMLFormElement>("form")!;
    await form.dispatchEvent(new Event("submit"));
    expect(document.body.textContent).toContain("6 至 64");
    expect(wrapper.emitted("confirm")).toBeUndefined();
  });
});

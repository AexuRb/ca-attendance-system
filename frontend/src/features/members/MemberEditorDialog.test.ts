import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it } from "vitest";
import MemberEditorDialog from "./MemberEditorDialog.vue";

const admin = {
  id: 1,
  studentNo: "admin",
  name: "管理员",
  role: "ADMIN" as const,
  status: "ACTIVE" as const,
  phone: "",
  major: "",
  grade: "",
  qq: "",
};

afterEach(() => {
  document.body.innerHTML = "";
});

function accountControls() {
  return {
    role: document.querySelector<HTMLSelectElement>('select[name="role"]')!,
    status: document.querySelector<HTMLSelectElement>(
      'select[name="status"]',
    )!,
  };
}

describe("MemberEditorDialog", () => {
  it("shows field errors and does not submit an invalid new member", async () => {
    const wrapper = mount(MemberEditorDialog, {
      attachTo: document.body,
      global: { stubs: { Teleport: true } },
      props: {
        open: true,
        member: null,
        operatorRole: "ADMIN",
        gradeChoices: [],
      },
    });

    await wrapper.get('input[name="studentNo"]').setValue("12A");
    await wrapper.get('input[name="name"]').setValue(" ");
    await wrapper.get("form").trigger("submit");

    expect(document.body.textContent).toContain("6 至 32 位纯数字");
    expect(document.body.textContent).toContain("姓名不能为空");
    expect(wrapper.emitted("save")).toBeUndefined();
  });

  it("allows an unchanged historical account to be edited", async () => {
    const wrapper = mount(MemberEditorDialog, {
      attachTo: document.body,
      global: { stubs: { Teleport: true } },
      props: {
        open: true,
        member: admin,
        operatorRole: "ADMIN",
        gradeChoices: [],
      },
    });

    await wrapper.get("form").trigger("submit");

    expect(wrapper.emitted("save")).toHaveLength(1);
  });

  it("locks role and status when editing the current administrator", () => {
    mount(MemberEditorDialog, {
      attachTo: document.body,
      props: {
        open: true,
        member: admin,
        operatorRole: "ADMIN",
        gradeChoices: [],
        lockAccountControls: true,
      },
    });

    const controls = accountControls();
    expect(controls.role.disabled).toBe(true);
    expect(controls.status.disabled).toBe(true);
  });

  it("keeps role and status editable for another administrator", () => {
    mount(MemberEditorDialog, {
      attachTo: document.body,
      props: {
        open: true,
        member: admin,
        operatorRole: "ADMIN",
        gradeChoices: [],
        lockAccountControls: false,
      },
    });

    const controls = accountControls();
    expect(controls.role.disabled).toBe(false);
    expect(controls.status.disabled).toBe(false);
  });
});

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

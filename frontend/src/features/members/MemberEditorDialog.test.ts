// @vitest-environment jsdom
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
  it("preserves a dirty draft on cancel and only closes after discard", async () => {
    const wrapper = mount(MemberEditorDialog, {
      attachTo: document.body,
      global: { stubs: { Teleport: true } },
      props: { open: true, member: null, operatorRole: "ADMIN", gradeChoices: [] },
    });
    await wrapper.get('input[name="name"]').setValue("虚构成员");
    await wrapper.get('[aria-label="关闭"]').trigger("click");
    expect(wrapper.emitted("close")).toBeUndefined();
    const confirmation = wrapper.findAll('[role="dialog"]').at(-1)!;
    expect(confirmation.text()).toContain("放弃未保存修改");
    await confirmation.findAll("button").find(button => button.text() === "取消")!.trigger("click");
    expect(wrapper.emitted("close")).toBeUndefined();
    expect((wrapper.get('input[name="name"]').element as HTMLInputElement).value).toBe("虚构成员");
    await wrapper.get('[aria-label="关闭"]').trigger("click");
    await wrapper.findAll("button").find(button => button.text() === "放弃修改")!.trigger("click");
    expect(wrapper.emitted("close")).toHaveLength(1);
    wrapper.unmount();
  });

  it("closes without confirmation after reverting edits to the initial values", async () => {
    const wrapper = mount(MemberEditorDialog, {
      attachTo: document.body,
      global: { stubs: { Teleport: true } },
      props: { open: true, member: admin, operatorRole: "ADMIN", gradeChoices: [] },
    });
    await wrapper.get('input[name="name"]').setValue("临时修改");
    await wrapper.get('input[name="name"]').setValue(admin.name);
    await wrapper.get('[aria-label="关闭"]').trigger("click");
    expect(wrapper.emitted("close")).toHaveLength(1);
    expect(wrapper.text()).not.toContain("成员资料尚未保存");
    wrapper.unmount();
  });

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
    wrapper.unmount();
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
    wrapper.unmount();
  });

  it("locks role and status when editing the current administrator", () => {
    const wrapper = mount(MemberEditorDialog, {
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
    wrapper.unmount();
  });

  it("keeps role and status editable for another administrator", () => {
    const wrapper = mount(MemberEditorDialog, {
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
    wrapper.unmount();
  });

  it("keeps a historical grade selectable while editing", () => {
    const wrapper = mount(MemberEditorDialog, {
      attachTo: document.body,
      props: {
        open: true,
        member: { ...admin, grade: "1998级" },
        operatorRole: "ADMIN",
        gradeChoices: ["2025级", "2026级"],
      },
    });

    const values = Array.from(
      document.querySelectorAll<HTMLOptionElement>('select[name="grade"] option'),
    ).map((option) => option.value);
    expect(values).toContain("1998级");
    expect(document.querySelector<HTMLSelectElement>('select[name="grade"]')?.value).toBe(
      "1998级",
    );
    wrapper.unmount();
  });
});

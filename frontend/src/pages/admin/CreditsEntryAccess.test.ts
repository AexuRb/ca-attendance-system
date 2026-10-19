// @vitest-environment jsdom
import { mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";
import SettingsPage from "./SettingsPage.vue";
import ProfilePage from "./ProfilePage.vue";
import { ref } from "vue";
const access = vi.hoisted(() => ({ admin: true, role: "ADMIN" }));
vi.mock("../../features/profile/useProfileWorkspace", () => ({ useProfileWorkspace: () => ({
  user: ref({ name: "测试账号", studentNo: "9900000001", role: access.role }),
  roleLabel: () => "测试角色", attendanceRecords: [], trainingRecords: [], activeRecords: [], profile: {}, profileErrors: {},
}) }));
vi.mock("../../features/settings/useSettingsWorkspace", () => ({ useSettingsWorkspace: () => ({
  actions:{isPending:()=>false},appearanceDraft:'SPATIAL',appearanceState:{active:'SPATIAL'},canEditAppearance:access.admin,
  attendancePolicy:{},weekdays:[],periods:[],loading:false,
  unsaved:{confirmOpen:{value:false}},
}) }));
const stubs={ExpandedWorkspaceShell:{template:'<main><header><slot name="heading"/></header><slot/></main>'},AppearanceSelector:true,WeekdayCalendarSelector:true,DutyTimeWorkspace:true,LoadingBlock:true,ConfirmDialog:true,CreditsDialog:true,EmptyState:true,StatusBadge:true};
describe("credits entry location and access", () => {
  it.each(["ADMIN", "PRESIDENT", "MINISTER", "MEMBER"])("opens and closes credits from the %s profile heading", async role => {
    access.role = role;
    const wrapper = mount(ProfilePage, { global: { stubs } });
    expect(wrapper.get("header").text()).toContain("个人资料");
    await wrapper.get('header button[aria-label="查看鸣谢"]').trigger("click");
    const dialog = wrapper.getComponent({ name: "CreditsDialog" });
    expect(dialog.attributes("open")).toBe("true");
    dialog.vm.$emit("close");
    await wrapper.vm.$nextTick();
    expect(dialog.attributes("open")).toBe("false");
    wrapper.unmount();
  });

  it.each([true, false])("removes the entry and dialog from settings (admin=%s)", admin => {
    access.admin = admin;
    const wrapper = mount(SettingsPage, { global: { stubs } });
    expect(wrapper.find('button[aria-label="查看鸣谢"]').exists()).toBe(false);
    expect(wrapper.find("credits-dialog-stub").exists()).toBe(false);
    wrapper.unmount();
  });
});

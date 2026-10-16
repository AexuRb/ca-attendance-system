// @vitest-environment jsdom
import { mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";
import SettingsPage from "./SettingsPage.vue";
const access = vi.hoisted(() => ({ admin: true }));
vi.mock("../../features/settings/useSettingsWorkspace", () => ({ useSettingsWorkspace: () => ({
  actions:{isPending:()=>false},appearanceDraft:'SPATIAL',appearanceState:{active:'SPATIAL'},canEditAppearance:access.admin,
  attendancePolicy:{},weekdays:[],periods:[],loading:false,
  unsaved:{confirmOpen:{value:false}},
}) }));
const stubs={ExpandedWorkspaceShell:{template:'<main><slot name="heading"/><slot/></main>'},AppearanceSelector:true,WeekdayCalendarSelector:true,DutyTimeWorkspace:true,LoadingBlock:true,ConfirmDialog:true,CreditsDialog:true};
describe('settings credits access',()=>{
  it('offers the entry and dialog only to administrators',async()=>{access.admin=true;const w=mount(SettingsPage,{global:{stubs}});expect(w.find('button[aria-label="查看鸣谢"]').exists()).toBe(true);await w.get('button[aria-label="查看鸣谢"]').trigger('click');expect(w.find('credits-dialog-stub').attributes('open')).toBe('true');w.unmount();});
  it('does not render the entry or mount the dialog for other settings users',()=>{access.admin=false;const w=mount(SettingsPage,{global:{stubs}});expect(w.find('button[aria-label="查看鸣谢"]').exists()).toBe(false);expect(w.find('credits-dialog-stub').exists()).toBe(false);w.unmount();});
});

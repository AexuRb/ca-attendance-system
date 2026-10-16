// @vitest-environment jsdom
import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import CreditsEditorPage from "./CreditsEditorPage.vue";
import { acknowledgements, fixedContributor } from "../../shared/config/acknowledgements";
const mocks = vi.hoisted(() => ({get:vi.fn(),put:vi.fn(),push:vi.fn(),leave:vi.fn()}));
vi.mock("../../shared/api",()=>({get:(...args:unknown[])=>mocks.get(...args),put:(...args:unknown[])=>mocks.put(...args)}));
vi.mock("../../app/session",()=>({useSession:()=>({state:{access:{mode:"LOCAL"}},user:{value:{role:"ADMIN"}}})}));
vi.mock("vue-router",()=>({useRouter:()=>({push:mocks.push}),onBeforeRouteLeave:(fn:unknown)=>mocks.leave(fn)}));
vi.mock("../../layouts/AuthLayout.vue",()=>({default:{template:'<main><slot name="header-actions"/><slot/></main>'}}));
vi.mock("../../shared/ui/CreditsDialog.vue",()=>({default:{props:['open','contributors'],template:'<div v-if="open" class="preview">{{ contributors }}</div>'}}));
vi.mock("../../shared/ui/ConfirmDialog.vue",()=>({default:{props:['open','title'],emits:['cancel','confirm'],template:'<div v-if="open" class="confirm"><span>{{title}}</span><button @click="$emit(\'confirm\')">确认</button><button @click="$emit(\'cancel\')">取消</button></div>'}}));
const extraA={name:"维护者－甲",contribution:"界面改进"},extraB={name:"维护者－乙",contribution:"测试"};
const wrappers:ReturnType<typeof mount>[]=[];
async function page(){const w=mount(CreditsEditorPage);wrappers.push(w);await flushPromises();return w;}
beforeEach(()=>{vi.clearAllMocks();mocks.get.mockResolvedValue({intro:acknowledgements.intro,contributors:[fixedContributor,extraA,extraB],revision:"r1"});});
afterEach(()=>wrappers.splice(0).forEach(w=>w.unmount()));
describe("credits editor",()=>{
  it("keeps the thanks and founder immutable, with no editable fields or row actions",async()=>{
    const w=await page();expect(w.get('.credits-editor-founder').text()).toContain(fixedContributor.name);
    expect(w.get('.credits-editor-founder').findAll('input,textarea,button')).toHaveLength(0);
    expect(w.findAll('input')).toHaveLength(2);expect(w.find('input[value="'+acknowledgements.intro+'"]').exists()).toBe(false);
    expect(w.get('button[aria-label="上移第2条"]').attributes('disabled')).toBeDefined();
  });
  it("reorders only additional entries and sends fixed values plus the loaded revision",async()=>{
    mocks.put.mockImplementation((_url,body)=>Promise.resolve({...body,revision:"r2"}));const w=await page();
    await w.get('button[aria-label="下移第2条"]').trigger('click');await w.get('form').trigger('submit');await flushPromises();
    expect(mocks.put).toHaveBeenCalledWith('/api/settings/credits',{intro:acknowledgements.intro,contributors:[fixedContributor,extraB,extraA],revision:"r1"});expect(w.text()).toContain('鸣谢已保存');
  });
  it("blocks incomplete rows and requires confirmation before removal",async()=>{
    const w=await page();await w.get('.credits-add').trigger('click');expect(w.get('button[type="submit"]').attributes('disabled')).toBeDefined();
    await w.get('button[aria-label="删除第4条"]').trigger('click');expect(w.findAll('.credits-editor-entry')).toHaveLength(3);
    await w.get('.confirm button').trigger('click');expect(w.findAll('.credits-editor-entry')).toHaveLength(2);expect(mocks.put).not.toHaveBeenCalled();
  });
  it("preserves entered fields after failed or conflicting saves",async()=>{
    mocks.put.mockRejectedValue(new Error('鸣谢已被更新'));const w=await page();await w.get('input').setValue('维护者－新姓名');await w.get('form').trigger('submit');await flushPromises();
    expect((w.get('input').element as HTMLInputElement).value).toBe('维护者－新姓名');expect(w.get('[role="alert"]').text()).toContain('已被更新');
  });
  it("prevents duplicate requests and leaving while a save is pending",async()=>{
    let resolve!:(x:unknown)=>void;mocks.put.mockReturnValue(new Promise(r=>{resolve=r}));const w=await page();await w.get('input').setValue('维护者－改名');await w.get('form').trigger('submit');await w.get('form').trigger('submit');
    expect(mocks.put).toHaveBeenCalledTimes(1);expect(mocks.leave.mock.calls[0]![0]()).toBe(false);resolve({contributors:[fixedContributor,extraA,extraB],revision:'r2'});await flushPromises();
  });
  it("requires a discard decision when leaving with unsaved changes",async()=>{
    const w=await page();await w.get('input').setValue('未保存');const decision=mocks.leave.mock.calls[0]![0]();await flushPromises();expect(w.text()).toContain('放弃未保存修改');await w.get('.confirm button:last-child').trigger('click');expect(await decision).toBe(false);
  });
});

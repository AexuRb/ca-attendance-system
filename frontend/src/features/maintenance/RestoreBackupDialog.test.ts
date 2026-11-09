// @vitest-environment jsdom
import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import RestoreBackupDialog from "./RestoreBackupDialog.vue";
const mocks=vi.hoisted(()=>({api:vi.fn()}));
vi.mock('../../shared/api',()=>({api:(...args:unknown[])=>mocks.api(...args)}));
const file=()=>new File(['archive'],'backup.zip',{type:'application/zip'});
const review=(bad=true,hash='hash-a')=>({fileHash:hash,phoneCount:bad?1:0,qqCount:bad?1:0,issues:bad?[{rowNumber:2,field:'QQ',reason:'格式错误'}]:[],truncated:false});
const wrappers:ReturnType<typeof mount>[]=[];
function page(){const w=mount(RestoreBackupDialog,{props:{open:true,file:file(),busy:false},global:{stubs:{ModalDialog:{template:'<section><slot/><slot name="footer"/></section>'}}}});wrappers.push(w);return w;}
beforeEach(()=>{vi.clearAllMocks();mocks.api.mockResolvedValue(review());});
afterEach(()=>wrappers.splice(0).forEach(w=>w.unmount()));
describe('backup contact confirmation',()=>{
 it('requires both the cleanup checkbox and typed confirmation',async()=>{
  const w=page();await flushPromises();await w.get('input[name="restoreConfirmation"]').setValue('恢复');
  const confirm=w.findAll('button').find(b=>b.text()==='确认恢复')!;
  expect(confirm.attributes('disabled')).toBeDefined();await w.get('input[name="restoreConfirmation"]').trigger('keyup.enter');expect(w.emitted('confirm')).toBeUndefined();
  await w.get('input[type="checkbox"]').setValue(true);await confirm.trigger('click');
  expect(w.emitted('confirm')?.[0]).toEqual([{clearInvalidContacts:true,reviewHash:'hash-a'}]);
  expect(mocks.api.mock.calls[0][1].body.get('file')).toBeInstanceOf(File);
 });
 it('keeps valid backup restores free of cleanup consent',async()=>{
  mocks.api.mockResolvedValue(review(false));const w=page();await flushPromises();expect(w.find('input[type="checkbox"]').exists()).toBe(false);
  await w.get('input[name="restoreConfirmation"]').setValue('恢复');await w.get('input[name="restoreConfirmation"]').trigger('keyup.enter');
  expect(w.emitted('confirm')?.[0]).toEqual([{clearInvalidContacts:false,reviewHash:'hash-a'}]);
 });
 it('blocks loading and failed previews and allows retry',async()=>{
  let reject!:(cause:Error)=>void;mocks.api.mockImplementationOnce(()=>new Promise((_,r)=>{reject=r;}));
  const w=page();await w.get('input[name="restoreConfirmation"]').setValue('恢复');await w.get('input[name="restoreConfirmation"]').trigger('keyup.enter');expect(w.emitted('confirm')).toBeUndefined();
  reject(new Error('文件校验失败'));await flushPromises();expect(w.text()).toContain('文件校验失败');
  await w.findAll('button').find(b=>b.text()==='重新校验')!.trigger('click');await flushPromises();expect(w.text()).not.toContain('文件校验失败');
  expect((w.get('input[name="restoreConfirmation"]').element as HTMLInputElement).value).toBe('恢复');
 });
 it('resets both confirmations when the file changes',async()=>{
  const w=page();await flushPromises();await w.get('input[type="checkbox"]').setValue(true);await w.get('input[name="restoreConfirmation"]').setValue('恢复');
  mocks.api.mockResolvedValue(review(true,'hash-b'));await w.setProps({file:file()});await flushPromises();
  expect((w.get('input[name="restoreConfirmation"]').element as HTMLInputElement).value).toBe('');expect((w.get('input[type="checkbox"]').element as HTMLInputElement).checked).toBe(false);
  await w.get('input[name="restoreConfirmation"]').trigger('keyup.enter');expect(w.emitted('confirm')).toBeUndefined();
 });
 it('aborts an old file check and ignores its late response',async()=>{
  let resolve!:(r:unknown)=>void;mocks.api.mockImplementationOnce(()=>new Promise(r=>{resolve=r;}));const w=page();
  const signal=mocks.api.mock.calls[0][1].signal as AbortSignal;
  mocks.api.mockResolvedValue(review(false,'hash-b'));await w.setProps({file:file()});await flushPromises();expect(signal.aborted).toBe(true);
  resolve(review(true));await flushPromises();expect(w.find('input[type="checkbox"]').exists()).toBe(false);
 });
});

// @vitest-environment jsdom
import { flushPromises, mount } from "@vue/test-utils";
import { ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import CreditsDialog from "./CreditsDialog.vue";
import ModalDialog from "./ModalDialog.vue";
import { acknowledgements, fixedContributor } from "../config/acknowledgements";
const mocks=vi.hoisted(()=>({get:vi.fn(),push:vi.fn(),role:'ADMIN'}));
vi.mock('../api',()=>({get:(...args:unknown[])=>mocks.get(...args)}));
vi.mock('../../app/session',()=>({useSession:()=>({user:ref({role:mocks.role})})}));
vi.mock('vue-router',()=>({useRouter:()=>({push:mocks.push})}));
vi.mock('./ModalDialog.vue',()=>({default:{props:['open','title','titleInteractive'],emits:['title-activate','close'],template:'<section v-if="open"><h2 @click="$emit(\'title-activate\')">{{title}}</h2><slot/></section>'}}));
const wrappers:ReturnType<typeof mount>[]=[];
function page(props={open:true}){const w=mount(CreditsDialog,{props});wrappers.push(w);return w;}
beforeEach(()=>{vi.useFakeTimers();vi.clearAllMocks();mocks.role='ADMIN';mocks.get.mockResolvedValue({contributors:[fixedContributor,{name:'后续维护者',contribution:'修复问题'}]});mocks.push.mockResolvedValue(undefined);});
afterEach(()=>{wrappers.splice(0).forEach(w=>w.unmount());vi.useRealTimers();});
describe('credits dialog',()=>{
  it('loads the saved contributors with the fixed thanks',async()=>{const w=page();await flushPromises();expect(w.text()).toContain(acknowledgements.intro);expect(w.text()).toContain('后续维护者');expect(w.find('.credits-mark').exists()).toBe(false);});
  it('opens the editor only after five consecutive administrator activations',async()=>{const w=page();for(let i=0;i<4;i++)await w.get('h2').trigger('click');expect(mocks.push).not.toHaveBeenCalled();await w.get('h2').trigger('click');expect(mocks.push).toHaveBeenCalledTimes(1);expect(mocks.push).toHaveBeenCalledWith({name:'credits-editor'});});
  it('resets the sequence after a long gap or closing',async()=>{const w=page();for(let i=0;i<4;i++)await w.get('h2').trigger('click');vi.advanceTimersByTime(2100);await w.get('h2').trigger('click');expect(mocks.push).not.toHaveBeenCalled();await w.setProps({open:false});await w.setProps({open:true});await w.get('h2').trigger('click');expect(mocks.push).not.toHaveBeenCalled();});
  it.each(['PRESIDENT','MINISTER','MEMBER'])('loads credits for %s without allowing editor activation',async(role)=>{mocks.role=role;const w=page();await flushPromises();expect(w.text()).toContain('后续维护者');expect(mocks.get).toHaveBeenCalledWith('/api/settings/credits',expect.any(Object));expect(w.getComponent(ModalDialog).props('titleInteractive')).toBe(false);for(let i=0;i<5;i++)await w.get('h2').trigger('click');expect(mocks.push).not.toHaveBeenCalled();});
  it('previews drafts without fetching or offering the hidden editor',async()=>{const w=mount(CreditsDialog,{props:{open:true,contributors:[fixedContributor]}});wrappers.push(w);for(let i=0;i<5;i++)await w.get('h2').trigger('click');expect(mocks.get).not.toHaveBeenCalled();expect(mocks.push).not.toHaveBeenCalled();});
  it('does not close the dialog when a route guard refuses navigation',async()=>{mocks.push.mockResolvedValue({type:4});const w=page();for(let i=0;i<5;i++)await w.get('h2').trigger('click');await flushPromises();expect(w.emitted('close')).toBeUndefined();});
});

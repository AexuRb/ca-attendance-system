// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createEmblemTransition, type EmblemKind } from "./emblemTransition";
type Playback = { element: HTMLElement; frames: Keyframe[]; options?: KeyframeAnimationOptions; finish: () => void; cancel: ReturnType<typeof vi.fn>; id?: string };
let playbacks: Playback[];
let root: HTMLElement;
let motion: ReturnType<typeof createEmblemTransition>;
let reduced = false;
let preference: EventTarget & { matches: boolean };
const bounds = (x=30,y=30,width=64,height=64) => ({ x,y,left:x,top:y,right:x+width,bottom:y+height,width,height,toJSON() { return {}; } });
function image(kind: EmblemKind) {
  const img=document.createElement("img");
  img.dataset.sharedEmblem=kind; img.src="/brand/ca-logo-transparent.svg"; img.alt="计算机协会会徽";
  img.getBoundingClientRect=()=>kind === "large" ? bounds(240,180,300,301) : bounds();
  return img;
}
function replace(kind: EmblemKind) { const img=image(kind); root.replaceChildren(img); motion.refresh(); return img; }
beforeEach(() => {
  playbacks=[]; reduced=false;
  preference=Object.assign(new EventTarget(), {get matches() {return reduced;}});
  Object.defineProperty(preference,"matches",{get:()=>reduced});
  vi.stubGlobal("matchMedia",()=>preference);
  vi.spyOn(HTMLElement.prototype,"getBoundingClientRect").mockImplementation(function(this: HTMLElement) { return this.classList.contains("emblem-transition-image") ? bounds(130,90,140,141) : bounds(); });
  Object.defineProperty(HTMLElement.prototype,"animate",{configurable:true,writable:true,value:function(this:HTMLElement,frames:Keyframe[],options?: KeyframeAnimationOptions) {
    let finish!:()=>void;
    const finished=new Promise<void>(resolve=>{finish=resolve;});
    const playback={element:this,frames,options,finish,cancel:vi.fn()};
    playbacks.push(playback);
    return Object.assign(playback,{finished});
  }});
  root=document.createElement("main"); document.body.append(root);
});
afterEach(() => { motion?.destroy(); root.remove(); document.querySelectorAll('.emblem-transition-image').forEach(e=>e.remove()); document.documentElement.removeAttribute('data-emblem-transition'); document.documentElement.style.removeProperty('--ui-duration-emblem'); vi.useRealTimers(); vi.restoreAllMocks(); vi.unstubAllGlobals(); delete (HTMLElement.prototype as Partial<HTMLElement>).animate; });
describe("shared emblem lifecycle", () => {
  it.each([["560ms",560],["700ms",700],[".64s",640],["invalid",640]] as const)("uses theme duration %s in milliseconds", (token, duration) => {
    document.documentElement.style.setProperty('--ui-duration-emblem',token);
    root.append(image('large')); motion=createEmblemTransition(root); replace('small');
    expect(playbacks[0].options?.duration).toBe(duration);
  });
  it("does not animate the first render, only large-to-small layout changes", () => {
    root.append(image("large")); motion=createEmblemTransition(root);
    expect(playbacks).toHaveLength(0);
    motion.prepare("small"); const target=replace("small");
    expect(document.querySelectorAll('.emblem-transition-image')).toHaveLength(1);
    expect(target.getAttribute('data-emblem-suppressed')).toBe('true');
    expect(playbacks[0].frames).toEqual([{transform:'translate3d(240px, 180px, 0) scale(1, 1)'},{transform:`translate3d(30px, 30px, 0) scale(${64/300}, ${64/301})`}]);
  });
  it.each(["small","large"] as const)("keeps %s-to-%s page navigation still", kind => {
    root.append(image(kind)); motion=createEmblemTransition(root); motion.prepare(kind); replace(kind);
    expect(playbacks).toHaveLength(0); expect(document.querySelector('.emblem-transition-image')).toBeNull();
  });
  it("carries only a decorative image across the authentication boundary and restores the actual target", async () => {
    const old=document.createElement('section'); old.textContent='private synthetic record'; old.append(image('small'));
    root.append(old); motion=createEmblemTransition(root); motion.prepare('large');
    const target=replace('large');
    const overlay=document.querySelector('.emblem-transition-image')!;
    expect(overlay.tagName).toBe('IMG'); expect(overlay.getAttribute('aria-hidden')).toBe('true'); expect(overlay.getAttribute('alt')).toBe('');
    expect(root.contains(old)).toBe(false); expect(document.body.textContent).not.toContain('private synthetic record');
    playbacks[0].finish(); await Promise.resolve();
    expect(target.hasAttribute('data-emblem-suppressed')).toBe(false); expect(document.querySelector('.emblem-transition-image')).toBeNull();
    expect(document.documentElement.hasAttribute('data-emblem-transition')).toBe(false);
  });
  it("can bridge startup replacement without a route guard", () => {
    root.append(image('large')); motion=createEmblemTransition(root); replace('small');
    expect(playbacks).toHaveLength(1);
  });
  it("keeps a flight continuous when the safe login fallback becomes the real login route", async () => {
    root.append(image('small')); motion=createEmblemTransition(root); motion.prepare('large'); const fallback=replace('large');
    motion.prepare('large'); const login=replace('large');
    expect(playbacks).toHaveLength(1); expect(playbacks[0].cancel).not.toHaveBeenCalled();
    expect(fallback.hasAttribute('data-emblem-suppressed')).toBe(false);
    expect(login.hasAttribute('data-emblem-suppressed')).toBe(true);
    playbacks[0].finish(); await Promise.resolve();
    expect(login.hasAttribute('data-emblem-suppressed')).toBe(false);
  });
  it("does not restart when the destination synchronizes its query", () => {
    root.append(image('large')); motion=createEmblemTransition(root); motion.prepare('small'); replace('small'); motion.prepare('small');
    expect(playbacks).toHaveLength(1); expect(playbacks[0].cancel).not.toHaveBeenCalled();
  });
  it("reverses a rapid navigation without retaining or duplicating old flights", async () => {
    root.append(image('small')); motion=createEmblemTransition(root); motion.prepare('large'); replace('large');
    const first=playbacks[0]; motion.prepare('small'); const final=replace('small');
    expect(first.cancel).toHaveBeenCalledTimes(1); expect(document.querySelectorAll('.emblem-transition-image')).toHaveLength(1);
    first.finish(); await Promise.resolve();
    expect(final.hasAttribute('data-emblem-suppressed')).toBe(true);
    playbacks.at(-1)!.finish(); await Promise.resolve();
    expect(document.querySelector('.emblem-transition-image')).toBeNull(); expect(final.hasAttribute('data-emblem-suppressed')).toBe(false);
  });
  it("reveals both original images when a destination never arrives", () => {
    vi.useFakeTimers(); const source=image('large'); root.append(source); motion=createEmblemTransition(root); motion.prepare('small');
    expect(source.hasAttribute('data-emblem-suppressed')).toBe(true); vi.advanceTimersByTime(1801);
    expect(source.hasAttribute('data-emblem-suppressed')).toBe(false); expect(document.querySelector('.emblem-transition-image')).toBeNull();
  });
  it("cancels on viewport changes and leaves controls interactive", () => {
    root.append(image('large')); motion=createEmblemTransition(root); const input=document.createElement('input'); input.value='unsaved draft';
    motion.prepare('small'); const target=replace('small'); root.append(input); window.dispatchEvent(new Event('resize'));
    expect(playbacks[0].cancel).toHaveBeenCalled(); expect(target.hasAttribute('data-emblem-suppressed')).toBe(false);
    expect(input.value).toBe('unsaved draft'); expect(input.disabled).toBe(false);
  });
  it("skips reduced motion and cancels immediately when the preference changes", () => {
    reduced=true; root.append(image('large')); motion=createEmblemTransition(root); motion.prepare('small'); replace('small');
    expect(playbacks).toHaveLength(0);
    reduced=false; motion.prepare('large'); const target=replace('large'); expect(playbacks).toHaveLength(1);
    reduced=true; preference.dispatchEvent(new Event('change'));
    expect(target.hasAttribute('data-emblem-suppressed')).toBe(false); expect(document.querySelector('.emblem-transition-image')).toBeNull();
  });
  it("falls back without Web Animations and cleans up on disposal", () => {
    delete (HTMLElement.prototype as Partial<HTMLElement>).animate;
    root.append(image('large')); motion=createEmblemTransition(root); motion.prepare('small'); const target=replace('small');
    expect(target.hasAttribute('data-emblem-suppressed')).toBe(false); expect(document.querySelector('.emblem-transition-image')).toBeNull();
    motion.destroy();
  });
});

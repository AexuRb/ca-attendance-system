// @vitest-environment jsdom
import { flushPromises, mount } from "@vue/test-utils";
import { defineComponent, h } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { get, post } from "../../shared/api";
import { useKioskAttendance } from "./useKioskAttendance";

vi.mock("../../shared/api", async original => ({ ...await original<typeof import("../../shared/api")>(), get: vi.fn(), post: vi.fn() }));
const cleanups: (() => void)[] = [];
beforeEach(() => {
  vi.useFakeTimers();
  vi.mocked(get).mockImplementation(async path => (path.endsWith("/week") ? [] : { slots: [] }) as never);
});
afterEach(() => { cleanups.splice(0).forEach(fn => fn()); vi.clearAllTimers(); vi.useRealTimers(); vi.resetAllMocks(); });
function setup() {
  let state!: ReturnType<typeof useKioskAttendance>;
  const wrapper = mount(defineComponent({ setup() { state = useKioskAttendance(); return () => h("div"); } }));
  cleanups.push(() => wrapper.unmount());
  return { state, wrapper };
}
const member = { exists: true, memberToken: "synthetic-selection", name: "合成成员", action: "CHECK_IN" as const, message: "" };

describe("bounded kiosk requests", () => {
  it("ends a stalled lookup after eight seconds and recovers without accepting its late result", async () => {
    let resolveOld!: (value: never) => void;
    vi.mocked(get).mockImplementation(path => path.includes("/lookup")
      ? new Promise(resolve => { resolveOld = resolve; })
      : Promise.resolve((path.endsWith("/week") ? [] : { slots: [] }) as never));
    const { state } = setup();
    await flushPromises();
    state.query.value = "990000204001";
    const lookup = state.lookup();
    await vi.advanceTimersByTimeAsync(8000);
    expect(state.busy.value).toBe(false);
    expect(state.error.value).toContain("超时");
    expect(state.query.value).toBe("990000204001");
    await lookup;
    vi.mocked(get).mockResolvedValue(member as never);
    await vi.advanceTimersByTimeAsync(2500);
    expect(state.step.value).toBe("confirm");
    resolveOld({ ...member, name: "迟到成员" } as never);
    await flushPromises();
    expect(state.lookupResult.value?.name).toBe("合成成员");
  });

  it("ends a stalled write after fifteen seconds and retries only on confirmation with the same id", async () => {
    let resolveOld!: (value: never) => void;
    vi.mocked(post).mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve; }));
    const { state } = setup();
    await flushPromises();
    state.lookupResult.value = member;
    state.step.value = "confirm";
    const write = state.submitAttendance();
    await state.submitAttendance();
    expect(post).toHaveBeenCalledOnce();
    await vi.advanceTimersByTimeAsync(15000);
    expect(state.busy.value).toBe(false);
    expect(state.step.value).toBe("confirm");
    expect(state.error.value).toContain("超时");
    await write;
    await vi.advanceTimersByTimeAsync(3000);
    expect(post).toHaveBeenCalledOnce();
    vi.mocked(post).mockResolvedValueOnce({ name: "合成成员", action: "CHECK_IN", submittedAt: new Date().toISOString() } as never);
    await state.submitAttendance();
    expect(vi.mocked(post).mock.calls[1]![1]).toEqual(vi.mocked(post).mock.calls[0]![1]);
    resolveOld({ name: "迟到成员", action: "CHECK_OUT", submittedAt: new Date().toISOString() } as never);
    await flushPromises();
    expect(state.successName.value).toBe("合成成员");
    expect(state.successAction.value).toBe("签到成功");
  });

  it("releases a stalled schedule batch so a later refresh can recover", async () => {
    const late = new Map<string, (value: never) => void>();
    vi.mocked(get).mockImplementation(path => new Promise(resolve => { late.set(path, resolve); }));
    const { state } = setup();
    await vi.advanceTimersByTimeAsync(8000);
    expect(state.scheduleError.value).toContain("超时");
    const fresh = { date: "2026-10-05", weekdayName: "星期一", slots: [] };
    vi.mocked(get).mockImplementation(async path => (path.endsWith("/week") ? [fresh] : fresh) as never);
    await vi.advanceTimersByTimeAsync(3000);
    expect(state.scheduleError.value).toBe("");
    expect(state.todaySchedule.value?.slots).toEqual([]);
    late.get("/api/public/schedules/today")!({ ...fresh, date: "2026-10-04" } as never);
    late.get("/api/public/schedules/week")!([] as never);
    await flushPromises();
    expect(state.todaySchedule.value).toEqual(fresh);
    expect(state.weekSchedule.value).toEqual([fresh]);
  });

  it("cancels an edited lookup without its late result or completion disturbing a newer lookup", async () => {
    const pending: { signal: AbortSignal; resolve: (value: never) => void }[] = [];
    vi.mocked(get).mockImplementation((path, options) => path.includes("/lookup")
      ? new Promise(resolve => { pending.push({ signal: options!.signal as AbortSignal, resolve }); })
      : Promise.resolve((path.endsWith("/week") ? [] : { slots: [] }) as never));
    const { state } = setup();
    await flushPromises();
    state.query.value = "old";
    const old = state.lookup();
    state.query.value = "new";
    state.clearError();
    expect(pending[0]!.signal.aborted).toBe(true);
    const current = state.lookup();
    await old;
    expect(state.busy.value).toBe(true);
    pending[0]!.resolve({ ...member, name: "旧查询" } as never);
    await flushPromises();
    expect(state.step.value).toBe("input");
    expect(state.busy.value).toBe(true);
    pending[1]!.resolve(member as never);
    await current;
    expect(state.lookupResult.value?.name).toBe("合成成员");
    await vi.advanceTimersByTimeAsync(11000);
    expect(pending).toHaveLength(2);
  });

  it.each(["lookup", "write"])("aborts pending schedules and %s on unmount without feedback or retries", async action => {
    const signals: AbortSignal[] = [];
    vi.mocked(get).mockImplementation((_path, options) => {
      signals.push(options!.signal as AbortSignal);
      return new Promise(() => {});
    });
    vi.mocked(post).mockImplementation((_path, _body, options) => {
      signals.push(options!.signal as AbortSignal);
      return new Promise(() => {});
    });
    const { state, wrapper } = setup();
    state.query.value = "990000204001";
    state.lookupResult.value = member;
    const pending = action === "lookup" ? state.lookup() : state.submitAttendance();
    wrapper.unmount();
    await pending;
    await vi.advanceTimersByTimeAsync(40000);
    expect(signals).toHaveLength(3);
    expect(signals.every(signal => signal.aborted)).toBe(true);
    expect(state.error.value).toBe("");
    expect(state.scheduleError.value).toBe("");
    expect(vi.getTimerCount()).toBe(0);
  });
});

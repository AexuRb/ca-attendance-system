// @vitest-environment jsdom
import { flushPromises, mount } from "@vue/test-utils";
import { defineComponent, h, type Ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, get, post } from "../../shared/api";
import { useKioskAttendance } from "./useKioskAttendance";

vi.mock("../../shared/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../shared/api")>();
  return {
    ...actual,
    get: vi.fn(),
    post: vi.fn(),
  };
});

afterEach(() => {
  vi.clearAllTimers();
  vi.useRealTimers();
  vi.mocked(get).mockReset();
  vi.mocked(post).mockReset();
  document.body.innerHTML = "";
});

type KioskState = ReturnType<typeof useKioskAttendance> & {
  currentDate: Ref<Date>;
};

function mountKioskState() {
  let state: KioskState | undefined;
  const wrapper = mount(defineComponent({
    setup() {
      state = useKioskAttendance() as KioskState;
      return () => h("div");
    },
  }));
  return { wrapper, get state() { return state!; } };
}

describe("useKioskAttendance schedule refresh", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 7, 9, 23, 59, 50));
    vi.mocked(get).mockImplementation(async (path) =>
      (path.endsWith("/week") ? [] : { slots: [] }) as never,
    );
  });

  it("retries a 503 lookup without letting a successful schedule poll hide its failure", async () => {
    let lookupAttempts = 0;
    vi.mocked(get).mockImplementation(async path => {
      if (path.includes("/attendance/lookup")) {
        if (++lookupAttempts === 1) throw new ApiError("synthetic internal detail", 503);
        return { exists: true, memberToken: "sel_member", name: "合成成员", action: "CHECK_IN" } as never;
      }
      return (path.endsWith("/week") ? [] : { slots: [] }) as never;
    });
    const mounted = mountKioskState();
    try {
      await flushPromises();
      mounted.state.query.value = "9900000011";
      await mounted.state.lookup();
      expect(mounted.state.online.value).toBe(false);
      expect(mounted.state.error.value).toContain("自动重试");
      expect(mounted.state.error.value).not.toContain("synthetic internal");
      window.dispatchEvent(new Event("focus"));
      await flushPromises();
      expect(mounted.state.online.value).toBe(false);
      await vi.advanceTimersByTimeAsync(2500);
      expect(lookupAttempts).toBe(2);
      expect(mounted.state.step.value).toBe("confirm");
      expect(mounted.state.online.value).toBe(true);
    } finally { mounted.wrapper.unmount(); }
  });

  it("keeps a failed write manual and reuses its attempt after background reads recover", async () => {
    const mounted = mountKioskState();
    try {
      await flushPromises();
      mounted.state.lookupResult.value = { exists: true, memberToken: "sel_member", name: "合成成员", action: "CHECK_IN", message: "" };
      mounted.state.step.value = "confirm";
      vi.mocked(post).mockRejectedValueOnce(new ApiError("synthetic private detail", 503))
        .mockResolvedValueOnce({ name: "合成成员", action: "CHECK_IN", submittedAt: new Date().toISOString() } as never);
      await mounted.state.submitAttendance();
      const firstBody = vi.mocked(post).mock.calls[0]![1];
      expect(mounted.state.online.value).toBe(false);
      expect(mounted.state.error.value).toContain("暂未确认");
      expect(mounted.state.error.value).not.toContain("synthetic private");
      window.dispatchEvent(new Event("focus"));
      await vi.advanceTimersByTimeAsync(5000);
      expect(post).toHaveBeenCalledOnce();
      expect(mounted.state.online.value).toBe(false);
      await mounted.state.submitAttendance();
      expect(vi.mocked(post).mock.calls[1]![1]).toEqual(firstBody);
      expect(mounted.state.step.value).toBe("success");
    } finally { mounted.wrapper.unmount(); }
  });

  it("does not mark a failed schedule service healthy after an attendance lookup succeeds", async () => {
    let scheduleFailed = true;
    vi.mocked(get).mockImplementation(async path => {
      if (path.includes("/attendance/lookup")) return { exists: true, memberToken: "sel_member", name: "合成成员", action: "CHECK_IN" } as never;
      if (scheduleFailed) throw new ApiError("synthetic schedule detail", 503);
      return (path.endsWith("/week") ? [] : { slots: [] }) as never;
    });
    const mounted = mountKioskState();
    try {
      await flushPromises();
      expect(mounted.state.scheduleError.value).not.toContain("synthetic");
      mounted.state.query.value = "9900000011";
      await mounted.state.lookup();
      expect(mounted.state.online.value).toBe(false);
      expect(mounted.state.step.value).toBe("confirm");
      scheduleFailed = false;
      await vi.advanceTimersByTimeAsync(3000);
      expect(mounted.state.online.value).toBe(true);
      expect(mounted.state.scheduleError.value).toBe("");
      expect(post).not.toHaveBeenCalled();
    } finally { mounted.wrapper.unmount(); }
  });

  it("does not retry an obsolete 503 lookup after the query is edited", async () => {
    vi.mocked(get).mockImplementation(async path => {
      if (path.includes("/attendance/lookup")) throw new ApiError("unavailable", 503);
      return (path.endsWith("/week") ? [] : { slots: [] }) as never;
    });
    const mounted = mountKioskState();
    try {
      await flushPromises();
      mounted.state.query.value = "9900000011";
      await mounted.state.lookup();
      mounted.state.query.value = "9900000022";
      mounted.state.clearError();
      await vi.advanceTimersByTimeAsync(5000);
      expect(vi.mocked(get).mock.calls.filter(([path]) => path.includes("/attendance/lookup"))).toHaveLength(1);
    } finally { mounted.wrapper.unmount(); }
  });

  it("updates the shared date and reloads schedules after midnight", async () => {
    let state: KioskState | undefined;
    const wrapper = mount(defineComponent({
      setup() {
        state = useKioskAttendance() as KioskState;
        return () => h("div");
      },
    }));
    await flushPromises();

    expect(state?.currentDate.value.getDate()).toBe(9);
    expect(vi.mocked(get)).toHaveBeenCalledTimes(2);

    vi.setSystemTime(new Date(2026, 7, 10, 0, 0, 20));
    await vi.advanceTimersByTimeAsync(30_000);
    await flushPromises();

    expect(state?.currentDate.value.getDate()).toBe(10);
    expect(vi.mocked(get)).toHaveBeenCalledTimes(4);
    wrapper.unmount();
  });

  it("reloads schedules when the kiosk regains focus", async () => {
    const wrapper = mount(defineComponent({
      setup() {
        useKioskAttendance();
        return () => h("div");
      },
    }));
    await flushPromises();
    expect(vi.mocked(get)).toHaveBeenCalledTimes(2);

    window.dispatchEvent(new Event("focus"));
    await flushPromises();

    expect(vi.mocked(get)).toHaveBeenCalledTimes(4);
    wrapper.unmount();
  });

  it("removes focus refresh listeners when unmounted", async () => {
    const wrapper = mount(defineComponent({
      setup() {
        useKioskAttendance();
        return () => h("div");
      },
    }));
    await flushPromises();
    wrapper.unmount();

    window.dispatchEvent(new Event("focus"));
    await flushPromises();

    expect(vi.mocked(get)).toHaveBeenCalledTimes(2);
  });

  it("does not restart a failed schedule request after unmount", async () => {
    let rejectToday: (cause: unknown) => void = () => undefined;
    let resolveWeek: (value: never) => void = () => undefined;
    vi.mocked(get).mockImplementation((path) => {
      if (path.endsWith("/week")) {
        return new Promise((resolve) => { resolveWeek = resolve; });
      }
      return new Promise((_, reject) => { rejectToday = reject; });
    });

    const mounted = mountKioskState();
    mounted.wrapper.unmount();
    rejectToday(new ApiError("连接失败", 0, true));
    resolveWeek([] as never);
    await flushPromises();
    await vi.advanceTimersByTimeAsync(3_000);

    expect(vi.mocked(get)).toHaveBeenCalledTimes(2);
  });
});

describe("useKioskAttendance lookup recovery", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.mocked(get).mockReset();
    vi.mocked(get).mockImplementation(async (path) =>
      (path.endsWith("/week") ? [] : { slots: [] }) as never,
    );
  });

  it("ignores a late selection response after returning to input", async () => {
    let resolveLookup!: (value: never) => void;
    vi.mocked(get).mockImplementation((path) => path.includes('/attendance/lookup')
      ? new Promise(resolve => { resolveLookup = resolve; })
      : Promise.resolve((path.endsWith('/week') ? [] : { slots: [] }) as never));
    const mounted = mountKioskState();
    mounted.state.step.value = 'choose';
    const pending = mounted.state.selectMember('sel_member');
    mounted.state.reset();
    mounted.state.query.value = '下一位';
    resolveLookup({ exists: true, memberToken: 'sel_member', name: '测试成员', action: 'CHECK_IN' } as never);
    await pending;
    expect(mounted.state.step.value).toBe('input');
    expect(mounted.state.query.value).toBe('下一位');
    mounted.wrapper.unmount();
  });

  it.each(['success', 'failure'])("ignores late submission %s after reset", async (outcome) => {
    let resolvePost!: (value: never) => void;
    let rejectPost!: (error: Error) => void;
    vi.mocked(post).mockImplementation(() => new Promise((resolve, reject) => { resolvePost = resolve; rejectPost = reject; }));
    const mounted = mountKioskState();
    mounted.state.lookupResult.value = { exists: true, memberToken: 'sel_member', name: '测试成员', action: 'CHECK_IN', message: '' };
    mounted.state.step.value = 'confirm';
    const pending = mounted.state.submitAttendance();
    mounted.state.reset();
    mounted.state.query.value = '下一位';
    if (outcome === 'success') resolvePost({ name: '测试成员', action: 'CHECK_IN', submittedAt: new Date().toISOString() } as never);
    else rejectPost(new ApiError('连接中断', 0, true));
    await pending;
    expect(mounted.state.step.value).toBe('input');
    expect(mounted.state.error.value).toBe('');
    await vi.advanceTimersByTimeAsync(5000);
    expect(mounted.state.query.value).toBe('下一位');
    mounted.wrapper.unmount();
  });

  it("does not clear the next lookup's pending state when a cancelled lookup finishes", async () => {
    const resolvers: Array<(value: never) => void> = [];
    vi.mocked(get).mockImplementation((path) => path.includes('/attendance/lookup')
      ? new Promise(resolve => { resolvers.push(resolve); })
      : Promise.resolve((path.endsWith('/week') ? [] : { slots: [] }) as never));
    const mounted = mountKioskState();
    const first = mounted.state.selectMember('sel_first');
    mounted.state.reset();
    const second = mounted.state.selectMember('sel_second');
    resolvers[0]!({ exists: true, memberToken: 'sel_first', name: '上一位', action: 'CHECK_IN' } as never);
    await first;
    expect(mounted.state.busy.value).toBe(true);
    expect(mounted.state.lookupResult.value).toBeNull();
    resolvers[1]!({ exists: true, memberToken: 'sel_second', name: '下一位', action: 'CHECK_IN' } as never);
    await second;
    expect(mounted.state.lookupResult.value?.name).toBe('下一位');
    expect(mounted.state.busy.value).toBe(false);
    mounted.wrapper.unmount();
  });

  it("does not start a success reset timer after unmount", async () => {
    let resolvePost!: (value: never) => void;
    vi.mocked(post).mockImplementation(() => new Promise(resolve => { resolvePost = resolve; }));
    const mounted = mountKioskState();
    await flushPromises();
    mounted.state.lookupResult.value = { exists: true, memberToken: 'sel_member', name: '测试成员', action: 'CHECK_IN', message: '' };
    mounted.state.step.value = 'confirm';
    const pending = mounted.state.submitAttendance();
    mounted.wrapper.unmount();
    resolvePost({ name: '测试成员', action: 'CHECK_IN', submittedAt: new Date().toISOString() } as never);
    await pending;
    expect(vi.getTimerCount()).toBe(0);
    expect(mounted.state.step.value).toBe('confirm');
  });

  it("retries the selected member after a network interruption", async () => {
    let selectionAttempts = 0;
    vi.mocked(get).mockImplementation(async (path) => {
      if (path.endsWith("/week")) return [] as never;
      if (path.includes("/attendance/lookup")) {
        selectionAttempts += 1;
        if (selectionAttempts === 1) {
          throw new ApiError("本机服务暂时无法连接", 0, true);
        }
        return {
          exists: true,
          memberToken: "sel_member",
          maskedStudentNo: "******1224",
          name: "测试成员",
          action: "CHECK_IN",
          message: "请确认",
        } as never;
      }
      return { slots: [] } as never;
    });

    const mounted = mountKioskState();
    await flushPromises();
    mounted.state.step.value = "choose";
    await mounted.state.selectMember("sel_member");

    expect(mounted.state.error.value).toContain("自动重试");
    expect(mounted.state.online.value).toBe(false);

    await vi.advanceTimersByTimeAsync(2_500);
    await flushPromises();

    expect(selectionAttempts).toBe(2);
    expect(mounted.state.step.value).toBe("confirm");
    expect(mounted.state.online.value).toBe(true);
    mounted.wrapper.unmount();
  });

  it("keeps business errors online and does not retry them", async () => {
    let lookupAttempts = 0;
    vi.mocked(get).mockImplementation(async (path) => {
      if (path.endsWith("/week")) return [] as never;
      if (path.includes("/attendance/lookup")) {
        lookupAttempts += 1;
        throw new ApiError("账号已停用", 400, false);
      }
      return { slots: [] } as never;
    });

    const mounted = mountKioskState();
    await flushPromises();
    mounted.state.query.value = "1000000000";
    await mounted.state.lookup();

    expect(mounted.state.online.value).toBe(true);
    expect(mounted.state.error.value).toBe("账号已停用");

    await vi.advanceTimersByTimeAsync(5_000);
    expect(lookupAttempts).toBe(1);
    mounted.wrapper.unmount();
  });
});

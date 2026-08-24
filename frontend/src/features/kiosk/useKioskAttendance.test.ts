// @vitest-environment jsdom
import { flushPromises, mount } from "@vue/test-utils";
import { defineComponent, h, type Ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, get } from "../../shared/api";
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

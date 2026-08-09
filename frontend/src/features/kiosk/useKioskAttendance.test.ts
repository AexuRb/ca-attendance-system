import { flushPromises, mount } from "@vue/test-utils";
import { defineComponent, h, type Ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { get } from "../../shared/api";
import { useKioskAttendance } from "./useKioskAttendance";

vi.mock("../../shared/api", () => ({
  get: vi.fn(),
  post: vi.fn(),
}));

type KioskState = ReturnType<typeof useKioskAttendance> & {
  currentDate: Ref<Date>;
};

describe("useKioskAttendance schedule refresh", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 7, 9, 23, 59, 50));
    vi.mocked(get).mockImplementation(async (path) =>
      (path.endsWith("/week") ? [] : { slots: [] }) as never,
    );
  });

  afterEach(() => {
    vi.useRealTimers();
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
});

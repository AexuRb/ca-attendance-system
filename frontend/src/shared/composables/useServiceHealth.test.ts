// @vitest-environment jsdom
import { defineComponent, nextTick } from "vue";
import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import { useServiceHealth } from "./useServiceHealth";

const mocks = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock("../api", () => ({ get: (...args: unknown[]) => mocks.get(...args) }));

afterEach(() => {
  mocks.get.mockReset();
  vi.useRealTimers();
});

describe("useServiceHealth", () => {
  it("reports a failed health check and recovers on focus", async () => {
    mocks.get
      .mockRejectedValueOnce(new Error("offline"))
      .mockResolvedValueOnce({ status: "UP" });
    const Probe = defineComponent({
      setup() {
        return useServiceHealth(60_000);
      },
      template: '<span>{{ checking ? "checking" : online ? "online" : "offline" }}</span>',
    });
    const wrapper = mount(Probe);
    await vi.waitFor(() => expect(wrapper.text()).toBe("offline"));

    window.dispatchEvent(new Event("focus"));
    await vi.waitFor(() => expect(wrapper.text()).toBe("online"));
    expect(mocks.get).toHaveBeenCalledTimes(2);
    wrapper.unmount();
    await nextTick();
  });
});

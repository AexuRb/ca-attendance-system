// @vitest-environment jsdom
import { mount } from "@vue/test-utils";
import { defineComponent, h, ref } from "vue";
import { describe, expect, it, vi } from "vitest";
import { useUnsavedChanges } from "./useUnsavedChanges";

describe("useUnsavedChanges", () => {
  it("defers a guarded action until changes are discarded", () => {
    const dirty = ref(true);
    const action = vi.fn();
    let guard!: ReturnType<typeof useUnsavedChanges>;
    const wrapper = mount(
      defineComponent({
        setup() {
          guard = useUnsavedChanges(() => dirty.value);
          return () => h("div");
        },
      }),
    );

    expect(guard.request(action)).toBe(false);
    expect(guard.confirmOpen.value).toBe(true);
    expect(action).not.toHaveBeenCalled();
    guard.discard();
    expect(action).toHaveBeenCalledTimes(1);
    expect(guard.confirmOpen.value).toBe(false);
    wrapper.unmount();
  });

  it("only prevents browser unload while dirty", () => {
    const dirty = ref(false);
    let guard!: ReturnType<typeof useUnsavedChanges>;
    const wrapper = mount(
      defineComponent({
        setup() {
          guard = useUnsavedChanges(() => dirty.value);
          return () => h("div");
        },
      }),
    );
    const cleanEvent = new Event("beforeunload", { cancelable: true });
    window.dispatchEvent(cleanEvent);
    expect(cleanEvent.defaultPrevented).toBe(false);

    dirty.value = true;
    const dirtyEvent = new Event("beforeunload", { cancelable: true });
    window.dispatchEvent(dirtyEvent);
    expect(dirtyEvent.defaultPrevented).toBe(true);
    guard.cancel();
    wrapper.unmount();
  });

  it("runs the deferred cancellation when the user keeps editing", () => {
    const dirty = ref(true);
    const action = vi.fn();
    const cancelAction = vi.fn();
    let guard!: ReturnType<typeof useUnsavedChanges>;
    const wrapper = mount(
      defineComponent({
        setup() {
          guard = useUnsavedChanges(() => dirty.value);
          return () => h("div");
        },
      }),
    );

    guard.request(action, cancelAction);
    guard.cancel();
    expect(action).not.toHaveBeenCalled();
    expect(cancelAction).toHaveBeenCalledTimes(1);
    wrapper.unmount();
  });
});

// @vitest-environment jsdom
import { mount } from "@vue/test-utils";
import { defineComponent, h } from "vue";
import { afterEach, expect, it, vi } from "vitest";
import { reloadAt } from "./navigationRecovery";
import { useUnsavedChanges } from "../shared/composables/useUnsavedChanges";

afterEach(() => { vi.restoreAllMocks(); window.history.replaceState(null, "", "/"); });

it("leaves the current address and dirty workspace intact when document departure is cancelled", () => {
  window.history.replaceState(null, "", "/?stage=source#/source");
  const before = window.location.href;
  const wrapper = mount(defineComponent({ setup() {
    useUnsavedChanges(() => true);
    return () => h("input", { value: "合成未保存草稿" });
  } }));
  const location = {
    href: before,
    reload: vi.fn(),
    replace: vi.fn((href: string) => {
      const departure = new Event("beforeunload", { cancelable: true });
      window.dispatchEvent(departure);
      if (!departure.defaultPrevented) location.href = href;
    }),
  };
  try {
    reloadAt("#/target?from=2026-10-02", location);
    expect(window.location.href).toBe(before);
    expect(location.href).toBe(before);
    expect(location.replace).toHaveBeenCalledOnce();
    expect(wrapper.find("input").element.value).toBe("合成未保存草稿");
  } finally { wrapper.unmount(); }
});

it("requests a new document at the intended hash while preserving the application's base URL", () => {
  const location = { href: "http://localhost:3000/offline/?stage=final&_reload=old#/source", replace: vi.fn<(href: string) => void>(), reload: vi.fn() };
  reloadAt("#/target?from=2026-10-02&page=2", location);
  expect(location.replace).toHaveBeenCalledOnce();
  const target = new URL(location.replace.mock.calls[0]![0]);
  expect(target.origin).toBe("http://localhost:3000");
  expect(target.pathname).toBe("/offline/");
  expect(target.searchParams.get("stage")).toBe("final");
  expect(target.searchParams.get("_reload")).not.toBe("old");
  expect(target.hash).toBe("#/target?from=2026-10-02&page=2");
  // A hash-only navigation would reuse the document and its failed module cache.
  expect(target.search).not.toBe(new URL(location.href).search);
  expect(location.reload).not.toHaveBeenCalled();
});

// @vitest-environment jsdom
import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import BackupWorkspace from "./BackupWorkspace.vue";

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  document.body.innerHTML = "";
});

describe("BackupWorkspace", () => {
  it("refreshes the relative backup age while the page remains open", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-08-30T12:01:30"));
    vi.stubGlobal("matchMedia", vi.fn(() => ({
      matches: false,
      media: "(max-width: 1280px)",
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })));
    const wrapper = mount(BackupWorkspace, {
      props: {
        summary: null,
        backups: [{
          filename: "backup.zip",
          size: 1024,
          createdAt: "2026-08-30T12:00:00",
        }],
        loading: false,
        createPending: false,
        canRestore: true,
        canDelete: true,
        restoreFileError: "",
      },
      global: { stubs: { Teleport: true } },
    });

    expect(wrapper.get(".data-backup-action time").text()).toBe("1 分钟前");
    await vi.advanceTimersByTimeAsync(60_000);
    expect(wrapper.get(".data-backup-action time").text()).toBe("2 分钟前");
    wrapper.unmount();
  });
});

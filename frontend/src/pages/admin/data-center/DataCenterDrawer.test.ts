// @vitest-environment jsdom
import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import DataCenterDrawer from "./DataCenterDrawer.vue";

function stubCompactViewport(matches: boolean) {
  const matchMedia = vi.fn(() => ({
    matches,
    media: "(max-width: 1280px)",
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }));
  vi.stubGlobal("matchMedia", matchMedia);
  return matchMedia;
}

afterEach(() => {
  vi.unstubAllGlobals();
  document.documentElement.style.overflow = "";
  document.body.style.overflow = "";
  document.body.innerHTML = "";
});

describe("DataCenterDrawer", () => {
  it("behaves as a modal drawer on compact viewports", async () => {
    const matchMedia = stubCompactViewport(true);
    const opener = document.createElement("button");
    opener.textContent = "打开抽屉";
    document.body.append(opener);
    opener.focus();

    const wrapper = mount(DataCenterDrawer, {
      attachTo: document.body,
      props: {
        open: false,
        eyebrow: "备份详情",
        title: "可下载恢复点",
        closeLabel: "关闭备份详情",
        panelClass: "data-backup-drawer",
        panelId: "backup-detail-panel",
      },
      slots: { default: "<button type='button'>下载备份</button>" },
    });

    await wrapper.setProps({ open: true });
    await flushPromises();

    const dialog = document.body.querySelector<HTMLElement>('[role="dialog"]');
    expect(matchMedia).toHaveBeenCalledWith("(max-width: 1280px)");
    expect(dialog?.id).toBe("backup-detail-panel");
    expect(dialog?.getAttribute("aria-modal")).toBe("true");
    const backdrop = document.body.querySelector<HTMLElement>(".data-drawer-backdrop");
    expect(backdrop?.tagName).toBe("DIV");
    expect(backdrop?.getAttribute("aria-hidden")).toBe("true");
    expect(document.activeElement?.getAttribute("aria-label")).toBe("关闭备份详情");
    expect(document.body.style.overflow).toBe("hidden");

    document.dispatchEvent(new KeyboardEvent("keydown", {
      key: "Escape",
      bubbles: true,
    }));
    expect(wrapper.emitted("close")).toHaveLength(1);

    await wrapper.setProps({ open: false });
    await flushPromises();
    expect(document.body.style.overflow).toBe("");
    expect(document.activeElement).toBe(opener);
    wrapper.unmount();
  });

  it("remains an inline complementary region on wide viewports", async () => {
    stubCompactViewport(false);
    const wrapper = mount(DataCenterDrawer, {
      props: {
        open: true,
        eyebrow: "导出配置",
        title: "调整条件与字段",
        closeLabel: "收起导出配置",
        panelClass: "data-config-drawer",
      },
      slots: { default: "<p>配置内容</p>" },
    });
    await flushPromises();

    expect(wrapper.get('[role="complementary"]').attributes("aria-modal")).toBeUndefined();
    expect(document.body.style.overflow).toBe("");
    wrapper.unmount();
  });
});

// @vitest-environment jsdom
import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it } from "vitest";
import RepairRecycleWorkspace from "./RepairRecycleWorkspace.vue";
import RecycleDetailDialog from "./RecycleDetailDialog.vue";
import type { RecycledRepairCase } from "../../../features/maintenance/dataCenterTypes";

const items: RecycledRepairCase[] = Array.from({ length: 30 }, (_, index) => ({
  id: index + 1, caseNo: `TEST-${index + 1}`, agreementType: "REPAIR",
  ownerName: `测试送修人${index + 1}`, ownerPhone: "00000000000", deviceType: "笔记本",
  deviceModel: "测试设备", faultDescription: "完整故障描述", serviceDescription: "完整处理内容",
  status: "COMPLETED", receivedAt: "2026-10-01T14:00:00", completedAt: "2026-10-02T14:00:00",
  deletedAt: "2026-10-14T14:00:00", dataBackupConfirmed: true, riskAcknowledged: true, privacyAcknowledged: true,
}));
afterEach(() => { document.body.innerHTML = ""; });

describe("recycle result navigation", () => {
  it("pages 20/10, resets on a search and clamps when a refreshed result loses its last page", async () => {
    const wrapper = mount(RepairRecycleWorkspace, {
      props: { items, loading: false, canPurge: true, isRestorePending: () => false },
      global: { stubs: { Teleport: true } },
    });
    expect(wrapper.findAll(".recycle-record")).toHaveLength(20);
    await wrapper.get(".recycle-pagination div button:last-child").trigger("click");
    expect(wrapper.findAll(".recycle-record")).toHaveLength(10);
    expect(wrapper.get(".recycle-record .recycle-case-number").text()).toBe("TEST-21");
    await wrapper.get("input").setValue("测试送修人30");
    expect(wrapper.findAll(".recycle-record")).toHaveLength(1);
    expect(wrapper.get(".data-table-toolbar").text()).toContain("匹配 1 项 / 全部 30 项");
    expect(wrapper.get(".recycle-pagination").text()).toContain("第 1 / 1 页");
    await wrapper.get("input").setValue("");
    await wrapper.get(".recycle-pagination div button:last-child").trigger("click");
    await wrapper.setProps({ items: items.slice(0, 20) });
    expect(wrapper.findAll(".recycle-record")).toHaveLength(20);
    expect(wrapper.get(".recycle-pagination").text()).toContain("第 1 / 1 页");
    wrapper.unmount();
  });

  it("opens full read-only details without triggering recovery or purge", async () => {
    const wrapper = mount(RepairRecycleWorkspace, {
      props: { items, loading: false, canPurge: true, isRestorePending: () => false },
      global: { stubs: { Teleport: true } },
    });
    await wrapper.get(".recycle-device").trigger("click");
    const dialog = wrapper.get('[role="dialog"]');
    expect(dialog.text()).toContain("完整故障描述");
    expect(dialog.text()).toContain("完整处理内容");
    expect(dialog.text()).toContain("原状态 · 已完成");
    expect(dialog.text()).toContain("**** **** 0000");
    expect(dialog.find("input,textarea").exists()).toBe(false);
    expect(wrapper.emitted("restore")).toBeUndefined();
    expect(wrapper.emitted("request-purge")).toBeUndefined();
    wrapper.unmount();
  });

  it("conceals a revealed phone when switching records and hides the toggle for missing phones", async () => {
    const wrapper = mount(RecycleDetailDialog, { props: { item: items[0] }, global: { stubs: { Teleport: true } } });
    await wrapper.get('button[aria-label="显示完整电话"]').trigger("click");
    expect(wrapper.get('[role="dialog"]').text()).toContain("00000000000");
    await wrapper.setProps({ item: items[1] });
    expect(wrapper.get('[role="dialog"]').text()).not.toContain("00000000000");
    await wrapper.setProps({ item: { ...items[2]!, ownerPhone: "" } });
    expect(wrapper.find('button[aria-label="显示完整电话"]').exists()).toBe(false);
    wrapper.unmount();
  });
});

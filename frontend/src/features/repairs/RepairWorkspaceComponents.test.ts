// @vitest-environment jsdom
import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import ActiveRepairGrid from "./ActiveRepairGrid.vue";
import RepairDetailDrawer from "./RepairDetailDrawer.vue";
import RepairHistoryTable from "./RepairHistoryTable.vue";
import RepairLedgerTable from "./RepairLedgerTable.vue";
import RepairStatusTabs from "./RepairStatusTabs.vue";
import type { RepairCase } from "./repairTypes";

const activeRepair: RepairCase = {
  id: 7,
  caseNo: "WX-2026-0007",
  agreementType: "REPAIR",
  ownerName: "张同学",
  ownerPhone: "13812345678",
  deviceType: "笔记本电脑",
  deviceBrand: "联想",
  deviceModel: "小新 Pro",
  faultDescription: "无法进入系统，偶发蓝屏",
  serviceDescription: "正在检测硬盘健康状态",
  accessories: "电源适配器",
  dataBackupConfirmed: true,
  riskAcknowledged: true,
  privacyAcknowledged: true,
  status: "REPAIRING",
  receivedAt: "2026-07-01T14:00:00",
  handlerName: "陈部长",
  updatedAt: "2026-08-12T16:30:00",
};

const historyRepair: RepairCase = {
  ...activeRepair,
  id: 8,
  caseNo: "WX-2026-0008",
  status: "COMPLETED",
  completedAt: "2026-08-12T17:30:00",
};

describe("repair workspace components", () => {
  it("switches state through a counted segmented control", async () => {
    const wrapper = mount(RepairStatusTabs, {
      props: {
        activeStatus: "REPAIRING",
        counts: { REPAIRING: 5, COMPLETED: 1000, CANCELED: 30 },
      },
    });

    expect(wrapper.findAll('[role="tab"]')).toHaveLength(3);
    expect(wrapper.text()).toContain("1,000");
    await wrapper.findAll('[role="tab"]')[1].trigger("click");
    expect(wrapper.emitted("change")?.[0]).toEqual(["COMPLETED"]);
  });

  it("moves repair tabs with arrow keys", async () => {
    const wrapper = mount(RepairStatusTabs, {
      attachTo: document.body,
      props: {
        activeStatus: "REPAIRING",
        counts: { REPAIRING: 5, COMPLETED: 1000, CANCELED: 30 },
      },
    });

    const tabs = wrapper.findAll<HTMLButtonElement>('[role="tab"]');
    tabs[0].element.focus();
    await tabs[0].trigger("keydown", { key: "ArrowRight" });

    expect(wrapper.emitted("change")?.[0]).toEqual(["COMPLETED"]);
    expect(document.activeElement).toBe(tabs[1].element);
    expect(tabs[0].attributes("aria-controls")).toBe("repair-panel-REPAIRING");
    wrapper.unmount();
  });

  it("renders active work as focused cards and keeps phone numbers masked", async () => {
    const wrapper = mount(ActiveRepairGrid, {
      props: {
        items: [activeRepair],
        loading: false,
        error: "",
        revealedPhones: new Set<number>(),
        canManage: true,
      },
    });

    expect(wrapper.get(".repair-active-card").classes()).toContain(
      "is-long-running",
    );
    expect(wrapper.text()).toContain("**** **** 5678");
    expect(wrapper.text()).not.toContain("13812345678");
    await wrapper.get(".repair-card-main").trigger("click");
    expect(wrapper.emitted("view")?.[0]).toEqual([activeRepair]);
  });

  it("renders completed and canceled records as dense history rows", async () => {
    const wrapper = mount(RepairHistoryTable, {
      props: {
        items: [historyRepair],
        status: "COMPLETED",
        loading: false,
        error: "",
        revealedPhones: new Set<number>(),
      },
    });

    expect(wrapper.findAll(".repair-history-row")).toHaveLength(1);
    expect(wrapper.text()).toContain("完成时间");
    expect(wrapper.text()).toContain("**** **** 5678");
    await wrapper.get(".repair-history-row").trigger("click");
    expect(wrapper.emitted("view")?.[0]).toEqual([historyRepair]);
  });

  it("renders active and archived cases through one ledger surface", async () => {
    const activeWrapper = mount(RepairLedgerTable, {
      props: {
        items: [activeRepair],
        status: "REPAIRING",
        loading: false,
        error: "",
        revealedPhones: new Set<number>(),
        canManage: true,
        canDelete: true,
      },
    });

    expect(activeWrapper.findAll(".repair-ledger-row")).toHaveLength(1);
    expect(activeWrapper.text()).toContain("受理时间");
    expect(activeWrapper.text()).toContain("进行中");
    expect(activeWrapper.text()).toContain("**** **** 5678");
    expect(activeWrapper.find('[title="编辑"]').exists()).toBe(true);
    expect(activeWrapper.find('[title="移入回收站"]').exists()).toBe(true);
    await activeWrapper.get(".repair-ledger-row").trigger("click");
    expect(activeWrapper.emitted("view")?.[0]).toEqual([activeRepair]);

    const archivedWrapper = mount(RepairLedgerTable, {
      props: {
        items: [historyRepair],
        status: "COMPLETED",
        loading: false,
        error: "",
        revealedPhones: new Set<number>(),
        canManage: true,
        canDelete: true,
      },
    });
    expect(archivedWrapper.text()).toContain("完成时间");
    expect(archivedWrapper.text()).toContain("已完成");
    expect(archivedWrapper.find('[title="编辑"]').exists()).toBe(false);
    expect(archivedWrapper.find('[title="移入回收站"]').exists()).toBe(false);
  });

  it("opens a history row with the Space key", async () => {
    const wrapper = mount(RepairHistoryTable, {
      props: {
        items: [historyRepair],
        status: "COMPLETED",
        loading: false,
        error: "",
        revealedPhones: new Set<number>(),
      },
    });

    await wrapper.get(".repair-history-row").trigger("keydown", { key: " " });
    expect(wrapper.emitted("view")?.[0]).toEqual([historyRepair]);
  });

  it("shows shared details and exposes actions according to permissions", async () => {
    const wrapper = mount(RepairDetailDrawer, {
      attachTo: document.body,
      props: {
        open: true,
        item: historyRepair,
        phoneVisible: false,
        canManage: true,
        canDelete: false,
      },
    });

    const dialog = document.body.querySelector<HTMLElement>('[role="dialog"]');
    expect(dialog?.getAttribute("aria-modal")).toBe("true");
    expect(dialog?.textContent).toContain("无法进入系统，偶发蓝屏");
    expect(dialog?.textContent).toContain("正在检测硬盘健康状态");
    expect(dialog?.textContent).toContain("查看协议");
    expect(dialog?.textContent).toContain("编辑事务");
    expect(dialog?.textContent).not.toContain("移入回收站");

    (dialog?.querySelector('[data-action="preview"]') as HTMLButtonElement).click();
    expect(wrapper.emitted("preview")?.[0]).toEqual([historyRepair]);
    wrapper.unmount();
  });
});

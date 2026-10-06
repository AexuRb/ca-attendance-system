// @vitest-environment jsdom
import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import { defineComponent, ref } from "vue";
import RepairsPage from "./RepairsPage.vue";
import type {
  RepairCase,
  RepairPage,
  RepairStatus,
} from "../../features/repairs/repairTypes";

const apiRequest = vi.fn();
const apiGet = vi.fn();
const apiPut = vi.fn();
const routerReplace = vi.fn();
const routerPush = vi.fn();

vi.mock("../../shared/api", () => ({
  api: (...args: unknown[]) => apiRequest(...args),
  get: (...args: unknown[]) => apiGet(...args),
  post: vi.fn(),
  put: (...args: unknown[]) => apiPut(...args),
  del: vi.fn(),
  downloadBlob: vi.fn(),
}));

vi.mock("../../app/session", () => ({
  useSession: () => ({
    user: ref({ id: 1, name: "管理员", role: "ADMIN" }), state: { access: { kioskAvailable: true } }, logout: vi.fn(),
  }),
}));

vi.mock("vue-router", () => ({
  RouterLink: { template: "<a><slot /></a>" },
  useRoute: () => ({ name: "repairs", query: {} }),
  useRouter: () => ({ replace: routerReplace, push: routerPush, afterEach: () => () => {}, onError: () => () => {} }),
  onBeforeRouteLeave: vi.fn(),
}));

afterEach(() => {
  apiRequest.mockReset();
  apiGet.mockReset();
  apiPut.mockReset();
  routerReplace.mockReset();
  routerPush.mockReset();
  document.body.innerHTML = "";
});

describe("RepairsPage workspace", () => {
  it("browses only the current page and remasks phone numbers between records", async () => {
    apiGet.mockResolvedValue([]);
    apiRequest.mockResolvedValue(repairPage("REPAIRING", 1, 2, 2));
    const wrapper = mount(RepairsPage);
    await flushPromises();
    await wrapper.get(".repair-ledger-row").trigger("click");
    await flushPromises();
    const drawer = wrapper.findComponent({ name: "RepairDetailDrawer" });
    expect(drawer.props("canPrevious")).toBe(false);
    drawer.vm.$emit("toggle-phone", 1);
    await flushPromises();
    expect(drawer.props("phoneVisible")).toBe(true);
    drawer.vm.$emit("next");
    await flushPromises();
    expect(drawer.props("item").id).toBe(2);
    expect(drawer.props("phoneVisible")).toBe(false);
    expect(drawer.props("canNext")).toBe(false);
    expect(apiRequest).toHaveBeenCalledTimes(1);
    wrapper.unmount();
  });

  it("returns to the detail on cancel or save, including a record moved out of the current status", async () => {
    apiGet.mockResolvedValue([]);
    apiRequest.mockResolvedValue(repairPage("REPAIRING", 1, 1, 1));
    const wrapper = mount(RepairsPage);
    await flushPromises();
    await wrapper.get(".repair-ledger-row").trigger("click");
    const drawer = wrapper.findComponent({ name: "RepairDetailDrawer" });
    const original = drawer.props("item");
    drawer.vm.$emit("process", original);
    await flushPromises();
    const editor = wrapper.findComponent({ name: "RepairEditorDialog" });
    expect(editor.props("initialStep")).toBe(2);
    expect(document.body.querySelector('[name="repair-received-at"]')).not.toBeNull();
    editor.vm.$emit("close");
    await flushPromises();
    expect(drawer.props("open")).toBe(true);
    expect(drawer.props("item").id).toBe(1);
    drawer.vm.$emit("process", original);
    await flushPromises();
    apiPut.mockRejectedValueOnce(new Error("保存失败"));
    editor.vm.$emit("save");
    await flushPromises();
    expect(editor.props("open")).toBe(true);
    const saved = { ...original, status: "COMPLETED", serviceDescription: "已更换配件", completedAt: "2026-08-13T14:00:00" };
    apiPut.mockResolvedValueOnce(saved);
    apiRequest.mockResolvedValueOnce(repairPage("REPAIRING", 1, 0, 0));
    editor.vm.$emit("save");
    await flushPromises();
    expect(drawer.props("open")).toBe(true);
    expect(drawer.props("item").serviceDescription).toBe("已更换配件");
    expect(drawer.props("position")).toContain("已不在当前页");
    expect(drawer.props("canNext")).toBe(false);
    wrapper.unmount();
  });

  it("loads, pages and switches only the active status", async () => {
    apiGet.mockImplementation((url: string) => {
      if (url === "/api/repairs/handler-candidates") return Promise.resolve([]);
      return Promise.resolve([]);
    });
    apiRequest.mockImplementation((url: string) => {
      const params = new URL(url, "http://localhost").searchParams;
      const status = params.get("status") as RepairStatus;
      const page = Number(params.get("page"));
      const total = status === "REPAIRING" ? 31 : status === "COMPLETED" ? 4 : 2;
      const count = status === "REPAIRING" && page === 1 ? 20 : page === 2 ? 11 : total;
      return Promise.resolve(repairPage(status, page, count, total));
    });

    const wrapper = mount(RepairsPage, {
      global: {
        stubs: {
          AgreementDialog: true,
          AccountPicker: true,
          ConfirmDialog: true,
          ModalDialog: true,
        },
      },
    });
    await flushPromises();

    expect(requests()).toEqual([
      { status: "REPAIRING", page: "1", pageSize: "20" },
    ]);
    expect(
      wrapper.findAll(".repair-status-tabs b").map((item) => item.text()),
    ).toEqual(["31", "4", "2"]);
    expect(wrapper.findAll(".repair-ledger-row")).toHaveLength(20);
    expect(wrapper.find(".repair-ledger-table").exists()).toBe(true);

    await wrapper.get(".repair-workspace-pagination button:last-child").trigger("click");
    await flushPromises();

    expect(requests().at(-1)).toEqual({
      status: "REPAIRING",
      page: "2",
      pageSize: "20",
    });
    expect(wrapper.findAll(".repair-ledger-row")).toHaveLength(11);

    await wrapper
      .findAll('[role="tab"]')
      .find((button) => button.text().includes("已完成"))!
      .trigger("click");
    await flushPromises();

    expect(requests().at(-1)).toEqual({
      status: "COMPLETED",
      page: "1",
      pageSize: "20",
    });
    expect(wrapper.findAll(".repair-ledger-row")).toHaveLength(4);
    expect(wrapper.get('[role="tab"][aria-selected="true"]').text()).toContain(
      "已完成",
    );

    await wrapper.get(".repair-ledger-row").trigger("click");
    await flushPromises();

    expect(document.body.querySelector(".repair-detail-drawer")?.textContent).toContain(
      "PAGE-COMPLETED-10000",
    );
  });

  it("keeps the latest agreement when older preview requests finish later", async () => {
    apiGet.mockResolvedValue([]);
    let resolveFirst!: (value: string) => void;
    let resolveSecond!: (value: string) => void;
    const firstText = new Promise<string>((resolve) => { resolveFirst = resolve; });
    const secondText = new Promise<string>((resolve) => { resolveSecond = resolve; });
    apiRequest.mockImplementation((url: string) => {
      if (url === "/api/repairs/1/agreement") {
        return Promise.resolve({ text: () => firstText });
      }
      if (url === "/api/repairs/2/agreement") {
        return Promise.resolve({ text: () => secondText });
      }
      return Promise.resolve(repairPage("REPAIRING", 1, 2, 2));
    });

    const AgreementProbe = defineComponent({
      props: ["caseNo", "html", "loading"],
      template: '<div class="agreement-probe">{{ caseNo }}|{{ html }}|{{ loading }}</div>',
    });
    const wrapper = mount(RepairsPage, {
      global: { stubs: { AgreementDialog: AgreementProbe } },
    });
    await flushPromises();

    const previewButtons = wrapper.findAll(
      '.repair-ledger-actions button[title="查看协议"]',
    );
    await previewButtons[0].trigger("click");
    await previewButtons[1].trigger("click");
    resolveSecond("<p>第二份协议</p>");
    await flushPromises();
    expect(wrapper.get(".agreement-probe").text()).toContain("PAGE-REPAIRING-2");
    expect(wrapper.get(".agreement-probe").text()).toContain("第二份协议");

    resolveFirst("<p>第一份旧协议</p>");
    await flushPromises();
    expect(wrapper.get(".agreement-probe").text()).not.toContain("第一份旧协议");
    expect(wrapper.get(".agreement-probe").text()).toContain("第二份协议");
  });

  it("rejects an invalid draft query while exporting only the last applied range across all statuses", async () => {
    apiGet.mockResolvedValue([]);
    apiRequest.mockResolvedValue(repairPage("REPAIRING", 1, 0, 0));
    const wrapper = mount(RepairsPage);
    await flushPromises();
    const initialParams = new URL(String(apiRequest.mock.calls[0]?.[0]), "http://localhost").searchParams;
    apiRequest.mockClear();

    const dates = wrapper.findAll('input[type="date"]');
    expect(dates).toHaveLength(2);
    await dates[0].setValue("2026-08-22");
    await dates[1].setValue("2026-08-21");
    await wrapper.get("form.repair-query-bar").trigger("submit");

    expect(wrapper.get('[role="alert"]').text()).toContain(
      "开始日期不能晚于结束日期",
    );
    expect(wrapper.get(".mw-tools .button.secondary").text()).toContain("导出全部状态");
    expect(apiRequest).not.toHaveBeenCalled();

    await wrapper.get(".mw-tools .button.secondary").trigger("click");
    await flushPromises();
    expect(apiRequest).not.toHaveBeenCalled();
    const exportCall = apiGet.mock.calls.find(([url]) => String(url).startsWith("/api/repairs/export?"));
    const exported = new URL(String(exportCall?.[0]), "http://localhost").searchParams;
    expect(exported.get("from")).toBe(initialParams.get("from"));
    expect(exported.get("to")).toBe(initialParams.get("to"));
    expect(exported.get("status")).toBe("ALL");
    wrapper.unmount();
  });
});

function requests() {
  return apiRequest.mock.calls.map(([url]) => {
    const params = new URL(String(url), "http://localhost").searchParams;
    return {
      status: params.get("status"),
      page: params.get("page"),
      pageSize: params.get("pageSize"),
    };
  });
}

function repairPage(
  status: RepairStatus,
  page: number,
  count: number,
  total: number,
): RepairPage {
  return {
    items: Array.from({ length: count }, (_, index) =>
      repairCase((page - 1) * 20 + index + statusOffset(status), status),
    ),
    total,
    page,
    pageSize: 20,
    hasMore: page * 20 < total,
    statusCounts: {
      REPAIRING: 31,
      COMPLETED: 4,
      CANCELED: 2,
    },
  };
}

function repairCase(id: number, status: RepairStatus): RepairCase {
  return {
    id,
    caseNo: `PAGE-${status}-${id}`,
    agreementType: "PERSONAL_DEVICE",
    ownerName: `送修人${id}`,
    ownerPhone: "13800000000",
    deviceType: "笔记本电脑",
    faultDescription: "无法开机",
    dataBackupConfirmed: true,
    riskAcknowledged: true,
    privacyAcknowledged: true,
    status,
    receivedAt: "2026-08-12T14:00:00",
    updatedAt: "2026-08-12T14:00:00",
  };
}

function statusOffset(status: RepairStatus) {
  if (status === "COMPLETED") return 10_000;
  if (status === "CANCELED") return 20_000;
  return 1;
}

vi.mock("../../shared/composables/useServiceHealth", () => ({ useServiceHealth: () => ({ online: true }) }));

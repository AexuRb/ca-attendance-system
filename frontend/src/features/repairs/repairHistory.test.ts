// @vitest-environment jsdom
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { defineComponent, h, ref } from "vue";
import { createRouter, createWebHashHistory, RouterView, type Router } from "vue-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { clearPrivateNavigationState } from "../../shared/navigation/privateNavigationState";
import RepairLedgerTable from "./RepairLedgerTable.vue";
import { useRepairManagementWorkspace } from "./useRepairManagementWorkspace";
import type { RepairCase, RepairStatus } from "./repairTypes";

const api = vi.hoisted(() => ({ request: vi.fn(), get: vi.fn() }));
vi.mock("../../shared/api", () => ({ api: api.request, get: api.get, post: vi.fn(), put: vi.fn(), del: vi.fn(), downloadBlob: vi.fn() }));
vi.mock("../../app/session", () => ({ useSession: () => ({ user: ref({ id: 1, name: "合成管理员", role: "ADMIN" }) }) }));

let workspace: ReturnType<typeof useRepairManagementWorkspace>;
let router: Router;
let wrapper: VueWrapper;
const page = defineComponent({ setup() {
  workspace = useRepairManagementWorkspace();
  return () => h(RepairLedgerTable, {
    items: workspace.repairPage.items, status: workspace.activeStatus.value,
    loading: workspace.repairPage.loading, error: workspace.repairPage.error,
    revealedPhones: workspace.revealedPhones.value, canManage: true, canDelete: true,
    bindScroll: workspace.bindTableScroll, onScroll: workspace.rememberTableScroll,
  });
} });
async function navigate(url: string) { await router.push(url); await flushPromises(); }
async function back() {
  const arrived = new Promise<void>((resolve) => { const stop = router.afterEach(() => { stop(); resolve(); }); });
  router.back(); await arrived; await flushPromises();
}
function lastQuery() {
  return new URL(String(api.request.mock.calls.at(-1)![0]), "http://localhost").searchParams;
}
async function query(keyword = "成功条件") {
  workspace.filters.keyword = keyword;
  workspace.filters.from = "2026-08-01";
  workspace.filters.to = "2026-08-31";
  await workspace.load(); await flushPromises();
}
function repair(id: number, status: RepairStatus): RepairCase {
  return { id, caseNo: `DEMO-${id}`, agreementType: "REPAIR", ownerName: "合成联系人", ownerPhone: "13800005678", deviceType: "合成设备", faultDescription: "演示故障", dataBackupConfirmed: true, riskAcknowledged: true, privacyAcknowledged: true, status, receivedAt: "2026-08-01T14:00:00" };
}
beforeEach(async () => {
  clearPrivateNavigationState();
  window.history.replaceState({}, "", "/#/elsewhere");
  api.get.mockResolvedValue([]);
  api.request.mockImplementation((url: string) => {
    const q = new URL(url, "http://localhost").searchParams;
    const target = Number(q.get("page") || 1);
    const status = q.get("status") as RepairStatus;
    if (q.get("keyword") === "失败条件") return Promise.reject(new Error("合成查询失败"));
    return Promise.resolve({ items: [repair(target * 10, status)], total: 40, page: target, pageSize: 20, hasMore: target < 2, statusCounts: { REPAIRING: 40, COMPLETED: 40, CANCELED: 0 } });
  });
  router = createRouter({ history: createWebHashHistory(), routes: [
    { path: "/repairs", name: "repairs", component: page },
    { path: "/elsewhere", component: defineComponent({ render: () => h("p", "另一页") }) },
  ] });
  await router.push("/elsewhere"); await router.isReady();
  wrapper = mount(defineComponent({ render: () => h(RouterView) }), { global: { plugins: [router] } });
  await navigate("/repairs");
});
afterEach(() => { wrapper.unmount(); router.options.history.destroy(); api.get.mockReset(); api.request.mockReset(); clearPrivateNavigationState(); });

describe("repair private history context", () => {
  it("restores successful keyword, range, status and page without drafts", async () => {
    await query(); await workspace.setStatus("COMPLETED"); await workspace.setPage(2); await flushPromises();
    workspace.tableScroll.value!.scrollTop = 480;
    workspace.tableScroll.value!.scrollLeft = 150;
    workspace.tableScroll.value!.dispatchEvent(new Event("scroll"));
    workspace.filters.keyword = "未提交草稿";
    await navigate("/elsewhere"); await back();
    expect(workspace.filters).toEqual({ keyword: "成功条件", from: "2026-08-01", to: "2026-08-31" });
    expect(workspace.activeStatus.value).toBe("COMPLETED");
    expect(workspace.repairPage.page).toBe(2);
    expect(lastQuery().get("keyword")).toBe("成功条件");
    expect(workspace.tableScroll.value!.scrollTop).toBe(480);
    expect(workspace.tableScroll.value!.scrollLeft).toBe(150);
    expect(location.href + JSON.stringify(window.history.state) + JSON.stringify(localStorage) + JSON.stringify(sessionStorage)).not.toMatch(/成功条件|未提交草稿|13800005678|合成联系人/);
  });

  it("keeps visits separate and restores the failed history request on retry", async () => {
    await query("查询甲"); await workspace.setPage(2); await flushPromises();
    workspace.tableScroll.value!.scrollTop = 310;
    workspace.filters.from = "2026-09-01"; workspace.filters.to = "2026-09-30"; workspace.filters.keyword = "查询乙";
    await workspace.load(); await flushPromises();
    api.request.mockRejectedValueOnce(new Error("历史读取失败"));
    await back();
    expect(workspace.repairPage.error).toBe("历史读取失败");
    expect(workspace.appliedFilters.keyword).toBe("查询乙");
    workspace.filters.keyword = "失败后草稿";
    await workspace.retry(); await flushPromises();
    expect(lastQuery().get("keyword")).toBe("查询甲");
    expect(workspace.repairPage.page).toBe(2);
    expect(workspace.tableScroll.value!.scrollTop).toBe(310);
    expect(workspace.appliedFilters.from).toBe("2026-08-01");
  });

  it("does not let a failed query replace the successful visit", async () => {
    await query(); await workspace.setPage(2); await flushPromises();
    await query("失败条件");
    expect(workspace.repairPage.error).toBe("合成查询失败");
    await navigate("/elsewhere"); await back();
    expect(workspace.filters.keyword).toBe("成功条件");
    expect(workspace.repairPage.page).toBe(2);
  });

  it("does not reuse private conditions on fresh navigation or after a session reset", async () => {
    await query();
    const url = router.currentRoute.value.fullPath;
    await navigate("/elsewhere"); await navigate(url);
    expect(workspace.filters.keyword).toBe("");
    await query();
    clearPrivateNavigationState();
    workspace.tableScroll.value!.scrollTop = 240;
    await navigate("/elsewhere"); await back();
    expect(workspace.filters.keyword).toBe("");
    expect(workspace.tableScroll.value!.scrollTop).toBe(0);
  });

  it("consumes an incoming keyword before a failed initial request and retries its original value", async () => {
    await navigate("/elsewhere");
    await navigate("/repairs?keyword=失败条件&page=2&status=COMPLETED");
    expect(router.currentRoute.value.query.keyword).toBeUndefined();
    expect(workspace.repairPage.error).toBe("合成查询失败");
    workspace.filters.keyword = "其他输入";
    api.request.mockResolvedValueOnce({ items: [repair(20, "COMPLETED")], total: 40, page: 2, pageSize: 20, hasMore: false, statusCounts: { REPAIRING: 0, COMPLETED: 40, CANCELED: 0 } });
    await workspace.retry(); await flushPromises();
    expect(lastQuery().get("keyword")).toBe("失败条件");
    expect(workspace.activeStatus.value).toBe("COMPLETED");
    expect(workspace.repairPage.page).toBe(2);
    expect(router.currentRoute.value.query.keyword).toBeUndefined();
  });

  it("returns to a freshly loaded masked ledger without reopening dialogs or restoring operation targets", async () => {
    await query();
    const item = workspace.repairPage.items[0];
    workspace.openEditor(item);
    workspace.detailTarget.value = item;
    workspace.deleteTarget.value = item;
    workspace.agreementOpen.value = true;
    workspace.agreementHtml.value = "旧协议内容";
    workspace.togglePhone(item.id);
    await navigate("/elsewhere"); await back();
    expect(workspace.editorOpen.value || workspace.agreementOpen.value).toBe(false);
    expect(workspace.detailTarget.value || workspace.deleteTarget.value).toBeNull();
    expect(workspace.agreementHtml.value).toBe("");
    expect(workspace.revealedPhones.value.size).toBe(0);
    expect(wrapper.text()).not.toContain("13800005678");
    expect(workspace.form.ownerName).toBe("");
  });

  it("does not apply another page's position when the backend clamps the saved page", async () => {
    await query(); await workspace.setPage(2); await flushPromises();
    workspace.tableScroll.value!.scrollTop = 440;
    await navigate("/elsewhere");
    const successful = api.request.getMockImplementation()!;
    api.request.mockImplementation(async (url: string) => {
      const result = await successful(url);
      return { ...result, items: result.page === 2 ? [] : result.items, total: 5, hasMore: false };
    });
    await back();
    expect(workspace.repairPage.page).toBe(1);
    expect(workspace.tableScroll.value!.scrollTop).toBe(0);
    expect(router.currentRoute.value.query.page).toBeUndefined();
  });
});

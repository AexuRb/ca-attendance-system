// @vitest-environment jsdom
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { defineComponent, h, nextTick } from "vue";
import { createRouter, createWebHashHistory, RouterView, type Router } from "vue-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { clearPrivateNavigationState } from "../../shared/navigation/privateNavigationState";
import { useAttendanceRecordsWorkspace } from "./useAttendanceRecordsWorkspace";

const apiGet = vi.hoisted(() => vi.fn());
vi.mock("../../shared/api", () => ({ get: apiGet, post: vi.fn(), put: vi.fn(), del: vi.fn() }));
vi.mock("../../app/session", () => ({ useSession: () => ({ user: { value: { role: "MINISTER" } } }) }));

let workspace: ReturnType<typeof useAttendanceRecordsWorkspace>;
let router: Router;
let wrapper: VueWrapper;
const page = defineComponent({
  setup() {
    workspace = useAttendanceRecordsWorkspace();
    return () => h("div", { ref: workspace.tableScroll, onScroll: workspace.rememberTableScroll },
      workspace.records.value.map((item) => h("span", item.name)));
  },
});
async function navigate(url: string) {
  await router.push(url);
  await flushPromises();
}
async function back() {
  const arrived = new Promise<void>((resolve) => {
    const stop = router.afterEach(() => { stop(); resolve(); });
  });
  router.back();
  await arrived;
  await flushPromises();
}
function lastQuery() {
  return new URLSearchParams((apiGet.mock.calls.at(-1)![0] as string).split("?")[1]);
}
async function query(keyword: string, status = "") {
  workspace.filters.keyword = keyword;
  workspace.filters.status = status;
  await workspace.applyFilters();
  await flushPromises();
}

beforeEach(async () => {
  clearPrivateNavigationState();
  window.history.replaceState({}, "", "/#/elsewhere");
  apiGet.mockImplementation((url: string) => {
    const q = new URLSearchParams(url.split("?")[1]);
    if (q.get("studentNo") === "失败条件") return Promise.reject(new Error("演示失败"));
    return Promise.resolve({ items: [{ id: 1, name: q.get("studentNo") || "合成成员" }], total: 40, page: Number(q.get("page") || 1) });
  });
  router = createRouter({ history: createWebHashHistory(), routes: [
    { path: "/attendance", component: page },
    { path: "/elsewhere", component: defineComponent({ render: () => h("p", "另一页") }) },
  ] });
  await router.push("/elsewhere");
  await router.isReady();
  wrapper = mount(defineComponent({ render: () => h(RouterView) }), { global: { plugins: [router] } });
  await navigate("/attendance");
});
afterEach(() => {
  wrapper.unmount();
  router.options.history.destroy();
  apiGet.mockReset();
  clearPrivateNavigationState();
});

describe("attendance private history context", () => {
  it("restores successful keyword, page and both table axes after leaving, without persisting private values", async () => {
    await query("合成关键词", "PENDING");
    await workspace.setPage(2);
    const table = workspace.tableScroll.value!;
    table.scrollTop = 480;
    table.scrollLeft = 150;
    table.dispatchEvent(new Event("scroll"));
    workspace.filters.keyword = "未提交输入";
    await navigate("/elsewhere");
    await back();
    expect(workspace.filters.keyword).toBe("合成关键词");
    expect(workspace.filters.status).toBe("PENDING");
    expect(workspace.page.value).toBe(2);
    expect(lastQuery().get("studentNo")).toBe("合成关键词");
    expect(workspace.tableScroll.value!.scrollTop).toBe(480);
    expect(workspace.tableScroll.value!.scrollLeft).toBe(150);
    expect(location.href + JSON.stringify(window.history.state) + JSON.stringify(localStorage) + JSON.stringify(sessionStorage)).not.toMatch(/合成关键词|未提交输入/);
  });

  it("restores each query history entry independently and keeps it through a failed return request", async () => {
    await query("查询甲", "VALID");
    workspace.tableScroll.value!.scrollTop = 210;
    await query("查询乙", "PENDING");
    apiGet.mockRejectedValueOnce(new Error("返回失败"));
    await back();
    expect(workspace.appliedFilters.value.keyword).toBe("查询乙");
    expect(workspace.listError.value).toBe("返回失败");
    workspace.filters.keyword = "失败后草稿";
    await workspace.retryLoad();
    await nextTick();
    expect(lastQuery().get("studentNo")).toBe("查询甲");
    expect(workspace.appliedFilters.value.keyword).toBe("查询甲");
    expect(workspace.tableScroll.value!.scrollTop).toBe(210);
  });

  it("does not save a failed query or reuse a previous visit on fresh navigation", async () => {
    await query("成功查询");
    await query("失败条件", "INVALID");
    await navigate("/elsewhere");
    await back();
    expect(workspace.filters.keyword).toBe("成功查询");
    const previousUrl = router.currentRoute.value.fullPath;
    await navigate("/elsewhere");
    await navigate(previousUrl);
    expect(workspace.filters.keyword).toBe("");
    expect(lastQuery().has("studentNo")).toBe(false);
  });

  it("clears old scopes immediately on session reset and rejects late writes", async () => {
    await query("旧会话关键词");
    clearPrivateNavigationState();
    workspace.tableScroll.value!.scrollTop = 320;
    await navigate("/elsewhere");
    await back();
    expect(workspace.filters.keyword).toBe("");
    expect(workspace.tableScroll.value!.scrollTop).toBe(0);
  });

  it("consumes an incoming private keyword before a failing request and retains the retry snapshot", async () => {
    await navigate("/elsewhere");
    await navigate("/attendance?keyword=失败条件");
    expect(router.currentRoute.value.query.keyword).toBeUndefined();
    expect(workspace.listError.value).toBe("演示失败");
    workspace.filters.keyword = "其他草稿";
    apiGet.mockResolvedValueOnce({ items: [], total: 0, page: 1 });
    await workspace.retryLoad();
    expect(lastQuery().get("studentNo")).toBe("失败条件");
  });
});

// @vitest-environment jsdom
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { defineComponent, h } from "vue";
import { createRouter, createWebHashHistory, RouterView, type Router } from "vue-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { clearPrivateNavigationState } from "../../shared/navigation/privateNavigationState";
import MemberRecords from "./MemberRecords.vue";
import { useMemberDirectoryWorkspace } from "./useMemberDirectoryWorkspace";

const apiGet = vi.hoisted(() => vi.fn());
vi.mock("../../shared/api", () => ({ get: apiGet, post: vi.fn(), put: vi.fn(), del: vi.fn() }));
vi.mock("../../app/session", () => ({ useSession: () => ({ user: { value: { id: 1, role: "ADMIN" } } }) }));

let workspace: ReturnType<typeof useMemberDirectoryWorkspace>;
let router: Router;
let wrapper: VueWrapper;
const page = defineComponent({
  setup() {
    workspace = useMemberDirectoryWorkspace();
    return () => h(MemberRecords, {
      members: workspace.members.value, selected: workspace.selected.value,
      selectableIds: workspace.selectableIds.value, allSelected: workspace.allFilteredSelected.value,
      bindScroll: workspace.bindTableScroll, onTableScroll: workspace.rememberTableScroll,
    });
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
  const request = apiGet.mock.calls.filter(([url]) => (url as string).startsWith("/api/users/page?")).at(-1)![0] as string;
  return new URLSearchParams(request.split("?")[1]);
}
async function query(keyword: string, role = "MEMBER") {
  workspace.filters.keyword = keyword;
  workspace.filters.role = role;
  await workspace.applyFilters();
  await flushPromises();
}

beforeEach(async () => {
  clearPrivateNavigationState();
  window.history.replaceState({}, "", "/#/elsewhere");
  apiGet.mockImplementation((url: string) => {
    if (url === "/api/users/grades") return Promise.resolve(["2025级"]);
    if (url.startsWith("/api/users/selection")) return Promise.resolve([2, 22]);
    const q = new URLSearchParams(url.split("?")[1]);
    if (q.get("keyword") === "失败条件") return Promise.reject(new Error("演示失败"));
    const target = Number(q.get("page") || 1);
    return Promise.resolve({ items: [{ id: target === 1 ? 2 : 22, name: q.get("keyword") || "合成成员", studentNo: "demo", role: "MEMBER", status: "ACTIVE" }], total: 40, page: target });
  });
  router = createRouter({ history: createWebHashHistory(), routes: [
    { path: "/members", component: page },
    { path: "/elsewhere", component: defineComponent({ render: () => h("p", "另一页") }) },
  ] });
  await router.push("/elsewhere");
  await router.isReady();
  wrapper = mount(defineComponent({ render: () => h(RouterView) }), { global: { plugins: [router] } });
  await navigate("/members");
});
afterEach(() => {
  wrapper.unmount();
  router.options.history.destroy();
  apiGet.mockReset();
  clearPrivateNavigationState();
});

describe("member private history context", () => {
  it("restores successful filters, page and table axes without restoring selection or private drafts", async () => {
    workspace.filters.status = "ACTIVE";
    workspace.filters.grade = "2025级";
    await query("合成关键词");
    workspace.toggleMember(2);
    await workspace.setPage(2);
    expect([...workspace.selected.value]).toEqual([2]);
    const table = workspace.tableScroll.value!;
    table.scrollTop = 480;
    table.scrollLeft = 150;
    table.dispatchEvent(new Event("scroll"));
    workspace.filters.keyword = "未提交输入";
    await navigate("/elsewhere");
    await back();
    expect(workspace.filters).toEqual({ keyword: "合成关键词", role: "MEMBER", status: "ACTIVE", grade: "2025级" });
    expect(workspace.page.value).toBe(2);
    expect(workspace.selected.value.size).toBe(0);
    expect(lastQuery().get("keyword")).toBe("合成关键词");
    expect(workspace.tableScroll.value!.scrollTop).toBe(480);
    expect(workspace.tableScroll.value!.scrollLeft).toBe(150);
    expect(location.href + JSON.stringify(window.history.state) + JSON.stringify(localStorage) + JSON.stringify(sessionStorage)).not.toMatch(/合成关键词|未提交输入/);
  });

  it("keeps each query entry separate, clears changed-filter selection and retries a failed history request", async () => {
    await query("查询甲");
    workspace.tableScroll.value!.scrollTop = 210;
    workspace.toggleMember(2);
    await query("查询乙", "MINISTER");
    expect(workspace.selected.value.size).toBe(0);
    apiGet.mockRejectedValueOnce(new Error("返回失败"));
    await back();
    expect(workspace.appliedFilters.value.keyword).toBe("查询乙");
    expect(workspace.listError.value).toBe("返回失败");
    workspace.filters.keyword = "失败后草稿";
    await workspace.retry();
    expect(lastQuery().get("keyword")).toBe("查询甲");
    expect(workspace.appliedFilters.value.keyword).toBe("查询甲");
    expect(workspace.tableScroll.value!.scrollTop).toBe(210);
  });

  it("does not let a failed query replace the successful visit or reuse it on fresh navigation", async () => {
    await query("成功查询");
    workspace.toggleMember(2);
    await query("失败条件", "MINISTER");
    expect([...workspace.selected.value]).toEqual([2]);
    await navigate("/elsewhere");
    await back();
    expect(workspace.filters.keyword).toBe("成功查询");
    const previousUrl = router.currentRoute.value.fullPath;
    await navigate("/elsewhere");
    await navigate(previousUrl);
    expect(workspace.filters.keyword).toBe("");
    expect(lastQuery().has("keyword")).toBe(false);
  });

  it("does not cache the all-results selection across leaving", async () => {
    await query("查询甲");
    const input = document.createElement("input");
    input.checked = true;
    input.addEventListener("change", (event) => { void workspace.toggleAll(event); });
    input.dispatchEvent(new Event("change"));
    await flushPromises();
    expect(workspace.allFilteredSelected.value).toBe(true);
    expect([...workspace.selected.value]).toEqual([2, 22]);
    await navigate("/elsewhere");
    await back();
    expect(workspace.selected.value.size).toBe(0);
    expect(workspace.allFilteredSelected.value).toBe(false);
  });

  it("invalidates old scopes on session reset, including late scroll writes", async () => {
    await query("旧会话关键词");
    clearPrivateNavigationState();
    workspace.tableScroll.value!.scrollTop = 320;
    await navigate("/elsewhere");
    await back();
    expect(workspace.filters.keyword).toBe("");
    expect(workspace.tableScroll.value!.scrollTop).toBe(0);
  });

  it("consumes the incoming keyword before a failing initial request and preserves the retry snapshot", async () => {
    await navigate("/elsewhere");
    await navigate("/members?keyword=失败条件");
    expect(router.currentRoute.value.query.keyword).toBeUndefined();
    expect(workspace.listError.value).toBe("演示失败");
    workspace.filters.keyword = "其他草稿";
    apiGet.mockResolvedValueOnce({ items: [], total: 0, page: 1 });
    await workspace.retry();
    expect(lastQuery().get("keyword")).toBe("失败条件");
  });
});

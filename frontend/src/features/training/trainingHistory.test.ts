// @vitest-environment jsdom
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { defineComponent, h } from "vue";
import { createRouter, createWebHashHistory, RouterView, type Router } from "vue-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { clearPrivateNavigationState } from "../../shared/navigation/privateNavigationState";
import { useTrainingManagementWorkspace } from "./useTrainingManagementWorkspace";
import TrainingMonthRibbon from "./TrainingMonthRibbon.vue";
import TrainingParticipantList from "./TrainingParticipantList.vue";

const api = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock("../../shared/api", () => ({ api: api.get, get: api.get, post: vi.fn(), put: vi.fn(), del: vi.fn(), downloadBlob: vi.fn() }));

let workspace: ReturnType<typeof useTrainingManagementWorkspace>;
let router: Router;
let wrapper: VueWrapper;
const page = defineComponent({
  setup() {
    workspace = useTrainingManagementWorkspace();
    return () => h("div", [
      h(TrainingMonthRibbon, { label: "合成月份", selectedId: workspace.selected.value?.id || null,
        ...workspace.sessionState, bindScroll: workspace.bindRibbonScroll, restoringHistory: workspace.restoringHistory.value, onScroll: workspace.rememberScroll }),
      workspace.selected.value && h(TrainingParticipantList, { ...workspace.participantState, keyword: workspace.participantKeyword.value,
        sessionId: workspace.selected.value.id, bindScroll: workspace.bindParticipantScroll, onScroll: workspace.rememberScroll }),
    ]);
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
function lastParticipantQuery() {
  const url = api.get.mock.calls.filter(([url]) => String(url).includes("/participants/page?")).at(-1)![0] as string;
  return new URL(url, "http://localhost");
}
async function query() {
  workspace.filters.keyword = "场次条件";
  workspace.filters.from = "2026-08-01";
  workspace.filters.to = "2026-08-31";
  await workspace.applyFilters();
  await workspace.setSessionPage(2);
  await workspace.selectSession(workspace.sessions.value[1]);
  workspace.participantKeyword.value = "名单条件";
  await workspace.searchParticipants();
  await workspace.setParticipantPage(2);
  await flushPromises();
}
beforeEach(async () => {
  clearPrivateNavigationState();
  window.history.replaceState({}, "", "/#/elsewhere");
  api.get.mockImplementation((url: string) => {
    const q = new URL(url, "http://localhost").searchParams;
    const number = Number(q.get("page") || 1);
    const participants = url.includes("participants/page");
    const items = participants ? [{ id: number, sessionId: 22, name: "合成参与者", durationHours: 2 }] : [number * 10 + 1, number * 10 + 2].map(id => ({ id, title: `合成场次${id}`, trainingDate: "2026-08-01", participantCount: 40, totalDurationHours: 80 }));
    return Promise.resolve({ items, total: 40, page: number, pageSize: 20, hasMore: number < 2 });
  });
  router = createRouter({ history: createWebHashHistory(), routes: [
    { path: "/training", component: page },
    { path: "/elsewhere", component: defineComponent({ render: () => h("p", "另一页") }) },
  ] });
  await router.push("/elsewhere");
  await router.isReady();
  wrapper = mount(defineComponent({ render: () => h(RouterView) }), { global: { plugins: [router] } });
  await navigate("/training");
});
afterEach(() => {
  wrapper.unmount();
  router.options.history.destroy();
  api.get.mockReset();
  clearPrivateNavigationState();
});

describe("training private history context", () => {
  it("restores both successful queries, session and pages without drafts or form state", async () => {
    await query();
    workspace.ribbonScroll.value!.scrollLeft = 460;
    workspace.ribbonScroll.value!.scrollTop = 125;
    workspace.ribbonScroll.value!.dispatchEvent(new Event("scroll"));
    workspace.participantScroll.value!.scrollTop = 350;
    workspace.participantScroll.value!.scrollLeft = 90;
    workspace.participantScroll.value!.dispatchEvent(new Event("scroll"));
    workspace.filters.keyword = "场次草稿";
    workspace.participantKeyword.value = "名单草稿";
    await navigate("/elsewhere");
    await back();
    expect(workspace.filters.keyword).toBe("场次条件");
    expect(workspace.participantKeyword.value).toBe("名单条件");
    expect(workspace.sessionState.page).toBe(2);
    expect(workspace.selected.value?.id).toBe(22);
    expect(workspace.participantState.page).toBe(2);
    expect(workspace.ribbonScroll.value!.scrollLeft).toBe(460);
    expect(workspace.ribbonScroll.value!.scrollTop).toBe(125);
    expect(workspace.participantScroll.value!.scrollTop).toBe(350);
    expect(workspace.participantScroll.value!.scrollLeft).toBe(90);
    expect(lastParticipantQuery().searchParams.get("keyword")).toBe("名单条件");
    expect(workspace.sessionOpen.value || workspace.participantOpen.value || workspace.importOpen.value).toBe(false);
    expect(location.href + JSON.stringify(window.history.state) + JSON.stringify(localStorage) + JSON.stringify(sessionStorage)).not.toMatch(/场次条件|名单条件|场次草稿|名单草稿/);
  });

  it("retries the complete history context when the session directory fails", async () => {
    await query();
    workspace.participantScroll.value!.scrollTop = 350;
    await navigate("/elsewhere");
    api.get.mockRejectedValueOnce(new Error("场次返回失败"));
    await back();
    expect(workspace.sessionState.error).toBe("场次返回失败");
    workspace.filters.keyword = "失败后场次草稿";
    workspace.participantKeyword.value = "失败后名单草稿";
    await workspace.retrySessions();
    await flushPromises();
    expect(workspace.appliedFilters.keyword).toBe("场次条件");
    expect(workspace.selected.value?.id).toBe(22);
    expect(workspace.participantState.page).toBe(2);
    expect(lastParticipantQuery().searchParams.get("keyword")).toBe("名单条件");
    expect(workspace.participantScroll.value!.scrollTop).toBe(350);
  });

  it("does not commit a partially restored context before the participant retry succeeds", async () => {
    await query();
    workspace.participantScroll.value!.scrollTop = 330;
    await navigate("/elsewhere");
    const successful = api.get.getMockImplementation()!;
    let fail = true;
    api.get.mockImplementation((url: string) => {
      if (fail && url.includes("participants/page")) { fail = false; return Promise.reject(new Error("名单返回失败")); }
      return successful(url);
    });
    await back();
    expect(workspace.participantState.error).toBe("名单返回失败");
    workspace.participantKeyword.value = "重试前草稿";
    await workspace.retryParticipants();
    await flushPromises();
    expect(lastParticipantQuery().searchParams.get("keyword")).toBe("名单条件");
    expect(lastParticipantQuery().searchParams.get("page")).toBe("2");
    expect(workspace.participantScroll.value!.scrollTop).toBe(330);
    await navigate("/elsewhere");
    await back();
    expect(workspace.participantKeyword.value).toBe("名单条件");
  });

  it("keeps failed queries out of the successful visit and starts a fresh navigation without private keywords", async () => {
    await query();
    api.get.mockRejectedValueOnce(new Error("查询失败"));
    workspace.filters.keyword = "失败条件";
    await workspace.applyFilters();
    await navigate("/elsewhere");
    await back();
    expect(workspace.filters.keyword).toBe("场次条件");
    const url = router.currentRoute.value.fullPath;
    await navigate("/elsewhere");
    await navigate(url);
    expect(workspace.filters.keyword).toBe("");
    expect(workspace.participantKeyword.value).toBe("");
  });

  it("clears both incoming keywords before a failed initial request, then retries that snapshot", async () => {
    await navigate("/elsewhere");
    api.get.mockRejectedValueOnce(new Error("初次失败"));
    await navigate("/training?keyword=入口场次&participantKeyword=入口名单&sessionPage=2&sessionId=22&participantPage=2");
    expect(router.currentRoute.value.query).not.toHaveProperty("keyword");
    expect(router.currentRoute.value.query).not.toHaveProperty("participantKeyword");
    workspace.filters.keyword = "其他草稿";
    workspace.participantKeyword.value = "其他名单";
    await workspace.retrySessions();
    await flushPromises();
    expect(workspace.appliedFilters.keyword).toBe("入口场次");
    expect(lastParticipantQuery().searchParams.get("keyword")).toBe("入口名单");
    expect(workspace.participantState.page).toBe(2);
  });

  it("does not let a failed participant search overwrite the previous successful keyword", async () => {
    await query();
    api.get.mockRejectedValueOnce(new Error("名单查询失败"));
    workspace.participantKeyword.value = "失败名单条件";
    await workspace.searchParticipants();
    await navigate("/elsewhere");
    await back();
    expect(workspace.participantKeyword.value).toBe("名单条件");
    expect(workspace.participantState.page).toBe(2);
  });

  it("invalidates previous scopes and late scroll writes after session reset", async () => {
    await query();
    clearPrivateNavigationState();
    workspace.participantScroll.value!.scrollTop = 230;
    await navigate("/elsewhere");
    await back();
    expect(workspace.filters.keyword).toBe("");
    expect(workspace.participantKeyword.value).toBe("");
    expect(workspace.participantScroll.value!.scrollTop).toBe(0);
  });

  it("falls back safely when the saved session has been removed", async () => {
    await query();
    workspace.participantScroll.value!.scrollTop = 230;
    await navigate("/elsewhere");
    const successful = api.get.getMockImplementation()!;
    api.get.mockImplementation(async (url: string) => {
      const result = await successful(url);
      return url.includes("participants/page") ? result : { ...result, items: result.items.filter((item: { id: number }) => item.id !== 22) };
    });
    await back();
    expect(workspace.selected.value?.id).toBe(21);
    expect(workspace.participantScroll.value!.scrollTop).toBe(0);
    expect(workspace.restoringHistory.value).toBe(false);
  });
});

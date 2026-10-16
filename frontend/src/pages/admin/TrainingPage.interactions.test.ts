// @vitest-environment jsdom
import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import TrainingPage from "./TrainingPage.vue";
import { downloadBlob } from "../../shared/api";

const apiGet = vi.fn();
const apiPost = vi.fn();

vi.mock("../../shared/api", () => ({
  api: (...args: unknown[]) => apiGet(...args),
  get: (...args: unknown[]) => apiGet(...args),
  post: (...args: unknown[]) => apiPost(...args),
  put: vi.fn(),
  del: vi.fn(),
  downloadBlob: vi.fn(),
}));

vi.mock("vue-router", () => ({
  RouterLink: { template: "<a><slot /></a>" },
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ replace: vi.fn() }),
  onBeforeRouteLeave: vi.fn(),
}));

afterEach(() => {
  apiGet.mockReset();
  apiPost.mockReset();
  vi.mocked(downloadBlob).mockClear();
  document.body.innerHTML = "";
});

describe("TrainingPage interactions", () => {
  it("defaults to January 1 of the current year while retaining the current month end", async () => {
    apiGet.mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 8, hasMore: false });
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, "0");
    const lastDay = new Date(year, now.getMonth() + 1, 0).getDate();
    const wrapper = mount(TrainingPage);
    await flushPromises();
    expect((wrapper.get('input[name="trainingFrom"]').element as HTMLInputElement).value).toBe(`${year}-01-01`);
    expect((wrapper.get('input[name="trainingTo"]').element as HTMLInputElement).value).toBe(`${year}-${month}-${lastDay}`);
    const request = String(apiGet.mock.calls.find(([url]) => String(url).startsWith("/api/trainings/page?"))?.[0]);
    expect(new URL(request, "http://localhost").searchParams.get("from")).toBe(`${year}-01-01`);
    wrapper.unmount();
  });

  it("shows the successful exact session range despite an invalid draft without export actions", async () => {
    apiGet.mockImplementation((url: string) => Promise.resolve(url.includes("/trainings/export?")
      ? new Blob(["synthetic export"])
      : { items: [], total: 0, page: 1, pageSize: 20, hasMore: false }));
    const wrapper = mount(TrainingPage);
    await flushPromises();
    await wrapper.get('input[name="trainingFrom"]').setValue("2026-08-03");
    await wrapper.get('input[name="trainingTo"]').setValue("2026-08-12");
    await wrapper.get('input[name="trainingKeyword"]').setValue("已查询场次");
    await wrapper.get("form.filter-bar").trigger("submit");
    await flushPromises();
    const context = wrapper.get(".training-query-context");
    expect(context.text()).toContain("2026-08-03 至 2026-08-12");
    expect(context.text()).toContain("已查询场次");
    expect(context.text()).toContain("0 场");
    await wrapper.get('input[name="trainingFrom"]').setValue("2026-08-20");
    await wrapper.get('input[name="trainingKeyword"]').setValue("未提交关键词");
    expect(wrapper.find(".training-page-tools .button.secondary").exists()).toBe(false);
    expect(context.text()).toContain("2026-08-03 至 2026-08-12");
    expect(context.text()).not.toContain("未提交关键词");
    expect(context.get('[data-query-state="dirty"]').text()).toContain("查询后生效");
    await flushPromises();
    expect(apiGet.mock.calls.some(([url]) => String(url).includes("/trainings/export?"))).toBe(false);
    wrapper.unmount();
  });

  it("keeps session context independent of the participant query without an export action", async () => {
    let failParticipants!: (error: Error) => void;
    const session = { id: 1, title: "合成培训", trainingDate: "2026-09-29", status: "ACTIVE", participantCount: 0, totalDurationHours: 0 };
    apiGet.mockImplementation((url: string) => url.includes("participants/page")
      ? new Promise((_resolve, reject) => { failParticipants = reject; })
      : Promise.resolve({ items: [session], total: 1, page: 1, pageSize: 20, hasMore: false }));
    const wrapper = mount(TrainingPage);
    await flushPromises();
    expect(wrapper.find(".training-page-tools .button.secondary").exists()).toBe(false);
    expect(wrapper.get(".training-query-context").text()).toContain("1 场");
    failParticipants(new Error("合成名单失败"));
    await flushPromises();
    expect(wrapper.get(".training-query-context").find('[data-query-state="failed"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it("keeps the last successful context after a failed session query and prioritizes failure over drafts", async () => {
    let fail = false;
    apiGet.mockImplementation(() => fail ? Promise.reject(new Error("合成场次失败"))
      : Promise.resolve({ items: [], total: 0, page: 1, pageSize: 20, hasMore: false }));
    const wrapper = mount(TrainingPage);
    await flushPromises();
    const original = wrapper.get(".training-applied-filters").text();
    fail = true;
    await wrapper.get('input[name="trainingKeyword"]').setValue("失败条件");
    await wrapper.get("form.filter-bar").trigger("submit");
    await flushPromises();
    await wrapper.get('input[name="trainingKeyword"]').setValue("后续草稿");
    expect(wrapper.get(".training-applied-filters").text()).toBe(original);
    expect(wrapper.get('[data-query-state="failed"]').text()).toContain("查询未成功");
    expect(wrapper.find('[data-query-state="dirty"]').exists()).toBe(false);
    expect(wrapper.find(".training-page-tools .button.secondary").exists()).toBe(false);
    expect(wrapper.find(".training-page-tools .button.secondary").exists()).toBe(false);
    wrapper.unmount();
  });

  it("keeps removed export actions absent during a query and after its failure", async () => {
    let rejectQuery!: (error: Error) => void;
    apiGet.mockImplementation(() => new Promise((_resolve, reject) => { rejectQuery = reject; }));
    const wrapper = mount(TrainingPage);
    await flushPromises();
    expect(wrapper.find(".training-page-tools .button.secondary").exists()).toBe(false);
    expect(wrapper.find(".training-applied-filters").exists()).toBe(false);
    rejectQuery(new Error("合成查询失败"));
    await flushPromises();
    expect(wrapper.find(".training-applied-filters").exists()).toBe(false);
    expect(apiGet.mock.calls.some(([url]) => String(url).includes("/trainings/export?"))).toBe(false);
    wrapper.unmount();
  });

  function mockTraining() {
    const sessions = [1, 2].map(id => ({ id, title: `培训${id}`, trainingDate: "2026-09-29", startTime: "14:00", endTime: "16:00", speaker: "主讲人", status: "ACTIVE", participantCount: 0, totalDurationHours: 0 }));
    apiGet.mockImplementation((url: string) => Promise.resolve({ items: url.includes("participants/page") ? [] : sessions, total: url.includes("participants/page") ? 0 : 2, page: 1, pageSize: 20, hasMore: false }));
  }

  async function fillParticipant(name: string) {
    for (const [field, value] of [["student-no", "20260001"], ["name", name], ["duration", "1.5"], ["remark", "仅当前成员的备注"]]) {
      const input = document.body.querySelector<HTMLInputElement>(`[name="participant-${field}"]`)!;
      input.value = value!;
      input.dispatchEvent(new Event("input", { bubbles: true }));
    }
    await flushPromises();
  }

  it("continues with a clean identity and retained duration only after a successful save", async () => {
    mockTraining();
    apiPost.mockResolvedValue({ id: 9, name: "第一位" });
    const wrapper = mount(TrainingPage, { attachTo: document.body });
    await flushPromises();
    await wrapper.get('[data-action="add-participant"]').trigger("click");
    const editor = wrapper.findComponent({ name: "TrainingParticipantEditorDialog" });
    expect(editor.props("form").name).toBe("");
    expect(editor.props("form").durationHours).toBe(2);
    await fillParticipant("第一位");
    editor.vm.$emit("save", true);
    await flushPromises();
    expect(apiPost).toHaveBeenCalledWith("/api/trainings/1/participants", expect.objectContaining({ name: "第一位", durationHours: 1.5, remark: "仅当前成员的备注" }));
    expect(editor.props("open")).toBe(true);
    expect(editor.props("form")).toEqual({ id: null, studentNo: "", name: "", durationHours: 1.5, remark: "" });
    expect(document.activeElement?.getAttribute("name")).toBe("participant-student-no");
    expect(document.body.textContent).toContain("上一条已保存：第一位");
    editor.vm.$emit("close");
    await flushPromises();
    expect(editor.props("open")).toBe(false);
    expect(document.body.textContent).not.toContain("放弃未保存修改");
    wrapper.unmount();
  });

  it("preserves input on failure and prevents duplicate submissions while pending", async () => {
    mockTraining();
    let reject!: (cause: Error) => void;
    apiPost.mockReturnValue(new Promise((_, fail) => { reject = fail; }));
    const wrapper = mount(TrainingPage, { attachTo: document.body });
    await flushPromises();
    await wrapper.get('[data-action="add-participant"]').trigger("click");
    await fillParticipant("待保存成员");
    const editor = wrapper.findComponent({ name: "TrainingParticipantEditorDialog" });
    editor.vm.$emit("save", true);
    editor.vm.$emit("save", true);
    editor.vm.$emit("close");
    await flushPromises();
    expect(apiPost).toHaveBeenCalledTimes(1);
    expect(editor.props("open")).toBe(true);
    expect(document.body.querySelector<HTMLInputElement>('[name="participant-name"]')?.disabled).toBe(true);
    reject(new Error("保存失败"));
    await flushPromises();
    expect(editor.props("form").name).toBe("待保存成员");
    expect(editor.props("form").remark).toBe("仅当前成员的备注");
    expect(editor.props("savedMessage")).toBe("");
    wrapper.unmount();
  });

  it("submits to the session captured when the editor opened", async () => {
    mockTraining();
    apiPost.mockResolvedValue({ id: 9, name: "原场次成员" });
    const wrapper = mount(TrainingPage, { attachTo: document.body });
    await flushPromises();
    await wrapper.get('[data-action="add-participant"]').trigger("click");
    await fillParticipant("原场次成员");
    const ribbon = wrapper.findComponent({ name: "TrainingMonthRibbon" });
    ribbon.vm.$emit("select", ribbon.props("items")[1]);
    await flushPromises();
    expect(ribbon.props("selectedId")).toBe(2);
    const editor = wrapper.findComponent({ name: "TrainingParticipantEditorDialog" });
    expect(editor.props("session").id).toBe(1);
    editor.vm.$emit("save", false);
    await flushPromises();
    expect(apiPost.mock.calls[0]?.[0]).toBe("/api/trainings/1/participants");
    expect(editor.props("open")).toBe(false);
    wrapper.unmount();
  });

  it("prevents duplicate saves and confirms closing a dirty editor", async () => {
    apiGet.mockImplementation((url: string) =>
      Promise.resolve(url.includes("participants/page")
        ? { items: [], total: 0, page: 1, pageSize: 20, hasMore: false }
        : { items: [], total: 0, page: 1, pageSize: 20, hasMore: false }),
    );
    let resolveSave!: (value: unknown) => void;
    apiPost.mockReturnValue(new Promise((resolve) => { resolveSave = resolve; }));
    const wrapper = mount(TrainingPage, { attachTo: document.body });
    await flushPromises();

    await wrapper.get(".training-page-tools .button.primary").trigger("click");
    const title = document.body.querySelector<HTMLInputElement>('[name="training-title"]')!;
    title.value = "离线维修基础培训";
    title.dispatchEvent(new Event("input", { bubbles: true }));
    const save = document.body.querySelector<HTMLButtonElement>('[form="training-session-editor"]')!;
    save.click();
    save.click();
    await wrapper.vm.$nextTick();
    expect(apiPost).toHaveBeenCalledTimes(1);
    expect(save.disabled).toBe(true);

    resolveSave({ id: 8, title: "离线维修基础培训", trainingDate: new Date().toISOString().slice(0, 10) });
    await flushPromises();

    await wrapper.get(".training-page-tools .button.primary").trigger("click");
    const dirtyTitle = document.body.querySelector<HTMLInputElement>('[name="training-title"]')!;
    dirtyTitle.value = "尚未保存";
    dirtyTitle.dispatchEvent(new Event("input", { bubbles: true }));
    const cancel = Array.from(document.body.querySelectorAll<HTMLButtonElement>(".mw-modal-foot .button.secondary"))
      .find((button) => button.textContent?.includes("取消"))!;
    cancel.click();
    await wrapper.vm.$nextTick();
    expect(document.body.textContent).toContain("放弃未保存修改");
    expect(document.body.querySelector('[name="training-title"]')).not.toBeNull();
    wrapper.unmount();
  });

  it("rejects an inverted date draft without replacing the successful query range", async () => {
    apiGet.mockImplementation((url: string) =>
      Promise.resolve(url.includes("participants/page")
        ? { items: [], total: 0, page: 1, pageSize: 20, hasMore: false }
        : { items: [], total: 0, page: 1, pageSize: 20, hasMore: false }),
    );
    const wrapper = mount(TrainingPage);
    await flushPromises();
    const successfulRange = wrapper.get(".training-applied-filters").text();
    apiGet.mockClear();

    const dates = wrapper.findAll('input[type="date"]');
    await dates[0].setValue("2026-08-22");
    await dates[1].setValue("2026-08-21");
    await wrapper.get("form.filter-bar").trigger("submit");

    expect(wrapper.get('[role="alert"]').text()).toContain(
      "开始日期不能晚于结束日期",
    );
    expect(wrapper.find(".training-page-tools .button.secondary").exists()).toBe(false);
    expect(apiGet).not.toHaveBeenCalled();
    expect(wrapper.get(".training-applied-filters").text()).toBe(successfulRange);

    wrapper.unmount();
  });
});

vi.mock("../../shared/composables/useServiceHealth", () => ({ useServiceHealth: () => ({ online: true }) }));

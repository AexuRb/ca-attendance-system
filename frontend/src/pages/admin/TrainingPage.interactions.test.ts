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
  it("shows the successful exact session range and exports it despite an invalid draft", async () => {
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
    const button = wrapper.get(".mw-tools .button.secondary");
    expect(button.attributes("disabled")).toBeUndefined();
    expect(context.text()).toContain("2026-08-03 至 2026-08-12");
    expect(context.text()).not.toContain("未提交关键词");
    expect(context.get('[data-query-state="dirty"]').text()).toContain("查询后生效");
    await button.trigger("click");
    await flushPromises();
    const exportUrl = apiGet.mock.calls.find(([url]) => String(url).includes("/trainings/export?"))![0];
    const params = new URL(String(exportUrl), "http://localhost").searchParams;
    expect(Object.fromEntries(params)).toEqual({ from: "2026-08-03", to: "2026-08-12", keyword: "已查询场次" });
    wrapper.unmount();
  });

  it("allows session statistics export while the independent participant query is pending or failed", async () => {
    let failParticipants!: (error: Error) => void;
    const session = { id: 1, title: "合成培训", trainingDate: "2026-09-29", status: "ACTIVE", participantCount: 0, totalDurationHours: 0 };
    apiGet.mockImplementation((url: string) => url.includes("participants/page")
      ? new Promise((_resolve, reject) => { failParticipants = reject; })
      : Promise.resolve({ items: [session], total: 1, page: 1, pageSize: 20, hasMore: false }));
    const wrapper = mount(TrainingPage);
    await flushPromises();
    const button = wrapper.get(".mw-tools .button.secondary");
    expect(button.attributes("disabled")).toBeUndefined();
    expect(button.attributes("title")).toContain("不受分页或参与名单搜索影响");
    expect(wrapper.get(".training-query-context").text()).toContain("1 场");
    failParticipants(new Error("合成名单失败"));
    await flushPromises();
    expect(button.attributes("disabled")).toBeUndefined();
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
    expect(wrapper.get(".mw-tools .button.secondary").attributes("disabled")).toBeDefined();
    expect(wrapper.get(".mw-tools .button.secondary").attributes("title")).toContain("重试成功后导出");
    wrapper.unmount();
  });

  it("keeps the export filename and request on the same snapshot while querying another range", async () => {
    let finishExport!: (blob: Blob) => void;
    apiGet.mockImplementation((url: string) => url.includes("/trainings/export?")
      ? new Promise<Blob>(resolve => { finishExport = resolve; })
      : Promise.resolve({ items: [], total: 0, page: 1, pageSize: 20, hasMore: false }));
    const wrapper = mount(TrainingPage);
    await flushPromises();
    const dates = wrapper.findAll('input[type="date"]');
    await dates[0].setValue("2026-08-01");
    await dates[1].setValue("2026-08-31");
    await wrapper.get("form.filter-bar").trigger("submit");
    await flushPromises();
    await wrapper.get('input[name="trainingKeyword"]').setValue("未提交关键词");
    await wrapper.get(".mw-tools .button.secondary").trigger("click");
    const exportUrl = apiGet.mock.calls.find(([url]) => String(url).includes("/trainings/export?"))![0];
    const params = new URL(String(exportUrl), "http://localhost").searchParams;
    expect(params.get("from")).toBe("2026-08-01");
    expect(params.get("keyword")).toBe("");
    expect(params.has("page")).toBe(false);
    await dates[0].setValue("2026-09-01");
    await dates[1].setValue("2026-09-30");
    await wrapper.get("form.filter-bar").trigger("submit");
    await flushPromises();
    const blob = new Blob(["synthetic export"]);
    finishExport(blob);
    await flushPromises();
    expect(downloadBlob).toHaveBeenCalledWith(blob, "培训统计_2026-08-01_2026-08-31.xlsx");
    wrapper.unmount();
  });

  it("disables summary export during a query and after its failure", async () => {
    let rejectQuery!: (error: Error) => void;
    apiGet.mockImplementation(() => new Promise((_resolve, reject) => { rejectQuery = reject; }));
    const wrapper = mount(TrainingPage);
    await flushPromises();
    const button = wrapper.get(".mw-tools .button.secondary");
    expect(button.attributes("disabled")).toBeDefined();
    expect(wrapper.find(".training-applied-filters").exists()).toBe(false);
    expect(button.attributes("title")).toContain("正在查询");
    rejectQuery(new Error("合成查询失败"));
    await flushPromises();
    expect(button.attributes("disabled")).toBeDefined();
    expect(wrapper.find(".training-applied-filters").exists()).toBe(false);
    await button.trigger("click");
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

    await wrapper.get(".mw-tools .button.primary").trigger("click");
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

    await wrapper.get(".mw-tools .button.primary").trigger("click");
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

  it("rejects an inverted date draft without replacing the successful export range", async () => {
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
    expect(wrapper.get(".mw-tools .button.secondary").attributes("disabled"))
      .toBeUndefined();
    expect(apiGet).not.toHaveBeenCalled();
    expect(wrapper.get(".training-applied-filters").text()).toBe(successfulRange);

    await wrapper.get(".mw-tools .button.secondary").trigger("click");
    expect(apiGet).toHaveBeenCalledTimes(1);
    const params = new URL(String(apiGet.mock.calls[0][0]), "http://localhost").searchParams;
    expect(params.get("from")).not.toBe("2026-08-22");
    expect(params.get("to")).not.toBe("2026-08-21");
    wrapper.unmount();
  });
});

vi.mock("../../shared/composables/useServiceHealth", () => ({ useServiceHealth: () => ({ online: true }) }));

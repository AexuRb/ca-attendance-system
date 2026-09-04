// @vitest-environment jsdom
import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import DataPage from "./DataPage.vue";

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  del: vi.fn(),
  api: vi.fn(),
  download: vi.fn(),
  notify: vi.fn(),
  replace: vi.fn(),
  push: vi.fn(),
  expire: vi.fn(),
  routeQuery: {} as Record<string, string>,
}));

vi.mock("../../shared/api", () => ({
  get: (...args: unknown[]) => mocks.get(...args),
  post: (...args: unknown[]) => mocks.post(...args),
  del: (...args: unknown[]) => mocks.del(...args),
  api: (...args: unknown[]) => mocks.api(...args),
  downloadBlob: (...args: unknown[]) => mocks.download(...args),
}));
vi.mock("../../shared/composables/useToast", () => ({ notify: mocks.notify }));
vi.mock("../../app/session", () => ({
  useSession: () => ({
    user: { value: { role: "ADMIN" } },
    expireSession: mocks.expire,
  }),
}));
vi.mock("vue-router", () => ({
  useRoute: () => ({ query: mocks.routeQuery }),
  useRouter: () => ({ replace: mocks.replace, push: mocks.push }),
}));

const exportOptions = {
  sources: [{
    id: "members",
    label: "成员名册",
    fields: [{ id: "name", label: "姓名", defaultSelected: true }],
    filters: [],
  }],
};

const datedExportOptions = {
  sources: [{
    id: "attendance",
    label: "值班记录",
    fields: [{ id: "name", label: "姓名", defaultSelected: true }],
    filters: [
      { id: "from", label: "开始日期", type: "date", defaultValue: "" },
      { id: "to", label: "结束日期", type: "date", defaultValue: "" },
    ],
  }],
};

function installSuccessfulLoads() {
  mocks.get.mockImplementation((url: string) => {
    if (url === "/api/exports/options") return Promise.resolve(exportOptions);
    if (url === "/api/maintenance/summary") return Promise.resolve({ datasets: [] });
    if (url === "/api/maintenance/backups") {
      return Promise.resolve([{ filename: "backup.zip", size: 100, createdAt: "2026-08-21T12:00:00" }]);
    }
    if (url === "/api/repairs/recycle-bin") return Promise.resolve([]);
    return Promise.resolve([]);
  });
}

afterEach(() => {
  Object.values(mocks).forEach((mock) => {
    if (typeof mock === "function" && "mockReset" in mock) mock.mockReset();
  });
  Object.keys(mocks.routeQuery).forEach((key) => delete mocks.routeQuery[key]);
  document.body.innerHTML = "";
});

describe("DataPage request states", () => {
  it("shows an options load error and supports retry", async () => {
    mocks.get
      .mockRejectedValueOnce(new Error("导出配置不可用"))
      .mockResolvedValueOnce(exportOptions);
    const wrapper = mount(DataPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain("导出配置不可用");
    await wrapper.get(".inline-alert button").trigger("click");
    await flushPromises();
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect(wrapper.text()).toContain("成员名册");
    wrapper.unmount();
  });

  it("keeps the backup delete dialog open when deletion fails", async () => {
    installSuccessfulLoads();
    mocks.del.mockRejectedValue(new Error("备份删除失败"));
    const wrapper = mount(DataPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();
    await wrapper.findAll(".page-tabs button")[1].trigger("click");
    await flushPromises();
    await wrapper.get('button[title="删除备份"]').trigger("click");
    await wrapper.get(".modal-footer .button.danger").trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("永久删除备份 backup.zip");
    expect(mocks.notify).toHaveBeenCalledWith("备份删除失败", "danger");
    wrapper.unmount();
  });

  it("rejects an oversized restore archive before opening confirmation", async () => {
    installSuccessfulLoads();
    const wrapper = mount(DataPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();
    await wrapper.findAll(".page-tabs button")[1].trigger("click");
    await flushPromises();
    const input = wrapper.get('input[type="file"]');
    const file = new File(["x"], "large-backup.zip");
    Object.defineProperty(file, "size", { value: 128 * 1024 * 1024 + 1 });
    Object.defineProperty(input.element, "files", { value: [file], configurable: true });
    await input.trigger("change");

    expect(wrapper.get('[role="alert"]').text()).toContain("128 MB");
    expect(wrapper.text()).not.toContain("恢复本机备份");
    wrapper.unmount();
  });

  it("generates a preview from the new export configuration drawer", async () => {
    installSuccessfulLoads();
    mocks.post.mockResolvedValue({
      source: "members",
      sourceLabel: "成员名册",
      fields: exportOptions.sources[0].fields,
      filters: {},
      totalRows: 1,
      truncated: false,
      rows: [{ name: "测试成员" }],
    });
    const wrapper = mount(DataPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();

    await wrapper.get(".data-drawer-actions .button.primary").trigger("click");
    await flushPromises();

    expect(mocks.post).toHaveBeenCalledWith(
      "/api/exports/preview",
      expect.objectContaining({ source: "members", fields: ["name"] }),
    );
    expect(wrapper.text()).toContain("测试成员");
    expect(wrapper.get(".data-export-footer .button.primary").attributes("disabled")).toBeUndefined();
    wrapper.unmount();
  });

  it("writes the selected workspace to the URL without dropping other query parameters", async () => {
    mocks.routeQuery.intent = "create-backup";
    installSuccessfulLoads();
    const wrapper = mount(DataPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();

    await wrapper.findAll(".page-tabs button")[1].trigger("click");
    await flushPromises();

    expect(mocks.push).toHaveBeenCalledWith({
      query: { intent: "create-backup", tab: "backups" },
    });
    wrapper.unmount();
  });

  it("exposes complete tab semantics and supports arrow-key navigation", async () => {
    installSuccessfulLoads();
    const wrapper = mount(DataPage, {
      attachTo: document.body,
      global: { stubs: { Teleport: true } },
    });
    await flushPromises();

    const exportTab = wrapper.get("#data-tab-export");
    expect(exportTab.attributes("aria-controls")).toBe("data-panel-export");
    expect(exportTab.attributes("tabindex")).toBe("0");
    expect(wrapper.get("#data-panel-export").attributes("aria-labelledby"))
      .toBe("data-tab-export");

    (exportTab.element as HTMLElement).focus();
    await exportTab.trigger("keydown", { key: "ArrowRight" });
    await flushPromises();

    const backupTab = wrapper.get("#data-tab-backups");
    expect(backupTab.attributes("aria-selected")).toBe("true");
    expect(backupTab.attributes("tabindex")).toBe("0");
    expect(document.activeElement).toBe(backupTab.element as HTMLElement);
    expect(wrapper.get("#data-panel-backups").attributes("role")).toBe("tabpanel");
    wrapper.unmount();
  });

  it("shows a compact pending state before an export preview is generated", async () => {
    installSuccessfulLoads();
    const wrapper = mount(DataPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();

    expect(wrapper.get(".data-export-result").text()).toContain("待预览");
    expect(wrapper.get(".data-preview-pending").text()).toContain("生成预览");
    wrapper.unmount();
  });

  it("provides named backup controls and accurate recycle-bin wording", async () => {
    installSuccessfulLoads();
    const wrapper = mount(DataPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();

    await wrapper.findAll(".page-tabs button")[1].trigger("click");
    await flushPromises();
    expect(wrapper.get('input[name="backup-search"]').attributes("aria-label")).toBe("搜索备份");
    const backupDetailButton = wrapper.get('button[aria-label^="查看备份详情"]');
    expect(backupDetailButton.text()).toContain("backup.zip");
    expect(backupDetailButton.attributes("aria-controls")).toBe("data-backup-details");
    expect(backupDetailButton.attributes("aria-expanded")).toBe("false");
    expect(wrapper.get('button[aria-label^="下载备份"]').attributes("title")).toBe("下载备份");

    await backupDetailButton.trigger("click");
    expect(backupDetailButton.attributes("aria-expanded")).toBe("true");
    expect(wrapper.get("#data-backup-details").attributes("role")).toBe("complementary");

    await wrapper.findAll(".page-tabs button")[2].trigger("click");
    await flushPromises();
    expect(wrapper.text()).toContain("回收站内近 30 天分布");
    expect(wrapper.get(".data-trend-chart").attributes("aria-hidden")).toBe("true");
    expect(wrapper.get(".data-trend-summary").text()).toContain("项");
    expect(wrapper.get('input[name="repair-recycle-search"]').attributes("aria-label"))
      .toBe("搜索回收站维修事务");
    wrapper.unmount();
  });

  it("uses expanded-region semantics for export configuration", async () => {
    installSuccessfulLoads();
    const wrapper = mount(DataPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();

    const adjustButton = wrapper.get(".data-table-toolbar .button.secondary");
    expect(adjustButton.attributes("aria-controls")).toBe("data-export-config");
    expect(adjustButton.attributes("aria-haspopup")).toBeUndefined();
    await adjustButton.trigger("click");
    expect(adjustButton.attributes("aria-expanded")).toBe("true");
    expect(wrapper.get("#data-export-config").attributes("id")).toBe("data-export-config");
    wrapper.unmount();
  });

  it("blocks export preview when the date range is inverted", async () => {
    mocks.get.mockImplementation((url: string) => {
      if (url === "/api/exports/options") return Promise.resolve(datedExportOptions);
      return Promise.resolve([]);
    });
    const wrapper = mount(DataPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();
    const adjustButton = wrapper
      .findAll("button")
      .find((button) => button.text().includes("调整条件"));
    expect(adjustButton).toBeDefined();
    await adjustButton!.trigger("click");

    const inputs = wrapper.findAll('input[type="date"]');
    await inputs[0].setValue("2026-08-21");
    await inputs[1].setValue("2026-08-20");

    expect(wrapper.get('[role="alert"]').text()).toContain("开始日期不能晚于结束日期");
    expect(wrapper.get(".data-drawer-actions .button.primary").attributes("disabled")).toBeDefined();
    expect(mocks.post).not.toHaveBeenCalled();
    wrapper.unmount();
  });
});

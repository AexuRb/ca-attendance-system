// @vitest-environment jsdom
import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import DataPage from "./DataPage.vue";
import { defineComponent } from "vue";
import { useDataCenterWorkspace } from "../../features/maintenance/useDataCenterWorkspace";
import type { RecycledRepairCase } from "../../features/maintenance/dataCenterTypes";

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
  role: "ADMIN",
  recycleItems: [] as RecycledRepairCase[],
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
vi.mock("../../shared/composables/useServiceHealth", () => ({
  useServiceHealth: () => ({ online: { value: true } }),
}));
vi.mock("../../app/session", () => ({
  useSession: () => ({
    state: { access: { kioskAvailable: true } },
    logout: vi.fn(),
    user: { value: { role: mocks.role } },
    expireSession: mocks.expire,
  }),
}));
vi.mock("vue-router", () => ({
  onBeforeRouteLeave: vi.fn(),
  onBeforeRouteUpdate: vi.fn(),
  RouterLink: { props: ["to"], template: '<a :data-route="JSON.stringify(to)"><slot /></a>' },
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

async function openCustomSource(wrapper:ReturnType<typeof mount>){
  if(wrapper.find('button[data-source=custom]').exists()){await wrapper.get('button[data-source=custom]').trigger('click');return;}
  await wrapper.get('.data-type-card button[data-source]').trigger('click');
  if(wrapper.find('button[data-source=custom]').exists())await wrapper.get('button[data-source=custom]').trigger('click');
}
function installSuccessfulLoads() {
  mocks.get.mockImplementation((url: string) => {
    if (url === "/api/exports/options") return Promise.resolve(exportOptions);
    if (url === "/api/maintenance/summary") return Promise.resolve({ datasets: [] });
    if (url === "/api/maintenance/backups") {
      return Promise.resolve([{ filename: "backup.zip", size: 100, createdAt: "2026-08-21T12:00:00" }]);
    }
    if (url === "/api/repairs/recycle-bin") return Promise.resolve(mocks.recycleItems);
    return Promise.resolve([]);
  });
}

// Export behavior tests explicitly open the export subpage.
beforeEach(() => { mocks.routeQuery.tab = "export"; });

afterEach(() => {
  Object.values(mocks).forEach((mock) => {
    if (typeof mock === "function" && "mockReset" in mock) mock.mockReset();
  });
  Object.keys(mocks.routeQuery).forEach((key) => delete mocks.routeQuery[key]);
  mocks.recycleItems = [];
  mocks.role = "ADMIN";
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
    mocks.routeQuery.tab = "backups";
    installSuccessfulLoads();
    mocks.del.mockRejectedValue(new Error("备份删除失败"));
    const wrapper = mount(DataPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();
    await wrapper.get('button[title="删除备份"]').trigger("click");
    await wrapper.get('[role="dialog"] footer .button.danger').trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("永久删除备份 backup.zip");
    expect(mocks.notify).toHaveBeenCalledWith("备份删除失败", "danger");
    wrapper.unmount();
  });

  it("rejects an oversized restore archive before opening confirmation", async () => {
    mocks.routeQuery.tab = "backups";
    installSuccessfulLoads();
    const wrapper = mount(DataPage, { global: { stubs: { Teleport: true } } });
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

  it("opens one export type and generates its preview from inline configuration", async () => {
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

    await openCustomSource(wrapper);
    await wrapper.get("button[data-export-action=preview]").trigger("click");
    await flushPromises();

    expect(mocks.post).toHaveBeenCalledWith(
      "/api/exports/preview",
      expect.objectContaining({ source: "members", fields: ["name"] }),
    );
    expect(wrapper.text()).toContain("测试成员");
    expect(wrapper.get("button[data-export-action=download]").attributes("disabled")).toBeUndefined();
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it("writes the selected workspace to the URL without dropping other query parameters", async () => {
    mocks.routeQuery.intent = "create-backup";
    mocks.routeQuery.tab = "recycle";
    installSuccessfulLoads();
    const harness = defineComponent({
      setup: () => useDataCenterWorkspace(),
      template: `<button @click="selectTab('backups')">切换到备份</button>`,
    });
    const wrapper = mount(harness);
    await flushPromises();

    await wrapper.get("button").trigger("click");
    await flushPromises();

    expect(mocks.push).toHaveBeenCalledWith({
      query: { intent: "create-backup", tab: "backups" },
    });
    wrapper.unmount();
  });

  it("locks type and filter changes during preview generation and prevents duplicate requests", async () => {
    installSuccessfulLoads();
    let finish!: (value: unknown) => void;
    mocks.post.mockReturnValue(new Promise(resolve => { finish = resolve; }));
    const wrapper = mount(DataPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();
    await openCustomSource(wrapper);
    await wrapper.get('button[data-export-action=preview]').trigger('click');
    await wrapper.get('button[data-export-action=preview]').trigger('click');
    expect(mocks.post).toHaveBeenCalledTimes(1);
    expect(wrapper.get('.data-operation-header button').attributes('disabled')).toBeDefined();
    expect(wrapper.get('input[name="export-field-name"]').attributes('disabled')).toBeDefined();
    finish({ source:'members', sourceLabel:'成员名册', fields:exportOptions.sources[0].fields, filters:{}, totalRows:0, truncated:false, rows:[] });
    await flushPromises();
    expect(wrapper.get('.data-operation-header button').attributes('disabled')).toBeUndefined();
    expect(wrapper.text()).toContain('当前条件下没有数据');
    wrapper.unmount();
  });

  it("labels the recycle region and keeps its navigation under repairs", async () => {
    mocks.routeQuery.tab = "recycle";
    installSuccessfulLoads();
    const wrapper = mount(DataPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();
    expect(wrapper.find(".page-tabs").exists()).toBe(false);
    expect(wrapper.get("#data-panel-recycle").attributes("role")).toBe("region");
    expect(wrapper.get("#data-panel-recycle").attributes("aria-labelledby")).toBe("data-recycle-title");
    expect(wrapper.get('.overview-navigation .is-current').text()).toBe("维修事务");
    expect(JSON.parse(wrapper.get('.overview-page-heading a').attributes('data-route')!)).toEqual({ name: "repairs" });
    expect(wrapper.get('.overview-page-heading a').text()).toContain("返回维修事务");
    wrapper.unmount();
  });

  it("keeps inline configuration and the date draft on failure, then previews on retry", async () => {
    mocks.get.mockImplementation((url: string) => Promise.resolve(url === "/api/exports/options" ? datedExportOptions : { datasets: [] }));
    mocks.post.mockRejectedValueOnce(new Error("预览暂不可用"));
    const wrapper = mount(DataPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();
    await openCustomSource(wrapper);
    await wrapper.get('input[name="export-filter-from"]').setValue("2026-10-01");
    await wrapper.get("button[data-export-action=preview]").trigger("click");
    await flushPromises();
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    expect((wrapper.get('input[name="export-filter-from"]').element as HTMLInputElement).value).toBe("2026-10-01");
    expect(wrapper.get('[role="alert"]').text()).toContain("预览暂不可用");
    mocks.post.mockResolvedValueOnce({ source:"attendance", sourceLabel:"值班记录", fields:datedExportOptions.sources[0].fields, filters:{from:"2026-10-01"}, totalRows:1, truncated:false, rows:[{name:"测试成员"}] });
    await wrapper.get("button[data-export-action=preview]").trigger("click");
    await flushPromises();
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    expect(wrapper.get(".data-filter-summary").text()).toContain("开始日期：2026-10-01");
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it("blocks an empty field selection and invalidates the old preview when fields change", async () => {
    installSuccessfulLoads();
    mocks.post.mockResolvedValue({ source:"members", sourceLabel:"成员名册", fields:exportOptions.sources[0].fields, filters:{}, totalRows:1, truncated:false, rows:[{name:"测试成员"}] });
    const wrapper = mount(DataPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();
    await openCustomSource(wrapper);
    await wrapper.get('input[name="export-field-name"]').setValue(false);
    expect(wrapper.get("button[data-export-action=preview]").attributes("disabled")).toBeDefined();
    expect(wrapper.get('[role="alert"]').text()).toContain("至少选择一个");
    expect(mocks.post).not.toHaveBeenCalled();
    await wrapper.get('input[name="export-field-name"]').setValue(true);
    await wrapper.get("button[data-export-action=preview]").trigger("click");
    await flushPromises();
    expect(wrapper.find(".data-preview-table").exists()).toBe(true);
    await wrapper.get('input[name="export-filename"]').setValue("本月成员");
    expect(wrapper.find(".data-preview-table").exists()).toBe(true);

    await wrapper.get('input[name="export-field-name"]').setValue(false);
    expect(wrapper.find(".data-preview-table").exists()).toBe(false);
    expect(wrapper.get("button[data-export-action=download]").attributes("disabled")).toBeDefined();
    wrapper.unmount();
  });

  it("retains the export draft when returning to the catalog and reselecting its type", async () => {
    mocks.get.mockImplementation((url: string) => Promise.resolve(url === "/api/exports/options" ? datedExportOptions : { datasets: [] }));
    const wrapper = mount(DataPage, { global: { stubs: { Teleport: true } } });
      await flushPromises();
      expect(wrapper.find("#data-export-config").exists()).toBe(false);
      await openCustomSource(wrapper);
      await wrapper.get('input[name="export-filter-from"]').setValue("2026-10-01");
      await wrapper.get(".data-operation-header button").trigger("click");
      expect(wrapper.find("#data-export-config").exists()).toBe(false);
      await openCustomSource(wrapper);
      expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
      expect((wrapper.get('input[name="export-filter-from"]').element as HTMLInputElement).value).toBe("2026-10-01");
      wrapper.unmount();
  });

  it("shows a compact pending state before an export preview is generated", async () => {
    installSuccessfulLoads();
    const wrapper = mount(DataPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();

    expect(wrapper.find(".page-tabs").exists()).toBe(false);
    expect(wrapper.get("#data-panel-export").attributes("aria-labelledby")).toBe("data-export-title");
    expect(wrapper.get("#data-export-title").text()).toBe("数据中心");
    expect(wrapper.find(".data-preview-table").exists()).toBe(false);
    await openCustomSource(wrapper);
    expect(wrapper.get(".data-preview-status").text()).toContain("生成预览");
    wrapper.unmount();
  });

  it("provides named backup controls and accurate recycle-bin wording", async () => {
    mocks.routeQuery.tab = "backups";
    installSuccessfulLoads();
    const wrapper = mount(DataPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();

    expect(wrapper.get("h1").text()).toBe("备份与恢复");
    expect(wrapper.find("#data-export-title").exists()).toBe(false);
    expect(wrapper.get('input[name="backup-search"]').attributes("aria-label")).toBe("搜索备份");
    const backupDetailButton = wrapper.get('button[aria-label^="查看备份详情"]');
    expect(backupDetailButton.text()).toContain("backup.zip");
    expect(backupDetailButton.attributes("aria-controls")).toBe("data-backup-details");
    expect(backupDetailButton.attributes("aria-expanded")).toBe("false");
    expect(wrapper.get('button[aria-label^="下载备份"]').attributes("title")).toBe("下载备份");

    await backupDetailButton.trigger("click");
    expect(backupDetailButton.attributes("aria-expanded")).toBe("true");
    expect(wrapper.get("#data-backup-details").attributes("role")).toBe("complementary");

    wrapper.unmount();
    mocks.routeQuery.tab = "recycle";
    const recycleWrapper = mount(DataPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();
    expect(recycleWrapper.get("h1").text()).toBe("维修回收站");
    expect(recycleWrapper.text()).toContain("回收站内近 30 天分布");
    expect(recycleWrapper.get(".data-trend-chart").attributes("aria-hidden")).toBe("true");
    expect(recycleWrapper.get(".data-trend-summary").text()).toContain("项");
    expect(recycleWrapper.get('input[name="repair-recycle-search"]').attributes("aria-label"))
      .toBe("搜索回收站维修事务");
    recycleWrapper.unmount();
  });

  it("blocks export preview when the date range is inverted", async () => {
    mocks.get.mockImplementation((url: string) => {
      if (url === "/api/exports/options") return Promise.resolve(datedExportOptions);
      return Promise.resolve([]);
    });
    const wrapper = mount(DataPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();
    await openCustomSource(wrapper);

    const inputs = wrapper.findAll('input[type="date"]');
    await inputs[0].setValue("2026-08-21");
    await inputs[1].setValue("2026-08-20");

    expect(wrapper.get('[role="alert"]').text()).toContain("开始日期不能晚于结束日期");
    expect(wrapper.get("button[data-export-action=preview]").attributes("disabled")).toBeDefined();
    expect(mocks.post).not.toHaveBeenCalled();
    wrapper.unmount();
  });
});

const deletedRepair: RecycledRepairCase = {
  id: 41, caseNo: 'TEST-0041', agreementType: 'REPAIR', ownerName: '测试委托人', ownerPhone: '',
  deviceType: '笔记本', deviceBrand: '测试品牌', deviceModel: '测试型号', faultDescription: '测试故障',
  dataBackupConfirmed: true, riskAcknowledged: true, privacyAcknowledged: true,
  status: 'REPAIRING', receivedAt: '2026-10-01T14:00:00', deletedAt: '2026-10-13T14:00:00', deletedByName: '测试操作人',
};

async function mountRecycle() {
  mocks.routeQuery.tab = 'recycle';
  mocks.recycleItems = [deletedRepair];
  installSuccessfulLoads();
  const wrapper = mount(DataPage, { global: { stubs: { Teleport: true } } });
  await flushPromises();
  return wrapper;
}

describe('recycle recovery and deletion safeguards', () => {
  it('filters records without losing recovery controls and refreshes after restoring', async () => {
    const wrapper = await mountRecycle();
    const search = wrapper.get('input[name="repair-recycle-search"]');
    await search.setValue('无匹配');
    expect(wrapper.text()).toContain('没有匹配的维修事务');
    await search.setValue('测试型号');
    expect(wrapper.text()).toContain(deletedRepair.caseNo);
    mocks.post.mockImplementation(async () => { mocks.recycleItems = []; return {}; });
    await wrapper.get('button[aria-label^="恢复维修事务："]').trigger('click');
    await flushPromises();
    expect(mocks.post).toHaveBeenCalledWith('/api/repairs/41/restore');
    expect(wrapper.text()).toContain('回收站为空');
    wrapper.unmount();
  });

  it('requires the exact case number and retains the dialog and input on purge failure', async () => {
    const wrapper = await mountRecycle();
    await wrapper.get('button[aria-label^="彻底删除维修事务："]').trigger('click');
    const confirm = wrapper.get('[role="dialog"] footer .button.danger');
    expect(confirm.attributes('disabled')).toBeDefined();
    expect(wrapper.get('[role="dialog"] label').text()).toContain('维修编号');
    const input = wrapper.get('input[name="confirmReason"]');
    await input.setValue('错误编号');
    expect(confirm.attributes('disabled')).toBeDefined();
    expect(wrapper.get('[role="dialog"] [role="alert"]').text()).toContain('编号不匹配');
    await confirm.trigger('click');
    await flushPromises();
    expect(mocks.post).not.toHaveBeenCalled();
    mocks.post.mockRejectedValue(new Error('删除失败'));
    await input.setValue(deletedRepair.caseNo);
    await confirm.trigger('click');
    await flushPromises();
    expect(mocks.post).toHaveBeenCalledWith('/api/repairs/41/purge', { caseNo: deletedRepair.caseNo });
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true);
    expect((input.element as HTMLInputElement).value).toBe(deletedRepair.caseNo);
    expect(mocks.notify).toHaveBeenCalledWith('删除失败', 'danger');
    await wrapper.get('[role="dialog"] footer .button.secondary').trigger('click');
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('keeps recycle access restricted to the existing administrator role', async () => {
    mocks.role = 'PRESIDENT';
    mocks.routeQuery.tab = 'recycle';
    installSuccessfulLoads();
    const wrapper = mount(DataPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();
    expect(wrapper.find('#data-panel-recycle').exists()).toBe(false);
    expect(mocks.get).not.toHaveBeenCalledWith('/api/repairs/recycle-bin', expect.anything());
    expect(mocks.replace).toHaveBeenCalledWith({ query: { tab: 'import' } });
    wrapper.unmount();
  });
});

describe('DataPage import entry', () => {
  it('defaults to import and points the data center menu there without export or backup requests', async () => {
    delete mocks.routeQuery.tab;
    const wrapper = mount(DataPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();
    expect(wrapper.get('h1').text()).toBe('数据中心');
    expect(wrapper.findAll('.import-task')).toHaveLength(3);
    expect(mocks.get).not.toHaveBeenCalled();
    expect(wrapper.get('.overview-navigation .is-current').text()).toBe('数据中心');
    expect(JSON.parse(wrapper.get('.overview-navigation .is-current').attributes('data-route')!)).toEqual({ name: 'data', query: { tab: 'import' } });
    expect(mocks.replace).toHaveBeenCalledWith({ query: { tab: 'import' } });
    wrapper.unmount();
  });
});


describe("data center command destinations", () => {
  it.each(["repairs", "training"])("opens the selected %s configuration with command dates without exporting", async sourceId => {
    mocks.routeQuery.source=sourceId;
    mocks.routeQuery.from="2026-08-01"; mocks.routeQuery.to="2026-08-28";
    if(sourceId === "repairs") mocks.routeQuery.status="COMPLETED";
    const filters = [...datedExportOptions.sources[0].filters,
      ...(sourceId === "repairs" ? [{id:"status",label:"状态",type:"select",defaultValue:"",options:[{value:"COMPLETED",label:"已完成"}]}] : [])];
    const label=sourceId === "training" ? "培训记录" : "维修事务";
    const source={id:sourceId,label,fields:[{id:"name",label:"姓名",defaultSelected:true}],filters};
    mocks.get.mockImplementation((url:string) => Promise.resolve(url === "/api/exports/options" ? {sources:[...exportOptions.sources,source]} : {datasets:[]}));
    const wrapper=mount(DataPage,{global:{stubs:{Teleport:true}}});
    await flushPromises();
    expect(wrapper.get(".data-operation-header h2").text()).toBe(label);
    expect((wrapper.get('input[name="export-filter-from"]').element as HTMLInputElement).value).toBe("2026-08-01");
    expect((wrapper.get('input[name="export-filter-to"]').element as HTMLInputElement).value).toBe("2026-08-28");
    if(sourceId === "repairs") expect((wrapper.get('select[name="export-filter-status"]').element as HTMLSelectElement).value).toBe("COMPLETED");
    expect(mocks.post).not.toHaveBeenCalled(); expect(mocks.api).not.toHaveBeenCalled(); expect(mocks.download).not.toHaveBeenCalled();
    mocks.post.mockResolvedValue({source:sourceId,sourceLabel:label,fields:source.fields,filters:{},rows:[],totalRows:0,truncated:false});
    await wrapper.get('button[data-export-action="preview"]').trigger("click"); await flushPromises();
    expect(mocks.post).toHaveBeenCalledWith("/api/exports/preview",expect.objectContaining({source:sourceId,filters:expect.objectContaining({from:"2026-08-01",to:"2026-08-28"})}));
    expect(mocks.api).not.toHaveBeenCalled(); expect(mocks.download).not.toHaveBeenCalled();
    wrapper.unmount();
  });
  it("does not reopen the removed summary from an old data center link", async () => {
    mocks.routeQuery.source="stats"; installSuccessfulLoads();
    const wrapper=mount(DataPage,{global:{stubs:{Teleport:true}}});await flushPromises();
    expect(wrapper.find('button[data-source="stats"]').exists()).toBe(false);
    expect(wrapper.get('[role="alert"]').text()).toContain("指定的数据类型不可用");
    expect(mocks.post).not.toHaveBeenCalled();expect(mocks.api).not.toHaveBeenCalled();wrapper.unmount();
  });
  it("shows unavailable or forbidden command source instead of silently opening another type", async () => {
    mocks.routeQuery.source="logs"; mocks.role="PRESIDENT"; installSuccessfulLoads();
    const wrapper=mount(DataPage,{global:{stubs:{Teleport:true}}}); await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain("指定的数据类型不可用");
    expect(wrapper.find('.data-operation-header').exists()).toBe(false);
    expect(mocks.post).not.toHaveBeenCalled(); expect(mocks.api).not.toHaveBeenCalled();
    await wrapper.get('.data-type-card button[data-source="members"]').trigger("click"); await flushPromises();
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect(wrapper.get('.data-operation-header h2').text()).toBe("成员名册");
    wrapper.unmount();
  });
});

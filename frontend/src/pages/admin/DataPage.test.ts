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
  expire: vi.fn(),
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
  useRouter: () => ({ replace: mocks.replace }),
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
  Object.values(mocks).forEach((mock) => mock.mockReset());
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

  it("blocks the export wizard when the date range is inverted", async () => {
    mocks.get.mockImplementation((url: string) => {
      if (url === "/api/exports/options") return Promise.resolve(datedExportOptions);
      return Promise.resolve([]);
    });
    const wrapper = mount(DataPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();
    await wrapper.get(".wizard-actions .button.primary").trigger("click");

    const inputs = wrapper.findAll('input[type="date"]');
    await inputs[0].setValue("2026-08-21");
    await inputs[1].setValue("2026-08-20");

    expect(wrapper.get('[role="alert"]').text()).toContain("开始日期不能晚于结束日期");
    expect(wrapper.get(".wizard-actions .button.primary").attributes("disabled")).toBeDefined();
    expect(mocks.post).not.toHaveBeenCalled();
    wrapper.unmount();
  });
});

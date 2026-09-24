// @vitest-environment jsdom
import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import LogsPage from "./LogsPage.vue";

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  del: vi.fn(),
  download: vi.fn(),
  notify: vi.fn(),
}));

vi.mock("vue-router", () => ({
  RouterLink: { template: "<a><slot /></a>" },
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}));

vi.mock("../../shared/api", () => ({
  get: (...args: unknown[]) => mocks.get(...args),
  del: (...args: unknown[]) => mocks.del(...args),
  downloadBlob: (...args: unknown[]) => mocks.download(...args),
}));
vi.mock("../../shared/composables/useToast", () => ({ notify: mocks.notify }));

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => (resolve = done));
  return { promise, resolve };
}

function page(name: string) {
  return {
    items: [{
      id: name,
      operatorName: name,
      actionType: "UPDATE_USER",
      targetType: "USER",
      targetId: "1",
      reason: "测试",
      createdAt: "2026-08-21T12:00:00",
    }],
    total: 1,
    page: 1,
    pageSize: 20,
  };
}

afterEach(() => {
  Object.values(mocks).forEach((mock) => mock.mockReset());
  document.body.innerHTML = "";
});

describe("LogsPage request states", () => {
  it("offers readable operation types instead of a free-form code field", async () => {
    mocks.get.mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 20 });
    const wrapper = mount(LogsPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();

    const select = wrapper.get('select[name="logActionType"]');
    expect(select.text()).toContain("修改成员");
    expect(wrapper.find('input[placeholder="例如 UPDATE"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it("keeps the latest filtered result when an older response arrives late", async () => {
    const oldResult = deferred<ReturnType<typeof page>>();
    const newResult = deferred<ReturnType<typeof page>>();
    mocks.get.mockReturnValueOnce(oldResult.promise).mockReturnValueOnce(newResult.promise);
    const wrapper = mount(LogsPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();

    await wrapper.get('input[placeholder="操作人、对象或原因"]').setValue("新筛选");
    await wrapper.get("form.filter-bar").trigger("submit");
    newResult.resolve(page("新结果"));
    await flushPromises();
    oldResult.resolve(page("旧结果"));
    await flushPromises();

    expect(wrapper.text()).toContain("新结果");
    expect(wrapper.text()).not.toContain("旧结果");
    wrapper.unmount();
  });

  it("keeps the clear confirmation open when deletion fails", async () => {
    mocks.get.mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 20 });
    mocks.del.mockRejectedValue(new Error("清空失败"));
    const wrapper = mount(LogsPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();
    await wrapper.get(".mw-tools .button.danger").trigger("click");
    await wrapper.get(".mw-modal-foot .button.danger").trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("清空操作日志");
    expect(mocks.get).toHaveBeenCalledTimes(1);
    expect(mocks.notify).toHaveBeenCalledWith("清空失败", "danger");
    wrapper.unmount();
  });

  it("rejects an inverted date range before loading", async () => {
    mocks.get.mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 20 });
    const wrapper = mount(LogsPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();
    mocks.get.mockClear();
    const dates = wrapper.findAll('input[type="date"]');
    await dates[0].setValue("2026-08-22");
    await dates[1].setValue("2026-08-21");
    await wrapper.get("form.filter-bar").trigger("submit");

    expect(wrapper.get('[role="alert"]').text()).toContain(
      "开始日期不能晚于结束日期",
    );
    expect(mocks.get).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it("uses applied filters for pagination and export until the next query succeeds", async () => {
    mocks.get.mockImplementation((url: string) => {
      if (url.startsWith("/api/logs/export")) return Promise.resolve(new Blob(["fixture"]));
      const requestedPage = Number(new URL(url, "http://localhost").searchParams.get("page") || 1);
      return Promise.resolve({ items: page(`第${requestedPage}页`).items, total: 21, page: requestedPage, pageSize: 20 });
    });
    const wrapper = mount(LogsPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();

    await wrapper.get('input[name="logKeyword"]').setValue("尚未查询");
    await wrapper.findAll(".pagination button")[1]?.trigger("click");
    await flushPromises();
    expect(String(mocks.get.mock.lastCall?.[0])).toContain("page=2");
    expect(String(mocks.get.mock.lastCall?.[0])).not.toContain("keyword=");

    await wrapper.get(".mw-tools .button.secondary").trigger("click");
    await flushPromises();
    expect(String(mocks.get.mock.lastCall?.[0])).toContain("/api/logs/export?");
    expect(String(mocks.get.mock.lastCall?.[0])).not.toContain("keyword=");

    await wrapper.get("form.filter-bar").trigger("submit");
    await flushPromises();
    expect(String(mocks.get.mock.lastCall?.[0])).toContain("keyword=%E5%B0%9A%E6%9C%AA%E6%9F%A5%E8%AF%A2");
    wrapper.unmount();
  });

  it("keeps the previous results and retries the failed filter", async () => {
    mocks.get.mockResolvedValueOnce(page("原结果"))
      .mockRejectedValueOnce(new Error("模拟查询失败"))
      .mockResolvedValueOnce(page("新结果"));
    const wrapper = mount(LogsPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();

    await wrapper.get('input[name="logKeyword"]').setValue("待重试");
    await wrapper.get("form.filter-bar").trigger("submit");
    await flushPromises();
    expect(wrapper.text()).toContain("原结果");
    expect(wrapper.text()).toContain("模拟查询失败");

    await wrapper.get('[data-action="retry-logs"]').trigger("click");
    await flushPromises();
    expect(String(mocks.get.mock.lastCall?.[0])).toContain("keyword=%E5%BE%85%E9%87%8D%E8%AF%95");
    expect(wrapper.text()).toContain("新结果");
    expect(wrapper.text()).not.toContain("原结果");
    wrapper.unmount();
  });
});

vi.mock("../../shared/composables/useServiceHealth", () => ({ useServiceHealth: () => ({ online: true }) }));

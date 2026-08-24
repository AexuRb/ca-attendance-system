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
    await wrapper.get(".page-actions .button.danger").trigger("click");
    await wrapper.get(".modal-footer .button.danger").trigger("click");
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
});

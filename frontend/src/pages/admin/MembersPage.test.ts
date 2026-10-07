// @vitest-environment jsdom
import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import MembersPage from "./MembersPage.vue";
import MemberRowActions from "../../features/members/MemberRowActions.vue";

const mocks = vi.hoisted(() => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPut: vi.fn(),
  apiDelete: vi.fn(),
  notify: vi.fn(),
  push: vi.fn(),
  replace: vi.fn(),
}));

vi.mock("vue-router", async (importOriginal) => ({
  ...await importOriginal<typeof import("vue-router")>(),
  RouterLink: { template: '<a><slot /></a>' },
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push: mocks.push, replace: mocks.replace }),
}));

vi.mock("../../shared/api", () => ({
  get: (...args: unknown[]) => mocks.apiGet(...args),
  post: (...args: unknown[]) => mocks.apiPost(...args),
  put: (...args: unknown[]) => mocks.apiPut(...args),
  del: (...args: unknown[]) => mocks.apiDelete(...args),
  downloadBlob: vi.fn(),
}));

vi.mock("../../shared/composables/useToast", () => ({ notify: mocks.notify }));

vi.mock("../../app/session", () => ({
  useSession: () => ({
    state: { access: { kioskAvailable: true } },
    logout: vi.fn(),
    user: {
      value: {
        id: 1,
        studentNo: "9900000001",
        name: "测试管理员",
        role: "ADMIN",
      },
    },
  }),
}));

const linkedMember = {
  id: 2,
  studentNo: "9900000002",
  name: "历史成员",
  role: "MEMBER",
  status: "ACTIVE",
  phone: "",
  major: "计算机学院",
  grade: "2025级",
  qq: "",
  mustChangePassword: false,
  createdAt: "2026-08-13 10:00:00",
  updatedAt: "2026-08-13 10:00:00",
};

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => (resolve = done));
  return { promise, resolve };
}

beforeEach(() => {
  mocks.apiGet.mockImplementation((url: string) => {
    if (url.startsWith("/api/users/page?")) {
      return Promise.resolve({
        items: [linkedMember],
        total: 1,
        page: 1,
        pageSize: 20,
      });
    }
    if (url === "/api/users/grades") return Promise.resolve(["2025级"]);
    return Promise.resolve([]);
  });
});

afterEach(() => {
  mocks.apiGet.mockReset();
  mocks.apiDelete.mockReset();
  mocks.apiPost.mockReset();
  mocks.apiPut.mockReset();
  mocks.notify.mockReset();
  document.body.innerHTML = "";
});

describe("MembersPage deletion", () => {
  it("keeps the dialog open and exposes the backend history conflict", async () => {
    mocks.apiDelete.mockRejectedValue(
      new Error("该成员已有培训记录、维修事务，不能永久删除，请改为停用账号"),
    );
    const wrapper = mount(MembersPage, {
      global: { stubs: { Teleport: true } },
    });
    await flushPromises();

    wrapper.findComponent(MemberRowActions).vm.$emit("delete");
    await wrapper.vm.$nextTick();
    expect(wrapper.text()).toContain("仅可永久删除从未参与业务的空白账号");

    await wrapper.get(".confirm-copy + .field textarea").setValue("清理测试账号");
    await wrapper.get("[role=dialog] footer .button.danger").trigger("click");
    await flushPromises();

    expect(mocks.apiDelete).toHaveBeenCalledWith("/api/users/2", {
      reason: "清理测试账号",
    });
    expect(mocks.notify).toHaveBeenCalledWith(
      "该成员已有培训记录、维修事务，不能永久删除，请改为停用账号",
      "danger",
    );
    expect(wrapper.find(".confirm-copy").exists()).toBe(true);
  });

  it("closes the dialog and refreshes only after deletion succeeds", async () => {
    mocks.apiDelete.mockResolvedValue(null);
    const wrapper = mount(MembersPage, {
      global: { stubs: { Teleport: true } },
    });
    await flushPromises();

    wrapper.findComponent(MemberRowActions).vm.$emit("delete");
    await wrapper.vm.$nextTick();
    await wrapper.get(".confirm-copy + .field textarea").setValue("清理空白账号");
    await wrapper.get("[role=dialog] footer .button.danger").trigger("click");
    await flushPromises();

    expect(wrapper.find(".confirm-copy").exists()).toBe(false);
    expect(mocks.apiGet.mock.calls.filter(([url]) => String(url).startsWith("/api/users/page?")).length).toBe(2);
    expect(mocks.notify).toHaveBeenCalledWith("成员已删除", "success");
  });
});

describe("MembersPage request ordering", () => {
  it("keeps the latest filter result when an older request arrives late", async () => {
    const oldResult = deferred<unknown>();
    const newResult = deferred<unknown>();
    let pageCalls = 0;
    mocks.apiGet.mockImplementation((url: string) => {
      if (url.startsWith("/api/users/page?")) {
        pageCalls += 1;
        return pageCalls === 1 ? oldResult.promise : newResult.promise;
      }
      if (url === "/api/users/grades") return Promise.resolve(["2025级"]);
      return Promise.resolve([]);
    });
    const wrapper = mount(MembersPage, {
      global: { stubs: { Teleport: true } },
    });
    await flushPromises();

    await wrapper.get('input[name="memberKeyword"]').setValue("新成员");
    await wrapper.get("form.mw-filter").trigger("submit");
    newResult.resolve({
      items: [{ ...linkedMember, id: 3, name: "新成员" }],
      total: 1,
      page: 1,
      pageSize: 20,
    });
    await flushPromises();
    oldResult.resolve({
      items: [{ ...linkedMember, name: "旧成员" }],
      total: 1,
      page: 1,
      pageSize: 20,
    });
    await flushPromises();

    expect(wrapper.text()).toContain("新成员");
    expect(wrapper.text()).not.toContain("旧成员");
    wrapper.unmount();
  });
});

describe("MembersPage filter feedback", () => {
  it("keeps draft filters distinct from applied results and clears them in one action", async () => {
    const wrapper = mount(MembersPage, {
      global: { stubs: { Teleport: true } },
    });
    await flushPromises();
    const pageCalls = () => mocks.apiGet.mock.calls.filter(([url]) => String(url).startsWith("/api/users/page?")).length;
    expect(wrapper.get(".mw-filter-status-label").text()).toBe("当前筛选");
    const initialCalls = pageCalls();

    await wrapper.get('select[name="memberRole"]').setValue("MINISTER");
    expect(wrapper.get(".mw-filter-status-label").text()).toBe("待查询");
    expect(pageCalls()).toBe(initialCalls);

    await wrapper.get("form.mw-filter").trigger("submit");
    await flushPromises();
    expect(wrapper.get(".mw-filter-status-label").text()).toBe("当前筛选");
    expect(pageCalls()).toBe(initialCalls + 1);

    await wrapper.get(".mw-filter-reset").trigger("click");
    await flushPromises();
    expect(wrapper.get('select[name="memberRole"]').element).toHaveProperty("value", "");
    expect(pageCalls()).toBe(initialCalls + 2);
    wrapper.unmount();
  });
});

describe("MembersPage selection", () => {
  it("keeps one result toolbar while member selection changes", async () => {
    const wrapper = mount(MembersPage, {
      global: { stubs: { Teleport: true } },
    });
    await flushPromises();

    const toolbar = wrapper.get(".member-result-toolbar").element;
    const menuTrigger = wrapper.get<HTMLButtonElement>(".member-result-toolbar .action-menu > button");
    expect(menuTrigger.element.disabled).toBe(true);
    expect(wrapper.find(".selection-toolbar").exists()).toBe(false);

    await wrapper.get('input[name="memberSelection-2"]').setValue(true);
    expect(wrapper.get(".member-result-toolbar").element).toBe(toolbar);
    expect(wrapper.get(".member-result-summary").text()).toContain("已选 1 人");
    expect(menuTrigger.element.disabled).toBe(false);

    await menuTrigger.trigger("click");
    await flushPromises();
    expect(wrapper.findAll(".mw-menu [role=menuitem]")).toHaveLength(3);
    await wrapper.get(".mw-menu [role=menuitem]:last-child").trigger("click");
    await flushPromises();
    expect(wrapper.get(".member-result-toolbar").element).toBe(toolbar);
    expect(wrapper.get(".member-result-summary").text()).toContain("共 1 人");
    expect(menuTrigger.element.disabled).toBe(true);
    wrapper.unmount();
  });

  it("selects every manageable member in the current filtered result across pages", async () => {
    mocks.apiGet.mockImplementation((url: string) => {
      if (url.startsWith("/api/users/selection")) {
        return Promise.resolve([2, 22]);
      }
      if (url.startsWith("/api/users/page?")) {
        const requestedPage = new URL(url, "http://localhost").searchParams.get("page");
        return Promise.resolve({
          items: [
            requestedPage === "2"
              ? { ...linkedMember, id: 22, studentNo: "9900000022", name: "第二页成员" }
              : linkedMember,
          ],
          total: 40,
          page: Number(requestedPage),
          pageSize: 20,
        });
      }
      if (url === "/api/users/grades") return Promise.resolve(["2025级"]);
      return Promise.resolve([]);
    });
    const wrapper = mount(MembersPage, {
      global: { stubs: { Teleport: true } },
    });
    await flushPromises();

    const selectAll = wrapper.get('input[name="memberPageSelection"]');
    await selectAll.setValue(true);
    await flushPromises();

    expect(mocks.apiGet).toHaveBeenCalledWith("/api/users/selection");
    expect(wrapper.text()).toContain("已选 2 人");
    expect((selectAll.element as HTMLInputElement).checked).toBe(true);

    await wrapper.findAll(".pagination button")[1].trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("第二页成员");
    expect(
      (wrapper.get('input[name="memberSelection-22"]').element as HTMLInputElement)
        .checked,
    ).toBe(true);
    expect(
      (wrapper.get('input[name="memberPageSelection"]').element as HTMLInputElement)
        .checked,
    ).toBe(true);
  });
});

describe("MembersPage empty results", () => {
  it("does not call an empty page an empty directory when other members exist", async () => {
    mocks.apiGet.mockImplementation((url: string) => Promise.resolve(
      url.startsWith("/api/users/page") ? { items: [], total: 20, page: 2, pageSize: 20 } : [],
    ));
    const wrapper = mount(MembersPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();
    expect(wrapper.text()).toContain("没有符合条件的成员");
    expect(wrapper.text()).not.toContain("尚未添加成员");
    expect(wrapper.text()).not.toContain("新增第一位成员");
    wrapper.unmount();
  });

  it("uses the last successful query for its empty guidance, not the unsubmitted draft", async () => {
    mocks.apiGet.mockImplementation((url: string) => Promise.resolve(
      url.startsWith("/api/users/page") ? { items: [], total: 0, page: 1, pageSize: 20 } : [],
    ));
    const wrapper = mount(MembersPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();
    expect(wrapper.text()).toContain("尚未添加成员");
    await wrapper.get('[name="memberKeyword"]').setValue("不存在的成员");
    expect(wrapper.text()).toContain("尚未添加成员");
    await wrapper.get(".mw-filter").trigger("submit");
    await flushPromises();
    expect(wrapper.text()).toContain("没有符合条件的成员");
    expect(wrapper.text()).not.toContain("新增第一位成员");
    await wrapper.get('[name="memberKeyword"]').setValue("");
    expect(wrapper.text()).toContain("没有符合条件的成员");
    await wrapper.get(".mw-filter").trigger("submit");
    await flushPromises();
    expect(wrapper.text()).toContain("新增第一位成员");
    wrapper.unmount();
  });
});

describe("MembersPage applied query", () => {
  it("refreshes the applied result after a member change without submitting draft input", async () => {
    mocks.apiPut.mockResolvedValue({ ...linkedMember, status: 'DISABLED' });
    const wrapper = mount(MembersPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();
    await wrapper.get('[name="memberKeyword"]').setValue('未提交');
    wrapper.findComponent(MemberRowActions).vm.$emit('toggle-status');
    await flushPromises();
    expect(mocks.apiPut).toHaveBeenCalled();
    const calls = mocks.apiGet.mock.calls.filter(([url]) => String(url).startsWith('/api/users/page?'));
    expect(calls.at(-1)?.[0]).toBe('/api/users/page?page=1&pageSize=20');
    expect((wrapper.get('[name="memberKeyword"]').element as HTMLInputElement).value).toBe('未提交');
    wrapper.unmount();
  });

  it("pages with applied filters and commits route state only after a successful query", async () => {
    mocks.apiGet.mockImplementation((url: string) => {
      if (!url.startsWith('/api/users/page?')) return Promise.resolve([]);
      const query = new URL(url, 'http://localhost').searchParams;
      return Promise.resolve({ items: [linkedMember], total: 40, page: Number(query.get('page')), pageSize: 20 });
    });
    const wrapper = mount(MembersPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();
    await wrapper.get('[name="memberKeyword"]').setValue('尚未查询');
    await wrapper.get('[name="memberRole"]').setValue('MINISTER');
    await wrapper.findAll('.pagination button')[1].trigger('click');
    await flushPromises();
    const pageCalls = mocks.apiGet.mock.calls.filter(([url]) => String(url).startsWith('/api/users/page?'));
    expect(pageCalls.at(-1)?.[0]).toBe('/api/users/page?page=2&pageSize=20');
    expect(mocks.push).toHaveBeenLastCalledWith({ query: { page: '2' } });
    expect((wrapper.get('[name="memberKeyword"]').element as HTMLInputElement).value).toBe('尚未查询');
    await wrapper.get('.mw-filter').trigger('submit');
    await flushPromises();
    expect(mocks.push).toHaveBeenLastCalledWith({ query: { role: 'MINISTER' } });
    wrapper.unmount();
  });

  it("keeps old results and selection on failure, then retries the submitted snapshot", async () => {
    const wrapper = mount(MembersPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();
    await wrapper.get('input[name="memberSelection-2"]').setValue(true);
    mocks.push.mockClear();
    mocks.apiGet.mockRejectedValueOnce(new Error('查询失败'));
    await wrapper.get('[name="memberKeyword"]').setValue('失败条件');
    await wrapper.get('[name="memberRole"]').setValue('MINISTER');
    await wrapper.get('.mw-filter').trigger('submit');
    await flushPromises();
    expect(mocks.push).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('仍显示上次成功结果');
    expect(wrapper.text()).toContain('已选 1 人');
    expect(wrapper.findComponent(MemberRowActions).props('pending')).toBe(true);
    expect((wrapper.get('input[name="memberSelection-2"]').element as HTMLInputElement).disabled).toBe(true);
    await wrapper.get('[name="memberKeyword"]').setValue('再次修改');
    await wrapper.get('[data-action="retry-members"]').trigger('click');
    await flushPromises();
    const url = String(mocks.apiGet.mock.calls.at(-1)?.[0]);
    expect(new URL(url, 'http://localhost').searchParams.get('keyword')).toBe('失败条件');
    expect(mocks.push).toHaveBeenLastCalledWith({ query: { role: 'MINISTER' } });
    expect(wrapper.text()).not.toContain('已选 1 人');
    expect(wrapper.findComponent(MemberRowActions).props('pending')).toBe(false);
    wrapper.unmount();
  });
});

describe("MembersPage import", () => {
  it("requires a successful server preview before sending the confirmed file and token", async () => {
    mocks.apiPost.mockResolvedValueOnce({ valid: true, created: 1, updated: 0, errorCount: 0, errors: [], warnings: [], changes: [], token: 'preview-revision' })
      .mockResolvedValueOnce({ created: 1, updated: 0, skipped: 0, errors: [] });
    const wrapper = mount(MembersPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();
    await wrapper.get('.mw-tools .mw-button:not(.primary)').trigger('click');
    const input = wrapper.get('input[type="file"]');
    const file = new File(['test'], 'members.xlsx');
    Object.defineProperty(input.element, 'files', { value: [file], configurable: true });
    await input.trigger('change');
    await wrapper.get('[role=dialog] footer .button.primary').trigger('click');
    await flushPromises();
    expect(mocks.apiPost).toHaveBeenCalledTimes(1);
    expect(mocks.apiPost.mock.calls[0][0]).toBe('/api/users/import/preview');
    expect(wrapper.get('[role=dialog] footer .button.primary').text()).toBe('确认整批导入');
    await wrapper.get('[role=dialog] footer .button.primary').trigger('click');
    await flushPromises();
    expect(mocks.apiPost.mock.calls[1][0]).toBe('/api/users/import');
    expect(mocks.apiPost.mock.calls[1][1].get('previewToken')).toBe('preview-revision');
    expect(mocks.apiPost.mock.calls[1][1].get('file')).toBe(file);
    wrapper.unmount();
  });

  it("keeps a row-specific import error visible in the dialog", async () => {
    mocks.apiPost.mockRejectedValue(
      new Error("成员文件校验未通过，未写入任何成员：第 3 行：姓名不能超过 64 个字符"),
    );
    const wrapper = mount(MembersPage, {
      global: { stubs: { Teleport: true } },
    });
    await flushPromises();

    await wrapper.get(".mw-tools .mw-button:not(.primary)").trigger("click");
    const input = wrapper.get('input[type="file"]');
    Object.defineProperty(input.element, "files", {
      value: [new File(["test"], "members.xlsx")],
      configurable: true,
    });
    await input.trigger("change");
    await wrapper.get("[role=dialog] footer .button.primary").trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("第 3 行：姓名不能超过 64 个字符");
    expect(wrapper.find(".member-import-error").exists()).toBe(true);
  });
});

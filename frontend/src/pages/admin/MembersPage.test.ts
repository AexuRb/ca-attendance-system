// @vitest-environment jsdom
import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import MembersPage from "./MembersPage.vue";
import MemberRowActions from "../../features/members/MemberRowActions.vue";

const mocks = vi.hoisted(() => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiDelete: vi.fn(),
  notify: vi.fn(),
}));

vi.mock("vue-router", async (importOriginal) => ({
  ...await importOriginal<typeof import("vue-router")>(),
  RouterLink: { template: '<a><slot /></a>' },
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}));

vi.mock("../../shared/api", () => ({
  get: (...args: unknown[]) => mocks.apiGet(...args),
  post: (...args: unknown[]) => mocks.apiPost(...args),
  put: vi.fn(),
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

describe("MembersPage import", () => {
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

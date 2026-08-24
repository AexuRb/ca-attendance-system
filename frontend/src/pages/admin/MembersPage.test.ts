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
    await wrapper.get(".modal-footer .button.danger").trigger("click");
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
    await wrapper.get(".modal-footer .button.danger").trigger("click");
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
    await wrapper.get("form.member-filter-shell").trigger("submit");
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

describe("MembersPage import", () => {
  it("keeps a row-specific import error visible in the dialog", async () => {
    mocks.apiPost.mockRejectedValue(
      new Error("成员文件校验未通过，未写入任何成员：第 3 行：姓名不能超过 64 个字符"),
    );
    const wrapper = mount(MembersPage, {
      global: { stubs: { Teleport: true } },
    });
    await flushPromises();

    await wrapper.get(".page-actions .button.secondary").trigger("click");
    const input = wrapper.get('input[type="file"]');
    Object.defineProperty(input.element, "files", {
      value: [new File(["test"], "members.xlsx")],
      configurable: true,
    });
    await input.trigger("change");
    await wrapper.get(".modal-footer .button.primary").trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("第 3 行：姓名不能超过 64 个字符");
    expect(wrapper.find(".member-import-error").exists()).toBe(true);
  });
});

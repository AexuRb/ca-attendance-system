// @vitest-environment jsdom
import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import ProfilePasswordDialog from "../../features/profile/ProfilePasswordDialog.vue";
import ProfilePage from "./ProfilePage.vue";

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  put: vi.fn(),
  post: vi.fn(),
  replace: vi.fn(),
  notify: vi.fn(),
  refreshUser: vi.fn(),
}));

vi.mock("../../shared/api", () => ({
  get: (...args: unknown[]) => mocks.get(...args),
  put: (...args: unknown[]) => mocks.put(...args),
  post: (...args: unknown[]) => mocks.post(...args),
  setToken: vi.fn(),
}));
vi.mock("../../shared/composables/useToast", () => ({ notify: mocks.notify }));
vi.mock("vue-router", () => ({
  RouterLink: { template: "<a><slot /></a>" },
  useRouter: () => ({ replace: mocks.replace }),
}));
vi.mock("../../app/session", () => ({
  useSession: () => ({
    state: { user: { id: 1 }, access: { kioskAvailable: true } },
    user: {
      value: {
        id: 1,
        name: "测试管理员",
        studentNo: "9900000001",
        role: "ADMIN",
      },
    },
    refreshUser: mocks.refreshUser,
  }),
}));

function installLoads() {
  mocks.get.mockImplementation((url: string) => {
    if (url === "/api/auth/me") {
      return Promise.resolve({ phone: "", qq: "", major: "计算机学院", grade: "2026级" });
    }
    return Promise.resolve([]);
  });
}

afterEach(() => {
  Object.values(mocks).forEach((mock) => mock.mockReset());
  document.body.innerHTML = "";
});

describe("ProfilePage request states", () => {
  it("rejects an inverted record date range before requesting data", async () => {
    installLoads();
    const wrapper = mount(ProfilePage, { global: { stubs: { Teleport: true } } });
    await flushPromises();
    mocks.get.mockClear();

    const dates = wrapper.findAll('.profile-record-filter input[type="date"]');
    await dates[0].setValue("2026-08-22");
    await dates[1].setValue("2026-08-21");
    await wrapper.get("form.profile-record-filter").trigger("submit");

    expect(wrapper.get('[role="alert"]').text()).toContain(
      "开始日期不能晚于结束日期",
    );
    expect(mocks.get).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it("announces a successful password change before returning to login", async () => {
    installLoads();
    const wrapper = mount(ProfilePage, { global: { stubs: { Teleport: true } } });
    await flushPromises();
    wrapper.findComponent(ProfilePasswordDialog).vm.$emit("changed");
    await flushPromises();

    expect(mocks.notify).toHaveBeenCalledWith(
      "密码修改成功，请使用新密码登录",
      "success",
    );
    expect(mocks.replace).toHaveBeenCalledWith({
      name: "login",
      query: { reason: "password-changed" },
    });
    wrapper.unmount();
  });

  it("keeps the applied range and old records visible when a new query fails", async () => {
    let recordRequests = 0;
    mocks.get.mockImplementation((url: string) => {
      if (url === "/api/auth/me") return Promise.resolve({ phone: "", qq: "", major: "计算机学院", grade: "2026级" });
      if (url.startsWith("/api/attendance/me")) {
        recordRequests += 1;
        return recordRequests === 1
          ? Promise.resolve([{ id: 1, dutyDate: "2026-09-20", durationMinutes: 120, validHours: 2, effectiveStatus: "VALID" }])
          : Promise.reject(new Error("记录读取失败"));
      }
      return Promise.resolve([]);
    });
    const wrapper = mount(ProfilePage, { global: { stubs: { Teleport: true } } });
    await flushPromises();
    expect(wrapper.get(".profile-summary").text()).toContain("2");
    expect(wrapper.get(".profile-record-scroll tbody tr").text()).toContain("2026-09-20");

    await wrapper.get('input[name="profileRecordFrom"]').setValue("2026-08-01");
    expect(wrapper.get(".profile-record-context").text()).toContain("日期已修改，查询后生效");
    await wrapper.get("form.profile-record-filter").trigger("submit");
    await flushPromises();

    expect(wrapper.get(".profile-record-error").text()).toContain("下方保留上次结果");
    expect(wrapper.get(".profile-summary").text()).toContain("统计范围");
    expect(wrapper.get(".profile-record-scroll tbody tr").text()).toContain("2026-09-20");
    wrapper.unmount();
  });
});

vi.mock("../../shared/composables/useServiceHealth", () => ({ useServiceHealth: () => ({ online: true }) }));

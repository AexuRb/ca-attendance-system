// @vitest-environment jsdom
import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import SetupPage from "./SetupPage.vue";

const mocks = vi.hoisted(() => ({
  initialize: vi.fn(),
  replace: vi.fn(),
}));

vi.mock("../../app/session", () => ({
  useSession: () => ({ initialize: mocks.initialize }),
}));
vi.mock("../../layouts/AuthLayout.vue", () => ({
  default: { template: "<main><slot /></main>" },
}));
vi.mock("vue-router", () => ({
  useRouter: () => ({ replace: mocks.replace }),
}));

afterEach(() => {
  mocks.initialize.mockReset();
  mocks.replace.mockReset();
});

describe("SetupPage", () => {
  it("shows field errors and does not initialize with invalid credentials", async () => {
    const wrapper = mount(SetupPage);
    await wrapper.get('input[name="account"]').setValue("admin");
    await wrapper.get('input[name="name"]').setValue("管理员");
    await wrapper.get('input[name="password"]').setValue("12345");
    await wrapper.get('input[name="confirmation"]').setValue("54321");
    await wrapper.get("form").trigger("submit");

    expect(wrapper.text()).toContain("6 至 32 位纯数字");
    expect(wrapper.text()).toContain("密码长度必须为 6 至 64 个字符");
    expect(wrapper.text()).toContain("两次输入的密码不一致");
    expect(mocks.initialize).not.toHaveBeenCalled();
  });
});

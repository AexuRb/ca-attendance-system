// @vitest-environment jsdom
import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import PasswordPage from "./PasswordPage.vue";

const mocks = vi.hoisted(() => ({ post: vi.fn(), replace: vi.fn() }));

vi.mock("../../shared/api", () => ({
  post: (...args: unknown[]) => mocks.post(...args),
  setToken: vi.fn(),
}));
vi.mock("../../layouts/AuthLayout.vue", () => ({
  default: { template: "<main><slot /></main>" },
}));
vi.mock("../../app/session", () => ({
  useSession: () => ({ state: { user: { id: 1 } } }),
}));
vi.mock("vue-router", () => ({
  useRouter: () => ({ replace: mocks.replace }),
}));

afterEach(() => {
  mocks.post.mockReset();
  mocks.replace.mockReset();
});

describe("PasswordPage", () => {
  it("shows field errors and does not submit an invalid password change", async () => {
    const wrapper = mount(PasswordPage);
    await wrapper.get('input[name="oldPassword"]').setValue("old-password");
    await wrapper.get('input[name="newPassword"]').setValue("12345");
    await wrapper.get('input[name="confirmation"]').setValue("54321");
    await wrapper.get("form").trigger("submit");

    expect(wrapper.text()).toContain("6 至 64 个字符");
    expect(wrapper.text()).toContain("两次输入的新密码不一致");
    expect(mocks.post).not.toHaveBeenCalled();
  });

  it("prevents duplicate submission and returns to login with a success notice", async () => {
    let resolveChange!: (value: unknown) => void;
    mocks.post.mockReturnValue(
      new Promise((resolve) => {
        resolveChange = resolve;
      }),
    );
    const wrapper = mount(PasswordPage);
    await wrapper.get('input[name="oldPassword"]').setValue("old-password");
    await wrapper.get('input[name="newPassword"]').setValue("new-password");
    await wrapper.get('input[name="confirmation"]').setValue("new-password");

    await wrapper.get("form").trigger("submit");
    await wrapper.get("form").trigger("submit");
    expect(mocks.post).toHaveBeenCalledTimes(1);
    resolveChange({});
    await flushPromises();

    expect(mocks.replace).toHaveBeenCalledWith({
      name: "login",
      query: { reason: "password-changed" },
    });
  });
});

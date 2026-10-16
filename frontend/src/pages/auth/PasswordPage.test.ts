// @vitest-environment jsdom
import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import PasswordPage from "./PasswordPage.vue";

const mocks = vi.hoisted(() => ({
  post: vi.fn(), replace: vi.fn(), push: vi.fn(), setToken: vi.fn(),
  state: { user: { id: 1, mustChangePassword: true } as { id: number; mustChangePassword: boolean } | null, access: { mode: "LOCAL" } },
}));

vi.mock("../../shared/api", () => ({
  post: (...args: unknown[]) => mocks.post(...args),
  setToken: mocks.setToken,
}));
vi.mock("../../layouts/AuthLayout.vue", () => ({
  default: { template: '<main><slot name="header-actions" /><slot /></main>' },
}));
vi.mock("../../app/session", () => ({
  useSession: () => ({ state: mocks.state }),
}));
vi.mock("vue-router", () => ({
  useRouter: () => ({ replace: mocks.replace, push: mocks.push }),
}));

afterEach(() => {
  mocks.post.mockReset();
  mocks.replace.mockReset();
  mocks.push.mockReset();
  mocks.setToken.mockReset();
  mocks.state.user = { id: 1, mustChangePassword: true };
});

describe("PasswordPage", () => {
  it("keeps first-login setup mandatory without a profile return", () => {
    const wrapper = mount(PasswordPage);
    expect(wrapper.get('h2').text()).toBe('设置新密码');
    expect(wrapper.text()).toContain('首次登录需修改初始密码');
    expect(wrapper.find('.password-return').exists()).toBe(false);
  });

  it("offers voluntary password changes a return to profile", async () => {
    mocks.state.user = { id: 1, mustChangePassword: false };
    const wrapper = mount(PasswordPage);
    expect(wrapper.get('h2').text()).toBe('修改密码');
    expect(wrapper.text()).not.toContain('首次登录需修改初始密码');
    await wrapper.get('.password-return').trigger('click');
    expect(mocks.push).toHaveBeenCalledWith({ name: 'profile' });
    expect(mocks.post).not.toHaveBeenCalled();
  });

  it("keeps entered fields when the password update fails", async () => {
    mocks.post.mockRejectedValue(new Error('原密码不正确'));
    const wrapper = mount(PasswordPage);
    await wrapper.get('input[name="oldPassword"]').setValue('old-password');
    await wrapper.get('input[name="newPassword"]').setValue('new-password');
    await wrapper.get('input[name="confirmation"]').setValue('new-password');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain('原密码不正确');
    expect((wrapper.get('input[name="newPassword"]').element as HTMLInputElement).value).toBe('new-password');
    expect((wrapper.get('input[name="confirmation"]').element as HTMLInputElement).value).toBe('new-password');
    expect(mocks.replace).not.toHaveBeenCalled();
    expect(mocks.setToken).not.toHaveBeenCalled();
  });

  it("lets the user inspect each password field independently", async () => {
    const wrapper = mount(PasswordPage);
    const oldPassword = wrapper.get('input[name="oldPassword"]');
    const newPassword = wrapper.get('input[name="newPassword"]');
    const confirmation = wrapper.get('input[name="confirmation"]');

    await wrapper.get('button[aria-label="显示原密码"]').trigger("click");
    expect(oldPassword.attributes("type")).toBe("text");
    expect(newPassword.attributes("type")).toBe("password");

    await wrapper.get('button[aria-label="显示新密码"]').trigger("click");
    expect(newPassword.attributes("type")).toBe("text");
    expect(confirmation.attributes("type")).toBe("password");

    await wrapper.get('button[aria-label="显示确认新密码"]').trigger("click");
    expect(confirmation.attributes("type")).toBe("text");
  });

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
    mocks.state.user = { id: 1, mustChangePassword: false };
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
    expect(wrapper.get(".password-return").attributes("disabled")).toBeDefined();
    resolveChange({});
    await flushPromises();

    expect(mocks.setToken).toHaveBeenCalledWith("");
    expect(mocks.state.user).toBeNull();
    expect(mocks.replace).toHaveBeenCalledWith({
      name: "login",
      query: { reason: "password-changed" },
    });
  });
});

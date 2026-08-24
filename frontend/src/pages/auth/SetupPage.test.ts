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

  it("creates the administrator once and opens the admin home", async () => {
    mocks.initialize.mockResolvedValue({ role: "ADMIN" });
    const wrapper = mount(SetupPage);
    await fillValidSetupForm(wrapper);
    await wrapper.get("form").trigger("submit");

    expect(mocks.initialize).toHaveBeenCalledWith(
      "9900000001",
      "首位管理员",
      "setup-password",
    );
    expect(mocks.replace).toHaveBeenCalledWith({ name: "today" });
  });

  it("does not submit the initialization request twice while it is pending", async () => {
    let finishInitialization: (() => void) | undefined;
    mocks.initialize.mockImplementation(
      () =>
        new Promise<void>((resolve) => {
          finishInitialization = resolve;
        }),
    );
    const wrapper = mount(SetupPage);
    await fillValidSetupForm(wrapper);

    await wrapper.get("form").trigger("submit");
    await wrapper.get("form").trigger("submit");

    expect(mocks.initialize).toHaveBeenCalledOnce();
    finishInitialization?.();
    await vi.waitFor(() => expect(mocks.replace).toHaveBeenCalled());
  });

  it("lets the administrator inspect each password field independently", async () => {
    const wrapper = mount(SetupPage);
    const password = wrapper.get('input[name="password"]');
    const confirmation = wrapper.get('input[name="confirmation"]');

    await wrapper.get('button[aria-label="显示初始密码"]').trigger("click");
    expect(password.attributes("type")).toBe("text");
    expect(confirmation.attributes("type")).toBe("password");

    await wrapper.get('button[aria-label="显示确认密码"]').trigger("click");
    expect(confirmation.attributes("type")).toBe("text");
  });
});

async function fillValidSetupForm(
  wrapper: ReturnType<typeof mount<typeof SetupPage>>,
) {
  await wrapper.get('input[name="account"]').setValue("9900000001");
  await wrapper.get('input[name="name"]').setValue("首位管理员");
  await wrapper.get('input[name="password"]').setValue("setup-password");
  await wrapper
    .get('input[name="confirmation"]')
    .setValue("setup-password");
}

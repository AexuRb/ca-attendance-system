import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import TrainingPage from "./TrainingPage.vue";

const apiGet = vi.fn();
const apiPost = vi.fn();

vi.mock("../../shared/api", () => ({
  api: (...args: unknown[]) => apiGet(...args),
  get: (...args: unknown[]) => apiGet(...args),
  post: (...args: unknown[]) => apiPost(...args),
  put: vi.fn(),
  del: vi.fn(),
  downloadBlob: vi.fn(),
}));

vi.mock("vue-router", () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ replace: vi.fn() }),
  onBeforeRouteLeave: vi.fn(),
}));

afterEach(() => {
  apiGet.mockReset();
  apiPost.mockReset();
  document.body.innerHTML = "";
});

describe("TrainingPage interactions", () => {
  it("prevents duplicate saves and confirms closing a dirty editor", async () => {
    apiGet.mockImplementation((url: string) =>
      Promise.resolve(url.includes("participants/page")
        ? { items: [], total: 0, page: 1, pageSize: 30, hasMore: false }
        : { items: [], total: 0, page: 1, pageSize: 20, hasMore: false }),
    );
    let resolveSave!: (value: unknown) => void;
    apiPost.mockReturnValue(new Promise((resolve) => { resolveSave = resolve; }));
    const wrapper = mount(TrainingPage, { attachTo: document.body });
    await flushPromises();

    await wrapper.get(".page-header .button.primary").trigger("click");
    const title = document.body.querySelector<HTMLInputElement>('[name="training-title"]')!;
    title.value = "离线维修基础培训";
    title.dispatchEvent(new Event("input", { bubbles: true }));
    const save = document.body.querySelector<HTMLButtonElement>('[form="training-session-editor"]')!;
    save.click();
    save.click();
    await wrapper.vm.$nextTick();
    expect(apiPost).toHaveBeenCalledTimes(1);
    expect(save.disabled).toBe(true);

    resolveSave({ id: 8, title: "离线维修基础培训", trainingDate: new Date().toISOString().slice(0, 10) });
    await flushPromises();

    await wrapper.get(".page-header .button.primary").trigger("click");
    const dirtyTitle = document.body.querySelector<HTMLInputElement>('[name="training-title"]')!;
    dirtyTitle.value = "尚未保存";
    dirtyTitle.dispatchEvent(new Event("input", { bubbles: true }));
    const cancel = Array.from(document.body.querySelectorAll<HTMLButtonElement>(".modal-footer .button.secondary"))
      .find((button) => button.textContent?.includes("取消"))!;
    cancel.click();
    await wrapper.vm.$nextTick();
    expect(document.body.textContent).toContain("放弃未保存修改");
    expect(document.body.querySelector('[name="training-title"]')).not.toBeNull();
    wrapper.unmount();
  });
});

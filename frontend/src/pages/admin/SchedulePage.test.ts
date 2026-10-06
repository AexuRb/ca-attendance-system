// @vitest-environment jsdom
import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import SchedulePage from "./SchedulePage.vue";
import { useScheduleWorkspace } from "../../features/schedule/useScheduleWorkspace";

const apiGet = vi.fn();
const controls = vi.hoisted(() => ({ post: vi.fn(), leave: vi.fn() }));

vi.mock("vue-router", () => ({
  RouterLink: { template: "<a><slot /></a>" },
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  onBeforeRouteLeave: controls.leave,
}));

vi.mock("../../shared/api", () => ({
  get: (...args: unknown[]) => apiGet(...args),
  post: (...args: unknown[]) => controls.post(...args),
  put: vi.fn(),
  del: vi.fn(),
  downloadBlob: vi.fn(),
}));

afterEach(() => {
  apiGet.mockReset();
  controls.post.mockReset();
  controls.leave.mockReset();
  document.body.innerHTML = "";
});

describe("SchedulePage request states", () => {
  function harness() {
    apiGet.mockImplementation((url: string) => Promise.resolve(url.includes('duty-periods')
      ? [{ startTime: '09:00', endTime: '10:00', enabled: true }] : []));
    let workspace!: ReturnType<typeof useScheduleWorkspace>;
    const wrapper = mount({ setup() { workspace = useScheduleWorkspace(); return {}; }, template: '<div />' });
    return { wrapper, get workspace() { return workspace; } };
  }

  it("protects edits, permits unchanged cancellation, and clears the guard after discard", async () => {
    const { wrapper, workspace: w } = harness();
    await flushPromises();
    w.openFixed(null);
    w.closeEditor();
    expect(w.editorOpen.value).toBe(false);
    w.openFixed(null);
    w.fixedForm.title = '未保存标题';
    w.closeEditor();
    expect(w.unsaved.confirmOpen.value).toBe(true);
    w.unsaved.cancel();
    expect(w.fixedForm.title).toBe('未保存标题');
    expect(w.editorOpen.value).toBe(true);
    const event = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(event);
    expect(event.defaultPrevented).toBe(true);
    w.closeEditor();
    w.unsaved.discard();
    expect(w.editorOpen.value).toBe(false);
    const clean = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(clean);
    expect(clean.defaultPrevented).toBe(false);
    wrapper.unmount();
  });

  it("protects navigation and pending saves, retaining failed input for retry", async () => {
    const { wrapper, workspace: w } = harness();
    await flushPromises();
    w.openFixed(null);
    w.fixedForm.assignees = [{ studentNo: 'demo1', name: '合成人员' }];
    const leave = controls.leave.mock.lastCall![0];
    const cancelled = leave();
    w.unsaved.cancel();
    expect(await cancelled).toBe(false);
    let reject!: (error: Error) => void;
    controls.post.mockImplementationOnce(() => new Promise((_resolve, fail) => { reject = fail; }));
    const saving = w.saveFixed();
    expect(leave()).toBe(false);
    w.closeEditor();
    expect(w.editorOpen.value).toBe(true);
    reject(new Error('合成保存失败'));
    await saving;
    expect(w.fixedForm.assignees).toEqual([{ studentNo: 'demo1', name: '合成人员' }]);
    expect(w.editorOpen.value).toBe(true);
    const discarded = leave();
    w.unsaved.discard();
    expect(await discarded).toBe(true);
    controls.post.mockResolvedValueOnce({ id: 1 });
    await w.saveFixed();
    expect(w.editorOpen.value).toBe(false);
    expect(await leave()).toBe(true);
    wrapper.unmount();
  });
  it("shows a retryable load error instead of the configured-empty board", async () => {
    apiGet.mockRejectedValue(new Error("排班数据加载失败"));
    const wrapper = mount(SchedulePage, {
      global: { stubs: { Teleport: true } },
    });
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain("排班数据加载失败");
    expect(wrapper.text()).not.toContain("请先在系统设置中添加值班时间段");

    apiGet.mockResolvedValue([]);
    await wrapper.get('[data-action="retry-schedule"]').trigger("click");
    await flushPromises();
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    wrapper.unmount();
  });
});

vi.mock("../../shared/composables/useServiceHealth", () => ({ useServiceHealth: () => ({ online: true }) }));

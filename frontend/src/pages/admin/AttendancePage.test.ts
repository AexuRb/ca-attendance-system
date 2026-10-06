// @vitest-environment jsdom
import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import AttendancePage from "./AttendancePage.vue";

const apiGet = vi.fn();
const apiPut = vi.fn();
const apiPost = vi.fn();
const apiDelete = vi.fn();
const notify = vi.fn();
const routePush = vi.fn();
const routeReplace = vi.fn();

vi.mock("../../shared/api", () => ({
  get: (...args: unknown[]) => apiGet(...args),
  post: (...args: unknown[]) => apiPost(...args),
  put: (...args: unknown[]) => apiPut(...args),
  del: (...args: unknown[]) => apiDelete(...args),
}));

vi.mock("../../shared/composables/useToast", () => ({
  notify: (...args: unknown[]) => notify(...args),
}));

vi.mock("vue-router", () => ({
  RouterLink: { template: "<a><slot /></a>" },
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push: routePush, replace: routeReplace }),
  onBeforeRouteLeave: vi.fn(),
}));

vi.mock("../../app/session", () => ({
  useSession: () => ({ user: { value: { role: "ADMIN" } }, state: { access: { kioskAvailable: true } }, logout: vi.fn() }),
}));

afterEach(() => {
  apiGet.mockReset();
  apiPut.mockReset();
  apiPost.mockReset();
  apiDelete.mockReset();
  notify.mockReset();
  routePush.mockReset();
  routeReplace.mockReset();
  document.body.innerHTML = "";
});

describe("AttendancePage manual editing", () => {
  it("keeps a successful empty result range separate from drafts, failures and retry conditions", async () => {
    apiGet.mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 20 });
    const wrapper = mount(AttendancePage, { attachTo: document.body });
    await flushPromises();
    await wrapper.get('[name="attendanceFrom"]').setValue('2026-08-01');
    await wrapper.get('[name="attendanceTo"]').setValue('2026-08-31');
    await wrapper.get('[name="attendanceKeyword"]').setValue('空结果成员');
    await wrapper.get('[name="attendanceStatus"]').setValue('INVALID');
    await wrapper.get('form.daily-filter').trigger('submit');
    await flushPromises();
    const range = wrapper.get('.attendance-results-summary__range').text();
    expect(range).toContain('2026-08-01 — 2026-08-31');
    expect(range).toContain('空结果成员');
    expect(wrapper.get('.attendance-results-summary__title').text()).toContain('0 条');
    expect(wrapper.find('.pagination').exists()).toBe(false);
    const calls = apiGet.mock.calls.length;
    await wrapper.get('[data-action="adjust-attendance-filters"]').trigger('click');
    expect(document.activeElement).toBe(wrapper.get('[name="attendanceKeyword"]').element);
    expect(apiGet).toHaveBeenCalledTimes(calls);
    await wrapper.get('[name="attendanceKeyword"]').setValue('未提交草稿');
    expect(wrapper.get('.attendance-results-summary__range').text()).toBe(range);
    expect(wrapper.get('.attendance-query-status').text()).toContain('筛选已修改');
    apiGet.mockRejectedValueOnce(new Error('空结果后的失败查询'));
    await wrapper.get('form.daily-filter').trigger('submit');
    await flushPromises();
    expect(wrapper.get('.attendance-results-summary__range').text()).toBe(range);
    expect(wrapper.get('.attendance-query-status').text()).toContain('查询未成功');
    expect(wrapper.find('[data-action="adjust-attendance-filters"]').exists()).toBe(false);
    await wrapper.get('[name="attendanceKeyword"]').setValue('失败后的草稿');
    await wrapper.get('[data-action="retry-attendance"]').trigger('click');
    await flushPromises();
    const query = new URLSearchParams(String(apiGet.mock.lastCall?.[0]).split('?')[1]);
    expect(query.get('studentNo')).toBe('未提交草稿');
    expect(wrapper.get('.attendance-results-summary__range').text()).toContain('未提交草稿');
    expect(wrapper.get('.attendance-results-summary__range').text()).not.toContain('失败后的草稿');
    wrapper.unmount();
  });

  it("does not show a successful zero-result summary before the first request succeeds", async () => {
    let complete!: (value: { items: []; total: number; page: number; pageSize: number }) => void;
    apiGet.mockReturnValueOnce(new Promise(resolve => { complete = resolve; }));
    const wrapper = mount(AttendancePage);
    await flushPromises();
    expect(wrapper.find('.attendance-results-summary').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('没有符合条件的记录');
    complete({ items: [], total: 0, page: 1, pageSize: 20 });
    await flushPromises();
    expect(wrapper.get('.attendance-results-summary__title').text()).toContain('0 条');
    expect(wrapper.text()).toContain('没有符合条件的记录');
    wrapper.unmount();
  });

  it("keeps the first failed request distinct from a successful empty query", async () => {
    apiGet.mockRejectedValue(new Error('首次读取失败'));
    const wrapper = mount(AttendancePage);
    await flushPromises();
    expect(wrapper.find('.attendance-results-summary').exists()).toBe(false);
    expect(wrapper.text()).toContain('记录暂时无法加载');
    expect(wrapper.text()).not.toContain('没有符合条件的记录');
    expect(wrapper.find('[data-action="adjust-attendance-filters"]').exists()).toBe(false);
    wrapper.unmount();
  });

  function mockRecords() {
    apiGet.mockImplementation((url: string) => {
      if (!url.startsWith("/api/attendance/page?")) return Promise.resolve([]);
      const query = new URLSearchParams(url.split("?")[1]);
      return Promise.resolve({ items: [{
        id: 9, userId: 4, userRole: "MEMBER", name: "测试成员", studentNo: "20260009",
        dutyDate: "2026-09-28", checkInTime: "2026-09-28T09:00:00", checkOutTime: "2026-09-28T10:40:00",
        checkInStatus: "APPROVED", checkOutStatus: "APPROVED", effectiveStatus: "VALID",
        durationMinutes: 100, validHours: 2,
      }], total: 40, page: Number(query.get("page") || 1), pageSize: 20 });
    });
  }

  it("shows backend credited hours separately from effective minutes", async () => {
    mockRecords();
    const wrapper = mount(AttendancePage);
    await flushPromises();
    expect(wrapper.get(".daily-outcome strong").text()).toBe("2 小时");
    expect(wrapper.get(".daily-outcome small").text()).toBe("有效分钟 100");
    wrapper.unmount();
  });

  it("opens a clean editor and submits an unsigned-out supplement as not submitted", async () => {
    mockRecords();
    apiPost.mockResolvedValue({});
    const wrapper = mount(AttendancePage);
    await flushPromises();
    const create = wrapper.findAll("button").find(button => button.text().includes("补录记录"))!;
    await create.trigger("click");
    await flushPromises();
    const editor = wrapper.findAllComponents({ name: "ModalDialog" })[0];
    editor.vm.$emit("close");
    await flushPromises();
    expect(editor.props("open")).toBe(false);
    expect(wrapper.findAllComponents({ name: "ConfirmDialog" })[0].props("open")).toBe(false);
    await create.trigger("click");
    await flushPromises();
    wrapper.findComponent({ name: "AccountPicker" }).vm.$emit("update:modelValue", {
      id: 4, studentNo: "20260009", name: "测试成员", role: "MEMBER", department: "技术部", enabled: true,
    });
    const reason = document.body.querySelector<HTMLTextAreaElement>('[name="attendanceReason"]')!;
    reason.value = "补录签到";
    reason.dispatchEvent(new Event("input", { bubbles: true }));
    await flushPromises();
    document.body.querySelector<HTMLButtonElement>(".mw-modal-foot .button.primary")!.click();
    await flushPromises();
    expect(apiPost).toHaveBeenCalledWith("/api/attendance/manual", expect.objectContaining({
      studentNo: "20260009", checkOutTime: null, checkOutStatus: "NOT_SUBMITTED",
    }));
    wrapper.unmount();
  });

  it("paginates the applied results without submitting draft filters", async () => {
    mockRecords();
    const wrapper = mount(AttendancePage);
    await flushPromises();
    await wrapper.get('[name="attendanceKeyword"]').setValue("尚未查询的成员");
    await wrapper.findAll("button").find(button => button.text().includes("下一页"))!.trigger("click");
    await flushPromises();
    const request = apiGet.mock.calls.at(-1)![0] as string;
    expect(request).toContain("page=2");
    expect(request).not.toContain("studentNo=");
    expect(wrapper.get(".attendance-query-status").text()).toContain("查询后生效");
    wrapper.unmount();
  });

  it("keeps successful result context on failure and retries the failed snapshot", async () => {
    mockRecords();
    const wrapper = mount(AttendancePage);
    await flushPromises();
    const previousSummary = wrapper.get(".attendance-results-summary__range").text();
    apiGet.mockRejectedValueOnce(new Error("测试查询失败"));
    await wrapper.get('[name="attendanceFrom"]').setValue("2026-01-01");
    await wrapper.get('[name="attendanceKeyword"]').setValue("已提交成员");
    await wrapper.get("form.daily-filter").trigger("submit");
    await flushPromises();
    expect(wrapper.get(".attendance-results-summary__range").text()).toBe(previousSummary);
    expect(wrapper.get('[role="alert"]').text()).toContain("保留上次成功查询");
    expect(wrapper.get('.attendance-query-status').text()).toContain('查询未成功');
    expect(wrapper.get('.attendance-query-status').text()).not.toContain('筛选已修改');
    expect(routePush).not.toHaveBeenCalled();
    await wrapper.get('[name="attendanceKeyword"]').setValue("新的草稿");
    await wrapper.get('[data-action="retry-attendance"]').trigger("click");
    await flushPromises();
    const query = new URLSearchParams((apiGet.mock.calls.at(-1)![0] as string).split("?")[1]);
    expect(query.get("studentNo")).toBe("已提交成员");
    expect(routePush).toHaveBeenCalledWith({ query: expect.objectContaining({ from: "2026-01-01" }) });
    expect(JSON.stringify(routePush.mock.calls)).not.toContain("已提交成员");
    wrapper.unmount();
  });

  it("protects edited input until discard is explicitly confirmed", async () => {
    mockRecords();
    const wrapper = mount(AttendancePage);
    await flushPromises();
    await wrapper.get('button[aria-label="编辑"]').trigger("click");
    await flushPromises();
    const reason = document.body.querySelector<HTMLTextAreaElement>('[name="attendanceReason"]')!;
    reason.value = "保留这段说明";
    reason.dispatchEvent(new Event("input", { bubbles: true }));
    await flushPromises();
    const editor = wrapper.findAllComponents({ name: "ModalDialog" })[0];
    editor.vm.$emit("close");
    await flushPromises();
    const confirm = wrapper.findAllComponents({ name: "ConfirmDialog" })[0];
    expect(confirm.props("open")).toBe(true);
    confirm.vm.$emit("cancel");
    await flushPromises();
    expect(reason.value).toBe("保留这段说明");
    expect(editor.props("open")).toBe(true);
    editor.vm.$emit("close");
    await flushPromises();
    confirm.vm.$emit("confirm", "");
    await flushPromises();
    expect(editor.props("open")).toBe(false);
    wrapper.unmount();
  });

  it("only reevaluates the historical eligibility snapshot when explicitly selected", async () => {
    apiGet.mockImplementation((url: string) => {
      if (url.startsWith("/api/attendance/page?")) {
        return Promise.resolve({
          items: [{
            id: 9,
            userId: 4,
            userRole: "MEMBER",
            studentNo: "20260009",
            name: "测试成员",
            dutyDate: "2026-08-20",
            dutyDay: false,
            withinDutyPeriod: false,
            requireDutyDay: true,
            requireDutyPeriod: true,
            checkInTime: "2026-08-20T14:00:00",
            checkOutTime: "2026-08-20T16:00:00",
            checkInStatus: "APPROVED",
            checkOutStatus: "APPROVED",
            durationMinutes: 0,
            effectiveStatus: "INVALID",
          }],
          total: 1,
          page: 1,
          pageSize: 20,
        });
      }
      if (url === "/api/attendance/manual-candidates") {
        return Promise.resolve([]);
      }
      return Promise.resolve([]);
    });
    apiPut.mockResolvedValue({});

    const wrapper = mount(AttendancePage);
    await flushPromises();
    await wrapper.get('button[aria-label="编辑"]').trigger("click");
    await flushPromises();

    const checkbox = document.body.querySelector<HTMLInputElement>(
      '.attendance-reevaluate input[type="checkbox"]',
    );
    const reason = document.body.querySelector<HTMLTextAreaElement>("textarea");
    expect(checkbox?.checked).toBe(false);
    checkbox?.click();
    if (reason) {
      reason.value = "按新规则复核历史记录";
      reason.dispatchEvent(new Event("input", { bubbles: true }));
    }
    await flushPromises();

    const saveButton = document.body.querySelector<HTMLButtonElement>(
      ".mw-modal-foot .button.primary",
    );
    expect(saveButton?.disabled).toBe(false);
    saveButton?.click();
    await flushPromises();

    expect(apiPut).toHaveBeenCalledWith(
      "/api/attendance/9/manual",
      expect.objectContaining({ recomputeSnapshot: true }),
    );
    wrapper.unmount();
  });

  it("prevents duplicate saves while the first request is pending", async () => {
    let resolveSave!: (value: unknown) => void;
    apiPut.mockReturnValue(
      new Promise((resolve) => {
        resolveSave = resolve;
      }),
    );
    apiGet.mockImplementation((url: string) => {
      if (url.startsWith("/api/attendance/page?")) {
        return Promise.resolve({
          items: [{
            id: 9,
            userId: 4,
            userRole: "MEMBER",
            studentNo: "20260009",
            name: "测试成员",
            dutyDate: "2026-08-20",
            checkInTime: "2026-08-20T14:00:00",
            checkOutTime: "2026-08-20T16:00:00",
            checkInStatus: "APPROVED",
            checkOutStatus: "APPROVED",
            effectiveStatus: "VALID",
          }],
          total: 1,
          page: 1,
          pageSize: 20,
        });
      }
      return Promise.resolve([]);
    });

    const wrapper = mount(AttendancePage);
    await flushPromises();
    await wrapper.get('button[aria-label="编辑"]').trigger("click");
    const reason = document.body.querySelector<HTMLTextAreaElement>("textarea");
    if (reason) {
      reason.value = "修正测试记录";
      reason.dispatchEvent(new Event("input", { bubbles: true }));
    }
    await flushPromises();
    const saveButton = document.body.querySelector<HTMLButtonElement>(
      ".mw-modal-foot .button.primary",
    );
    saveButton?.click();
    saveButton?.click();
    await flushPromises();

    expect(apiPut).toHaveBeenCalledTimes(1);
    resolveSave({});
    await flushPromises();
    wrapper.unmount();
  });

  it("rejects an inverted date range without loading records", async () => {
    apiGet.mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 20 });
    const wrapper = mount(AttendancePage);
    await flushPromises();
    apiGet.mockClear();

    const dates = wrapper.findAll('input[type="date"]');
    await dates[0].setValue("2026-08-22");
    await dates[1].setValue("2026-08-21");
    await wrapper.get("form.daily-filter").trigger("submit");

    expect(wrapper.get('[role="alert"]').text()).toContain(
      "开始日期不能晚于结束日期",
    );
    expect(apiGet).not.toHaveBeenCalled();
    wrapper.unmount();
  });
});

vi.mock("../../shared/composables/useServiceHealth", () => ({ useServiceHealth: () => ({ online: true }) }));

// @vitest-environment jsdom
import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import AttendancePage from "./AttendancePage.vue";

const apiGet = vi.fn();
const apiPut = vi.fn();
const apiPost = vi.fn();
const apiDelete = vi.fn();
const notify = vi.fn();

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
  useRoute: () => ({ query: {} }),
}));

vi.mock("../../app/session", () => ({
  useSession: () => ({ user: { value: { role: "ADMIN" } } }),
}));

afterEach(() => {
  apiGet.mockReset();
  apiPut.mockReset();
  apiPost.mockReset();
  apiDelete.mockReset();
  notify.mockReset();
  document.body.innerHTML = "";
});

describe("AttendancePage manual editing", () => {
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
      ".modal-footer .button.primary",
    );
    expect(saveButton?.disabled).toBe(false);
    saveButton?.click();
    await flushPromises();

    expect(apiPut).toHaveBeenCalledWith(
      "/api/attendance/9/manual",
      expect.objectContaining({ recomputeSnapshot: true }),
    );
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
      ".modal-footer .button.primary",
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
    await wrapper.get("form.filter-bar").trigger("submit");

    expect(wrapper.get('[role="alert"]').text()).toContain(
      "开始日期不能晚于结束日期",
    );
    expect(apiGet).not.toHaveBeenCalled();
    wrapper.unmount();
  });
});

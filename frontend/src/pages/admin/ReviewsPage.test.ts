// @vitest-environment jsdom
import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import ReviewsPage from "./ReviewsPage.vue";

const mocks = vi.hoisted(() => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  notify: vi.fn(),
}));

vi.mock("../../shared/api", () => ({
  get: (...args: unknown[]) => mocks.apiGet(...args),
  post: (...args: unknown[]) => mocks.apiPost(...args),
}));

vi.mock("../../shared/composables/useToast", () => ({ notify: mocks.notify }));

const visibleRecord = {
  id: 1,
  userId: 2,
  studentNo: "9900000001",
  name: "审核测试成员",
  dutyDate: "2026-08-12",
  checkInTime: "2026-08-12 14:00:00",
  checkOutTime: "2026-08-12 16:00:00",
  checkInStatus: "PENDING",
  checkOutStatus: "APPROVED",
};

beforeEach(() => {
  mocks.apiGet
    .mockResolvedValueOnce({
      items: [visibleRecord],
      recordCount: 601,
      itemCount: 602,
      truncated: true,
    })
    .mockResolvedValue({
      items: [],
      recordCount: 0,
      itemCount: 0,
      truncated: false,
    });
  mocks.apiPost.mockResolvedValue({
    matched: 601,
    reviewed: 602,
    skipped: 0,
    errors: [],
  });
});

afterEach(() => {
  mocks.apiGet.mockReset();
  mocks.apiPost.mockReset();
  mocks.notify.mockReset();
  document.body.innerHTML = "";
});

describe("ReviewsPage bulk approval", () => {
  it("shows server totals and approves the complete pending queue", async () => {
    const wrapper = mount(ReviewsPage, {
      global: { stubs: { Teleport: true } },
    });
    await flushPromises();

    expect(wrapper.text()).toContain("602 项待审核");
    expect(wrapper.text()).toContain("共 601 条记录");

    await wrapper.get(".page-actions .button").trigger("click");
    await wrapper.vm.$nextTick();
    expect(wrapper.get(".confirm-copy").text()).toContain("全部 602 项待审核");
    expect(wrapper.get(".confirm-copy").text()).toContain("601 条记录");

    await wrapper.get(".modal-footer .button.primary").trigger("click");
    await flushPromises();

    expect(mocks.apiPost).toHaveBeenCalledWith(
      "/api/attendance/reviews/bulk",
      { ids: [], part: "ALL", scope: "ALL_PENDING" },
    );
    expect(mocks.notify).toHaveBeenCalledWith(
      "已处理 601 条记录，通过 602 项审核",
      "success",
    );
    expect(mocks.apiGet).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain("0 项待审核");
    expect(wrapper.text()).toContain("待审核已清空");
  });

  it("prevents duplicate approval of the same review item", async () => {
    let resolveReview!: (value: unknown) => void;
    mocks.apiPost.mockReturnValue(
      new Promise((resolve) => {
        resolveReview = resolve;
      }),
    );
    const wrapper = mount(ReviewsPage, {
      global: { stubs: { Teleport: true } },
    });
    await flushPromises();

    const approve = wrapper.get(".review-approve-check-in");
    await approve.trigger("click");
    await approve.trigger("click");
    expect(mocks.apiPost).toHaveBeenCalledTimes(1);
    expect(mocks.apiPost).toHaveBeenCalledWith(
      "/api/attendance/1/review",
      { part: "CHECK_IN", action: "APPROVE", reason: "" },
    );
    expect(approve.attributes("disabled")).toBeDefined();

    resolveReview({});
    await flushPromises();
    wrapper.unmount();
  });

  it("keeps the bulk confirmation open when approval fails", async () => {
    mocks.apiPost.mockRejectedValue(new Error("批量审核失败"));
    const wrapper = mount(ReviewsPage, {
      global: { stubs: { Teleport: true } },
    });
    await flushPromises();

    await wrapper.get(".page-actions .button").trigger("click");
    await wrapper.get(".modal-footer .button.primary").trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("通过全部待审核记录");
    expect(mocks.apiGet).toHaveBeenCalledTimes(1);
    expect(mocks.notify).toHaveBeenCalledWith("批量审核失败", "danger");
    wrapper.unmount();
  });
});

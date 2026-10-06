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
  it("selects only visible IDs and counts pending parts rather than records", async () => {
    mocks.apiGet.mockReset();
    mocks.apiGet.mockResolvedValueOnce({
      items: [{ ...visibleRecord, checkOutStatus: "PENDING" }, { ...visibleRecord, id: 2 }],
      recordCount: 601, itemCount: 602, truncated: true,
    }).mockResolvedValue({ items: [{ ...visibleRecord, id: 2 }], recordCount: 600, itemCount: 600, truncated: true });
    const wrapper = mount(ReviewsPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();
    await wrapper.findAll(".review-select-record")[0].setValue(true);
    expect(wrapper.get(".review-selection-count").text()).toContain("已选 1 条 · 2 项");
    expect((wrapper.get(".review-select-visible input").element as HTMLInputElement).indeterminate).toBe(true);
    await wrapper.get(".review-summary-actions .button.primary").trigger("click");
    expect(wrapper.get(".confirm-copy").text()).toContain("其他记录不会处理");
    await wrapper.get(".mw-modal-foot .button.primary").trigger("click");
    await flushPromises();
    expect(mocks.apiPost).toHaveBeenCalledWith("/api/attendance/reviews/bulk", { ids: [1], part: "ALL", scope: "SELECTED" });
    expect(wrapper.get(".review-selection-count").text()).toContain("已选 0 条");
    wrapper.unmount();
  });

  it("keeps selection on a failed request and prevents writes after refresh failure", async () => {
    const wrapper = mount(ReviewsPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();
    await wrapper.get(".review-select-visible input").setValue(true);
    mocks.apiPost.mockRejectedValueOnce(new Error("审核失败"));
    await wrapper.get(".review-summary-actions .button.primary").trigger("click");
    await wrapper.get(".mw-modal-foot .button.primary").trigger("click");
    await flushPromises();
    expect(wrapper.get(".review-selection-count").text()).toContain("已选 1 条");
    expect(wrapper.get(".confirm-copy").text()).toContain("所选 1 条");
    await wrapper.get(".mw-modal-foot .button.secondary").trigger("click");
    mocks.apiGet.mockRejectedValueOnce(new Error("刷新失败"));
    await wrapper.get('button[aria-label="刷新"]').trigger("click");
    await flushPromises();
    expect(wrapper.get(".review-summary-actions .button.primary").attributes("disabled")).toBeDefined();
    expect(wrapper.get(".review-select-record").attributes("disabled")).toBeDefined();
    expect(wrapper.get('[role="alert"]').text()).toContain("暂不可审核");
    wrapper.unmount();
  });

  it("preserves rejection input after failure and asks before discarding", async () => {
    mocks.apiPost.mockRejectedValueOnce(new Error("驳回失败"));
    const wrapper = mount(ReviewsPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();
    await wrapper.get(".review-approve-check-in .review-state-action__reject").trigger("click");
    await wrapper.get('[name="reviewRejectReason"]').setValue("时间需要核实");
    await wrapper.get(".mw-modal-foot .button.danger").trigger("click");
    await flushPromises();
    expect((wrapper.get('[name="reviewRejectReason"]').element as HTMLTextAreaElement).value).toBe("时间需要核实");
    await wrapper.get(".mw-modal-foot .button.secondary").trigger("click");
    const confirm = wrapper.findAllComponents({ name: "ConfirmDialog" }).find(component => component.props("title") === "放弃驳回原因")!;
    expect(confirm.props("open")).toBe(true);
    confirm.vm.$emit("cancel");
    await flushPromises();
    expect((wrapper.get('[name="reviewRejectReason"]').element as HTMLTextAreaElement).value).toBe("时间需要核实");
    wrapper.unmount();
  });

  it("moves focus to the next record after the current record leaves the queue", async () => {
    mocks.apiGet.mockReset();
    const next = { ...visibleRecord, id: 2, name: "下一位成员" };
    mocks.apiGet.mockResolvedValueOnce({ items: [visibleRecord, next], recordCount: 2, itemCount: 2, truncated: false })
      .mockResolvedValue({ items: [next], recordCount: 1, itemCount: 1, truncated: false });
    const wrapper = mount(ReviewsPage, { attachTo: document.body, global: { stubs: { Teleport: true } } });
    await flushPromises();
    await wrapper.findAll(".review-state-action__approve")[0].trigger("click");
    await flushPromises();
    expect(document.activeElement?.getAttribute("data-review-id")).toBe("2");
    wrapper.unmount();
  });

  it("focuses retry and locks old rows when review succeeds but queue refresh fails", async () => {
    const wrapper = mount(ReviewsPage, { attachTo: document.body, global: { stubs: { Teleport: true } } });
    await flushPromises();
    mocks.apiGet.mockRejectedValueOnce(new Error("刷新失败"));
    await wrapper.get(".review-state-action__approve").trigger("click");
    await flushPromises();
    expect(document.activeElement?.getAttribute("data-action")).toBe("retry-reviews");
    expect(wrapper.get(".review-state-action__approve").attributes("disabled")).toBeDefined();
    expect(mocks.apiPost).toHaveBeenCalledTimes(1);
    wrapper.unmount();
  });

  it("shows server totals and approves the complete pending queue", async () => {
    const wrapper = mount(ReviewsPage, {
      global: { stubs: { Teleport: true } },
    });
    await flushPromises();

    expect(wrapper.text()).toContain("602 项待审核");
    expect(wrapper.text()).toContain("共 601 条记录");

    await wrapper.get(".daily-review-summary .button.secondary").trigger("click");
    await wrapper.vm.$nextTick();
    expect(wrapper.get(".confirm-copy").text()).toContain("全部 602 项待审核");
    expect(wrapper.get(".confirm-copy").text()).toContain("601 条记录");

    await wrapper.get(".mw-modal-foot .button.primary").trigger("click");
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
    wrapper.unmount();
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

    const approve = wrapper.get(".review-approve-check-in .review-state-action__approve");
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

    await wrapper.get(".daily-review-summary .button.secondary").trigger("click");
    await wrapper.get(".mw-modal-foot .button.primary").trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("通过全部待审核记录");
    expect(mocks.apiGet).toHaveBeenCalledTimes(1);
    expect(mocks.notify).toHaveBeenCalledWith("批量审核失败", "danger");
    wrapper.unmount();
  });
});

vi.mock("vue-router", () => ({ RouterLink: { template: "<a><slot /></a>" }, useRouter: () => ({ push: vi.fn(), replace: vi.fn() }), onBeforeRouteLeave: vi.fn() }));

vi.mock("../../shared/composables/useServiceHealth", () => ({ useServiceHealth: () => ({ online: true }) }));

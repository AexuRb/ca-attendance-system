// @vitest-environment jsdom
import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import ReviewsPage from "./ReviewsPage.vue";
import { defineComponent } from "vue";
import { useAttendanceReviewWorkspace } from "../../features/attendance/useAttendanceReviewWorkspace";

// Full-queue behavior and API scope remain in the existing workspace.
function mountReviewWorkspace() {
  let workspace!: ReturnType<typeof useAttendanceReviewWorkspace>;
  const wrapper = mount(defineComponent({ setup() { workspace = useAttendanceReviewWorkspace(); return () => null; } }));
  return { wrapper, workspace };
}

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

describe("ReviewsPage individual review and retained workspace behavior", () => {
  it("selects only visible IDs and counts pending parts rather than records", async () => {
    mocks.apiGet.mockReset();
    mocks.apiGet.mockResolvedValueOnce({ items: [{ ...visibleRecord, checkOutStatus: "PENDING" }, { ...visibleRecord, id: 2 }], recordCount: 601, itemCount: 602, truncated: true })
      .mockResolvedValue({ items: [{ ...visibleRecord, id: 2 }], recordCount: 600, itemCount: 600, truncated: true });
    const { wrapper, workspace } = mountReviewWorkspace(); await flushPromises();
    workspace.toggleRecord(1); expect(workspace.selectedItemCount.value).toBe(2); expect(workspace.allVisibleSelected.value).toBe(false);
    workspace.openBulk(true); expect(workspace.bulkRecordCount.value).toBe(1); expect(workspace.bulkItemCount.value).toBe(2);
    await workspace.bulkApprove(); await flushPromises();
    expect(mocks.apiPost).toHaveBeenCalledWith("/api/attendance/reviews/bulk", { ids: [1], part: "ALL", scope: "SELECTED" });
    expect(workspace.selectedIds.value).toEqual([]); wrapper.unmount();
  });

  it("keeps selection on a failed request and prevents writes after refresh failure", async () => {
    const { wrapper, workspace } = mountReviewWorkspace(); await flushPromises();
    workspace.toggleVisible(); mocks.apiPost.mockRejectedValueOnce(new Error("审核失败")); workspace.openBulk(true); await workspace.bulkApprove();
    expect(workspace.selectedIds.value).toEqual([1]); expect(workspace.bulkConfirmOpen.value).toBe(true);
    workspace.bulkConfirmOpen.value = false; mocks.apiGet.mockRejectedValueOnce(new Error("刷新失败")); await workspace.load();
    expect(workspace.interactionLocked.value).toBe(true); expect(workspace.records.value).toHaveLength(1);
    expect(workspace.loadError.value).toContain("刷新失败");
    await workspace.review(1, "CHECK_IN", "APPROVE"); expect(mocks.apiPost).toHaveBeenCalledTimes(1); wrapper.unmount();
  });

  it("preserves rejection input after failure and asks before discarding", async () => {
    mocks.apiPost.mockRejectedValueOnce(new Error("驳回失败"));
    const wrapper = mount(ReviewsPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();
    await wrapper.get(".review-approve-check-in .review-state-action__reject").trigger("click");
    await wrapper.get('[name="reviewRejectReason"]').setValue("时间需要核实");
    await wrapper.get(".review-reject-actions .button.danger").trigger("click");
    await flushPromises();
    expect((wrapper.get('[name="reviewRejectReason"]').element as HTMLTextAreaElement).value).toBe("时间需要核实");
    await wrapper.get(".review-reject-actions .button.secondary").trigger("click");
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
    const { wrapper, workspace } = mountReviewWorkspace(); await flushPromises();
    expect(workspace.pendingItemCount.value).toBe(602); expect(workspace.pendingRecordCount.value).toBe(601);
    workspace.openBulk(false); expect(workspace.bulkItemCount.value).toBe(602); expect(workspace.bulkRecordCount.value).toBe(601);
    await workspace.bulkApprove(); await flushPromises();
    expect(mocks.apiPost).toHaveBeenCalledWith("/api/attendance/reviews/bulk", { ids: [], part: "ALL", scope: "ALL_PENDING" });
    expect(mocks.notify).toHaveBeenCalledWith("已处理 601 条记录，通过 602 项审核", "success");
    expect(mocks.apiGet).toHaveBeenCalledTimes(2); expect(workspace.pendingItemCount.value).toBe(0); expect(workspace.records.value).toEqual([]); wrapper.unmount();
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

  it("exposes direct actions and guards changing the rejection target", async () => {
    mocks.apiGet.mockReset();
    mocks.apiGet.mockResolvedValue({ items: [visibleRecord, { ...visibleRecord, id: 2, name: "第二位" }], recordCount: 2, itemCount: 2, truncated: false });
    const wrapper = mount(ReviewsPage, { global: { stubs: { Teleport: true } } });
    await flushPromises();
    expect(wrapper.findAll(".review-state-action__approve")).toHaveLength(2);
    expect(wrapper.find(".review-detail,.review-process").exists()).toBe(false);
    await wrapper.get(".review-state-action__reject").trigger("click");
    await wrapper.get('[name="reviewRejectReason"]').setValue("现场找不到该成员");
    await wrapper.findAll(".review-state-action__reject")[1].trigger("click");
    const confirmation = wrapper.findAllComponents({ name: "ConfirmDialog" }).find(c => c.props("title") === "放弃驳回原因")!;
    expect(confirmation.props("open")).toBe(true);
    expect(wrapper.text()).toContain("驳回审核测试成员的签到");
    confirmation.vm.$emit("cancel");await flushPromises();
    expect((wrapper.get('[name="reviewRejectReason"]').element as HTMLTextAreaElement).value).toBe("现场找不到该成员");
    await wrapper.findAll(".review-state-action__reject")[1].trigger("click");confirmation.vm.$emit("confirm");await flushPromises();
    expect(wrapper.text()).toContain("驳回第二位的签到");
    expect((wrapper.get('[name="reviewRejectReason"]').element as HTMLTextAreaElement).value).toBe("");
    expect(mocks.apiPost).not.toHaveBeenCalled();wrapper.unmount();
  });

  it("keeps approval draft protection after removing the header refresh button", async () => {
    const wrapper = mount(ReviewsPage, { global: { stubs: { Teleport: true } } }); await flushPromises();
    expect(wrapper.find('button[aria-label="刷新"]').exists()).toBe(false);
    await wrapper.get(".review-state-action__reject").trigger("click");
    await wrapper.get('[name="reviewRejectReason"]').setValue("需现场核对");
    const discard = wrapper.findAllComponents({ name: "ConfirmDialog" }).find(c => c.props("title") === "放弃驳回原因")!;
    await wrapper.get(".review-state-action__approve").trigger("click");
    expect(discard.props("open")).toBe(true);
    expect(mocks.apiPost).not.toHaveBeenCalled();
    discard.vm.$emit("cancel"); await flushPromises();
    expect((wrapper.get('[name="reviewRejectReason"]').element as HTMLTextAreaElement).value).toBe("需现场核对");
    await wrapper.get(".review-state-action__approve").trigger("click");
    discard.vm.$emit("confirm"); await flushPromises();
    expect(mocks.apiPost).toHaveBeenCalledWith("/api/attendance/1/review", { part: "CHECK_IN", action: "APPROVE", reason: "" }); wrapper.unmount();
  });

  it("allows retry after an initial load error and unlocks the recovered queue", async () => {
    mocks.apiGet.mockReset();
    mocks.apiGet.mockRejectedValueOnce(new Error('读取失败')).mockResolvedValue({ items: [visibleRecord], recordCount: 1, itemCount: 1, truncated: false });
    const wrapper = mount(ReviewsPage, { global: { stubs: { Teleport: true } } });await flushPromises();
    expect(wrapper.find('.review-record').exists()).toBe(false);
    await wrapper.get('[data-action="retry-reviews"]').trigger('click');await flushPromises();
    expect(mocks.apiGet).toHaveBeenCalledTimes(2);expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect(wrapper.get('.review-state-action__approve').attributes('disabled')).toBeUndefined();
    expect(wrapper.find('.review-record').exists()).toBe(true); wrapper.unmount();
  });

  it("returns focus to the rejection action when an empty dialog reason is cancelled", async () => {
    const wrapper = mount(ReviewsPage, { attachTo: document.body, global: { stubs: { Teleport: true } } });
    await flushPromises();await wrapper.get('.review-state-action__reject').trigger('click');
    await wrapper.get('[name="reviewRejectReason"]').trigger('keydown', { key: 'Escape' });await flushPromises();
    expect(wrapper.find('[name="reviewRejectReason"]').exists()).toBe(false);
    expect(document.activeElement).toBe(wrapper.get('.review-state-action__reject').element);
    expect(mocks.apiPost).not.toHaveBeenCalled();wrapper.unmount();
  });

  it("keeps the bulk confirmation open when approval fails", async () => {
    mocks.apiPost.mockRejectedValue(new Error("批量审核失败"));
    const { wrapper, workspace } = mountReviewWorkspace(); await flushPromises();
    workspace.openBulk(false); await workspace.bulkApprove(); await flushPromises();
    expect(workspace.bulkConfirmOpen.value).toBe(true); expect(mocks.apiGet).toHaveBeenCalledTimes(1);
    expect(mocks.notify).toHaveBeenCalledWith("批量审核失败", "danger"); wrapper.unmount();
  });

  it("hides page totals without selection controls and submits only the chosen checkout", async () => {
    mocks.apiGet.mockReset(); mocks.apiGet.mockResolvedValue({ items: [{ ...visibleRecord, checkInStatus: "APPROVED", checkOutStatus: "PENDING" }], recordCount: 601, itemCount: 602, truncated: true });
    const wrapper = mount(ReviewsPage, { global: { stubs: { Teleport: true } } }); await flushPromises();
    expect(wrapper.find(".review-summary-metrics").exists()).toBe(false);
    expect(wrapper.text()).toContain("列表仅显示最近的待审核记录，全部通过将处理完整待审核范围。");
    expect(wrapper.find('input[type="checkbox"]').exists()).toBe(false); expect(wrapper.find('.daily-review-summary').exists()).toBe(false);
    expect(wrapper.get('.review-approve-all').text()).toContain("全部通过"); expect(wrapper.text()).not.toContain("通过所选");
    expect(wrapper.find('.review-approve-check-in button').exists()).toBe(false);
    await wrapper.get('.review-approve-check-out .review-state-action__approve').trigger('click'); await flushPromises();
    expect(mocks.apiPost).toHaveBeenCalledTimes(1); expect(mocks.apiPost).toHaveBeenCalledWith("/api/attendance/1/review", { part: "CHECK_OUT", action: "APPROVE", reason: "" }); wrapper.unmount();
  });

  it("confirms and approves the complete server queue from the page header", async () => {
    const wrapper = mount(ReviewsPage, { global: { stubs: { Teleport: true } } }); await flushPromises();
    await wrapper.get('.review-approve-all').trigger('click');
    expect(wrapper.get('.confirm-copy').text()).toContain('全部 602 项待审核');
    expect(wrapper.get('.confirm-copy').text()).toContain('601 条记录');
    expect(wrapper.get('.confirm-copy').text()).toContain('包括当前未展示的记录');
    expect(mocks.apiPost).not.toHaveBeenCalled();
    await wrapper.get('.mw-modal-foot .button.primary').trigger('click'); await flushPromises();
    expect(mocks.apiPost).toHaveBeenCalledWith('/api/attendance/reviews/bulk', { ids: [], part: 'ALL', scope: 'ALL_PENDING' });
    expect(wrapper.get('.review-approve-all').attributes('disabled')).toBeDefined();
    expect(wrapper.text()).toContain('待审核已清空'); wrapper.unmount();
  });
  it("guards the draft before opening all-queue confirmation and retains it on cancel", async () => {
    const wrapper = mount(ReviewsPage, { global: { stubs: { Teleport: true } } }); await flushPromises();
    await wrapper.get('.review-state-action__reject').trigger('click');
    await wrapper.get('[name="reviewRejectReason"]').setValue('现场需要核对');
    await wrapper.get('.review-approve-all').trigger('click');
    const dialogs = wrapper.findAllComponents({ name: 'ConfirmDialog' });
    const discard = dialogs.find(c => c.props('title') === '放弃驳回原因')!;
    const bulk = dialogs.find(c => c.props('title') === '通过全部待审核记录')!;
    expect(discard.props('open')).toBe(true); expect(bulk.props('open')).toBe(false);
    discard.vm.$emit('cancel'); await flushPromises();
    expect((wrapper.get('[name="reviewRejectReason"]').element as HTMLTextAreaElement).value).toBe('现场需要核对');
    await wrapper.get('.review-approve-all').trigger('click'); discard.vm.$emit('confirm'); await flushPromises();
    expect(bulk.props('open')).toBe(true); expect(wrapper.find('[name="reviewRejectReason"]').exists()).toBe(false);
    expect(mocks.apiPost).not.toHaveBeenCalled(); wrapper.unmount();
  });
  it("shows partial all-queue failures for individual follow-up", async () => {
    mocks.apiPost.mockResolvedValueOnce({ matched: 601, reviewed: 601, skipped: 1, errors: ['一条记录需要重新核对'] });
    const wrapper = mount(ReviewsPage, { global: { stubs: { Teleport: true } } }); await flushPromises();
    await wrapper.get('.review-approve-all').trigger('click'); await wrapper.get('.mw-modal-foot .button.primary').trigger('click'); await flushPromises();
    expect(wrapper.text()).toContain('部分记录未处理'); expect(wrapper.get('.review-error-list').text()).toContain('一条记录需要重新核对');
    expect(mocks.notify).toHaveBeenCalledWith('已通过 601 项，1 条未处理', 'warning');
    await wrapper.get('.mw-modal-foot .button.primary').trigger('click'); expect(wrapper.find('.review-error-list').exists()).toBe(false); wrapper.unmount();
  });
  it("opens the checkout context directly and submits only its trimmed rejection reason", async () => {
    mocks.apiGet.mockReset();
    mocks.apiGet.mockResolvedValueOnce({ items: [{ ...visibleRecord, checkInStatus: 'APPROVED', checkOutStatus: 'PENDING' }], recordCount: 1, itemCount: 1, truncated: false })
      .mockResolvedValue({ items: [], recordCount: 0, itemCount: 0, truncated: false });
    const wrapper = mount(ReviewsPage, { global: { stubs: { Teleport: true } } });await flushPromises();
    expect(wrapper.find('.review-approve-check-in button').exists()).toBe(false);
    await wrapper.get('.review-approve-check-out .review-state-action__reject').trigger('click');
    expect(wrapper.text()).toContain('驳回审核测试成员的签退');expect(wrapper.get('.review-reject-context').text()).toContain('2026-08-12 · 16:00');
    expect(wrapper.get('.review-reject-actions .button.danger').attributes('disabled')).toBeDefined();
    await wrapper.get('[name="reviewRejectReason"]').setValue('  现场未找到该成员  ');
    await wrapper.get('.review-reject-actions .button.danger').trigger('click');await flushPromises();
    expect(mocks.apiPost).toHaveBeenCalledWith('/api/attendance/1/review', { part: 'CHECK_OUT', action: 'REJECT', reason: '现场未找到该成员' });
    expect(wrapper.find('[name="reviewRejectReason"]').exists()).toBe(false);wrapper.unmount();
  });
  it("locks the rejection dialog against close and duplicate submission while pending", async () => {
    let resolveReview!: (value: unknown) => void;
    mocks.apiPost.mockReturnValue(new Promise(resolve => { resolveReview = resolve; }));
    const wrapper = mount(ReviewsPage, { global: { stubs: { Teleport: true } } });await flushPromises();
    await wrapper.get('.review-state-action__reject').trigger('click');await wrapper.get('[name="reviewRejectReason"]').setValue('现场需要核对');
    await wrapper.get('.review-reject-actions .button.danger').trigger('click');
    const rejection = wrapper.findAllComponents({ name: 'ModalDialog' }).find(c => c.props('title') === '驳回审核测试成员的签到')!;
    rejection.vm.$emit('close');await flushPromises();
    expect(rejection.props('open')).toBe(true);expect(wrapper.get('[name="reviewRejectReason"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('.review-reject-actions .button.secondary').attributes('disabled')).toBeDefined();
    await wrapper.get('.review-reject-actions .button.danger').trigger('click');expect(mocks.apiPost).toHaveBeenCalledTimes(1);
    resolveReview({});await flushPromises();expect(wrapper.find('[name="reviewRejectReason"]').exists()).toBe(false);wrapper.unmount();
  });
});

vi.mock("vue-router", () => ({ RouterLink: { template: "<a><slot /></a>" }, useRouter: () => ({ push: vi.fn(), replace: vi.fn() }), onBeforeRouteLeave: vi.fn() }));

vi.mock("../../shared/composables/useServiceHealth", () => ({ useServiceHealth: () => ({ online: true }) }));

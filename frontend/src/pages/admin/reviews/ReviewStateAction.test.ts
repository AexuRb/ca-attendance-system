// @vitest-environment jsdom
import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import ReviewStateAction from "./ReviewStateAction.vue";

describe("ReviewStateAction", () => {
  it("keeps pending status separate from explicit approval and rejection", async () => {
    const wrapper = mount(ReviewStateAction, {
      props: {
        label: "签到",
        time: "14:16",
        status: "PENDING",
        actionPending: false,
      },
    });

    expect(wrapper.text()).toContain("待审核");
    expect(wrapper.get(".review-state-action__status").text()).toBe("待审核");
    expect(wrapper.get(".review-state-action__approve").attributes("aria-label")).toBe("通过签到 14:16");
    expect(wrapper.get(".review-state-action__reject").attributes("aria-label")).toBe("驳回签到 14:16");

    await wrapper.get(".review-state-action__approve").trigger("click");
    expect(wrapper.emitted("approve")).toHaveLength(1);
    await wrapper.get(".review-state-action__reject").trigger("click");
    expect(wrapper.emitted("reject")).toHaveLength(1);
  });

  it("keeps completed and unsubmitted states read only", () => {
    const approved = mount(ReviewStateAction, {
      props: {
        label: "签到",
        time: "13:58",
        status: "APPROVED",
        actionPending: false,
      },
    });
    const empty = mount(ReviewStateAction, {
      props: {
        label: "签退",
        time: "—",
        status: "NOT_SUBMITTED",
        actionPending: false,
      },
    });

    expect(approved.text()).toContain("已通过");
    expect(approved.find("button").exists()).toBe(false);
    expect(empty.text()).toContain("未提交");
    expect(empty.find("button").exists()).toBe(false);
  });

  it("exposes the processing state without changing dimensions", () => {
    const wrapper = mount(ReviewStateAction, {
      props: {
        label: "签退",
        time: "16:05",
        status: "PENDING",
        actionPending: true,
      },
    });

    expect(wrapper.text()).toContain("处理中");
    expect(wrapper.attributes("aria-busy")).toBe("true");
    expect(wrapper.get(".review-state-action__approve").attributes("disabled")).toBeDefined();
    expect(wrapper.get(".review-state-action__reject").attributes("disabled")).toBeDefined();
  });
});

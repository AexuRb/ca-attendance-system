// @vitest-environment jsdom
import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import ReviewStateAction from "./ReviewStateAction.vue";

describe("ReviewStateAction", () => {
  it("uses a pending status tile as the approval action", async () => {
    const wrapper = mount(ReviewStateAction, {
      props: {
        label: "签到",
        time: "14:16",
        status: "PENDING",
        actionPending: false,
      },
    });

    expect(wrapper.text()).toContain("待审核");
    expect(wrapper.attributes("disabled")).toBeUndefined();
    expect(wrapper.attributes("aria-label")).toContain("点击通过");

    await wrapper.trigger("click");
    expect(wrapper.emitted("approve")).toHaveLength(1);
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
    expect(approved.attributes("disabled")).toBeDefined();
    expect(empty.text()).toContain("未提交");
    expect(empty.attributes("disabled")).toBeDefined();
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
    expect(wrapper.attributes("disabled")).toBeDefined();
  });
});

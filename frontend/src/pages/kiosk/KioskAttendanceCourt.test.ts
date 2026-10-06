// @vitest-environment jsdom
import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it } from "vitest";
import KioskAttendanceCourt from "./KioskAttendanceCourt.vue";

const matches = [
  {
    memberToken: "sel_member",
    maskedStudentNo: "******1224",
    name: "测试成员",
    grade: "2025",
  },
];

function props(overrides: Record<string, unknown> = {}) {
  return {
    step: "input" as const,
    query: "9900000001",
    busy: false,
    error: "",
    online: true,
    lookupResult: null,
    matches: [],
    successName: "",
    successAction: "",
    successTime: "",
    selectingMemberToken: "",
    ...overrides,
  };
}

afterEach(() => {
  document.body.innerHTML = "";
});

describe("KioskAttendanceCourt", () => {
  it("does not make the changing attendance court a live region", () => {
    const wrapper = mount(KioskAttendanceCourt, {
      props: props(),
    });

    expect(wrapper.get(".kiosk-focus-court").attributes("aria-live")).toBeUndefined();
  });

  it("shows lookup errors while choosing a same-name account", () => {
    const wrapper = mount(KioskAttendanceCourt, {
      props: props({ step: "choose", matches, error: "连接失败" }),
    });

    expect(wrapper.get('[role="alert"]').text()).toContain("连接失败");
  });

  it("keeps an accessible name while a lookup is running", () => {
    const wrapper = mount(KioskAttendanceCourt, {
      props: props({ busy: true }),
    });

    expect(wrapper.get('.kiosk-focus-query-row button').text()).toContain("正在查询");
  });

  it("announces success text without making its action area a live region", () => {
    const wrapper = mount(KioskAttendanceCourt, {
      props: props({
        step: "success",
        successName: "测试成员",
        successAction: "签到成功",
        successTime: "14:00",
      }),
    });

    expect(wrapper.get(".kiosk-focus-success-state").attributes("role")).toBeUndefined();
    expect(wrapper.get(".kiosk-signal-success-copy").attributes("role")).toBe("status");
  });

  it("keeps the inline service message consistent with the header state", () => {
    const wrapper = mount(KioskAttendanceCourt, {
      props: props({ online: false }),
    });

    expect(wrapper.get(".kiosk-focus-hint").text()).toContain("服务连接异常");
    expect(wrapper.get(".kiosk-focus-hint").text()).not.toContain("正在重试");
    expect(wrapper.get(".kiosk-focus-hint").text()).not.toContain("本机服务正常");
  });

  it("blocks resetting during submission and refocuses confirmation after failure", async () => {
    const wrapper = mount(KioskAttendanceCourt, { attachTo: document.body, props: props({
      step: "confirm", busy: true,
      lookupResult: { exists: true, memberToken: "sel_member", name: "合成成员", action: "CHECK_IN", message: "" },
    }) });
    try {
      expect(wrapper.get('.kiosk-focus-secondary').attributes('disabled')).toBeDefined();
      await wrapper.get('.kiosk-focus-secondary').trigger('click');
      expect(wrapper.emitted('reset')).toBeUndefined();
      await wrapper.setProps({ busy: false, error: "提交结果暂未确认" });
      await flushPromises();
      expect(document.activeElement).toBe(wrapper.get('.kiosk-focus-primary').element);
    } finally { wrapper.unmount(); }
  });

  it("focuses the first account when the flow enters the choice step", async () => {
    const wrapper = mount(KioskAttendanceCourt, {
      attachTo: document.body,
      props: props(),
    });
    await flushPromises();

    await wrapper.setProps({ step: "choose", matches });
    await flushPromises();

    expect(document.activeElement).toBe(
      wrapper.get(".kiosk-focus-choice-list button").element,
    );
    wrapper.unmount();
  });
});

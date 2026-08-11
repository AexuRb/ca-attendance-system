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
    query: "1004231224",
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

  it("keeps the inline service message consistent with the header state", () => {
    const wrapper = mount(KioskAttendanceCourt, {
      props: props({ online: false }),
    });

    expect(wrapper.get(".kiosk-focus-hint").text()).toContain("连接中断，正在重试");
    expect(wrapper.get(".kiosk-focus-hint").text()).not.toContain("本机服务正常");
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

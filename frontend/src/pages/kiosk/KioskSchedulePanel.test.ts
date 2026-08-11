import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import KioskSchedulePanel from "./KioskSchedulePanel.vue";

const schedule = {
  date: "2026-08-10",
  weekdayName: "星期一",
  slots: [
    { key: "a", startTime: "14:00:00", endTime: "15:00:00", assignees: [] },
    { key: "b", startTime: "15:00:00", endTime: "16:00:00", assignees: [] },
    { key: "c", startTime: "16:00:00", endTime: "17:00:00", assignees: [] },
  ],
};

describe("KioskSchedulePanel", () => {
  it("reacts to the shared clock and marks only the nearest future shift", async () => {
    const wrapper = mount(KioskSchedulePanel, {
      props: {
        todaySchedule: schedule,
        scheduleError: "",
        scheduleCount: 0,
        weekdayLabel: "星期一",
        now: new Date(2026, 7, 10, 13, 30),
      },
    });

    expect(wrapper.findAll(".kiosk-signal-shift > span").map((node) => node.text()))
      .toEqual(["下一时段", "待开始", "待开始"]);

    await wrapper.setProps({ now: new Date(2026, 7, 10, 14, 30) });

    expect(wrapper.findAll(".kiosk-signal-shift > span").map((node) => node.text()))
      .toEqual(["当前时段", "下一时段", "待开始"]);
    expect(wrapper.findAll(".kiosk-signal-shift")[0].classes()).toContain("current");
  });
});

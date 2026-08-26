// @vitest-environment jsdom
import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import FixedScheduleBoard from "./FixedScheduleBoard.vue";

const periods = [
  { id: 1, startTime: "14:00:00", endTime: "16:00:00", enabled: true },
  { id: 2, startTime: "16:00:00", endTime: "18:00:00", enabled: true },
];

const weekdays = [
  { value: 1, label: "星期一", short: "周一", enabled: true },
  { value: 2, label: "星期二", short: "周二", enabled: true },
];

describe("FixedScheduleBoard", () => {
  it("renders a focused weekday schedule without redundant period actions", async () => {
    const wrapper = mount(FixedScheduleBoard, {
      props: {
        periods,
        weekdays,
        slots: [
          {
            id: 1,
            weekday: 1,
            weekdayName: "星期一",
            startTime: "14:00:00",
            endTime: "16:00:00",
            title: "值班",
            enabled: false,
            assignees: Array.from({ length: 5 }, (_, index) => ({
              id: index + 1,
              studentNo: `100${index}`,
              name: `成员${index + 1}`,
              sortOrder: index,
            })),
          },
          {
            id: 2,
            weekday: 2,
            weekdayName: "星期二",
            startTime: "16:00:00",
            endTime: "18:00:00",
            title: "值班",
            enabled: true,
            assignees: [
              {
                id: 6,
                studentNo: "2001",
                name: "星期二成员",
                sortOrder: 0,
              },
            ],
          },
        ],
      },
    });

    expect(wrapper.findAll(".schedule-focus-day")).toHaveLength(2);
    expect(wrapper.findAll(".schedule-focus-period")).toHaveLength(2);
    expect(wrapper.get(".schedule-focus-day.active").text()).toContain("星期一");
    expect(wrapper.get(".schedule-slot-card").classes()).toContain("muted");
    expect(wrapper.findAll(".schedule-assignee-item")).toHaveLength(5);
    expect(wrapper.get(".schedule-assignee-preview").text()).toContain("成员5");
    expect(wrapper.get(".schedule-assignee-preview").text()).not.toContain("+2");
    expect(
      wrapper.get(".schedule-slot-top").find(".schedule-card-actions").exists(),
    ).toBe(true);
    expect(wrapper.text()).toContain("14:00–16:00");

    expect(wrapper.find(".schedule-focus-add").exists()).toBe(false);

    await wrapper.findAll(".schedule-focus-day")[1].trigger("click");
    expect(wrapper.get(".schedule-focus-day.active").text()).toContain("星期二");
    expect(wrapper.text()).toContain("星期二成员");
    expect(wrapper.text()).not.toContain("成员1");
  });
});

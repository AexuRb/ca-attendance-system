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
  it("renders a weekday by period matrix and compacts long assignee lists", () => {
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
        ],
      },
    });

    expect(wrapper.findAll(".schedule-matrix-day")).toHaveLength(2);
    expect(wrapper.findAll(".schedule-matrix-period")).toHaveLength(2);
    expect(wrapper.findAll(".schedule-matrix-cell")).toHaveLength(4);
    expect(wrapper.get(".schedule-slot-card").classes()).toContain("muted");
    expect(wrapper.get(".schedule-assignee-preview").text()).toContain("+2");
    expect(wrapper.text()).toContain("14:00–16:00");
  });
});

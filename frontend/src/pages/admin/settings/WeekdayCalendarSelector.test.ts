// @vitest-environment jsdom
import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import WeekdayCalendarSelector from "./WeekdayCalendarSelector.vue";

const days = [
  { weekday: 1, weekday_name: "星期一", enabled: true },
  { weekday: 2, weekday_name: "星期二", enabled: false },
  { weekday: 3, weekday_name: "星期三", enabled: true },
  { weekday: 4, weekday_name: "星期四", enabled: true },
  { weekday: 5, weekday_name: "星期五", enabled: true },
  { weekday: 6, weekday_name: "星期六", enabled: false },
  { weekday: 7, weekday_name: "星期日", enabled: false },
];

describe("WeekdayCalendarSelector", () => {
  it("renders calendar leaves with clear selected states", () => {
    const wrapper = mount(WeekdayCalendarSelector, { props: { days } });

    expect(wrapper.get(".weekday-calendar-count").text()).toContain("4");
    expect(wrapper.findAll(".weekday-calendar-day")).toHaveLength(7);
    expect(
      wrapper.get('[data-weekday="1"]').attributes("aria-pressed"),
    ).toBe("true");
    expect(wrapper.get('[data-weekday="1"] .weekday-calendar-state').text()).toBe(
      "开放",
    );
    expect(wrapper.get('[data-weekday="2"] .weekday-calendar-state').text()).toBe(
      "关闭",
    );
  });

  it("emits the selected weekday without mutating its input", async () => {
    const wrapper = mount(WeekdayCalendarSelector, { props: { days } });

    await wrapper.get('[data-weekday="2"]').trigger("click");

    expect(wrapper.emitted("toggle")).toEqual([[2]]);
    expect(days[1].enabled).toBe(false);
  });
});

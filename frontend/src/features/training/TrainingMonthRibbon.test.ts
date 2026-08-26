// @vitest-environment jsdom
import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import TrainingMonthRibbon from "./TrainingMonthRibbon.vue";
import type { TrainingSession } from "./trainingTypes";

describe("TrainingMonthRibbon", () => {
  it("orders sessions, selects a node and exposes month navigation", async () => {
    const wrapper = mount(TrainingMonthRibbon, {
      props: {
        label: "2026年8月",
        items: [session(2, "2026-08-24"), session(1, "2026-08-09")],
        selectedId: 2,
        total: 2,
        page: 1,
        pageSize: 20,
        hasMore: false,
        loading: false,
        error: "",
      },
    });

    const events = wrapper.findAll(".training-ribbon-event");
    expect(events[0].text()).toContain("培训 1");
    expect(events[1].attributes("aria-pressed")).toBe("true");
    await events[0].trigger("click");
    await wrapper.get('button[aria-label="查看上个月培训"]').trigger("click");

    expect(wrapper.emitted("select")?.[0]).toEqual([session(1, "2026-08-09")]);
    expect(wrapper.emitted("shift-month")?.[0]).toEqual([-1]);
    expect(wrapper.text()).toContain("2 场培训");
  });

  it("keeps loading, failure and empty states distinct", async () => {
    const wrapper = mount(TrainingMonthRibbon, {
      props: {
        label: "2026年8月",
        items: [],
        selectedId: null,
        total: 0,
        page: 1,
        pageSize: 20,
        hasMore: false,
        loading: true,
        error: "",
      },
    });
    expect(wrapper.get('[aria-live="polite"]').text()).toContain("加载");

    await wrapper.setProps({ loading: false, error: "场次读取失败" });
    expect(wrapper.get('[role="alert"]').text()).toContain("场次读取失败");

    await wrapper.setProps({ error: "" });
    expect(wrapper.text()).toContain("本月暂无培训");
  });
});

function session(id: number, trainingDate: string): TrainingSession {
  return {
    id,
    title: `培训 ${id}`,
    trainingDate,
    startTime: "14:00",
    endTime: "16:00",
    speaker: "陈禹杭",
    location: "协会活动室",
    status: "COMPLETED",
    participantCount: id * 10,
    totalDurationHours: id * 20,
    createdAt: `${trainingDate}T10:00:00`,
    updatedAt: `${trainingDate}T10:00:00`,
  };
}

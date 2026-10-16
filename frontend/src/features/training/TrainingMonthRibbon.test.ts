// @vitest-environment jsdom
import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import TrainingMonthRibbon from "./TrainingMonthRibbon.vue";
import type { TrainingSession } from "./trainingTypes";

describe("TrainingMonthRibbon", () => {
  it("orders sessions newest first and selects a session", async () => {
    const wrapper = mount(TrainingMonthRibbon, {
      props: {
        label: "2026年8月",
        items: [session(2, "2026-08-24"), session(1, "2026-08-09")],
        selectedId: 2,
        total: 2,
        page: 1,
        pageSize: 8,
        hasMore: false,
        loading: false,
        error: "",
      },
    });

    const events = wrapper.findAll(".training-ribbon-event");
    expect(events[0].text()).toContain("培训 2");
    expect(events[0].attributes("aria-pressed")).toBe("true");
    await events[1].trigger("click");

    expect(wrapper.emitted("select")?.[0]).toEqual([session(1, "2026-08-09")]);
    expect(wrapper.text()).toContain("2 场");
  });

  it("bounds directory navigation and locks paging after a failed refresh", async () => {
    const wrapper = mount(TrainingMonthRibbon, { props: {
      label: "2026年8月", items: Array.from({ length: 8 }, (_, i) => session(i + 1, "2026-08-09")),
      selectedId: 1, total: 17, page: 1, pageSize: 8, hasMore: true, loading: false, error: "",
    } });
    const previous = wrapper.get('button[aria-label="上一页培训场次"]');
    const next = wrapper.get('button[aria-label="下一页培训场次"]');
    expect(previous.attributes("disabled")).toBeDefined();
    await next.trigger("click");
    expect(wrapper.emitted("page")?.[0]).toEqual([2]);
    await wrapper.setProps({ page: 3, hasMore: false, items: [session(17, "2026-08-09")] });
    expect(next.attributes("disabled")).toBeDefined();
    await previous.trigger("click");
    expect(wrapper.emitted("page")?.[1]).toEqual([2]);
    await wrapper.setProps({ error: "场次读取失败" });
    expect(previous.attributes("disabled")).toBeDefined();
  });

  it("keeps loading, failure and empty states distinct", async () => {
    const wrapper = mount(TrainingMonthRibbon, {
      props: {
        label: "2026年8月",
        items: [],
        selectedId: null,
        total: 0,
        page: 1,
        pageSize: 8,
        hasMore: false,
        loading: true,
        error: "",
      },
    });
    expect(wrapper.get('[aria-live="polite"]').text()).toContain("加载");

    await wrapper.setProps({ loading: false, error: "场次读取失败" });
    expect(wrapper.get('[role="alert"]').text()).toContain("场次读取失败");

    await wrapper.setProps({ error: "" });
    expect(wrapper.text()).toContain("没有符合条件的培训");
  });

  it("keeps the last result visible while refreshing or showing a fetch error", async () => {
    const wrapper = mount(TrainingMonthRibbon, {
      props: {
        label: "2026年8月",
        items: [session(1, "2026-08-09")],
        selectedId: 1,
        total: 1,
        page: 1,
        pageSize: 8,
        hasMore: false,
        loading: true,
        error: "",
      },
    });

    expect(wrapper.get(".training-month-shell").attributes("aria-busy")).toBe("true");
    expect(wrapper.get(".training-ribbon-event").text()).toContain("培训 1");
    expect(wrapper.text()).toContain("正在更新");

    await wrapper.setProps({ loading: false, error: "场次读取失败" });
    expect(wrapper.get(".training-ribbon-event").text()).toContain("培训 1");
    expect(wrapper.get('[role="alert"]').text()).toContain("下方仍显示上次结果");
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

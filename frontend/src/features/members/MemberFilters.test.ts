// @vitest-environment jsdom
import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import { nextTick } from "vue";
import MemberFilters from "./MemberFilters.vue";

describe("MemberFilters", () => {
  it("shows active filters and clears one before submitting", async () => {
    const wrapper = mount(MemberFilters, {
      props: {
        keyword: "",
        role: "MINISTER",
        status: "ACTIVE",
        grade: "",
        grades: ["2025级"],
      },
    });

    expect(wrapper.text()).toContain("角色：部长");
    expect(wrapper.text()).toContain("状态：启用");

    await wrapper
      .get('button[aria-label="移除筛选：角色：部长"]')
      .trigger("click");
    await nextTick();

    expect(wrapper.emitted("update:role")).toEqual([[""]]);
    expect(wrapper.emitted("submit")).toHaveLength(1);
  });

  it("reveals the compact filter panel from the toggle", async () => {
    const wrapper = mount(MemberFilters, {
      props: {
        keyword: "",
        role: "",
        status: "",
        grade: "",
        grades: ["2025级"],
      },
    });

    expect(wrapper.find("#member-advanced-filters").exists()).toBe(false);
    await wrapper.get(".member-filter-toggle").trigger("click");
    expect(wrapper.find("#member-advanced-filters").exists()).toBe(true);
  });

  it("shows the keyword as a removable filter and clears it with all filters", async () => {
    const wrapper = mount(MemberFilters, {
      props: {
        keyword: "陈",
        role: "MINISTER",
        status: "",
        grade: "",
        grades: ["2025级"],
      },
    });

    expect(wrapper.text()).toContain("关键词：陈");
    await wrapper.get(".member-filter-toggle").trigger("click");
    await wrapper.get(".member-filter-clear").trigger("click");

    expect(wrapper.emitted("update:keyword")).toEqual([[""]]);
    expect(wrapper.emitted("update:role")).toEqual([[""]]);
    expect(wrapper.emitted("submit")).toHaveLength(1);
  });
});

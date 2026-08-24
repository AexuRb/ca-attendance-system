// @vitest-environment jsdom
import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import PageHeader from "./PageHeader.vue";

describe("PageHeader", () => {
  it("renders the title, description, metadata and actions", () => {
    const wrapper = mount(PageHeader, {
      props: {
        title: "成员名册",
        description: "管理账号、角色、状态与基础资料。",
        meta: "2026年7月30日星期四",
      },
      slots: {
        actions: '<button type="button">新增成员</button>',
      },
    });

    expect(wrapper.get("h1").text()).toBe("成员名册");
    expect(wrapper.get(".page-description").text()).toBe(
      "管理账号、角色、状态与基础资料。",
    );
    expect(wrapper.get(".page-meta").text()).toBe("2026年7月30日星期四");
    expect(wrapper.get(".page-actions button").text()).toBe("新增成员");
  });

  it("omits optional rows when they are not provided", () => {
    const wrapper = mount(PageHeader, {
      props: { title: "今日" },
    });

    expect(wrapper.find(".page-description").exists()).toBe(false);
    expect(wrapper.find(".page-meta").exists()).toBe(false);
    expect(wrapper.find(".page-actions").exists()).toBe(false);
  });
});

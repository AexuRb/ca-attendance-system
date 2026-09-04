// @vitest-environment jsdom
import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it } from "vitest";
import AppearanceSelector from "./AppearanceSelector.vue";

afterEach(() => {
  document.body.innerHTML = "";
});

describe("AppearanceSelector", () => {
  it("supports arrow-key selection and confirms the requested appearance", async () => {
    const wrapper = mount(AppearanceSelector, {
      attachTo: document.body,
      props: {
        modelValue: "CLASSIC",
        activeAppearance: "CLASSIC",
        canEdit: true,
        pending: false,
      },
      global: { stubs: { Teleport: true } },
    });

    const choices = wrapper.findAll('[role="radio"]');
    await choices[0].trigger("keydown", { key: "ArrowRight" });
    expect(wrapper.emitted("update:modelValue")?.[0]).toEqual(["EDITORIAL"]);

    await wrapper.setProps({ modelValue: "EDITORIAL" });
    await wrapper.get(".appearance-heading .button").trigger("click");
    expect(wrapper.text()).toContain("确认切换为“编辑式”");
    const confirmButtons = wrapper.findAll(".modal-footer .button");
    await confirmButtons[1]?.trigger("click");
    expect(wrapper.emitted("save")).toHaveLength(1);
  });

  it("keeps choices read-only for non-admin users", () => {
    const wrapper = mount(AppearanceSelector, {
      props: {
        modelValue: "SPATIAL",
        activeAppearance: "SPATIAL",
        canEdit: false,
        pending: false,
      },
    });

    expect(wrapper.findAll('[role="radio"]').every((choice) => choice.attributes("disabled") !== undefined)).toBe(true);
    expect(wrapper.text()).toContain("仅管理员可以修改全局界面");
    expect(wrapper.find(".appearance-heading .button").exists()).toBe(false);
  });
});

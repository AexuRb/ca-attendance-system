// @vitest-environment jsdom
import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import ReasonPicker from "./ReasonPicker.vue";
import { reasonOptions } from "../config/reasonOptions";

function mountPicker() {
  const wrapper = mount(ReasonPicker, {
    props: { modelValue: "", options: reasonOptions.attendanceDelete, name: "reason",
      "onUpdate:modelValue": (value: string) => { void wrapper.setProps({ modelValue: value }); } },
  });
  return wrapper;
}

describe("ReasonPicker", () => {
  it("starts without a reason and emits the full fixed reason", async () => {
    const wrapper = mountPicker();
    expect(wrapper.find("input:checked").exists()).toBe(false);
    expect(wrapper.find("textarea").exists()).toBe(false);
    await wrapper.get('input[value="重复记录"]').setValue();
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["重复记录"]);
    expect(wrapper.find("textarea").exists()).toBe(false);
    wrapper.unmount();
  });

  it("requires a nonblank Other explanation and retains it when switching reasons", async () => {
    const wrapper = mountPicker();
    await wrapper.get('input[value="other"]').setValue();
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([""]);
    await wrapper.get("textarea").setValue("   ");
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([""]);
    await wrapper.get("textarea").setValue("  核对后更正  ");
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["其他：核对后更正"]);
    await wrapper.get('input[value="重复记录"]').setValue();
    await wrapper.get('input[value="other"]').setValue();
    expect((wrapper.get("textarea").element as HTMLTextAreaElement).value).toBe("  核对后更正  ");
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["其他：核对后更正"]);
    wrapper.unmount();
  });

  it("keeps the complete submitted reason within the API's 500 character limit", async () => {
    const wrapper = mountPicker();
    await wrapper.get('input[value="other"]').setValue();
    await wrapper.get("textarea").setValue("测".repeat(600));
    const value = wrapper.emitted("update:modelValue")?.at(-1)?.[0] as string;
    expect(value).toBe("其他：" + "测".repeat(497));
    expect(wrapper.get("textarea").attributes("maxlength")).toBe("497");
    wrapper.unmount();
  });

  it("locks choices and text while pending and follows an external clear", async () => {
    const wrapper = mountPicker();
    await wrapper.get('input[value="other"]').setValue();
    await wrapper.get("textarea").setValue("待核对");
    await wrapper.setProps({ disabled: true });
    const count = wrapper.emitted("update:modelValue")!.length;
    expect(wrapper.findAll("input,textarea").every(el => el.attributes("disabled") !== undefined)).toBe(true);
    await wrapper.get('input[value="重复记录"]').trigger("change");
    await wrapper.get("textarea").trigger("input");
    expect(wrapper.emitted("update:modelValue")).toHaveLength(count);
    await wrapper.setProps({ disabled: false, modelValue: "" });
    expect(wrapper.find("input:checked").exists()).toBe(false);
    expect(wrapper.find("textarea").exists()).toBe(false);
    wrapper.unmount();
  });
});

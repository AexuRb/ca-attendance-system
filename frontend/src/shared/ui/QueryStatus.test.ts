// @vitest-environment jsdom
import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import QueryStatus from "./QueryStatus.vue";

const texts = { loadingText: "正在读取", failedText: "失败后重试原条件", dirtyText: "草稿尚未应用", idleText: "已完成" };

describe("QueryStatus feedback priority", () => {
  it("keeps failure visible when a user edits a draft, then reports the retry before the remaining draft", async () => {
    const wrapper = mount(QueryStatus, { props: { ...texts, loading: false, failed: true, dirty: true } });
    expect(wrapper.text()).toBe(texts.failedText);
    await wrapper.setProps({ loading: true });
    expect(wrapper.text()).toBe(texts.loadingText);
    await wrapper.setProps({ loading: false, failed: false });
    expect(wrapper.text()).toBe(texts.dirtyText);
    await wrapper.setProps({ dirty: false });
    expect(wrapper.text()).toBe(texts.idleText);
    wrapper.unmount();
  });

  it("retains a polite atomic status region without displaying an empty idle hint", async () => {
    const wrapper = mount(QueryStatus, { attachTo: document.body, props: { ...texts, idleText: "", loading: false, failed: false, dirty: false } });
    expect(wrapper.isVisible()).toBe(false);
    expect(wrapper.attributes()).toMatchObject({ role: "status", "aria-live": "polite", "aria-atomic": "true" });
    await wrapper.setProps({ failed: true });
    expect(wrapper.isVisible()).toBe(true);
    expect(wrapper.text()).toBe(texts.failedText);
    wrapper.unmount();
  });
});

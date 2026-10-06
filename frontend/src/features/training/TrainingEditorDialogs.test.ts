// @vitest-environment jsdom
import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it } from "vitest";
import TrainingSessionEditorDialog from "./TrainingSessionEditorDialog.vue";
import TrainingParticipantEditorDialog from "./TrainingParticipantEditorDialog.vue";

afterEach(() => {
  document.body.innerHTML = "";
});

describe("TrainingSessionEditorDialog", () => {
  it("validates save-and-continue and limits it to new participant records", async () => {
    const wrapper = mount(TrainingParticipantEditorDialog, { attachTo: document.body, props: {
      open: true, pending: false, form: { id: null, studentNo: "20260001", name: "", durationHours: 1.5, remark: "" },
    } });
    await flushPromises();
    const continueButton = () => Array.from(document.body.querySelectorAll<HTMLButtonElement>("button")).find(button => button.textContent?.includes("保存并继续"));
    continueButton()!.click();
    await wrapper.vm.$nextTick();
    await new Promise<void>(resolve => requestAnimationFrame(() => resolve()));
    expect(wrapper.emitted("save")).toBeUndefined();
    expect(document.activeElement?.getAttribute("name")).toBe("participant-name");
    await wrapper.setProps({ form: { ...wrapper.props("form"), name: "测试成员" } });
    continueButton()!.click();
    await wrapper.vm.$nextTick();
    expect(wrapper.emitted("save")?.[0]).toEqual([true]);
    await wrapper.setProps({ form: { ...wrapper.props("form"), id: 8 } });
    expect(continueButton()).toBeUndefined();
    wrapper.unmount();
  });
  it("shows inline errors and focuses the first invalid field", async () => {
    const wrapper = mount(TrainingSessionEditorDialog, {
      attachTo: document.body,
      props: {
        open: true,
        pending: false,
        form: {
          id: null,
          title: "",
          trainingDate: "",
          startTime: "16:00",
          endTime: "15:00",
          location: "",
          speaker: "",
          description: "",
        },
      },
    });

    document.body.querySelector<HTMLFormElement>("#training-session-editor")!
      .dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
    await wrapper.vm.$nextTick();

    expect(document.body.textContent).toContain("请填写培训标题");
    expect(document.body.textContent).toContain("请选择培训日期");
    expect(document.activeElement?.getAttribute("name")).toBe("training-title");
    expect(wrapper.emitted("save")).toBeUndefined();
    wrapper.unmount();
  });
});

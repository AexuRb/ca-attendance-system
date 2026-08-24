// @vitest-environment jsdom
import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it } from "vitest";
import TrainingSessionEditorDialog from "./TrainingSessionEditorDialog.vue";

afterEach(() => {
  document.body.innerHTML = "";
});

describe("TrainingSessionEditorDialog", () => {
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

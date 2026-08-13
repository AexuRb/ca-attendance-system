import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it } from "vitest";
import TrainingImportDialog from "./TrainingImportDialog.vue";

afterEach(() => {
  document.body.innerHTML = "";
});

describe("TrainingImportDialog", () => {
  it("shows the selected file, retains it on failure and resets after reopening", async () => {
    const wrapper = mount(TrainingImportDialog, {
      attachTo: document.body,
      props: { open: true, pending: false, error: "" },
    });
    const input = document.body.querySelector<HTMLInputElement>(
      'input[type="file"]',
    )!;
    const file = new File([new Uint8Array(1536)], "培训名单.xlsx", {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });
    Object.defineProperty(input, "files", { configurable: true, value: [file] });
    input.dispatchEvent(new Event("change", { bubbles: true }));
    await wrapper.vm.$nextTick();

    expect(document.body.textContent).toContain("培训名单.xlsx");
    expect(document.body.textContent).toContain("1.5 KB");
    await wrapper.setProps({ error: "模板字段不正确" });
    expect(document.body.textContent).toContain("模板字段不正确");
    expect(document.body.textContent).toContain("培训名单.xlsx");

    await wrapper.setProps({ open: false });
    await wrapper.setProps({ open: true, error: "" });
    expect(document.body.textContent).not.toContain("培训名单.xlsx");
    wrapper.unmount();
  });
});

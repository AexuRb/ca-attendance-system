// @vitest-environment jsdom
import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it } from "vitest";
import RepairEditorDialog from "./RepairEditorDialog.vue";

afterEach(() => {
  document.body.innerHTML = "";
});

describe("RepairEditorDialog", () => {
  it("validates each step, focuses the error and uses factual confirmation wording", async () => {
    const wrapper = mount(RepairEditorDialog, {
      attachTo: document.body,
      props: {
        open: true,
        pending: false,
        handler: null,
        candidates: [],
        form: {
          id: null,
          agreementType: "REPAIR",
          ownerName: "",
          ownerPhone: "",
          deviceType: "",
          deviceBrand: "",
          deviceModel: "",
          accessories: "",
          faultDescription: "",
          serviceDescription: "",
          dataBackupConfirmed: false,
          riskAcknowledged: false,
          privacyAcknowledged: false,
          status: "REPAIRING",
          receivedAt: "2026-08-13T14:00",
          completedAt: "",
          handlerName: "",
          remark: "",
        },
      },
    });

    const next = Array.from(document.body.querySelectorAll<HTMLButtonElement>("button"))
      .find((button) => button.textContent?.includes("下一步"))!;
    next.click();
    await wrapper.vm.$nextTick();
    expect(document.body.textContent).toContain("请填写联系人");
    expect(document.activeElement?.getAttribute("name")).toBe("repair-owner-name");

    await wrapper.setProps({
      form: {
        ...wrapper.props("form"),
        ownerName: "测试联系人",
        deviceType: "笔记本电脑",
        faultDescription: "无法开机",
      },
    });
    next.click();
    await wrapper.vm.$nextTick();
    expect(document.body.textContent).toContain("已完成数据备份情况记录");

    document.body.querySelector<HTMLFormElement>("#repair-editor-form")!
      .dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
    await wrapper.vm.$nextTick();
    expect(document.body.textContent).toContain("请选择负责人");
    expect(document.activeElement?.getAttribute("name")).toBe("repair-handler");
    wrapper.unmount();
  });
});

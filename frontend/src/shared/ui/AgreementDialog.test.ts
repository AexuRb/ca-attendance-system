import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it } from "vitest";
import AgreementDialog from "./AgreementDialog.vue";

describe("AgreementDialog", () => {
  afterEach(() => {
    document.body.innerHTML = "";
  });

  it("renders the backend agreement through an offline srcdoc iframe", async () => {
    const html = "<!doctype html><html><body><h1>维修协议</h1></body></html>";
    mount(AgreementDialog, {
      attachTo: document.body,
      props: {
        open: true,
        caseNo: "JXWX20260810-0001",
        html,
      },
    });

    const frame = document.body.querySelector("iframe");
    expect(frame).not.toBeNull();
    expect(frame?.getAttribute("srcdoc")).toBe(html);
    expect(frame?.getAttribute("src")).toBeNull();
    expect(document.body.textContent).toContain("JXWX20260810-0001");
  });
});

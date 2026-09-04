// @vitest-environment jsdom
import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it } from "vitest";
import AgreementDialog from "./AgreementDialog.vue";

describe("AgreementDialog", () => {
  afterEach(() => {
    document.body.innerHTML = "";
  });

  it("renders a script-free agreement through an offline srcdoc iframe", async () => {
    const html = `<!doctype html><html><body onload="window.print()">
      <h1>维修协议</h1>
      <button class="print" onclick="window.print()">打印协议</button>
      <script>window.print()</script>
    </body></html>`;
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
    const previewHtml = frame?.getAttribute("srcdoc") || "";
    expect(previewHtml).toContain("维修协议");
    expect(previewHtml).not.toContain("onclick");
    expect(previewHtml).not.toContain("onload");
    expect(previewHtml).not.toContain("<script");
    expect(previewHtml).not.toContain('class="print"');
    expect(frame?.getAttribute("src")).toBeNull();
    expect(document.body.textContent).toContain("JXWX20260810-0001");
  });
});

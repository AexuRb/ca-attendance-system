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
    const wrapper = mount(AgreementDialog, {
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
    wrapper.unmount();
  });

  it("changes preview zoom without changing the printable A4 document", async () => {
    const html = '<!doctype html><html><head><style>@page{size:A4}</style></head><body><section class="sheet">原协议</section></body></html>';
    const wrapper = mount(AgreementDialog, { props: { open: true, html, caseNo: 'TEST-001' } });
    const frame = document.body.querySelector('iframe')!;
    const original = frame.getAttribute('srcdoc');
    const zoom = document.body.querySelector<HTMLSelectElement>('[aria-label="预览比例"]')!;
    zoom.value = '125';
    zoom.dispatchEvent(new Event('change', { bubbles: true }));
    await wrapper.vm.$nextTick();
    expect(frame.style.transform).toBe('scale(1.25)');
    expect(frame.getAttribute('srcdoc')).toBe(original);
    expect(original).toContain('@page{size:A4}');
    await wrapper.setProps({ open: false });
    await wrapper.setProps({ open: true });
    expect(document.body.querySelector<HTMLSelectElement>('[aria-label="预览比例"]')?.value).toBe('fit');
    wrapper.unmount();
  });
});

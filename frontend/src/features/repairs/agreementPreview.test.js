import { describe, expect, it } from 'vitest'
import { prepareAgreementPreviewHtml } from './agreementPreview.js'

describe('agreement preview document', () => {
  it('keeps agreement content while removing executable markup and the legacy print button', () => {
    const preview = prepareAgreementPreviewHtml(`
      <!doctype html>
      <html>
        <head><title>维修协议预览</title><style>.paper { color: #123; }</style></head>
        <body onload="steal()">
          <main class="paper"><h1>维修协议</h1><p>陈禹杭</p></main>
          <button class="print" onclick="window.print()">打印</button>
          <script>steal()</script>
        </body>
      </html>
    `)

    expect(preview.title).toBe('维修协议预览')
    expect(preview.html).toContain('陈禹杭')
    expect(preview.html).toContain('.paper { color: #123; }')
    expect(preview.html).not.toContain('<script')
    expect(preview.html).not.toContain('onclick=')
    expect(preview.html).not.toContain('onload=')
    expect(preview.html).not.toContain('class="print"')
  })

  it('rejects an empty agreement response', () => {
    expect(() => prepareAgreementPreviewHtml('')).toThrow('协议内容为空')
  })
})

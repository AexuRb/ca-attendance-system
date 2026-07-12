const BLOCKED_ELEMENTS = 'script, iframe, object, embed, form, base, link, meta[http-equiv]'

export function prepareAgreementPreviewHtml(source) {
  const html = String(source || '').trim()
  if (!html) throw new Error('协议内容为空，请稍后重试')

  const document = new DOMParser().parseFromString(html, 'text/html')
  if (document.querySelector('parsererror')) throw new Error('协议内容无法解析')

  document.querySelectorAll(BLOCKED_ELEMENTS).forEach(element => element.remove())
  document.querySelectorAll('*').forEach(element => {
    for (const attribute of Array.from(element.attributes)) {
      if (attribute.name.toLowerCase().startsWith('on')) element.removeAttribute(attribute.name)
    }
  })
  document.querySelectorAll('.print').forEach(element => element.remove())

  const title = document.querySelector('title')?.textContent?.trim() || '维修协议预览'
  return {
    title,
    html: `<!doctype html>\n${document.documentElement.outerHTML}`
  }
}

export async function agreementPreviewFromBlob(blob) {
  if (!(blob instanceof Blob)) throw new Error('协议文件格式不正确')
  return prepareAgreementPreviewHtml(await blob.text())
}

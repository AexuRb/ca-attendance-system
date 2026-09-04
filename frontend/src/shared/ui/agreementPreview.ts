export function sanitizeAgreementPreviewHtml(html = "") {
  if (!html) return "";

  const document = new DOMParser().parseFromString(html, "text/html");
  document.querySelectorAll("script, .print").forEach((element) => element.remove());
  document.querySelectorAll("*").forEach((element) => {
    for (const attribute of Array.from(element.attributes)) {
      if (attribute.name.toLowerCase().startsWith("on")) {
        element.removeAttribute(attribute.name);
      }
    }
  });

  return `<!doctype html>\n${document.documentElement.outerHTML}`;
}

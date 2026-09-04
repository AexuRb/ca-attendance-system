import { expect, test, type Page } from "@playwright/test";

async function assertFits(page: Page) {
  expect(await page.evaluate(() =>
    document.documentElement.scrollWidth <= document.documentElement.clientWidth,
  )).toBe(true);
}

for (const appearance of ["EDITORIAL", "SPATIAL"]) {
  test(appearance + " feedback supports retry, keyboard focus and reduced motion", async ({ page }) => {
    await page.emulateMedia({ reducedMotion: "reduce" });
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.addInitScript(() => {
      localStorage.setItem("ca_attendance_token", "feedback-fixture");
      localStorage.setItem("ca-admin-section-sidebar-collapsed", "true");
    });
    let releaseFirst: () => void = () => {};
    const firstResponse = new Promise<void>(resolve => { releaseFirst = resolve; });
    let reads = 0;
    let deletes = 0;
    await page.route("**/api/**", async route => {
      const request = route.request();
      const path = new URL(request.url()).pathname;
      const json = (body: unknown, status = 200) => route.fulfill({
        status, contentType: "application/json", body: JSON.stringify(body),
      });
      if (path === "/api/logs") {
        if (request.method() === "DELETE") {
          deletes++;
          return route.fulfill({ status: 204 });
        }
        if (++reads === 1) {
          await firstResponse;
          return json({ message: "演示请求失败，请重试" }, 500);
        }
        return json({ items: [], total: 0, page: 1, pageSize: 20 });
      }
      if (path === "/api/public/appearance") return json({ appearance, version: 1 });
      if (path === "/api/access/context") return json({ mode: "LOCAL", kioskAvailable: true, allowedRemoteRoles: [] });
      if (path === "/api/setup/status") return json({ initialized: true });
      if (path === "/api/auth/me") return json({
        id: 101, name: "演示管理员", role: "ADMIN",
        studentNo: "fixture-feedback", mustChangePassword: false,
      });
      if (path === "/api/health") return json({ status: "UP" });
      return json([]);
    });
    try {
      await page.goto("/#/admin/logs");
      await expect(page.locator("html")).toHaveAttribute("data-appearance", appearance.toLowerCase());
      const loading = page.locator(".loading-block");
      await expect(loading).toBeVisible();
      await expect(loading).toHaveAttribute("role", "status");
      const animation = await loading.locator(".spin").evaluate(el => getComputedStyle(el).animationDuration);
      expect(parseFloat(animation)).toBeLessThanOrEqual(.001);
      await expect(loading.locator("svg")).toHaveCSS("background-color", "rgba(0, 0, 0, 0)");
      releaseFirst();
      await expect(page.locator(".inline-alert[role=alert]")).toBeVisible();
      await assertFits(page);
      await page.getByRole("button", { name: "重试", exact: true }).click();
      await expect(page.getByText("暂无操作日志", { exact: true })).toBeVisible();
      await expect(page.locator(".inline-alert[role=alert]")).toHaveCount(0);
      await expect(page.locator(".empty-state > svg")).toHaveCSS("background-color", "rgba(0, 0, 0, 0)");

      const trigger = page.getByRole("button", { name: "清空日志", exact: true });
      const dialog = page.getByRole("dialog", { name: "清空操作日志" });
      for (const width of [1440, 960, 390]) {
        await page.setViewportSize({ width, height: width === 390 ? 844 : 900 });
        await trigger.click();
        await expect(dialog).toBeVisible();
        await expect(page.locator(".modal-backdrop")).toHaveCSS("opacity", "1");
        const topLine = await dialog.evaluate(el => getComputedStyle(el, "::before").display);
        expect(topLine).toBe("none");
        await expect(dialog.locator(".modal-header")).toHaveCSS("background-color",
          appearance === "EDITORIAL" ? "rgb(250, 249, 245)" : "rgb(255, 255, 255)");
        const cancel = dialog.getByRole("button", { name: "取消", exact: true });
        const submit = dialog.getByRole("button", { name: "备份并清空", exact: true });
        await expect(submit).toHaveCSS("box-shadow", "none");
        await cancel.focus();
        await page.keyboard.press("Tab");
        await expect(submit).toBeFocused();
        await expect(submit).toHaveCSS("outline-style", "solid");
        await expect(submit).toHaveCSS("outline-width", "2px");
        await page.keyboard.press("Tab");
        await expect(dialog.getByRole("button", { name: "关闭", exact: true })).toBeFocused();
        await page.keyboard.press("Shift+Tab");
        await expect(submit).toBeFocused();
        await assertFits(page);
        expect(await dialog.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
        await page.keyboard.press("Escape");
        await expect(dialog).toHaveCount(0);
        await expect(trigger).toBeFocused();
      }
      expect(deletes).toBe(0);
      await trigger.click();
      await dialog.getByRole("button", { name: "备份并清空", exact: true }).click();
      await expect(dialog).toHaveCount(0);
      expect(deletes).toBe(1);
      const toast = page.locator(".toast[data-tone=success]");
      await expect(toast).toBeVisible();
      await expect(toast).toHaveCSS("font-size", "15px");
      await expect(page.locator(".toast-stack")).toHaveAttribute("aria-live", "polite");
      await assertFits(page);
      await toast.getByRole("button").click();
      await expect(toast).toHaveCount(0);
    } finally {
      releaseFirst();
    }
  });
}

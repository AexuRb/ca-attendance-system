import { expect, test, type Page, type Route } from "@playwright/test";

function fulfillJson(route: Route, payload: unknown, status = 200) {
  return route.fulfill({
    status,
    contentType: "application/json",
    body: JSON.stringify(payload),
  });
}

async function installApiMocks(page: Page) {
  let failNextAppearanceSave = false;
  let currentAppearance = "CLASSIC";
  await page.addInitScript(() => {
    localStorage.setItem("ca_attendance_token", "appearance-settings-token");
    localStorage.setItem("ca-admin-section-sidebar-collapsed", "false");
  });
  await page.route("**/api/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/public/appearance") {
      return fulfillJson(route, { appearance: currentAppearance, version: 1 });
    }
    if (path === "/api/access/context") {
      return fulfillJson(route, { mode: "LOCAL", kioskAvailable: true, allowedRemoteRoles: [] });
    }
    if (path === "/api/setup/status") return fulfillJson(route, { initialized: true });
    if (path === "/api/auth/me") {
      return fulfillJson(route, {
        id: 1,
        studentNo: "visual-admin",
        name: "视觉验收管理员",
        role: "ADMIN",
        mustChangePassword: false,
      });
    }
    if (path === "/api/settings/weekdays") return fulfillJson(route, []);
    if (path === "/api/settings/duty-periods") return fulfillJson(route, []);
    if (path === "/api/settings/attendance-policy") {
      return fulfillJson(route, { requireDutyDay: false, requireDutyPeriod: false });
    }
    if (path === "/api/settings/appearance") {
      if (failNextAppearanceSave) {
        failNextAppearanceSave = false;
        return fulfillJson(route, { message: "模拟保存失败" }, 500);
      }
      const payload = route.request().postDataJSON() as { appearance: string };
      currentAppearance = payload.appearance;
      return fulfillJson(route, { appearance: payload.appearance, version: 1 });
    }
    return fulfillJson(route, []);
  });
  return {
    failNextSave() {
      failNextAppearanceSave = true;
    },
  };
}

test("switches appearance in place and preserves the active theme on failure", async ({ page }) => {
  const controls = await installApiMocks(page);
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto("/#/admin/settings?section=weekdays");

  await expect(page.locator("html")).toHaveAttribute("data-appearance", "classic");
  const classicStyle = await page.locator(".admin-layout").evaluate(el => ({
    background: getComputedStyle(el).backgroundColor,
    font: getComputedStyle(el).fontFamily,
  }));
  await page.locator(".appearance-choice.is-editorial").click();
  await page.getByRole("button", { name: "应用界面" }).click();
  await page.getByRole("button", { name: "确认应用" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-appearance", "editorial");
  await expect(page).toHaveURL(/#\/admin\/settings\?section=weekdays$/);
  await expect(page.locator(".appearance-choice.is-editorial .appearance-current")).toBeVisible();

  controls.failNextSave();
  await page.locator(".appearance-choice.is-spatial").click();
  await page.getByRole("button", { name: "应用界面" }).click();
  await page.getByRole("button", { name: "确认应用" }).click();
  await expect(page.getByText("模拟保存失败").first()).toBeVisible();
  await expect(page.locator("html")).toHaveAttribute("data-appearance", "editorial");
  await expect(page).toHaveURL(/#\/admin\/settings\?section=weekdays$/);
  // The failed save leaves the chosen draft available for an explicit retry.
  await page.getByRole("button", { name: "确认应用" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-appearance", "spatial");
  await page.reload();
  await expect(page.locator("html")).toHaveAttribute("data-appearance", "spatial");
  await page.locator(".appearance-choice.is-classic").click();
  await page.getByRole("button", { name: "应用界面" }).click();
  await page.getByRole("button", { name: "确认应用" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-appearance", "classic");
  expect(await page.locator(".admin-layout").evaluate(el => ({
    background: getComputedStyle(el).backgroundColor,
    font: getComputedStyle(el).fontFamily,
  }))).toEqual(classicStyle);
});

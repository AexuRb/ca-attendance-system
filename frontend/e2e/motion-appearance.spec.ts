import { expect, test, type Page, type Route } from "@playwright/test";

type Appearance = "CLASSIC" | "EDITORIAL" | "SPATIAL";

function json(route: Route, body: unknown) {
  return route.fulfill({ contentType: "application/json", body: JSON.stringify(body) });
}

async function install(page: Page, appearance: Appearance) {
  await page.addInitScript(() => localStorage.setItem("ca_attendance_token", "motion-review-token"));
  await page.route("**/api/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/public/appearance") return json(route, { appearance, version: 1 });
    if (path === "/api/access/context") return json(route, { mode: "LOCAL", kioskAvailable: true, allowedRemoteRoles: [] });
    if (path === "/api/setup/status") return json(route, { initialized: true });
    if (path === "/api/auth/me") return json(route, { id: 1, name: "动效验收", studentNo: "motion-admin", role: "ADMIN", mustChangePassword: false });
    if (path === "/api/settings/weekdays") return json(route, Array.from({ length: 7 }, (_, index) => ({ weekday: index + 1, weekday_name: `星期${"一二三四五六日"[index]}`, enabled: index < 5 })));
    if (path === "/api/settings/duty-periods") return json(route, [{ startTime: "09:00", endTime: "12:00", enabled: true }]);
    if (path === "/api/schedules") return json(route, [{ id: 1, weekday: 1, startTime: "09:00", endTime: "12:00", title: "示例排班", location: "活动室", enabled: true, assignees: [] }]);
    return json(route, []);
  });
}

for (const [appearance, duration] of [["CLASSIC", 180], ["EDITORIAL", 190], ["SPATIAL", 165]] as const) {
  test(`${appearance} switches weekdays with the theme's motion and respects reduced motion`, async ({ page }) => {
    await install(page, appearance);
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/#/admin/schedules");
    await expect(page.locator("html")).toHaveAttribute("data-appearance", appearance.toLowerCase());
    await expect(page.getByRole("heading", { name: "星期一固定排班" })).toBeVisible();

    const workspace = page.locator(".schedule-focus-workspace");
    expect(await workspace.evaluate((element) => {
      const value = getComputedStyle(element).getPropertyValue("--schedule-motion-time").trim();
      return value.endsWith("ms") ? Number.parseFloat(value) : Number.parseFloat(value) * 1000;
    })).toBe(duration);
    await page.locator('.schedule-focus-day[data-weekday="2"]').click();
    await expect(page.getByRole("heading", { name: "星期二固定排班" })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);

    await page.emulateMedia({ reducedMotion: "reduce" });
    expect(await workspace.evaluate((element) => {
      element.classList.add("schedule-day-next-enter-active");
      const value = getComputedStyle(element).transitionDuration;
      element.classList.remove("schedule-day-next-enter-active");
      return value;
    })).toBe("0s");
    await page.locator('.schedule-focus-day[data-weekday="1"]').click();
    await expect(page.getByRole("heading", { name: "星期一固定排班" })).toBeVisible();
  });
}

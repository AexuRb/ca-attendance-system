import { expect, test, type Page, type Route } from "@playwright/test";

function fulfillJson(route: Route, payload: unknown, status = 200) {
  return route.fulfill({
    status,
    contentType: "application/json",
    body: JSON.stringify(payload),
  });
}

async function installSpatialMocks(page: Page, authenticated = false) {
  if (authenticated) {
    await page.addInitScript(() => {
      localStorage.setItem("ca_attendance_token", "spatial-visual-token");
      localStorage.setItem("ca-admin-section-sidebar-collapsed", "false");
    });
  }

  await page.route("**/api/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/public/appearance") {
      return fulfillJson(route, { appearance: "SPATIAL", version: 1 });
    }
    if (path === "/api/access/context") {
      return fulfillJson(route, { mode: "LOCAL", kioskAvailable: true, allowedRemoteRoles: [] });
    }
    if (path === "/api/setup/status") return fulfillJson(route, { initialized: true });
    if (path === "/api/auth/me") {
      if (!authenticated) return fulfillJson(route, { message: "未登录" }, 401);
      return fulfillJson(route, {
        id: 1,
        studentNo: "visual-admin",
        name: "视觉验收管理员",
        role: "ADMIN",
        mustChangePassword: false,
      });
    }
    if (path === "/api/stats/dashboard") {
      return fulfillJson(route, {
        pendingReviews: 3,
        openAttendances: 2,
        todayAttendanceCount: 8,
        activeRepairCount: 4,
      });
    }
    if (path === "/api/public/schedules/today") {
      return fulfillJson(route, { date: "2026-09-03", weekday: 4, slots: [] });
    }
    if (path === "/api/public/schedules/week") return fulfillJson(route, []);
    return fulfillJson(route, []);
  });
}

test("loads the spatial public experience without horizontal overflow", async ({ page }) => {
  await installSpatialMocks(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/#/login");

  await expect(page.locator("html")).toHaveAttribute("data-appearance", "spatial");
  await expect(page.getByRole("heading", { name: "登录后台" })).toBeVisible();
  const fitsViewport = await page.evaluate(() =>
    document.documentElement.scrollWidth <= document.documentElement.clientWidth,
  );
  expect(fitsViewport).toBe(true);
});

test("loads the spatial admin console without horizontal overflow", async ({ page }) => {
  await installSpatialMocks(page, true);
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto("/#/admin/today");

  await expect(page.locator("html")).toHaveAttribute("data-appearance", "spatial");
  await expect(page.locator(".today-command-page")).toBeVisible();
  const fitsViewport = await page.evaluate(() =>
    document.documentElement.scrollWidth <= document.documentElement.clientWidth,
  );
  expect(fitsViewport).toBe(true);
});

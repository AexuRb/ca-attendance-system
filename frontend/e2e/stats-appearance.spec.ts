import { expect, test, type Page, type Route } from "@playwright/test";

type Appearance = "EDITORIAL" | "SPATIAL";

function json(route: Route, body: unknown) {
  return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body) });
}

const names = ["陈禹杭", "马昌德", "张津铭", "叶思齐", "江亦晨", "欧阳文轩", "林清和", "周可欣", "梁文博", "许知远", "方嘉仪", "沈星河", "陆明川", "唐予安"];
const summary = names.map((name, index) => ({
  userId: index + 1,
  studentNo: `2026${String(index + 1).padStart(6, "0")}`,
  name,
  grade: `${2023 + (index % 4)}级`,
  role: index === 0 ? "ADMIN" : index < 3 ? "PRESIDENT" : index < 7 ? "MINISTER" : "MEMBER",
  attendanceHours: 18 - index * 0.7,
  trainingHours: index % 4 === 0 ? 4 : 2,
  totalHours: 22 - index * 0.55,
  attendanceCount: 8 - (index % 4),
  trainingCount: index % 3,
  dutyCount: 8 - (index % 4),
}));

const days = [
  ["2026-08-31", "周一"], ["2026-09-01", "周二"], ["2026-09-02", "周三"],
  ["2026-09-03", "周四"], ["2026-09-04", "周五"], ["2026-09-05", "周六"], ["2026-09-06", "周日"],
].map(([dutyDate, weekdayName], index) => ({ dutyDate, weekday: index + 1, weekdayName }));

const cells = Object.fromEntries(days.map((day, dayIndex) => [
  day.dutyDate,
  Object.fromEntries(summary.map((member, index) => [String(member.userId), (index + dayIndex) % 4 === 0 ? 0 : 1 + ((index + dayIndex) % 5)])),
]));

async function installMocks(page: Page, appearance: Appearance) {
  await page.addInitScript(() => {
    localStorage.setItem("ca_attendance_token", "stats-appearance-token");
    localStorage.setItem("ca-admin-section-sidebar-collapsed", "true");
  });
  await page.route("**/api/**", async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname === "/api/public/appearance") return json(route, { appearance, version: 1 });
    if (url.pathname === "/api/access/context") return json(route, { mode: "LOCAL", kioskAvailable: true, allowedRemoteRoles: [] });
    if (url.pathname === "/api/setup/status") return json(route, { initialized: true });
    if (url.pathname === "/api/auth/me") return json(route, { id: 1, studentNo: "visual-admin", name: "视觉验收", role: "ADMIN", mustChangePassword: false });
    if (url.pathname === "/api/stats/summary") return json(route, summary);
    if (url.pathname === "/api/stats/weekly-detail") return json(route, { days, users: summary, cells });
    return json(route, []);
  });
}

async function hasDocumentOverflow(page: Page) {
  return page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);
}

for (const appearance of ["EDITORIAL", "SPATIAL"] as Appearance[]) {
  test(`${appearance} keeps weekly statistics and rankings usable across widths`, async ({ page }) => {
    await installMocks(page, appearance);
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/#/admin/stats");

    await expect(page.locator("html")).toHaveAttribute("data-appearance", appearance.toLowerCase());
    await expect(page.getByRole("heading", { name: "值班统计" })).toBeVisible();
    await expect(page.locator(".stats-metrics strong")).toHaveText(["14", "257.9", "93", "13"]);
    await expect(page.locator(".weekly-stats-table tbody tr")).toHaveCount(14);
    expect(await hasDocumentOverflow(page)).toBe(false);
    expect(await page.locator(".weekly-stats-table").evaluate((element) => element.scrollWidth > element.clientWidth)).toBe(true);

    await page.setViewportSize({ width: 960, height: 600 });
    await page.waitForTimeout(400);
    expect(await hasDocumentOverflow(page)).toBe(false);

    await page.getByRole("button", { name: "本月" }).click();
    await expect(page.getByRole("button", { name: "本月" })).toHaveClass(/active/);
    await expect(page.locator(".stats-ranking-table tbody tr")).toHaveCount(14);
    await expect(page.locator(".stats-ranking-table .rank").first()).toHaveText("1");
    expect(await hasDocumentOverflow(page)).toBe(false);
  });
}

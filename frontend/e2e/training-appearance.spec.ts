import { expect, test, type Page, type Route } from "@playwright/test";

type Appearance = "EDITORIAL" | "SPATIAL";

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
}

const titles = ["电脑组装与故障排查", "校园网络基础", "办公软件进阶", "数据安全与备份", "打印机维护", "新成员基础培训"];
const sessions = titles.map((title, index) => ({
  id: index + 1,
  title,
  trainingDate: `2026-09-${String(3 + index * 4).padStart(2, "0")}`,
  startTime: index % 2 ? "16:00:00" : "14:00:00",
  endTime: index % 2 ? "17:30:00" : "16:00:00",
  location: index % 2 ? "实训室 302" : "协会办公室",
  speaker: ["陈禹杭", "马昌德", "张津铭"][index % 3],
  description: "面向协会成员的实操培训，结合常见案例完成演示与练习。",
  status: "ACTIVE",
  participantCount: 18 + index * 3,
  totalDurationHours: (18 + index * 3) * (index % 2 ? 1.5 : 2),
  createdAt: "2026-09-01T10:00:00",
  updatedAt: "2026-09-01T10:00:00",
}));

const participants = Array.from({ length: 20 }, (_, index) => ({
  id: index + 1,
  sessionId: 1,
  userId: index + 2,
  studentNo: `2026${String(index + 1).padStart(6, "0")}`,
  name: ["陈禹杭", "马昌德", "张津铭", "叶思齐", "江亦晨", "欧阳文轩"][index % 6],
  durationHours: index % 3 ? 2 : 1.5,
  remark: index % 4 === 0 ? "完成实操并提交记录" : "",
}));

async function installMocks(page: Page, appearance: Appearance) {
  await page.addInitScript(() => {
    localStorage.setItem("ca_attendance_token", "training-appearance-token");
    localStorage.setItem("ca-admin-section-sidebar-collapsed", "true");
  });
  await page.route("**/api/**", async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname === "/api/public/appearance") return json(route, { appearance, version: 1 });
    if (url.pathname === "/api/access/context") return json(route, { mode: "LOCAL", kioskAvailable: true, allowedRemoteRoles: [] });
    if (url.pathname === "/api/setup/status") return json(route, { initialized: true });
    if (url.pathname === "/api/auth/me") return json(route, { id: 1, studentNo: "visual-admin", name: "视觉验收", role: "ADMIN", mustChangePassword: false });
    if (url.pathname === "/api/trainings/page") return json(route, { items: sessions, total: 24, page: 1, pageSize: 20, hasMore: true });
    if (/^\/api\/trainings\/\d+\/participants\/page$/.test(url.pathname)) return json(route, { items: participants, total: 47, page: 1, pageSize: 20, hasMore: true });
    return json(route, []);
  });
}

async function hasDocumentOverflow(page: Page) {
  return page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);
}

for (const appearance of ["EDITORIAL", "SPATIAL"] as Appearance[]) {
  test(`${appearance} keeps training history and long participant lists usable`, async ({ page }) => {
    await installMocks(page, appearance);
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/#/admin/trainings");

    await expect(page.locator("html")).toHaveAttribute("data-appearance", appearance.toLowerCase());
    await expect(page.getByRole("heading", { name: "培训记录" })).toBeVisible();
    await expect(page.locator(".training-ribbon-event")).toHaveCount(6);
    await expect(page.locator(".training-participant-row")).toHaveCount(20);
    await expect(page.locator(".training-participant-pagination")).toContainText("共 47 人");
    expect(await hasDocumentOverflow(page)).toBe(false);

    await page.locator(".training-ribbon-event").nth(1).click();
    await expect(page.locator(".training-ribbon-event").nth(1)).toHaveClass(/active/);
    await expect(page.locator(".training-session-heading h2")).toHaveText("校园网络基础");

    await page.setViewportSize({ width: 960, height: 600 });
    await page.reload();
    await expect(page.locator(".training-participant-row")).toHaveCount(20);
    expect(await hasDocumentOverflow(page)).toBe(false);
  });
}

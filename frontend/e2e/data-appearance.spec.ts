import { expect, test, type Page, type Route } from "@playwright/test";

type Appearance = "EDITORIAL" | "SPATIAL";

const sourceLabels = ["成员名册", "值班记录", "培训记录", "固定排班", "维修事务", "操作日志"];
const sources = sourceLabels.map((label, index) => ({
  id: `source-${index + 1}`,
  label,
  fields: [
    { id: "studentNo", label: "学号", defaultSelected: true },
    { id: "name", label: "姓名", defaultSelected: true },
    { id: "detail", label: "详细信息", defaultSelected: true },
  ],
  filters: [],
}));

const datasetTotals = [286, 1482, 27, 42, 163, 3956];
const datasets = sourceLabels.map((label, index) => ({
  key: `dataset-${index + 1}`,
  label,
  total: datasetTotals[index],
  detail: `${datasetTotals[index]} 条业务数据`,
}));

const backups = Array.from({ length: 9 }, (_, index) => {
  const createdAt = new Date(Date.UTC(2026, 8, 3 - index, 11, 30 - index));
  return {
    filename: index === 0
      ? "ca-attendance-complete-backup-20260903-193055.zip"
      : `backup-${createdAt.toISOString().slice(0, 10).replaceAll("-", "")}-${1930 - index}.zip`,
    size: 8_291_456 - index * 131_072,
    createdAt: createdAt.toISOString(),
  };
});

const recycle = Array.from({ length: 12 }, (_, index) => ({
  id: index + 1,
  caseNo: `WX-2026-${String(817 + index).padStart(4, "0")}`,
  ownerName: `测试成员${index + 1}`,
  deviceBrand: ["联想", "华硕", "戴尔"][index % 3],
  deviceModel: ["小新 Pro 14", "天选 5", "Inspiron 16"][index % 3],
  deviceType: "笔记本电脑",
  deletedAt: new Date(Date.UTC(2026, 7, 29 - index, 8, 42)).toISOString(),
  deletedByName: ["测试管理员一", "测试管理员二", "测试管理员三"][index % 3],
}));

function fulfillJson(route: Route, body: unknown) {
  return route.fulfill({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify(body),
  });
}

async function installApiMocks(page: Page, appearance: Appearance) {
  await page.addInitScript(() => {
    localStorage.setItem("ca_attendance_token", "data-appearance-token");
    localStorage.setItem("ca-admin-section-sidebar-collapsed", "true");
  });
  await page.route("**/api/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/public/appearance") {
      return fulfillJson(route, { appearance, version: 1 });
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
    if (path === "/api/exports/options") return fulfillJson(route, { sources });
    if (path === "/api/maintenance/summary") {
      return fulfillJson(route, {
        datasets,
        backups: {
          count: backups.length,
          totalSize: backups.reduce((sum, item) => sum + item.size, 0),
          latestFilename: backups[0].filename,
          latestCreatedAt: backups[0].createdAt,
          latestSize: backups[0].size,
        },
        generatedAt: "2026-09-03T19:42:00Z",
      });
    }
    if (path === "/api/maintenance/backups") return fulfillJson(route, backups);
    if (path === "/api/repairs/recycle-bin") return fulfillJson(route, recycle);
    return fulfillJson(route, []);
  });
}

async function expectNoDocumentOverflow(page: Page) {
  const fits = await page.evaluate(() =>
    document.documentElement.scrollWidth <= document.documentElement.clientWidth,
  );
  expect(fits).toBe(true);
}

for (const appearance of ["EDITORIAL", "SPATIAL"] as Appearance[]) {
  test(`${appearance} keeps dense data workspaces usable across viewports`, async ({ page }) => {
    await installApiMocks(page, appearance);
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/#/admin/data?tab=export");

    await expect(page.locator("html")).toHaveAttribute("data-appearance", appearance.toLowerCase());
    await expect(page.getByRole("tab", { name: "自定义导出" })).toHaveAttribute("aria-selected", "true");
    await expectNoDocumentOverflow(page);

    await page.getByRole("tab", { name: "本机备份" }).click();
    await expect(page.locator(".data-backup-table tbody tr")).toHaveCount(9);
    await expect(page.getByText("ca-attendance-complete-backup-20260903-193055.zip")).toBeVisible();
    await page.getByRole("button", { name: /^查看备份详情/ }).first().click();
    await expect(page.locator("#data-backup-details")).toHaveAttribute("role", "dialog");
    await expect(page.locator("#data-backup-details")).toHaveAttribute("aria-modal", "true");
    await expectNoDocumentOverflow(page);
    await page.getByRole("button", { name: "关闭备份详情" }).click();

    await page.getByRole("tab", { name: "维修回收站" }).click();
    await expect(page.locator(".data-recycle-table tbody tr")).toHaveCount(12);
    await expectNoDocumentOverflow(page);

    await page.setViewportSize({ width: 1440, height: 900 });
    await page.getByRole("tab", { name: "本机备份" }).click();
    await page.getByRole("button", { name: /^查看备份详情/ }).first().click();
    await expect(page.locator("#data-backup-details")).toHaveAttribute("role", "complementary");
    await expectNoDocumentOverflow(page);
  });
}

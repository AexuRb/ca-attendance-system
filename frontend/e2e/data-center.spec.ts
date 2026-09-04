import { expect, test, type Page, type Route } from "@playwright/test";

const exportOptions = {
  sources: [{
    id: "members",
    label: "成员名册",
    fields: [
      { id: "studentNo", label: "学号", defaultSelected: true },
      { id: "name", label: "姓名", defaultSelected: true },
      { id: "college", label: "学院", defaultSelected: true },
    ],
    filters: [],
  }],
};

const summary = {
  datasets: [
    { key: "users", label: "成员名册", total: 286, detail: "286 名协会成员" },
    { key: "attendance_records", label: "值班记录", total: 1482, detail: "本学年累计记录" },
    { key: "training_sessions", label: "培训记录", total: 27, detail: "近一年培训场次" },
    { key: "repair_cases", label: "维修事务", total: 163, detail: "累计受理事务" },
  ],
  backups: {
    count: 3,
    totalSize: 17_825_792,
    latestFilename: "backup-20260830-1942.zip",
    latestCreatedAt: "2026-08-30T19:42:00",
    latestSize: 6_291_456,
  },
  generatedAt: "2026-08-30T20:25:00",
};

const backups = Array.from({ length: 3 }, (_, index) => ({
  filename: `backup-202608${30 - index}-1942.zip`,
  size: 6_291_456 - index * 131_072,
  createdAt: `2026-08-${30 - index}T19:42:00`,
}));

const recycle = [{
  id: 1,
  caseNo: "WX-2026-0817",
  deviceBrand: "联想",
  deviceModel: "小新 Pro 14",
  deviceType: "笔记本电脑",
  deletedAt: "2026-08-29T16:42:00",
  deletedByName: "测试管理员",
}];

function fulfillJson(route: Route, payload: unknown) {
  return route.fulfill({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify(payload),
  });
}

async function installApiMocks(page: Page) {
  await page.addInitScript(() => {
    localStorage.setItem("ca_attendance_token", "data-center-visual-token");
    localStorage.setItem("ca-admin-section-sidebar-collapsed", "false");
  });
  await page.route("**/api/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/public/appearance") {
      return fulfillJson(route, { appearance: "EDITORIAL", version: 1 });
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
    if (path === "/api/exports/options") return fulfillJson(route, exportOptions);
    if (path === "/api/maintenance/summary") return fulfillJson(route, summary);
    if (path === "/api/maintenance/backups") return fulfillJson(route, backups);
    if (path === "/api/repairs/recycle-bin") return fulfillJson(route, recycle);
    return fulfillJson(route, []);
  });
}

test.beforeEach(async ({ page }) => {
  await installApiMocks(page);
});

test("activates the editorial appearance before rendering the workspace", async ({ page }) => {
  await page.goto("/#/admin/data?tab=export");
  await expect(page.locator("html")).toHaveAttribute("data-appearance", "editorial");
  await expect(page.locator(".data-center-page")).toBeVisible();
});

test("keeps the data workspaces inside the viewport across boundary widths", async ({ page }) => {
  const widths = [1440, 1281, 1280, 1180, 980, 950, 901, 900, 820, 390];

  for (const width of widths) {
    await page.setViewportSize({ width, height: 900 });
    await page.goto("/#/admin/data?tab=export");
    await expect(page.locator(".data-center-page")).toBeVisible();
    const fitsViewport = await page.evaluate(() =>
      document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    );
    expect(fitsViewport, `horizontal overflow at ${width}px`).toBe(true);
  }

  await page.setViewportSize({ width: 950, height: 900 });
  const narrowColumns = await page.locator(".data-export-overview").evaluate((element) =>
    getComputedStyle(element).gridTemplateColumns.split(" ").length,
  );
  expect(narrowColumns).toBeLessThanOrEqual(2);
});

test("switches drawer behavior without clipping the desktop backup table", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto("/#/admin/data?tab=export");
  const adjustButton = page.getByRole("button", { name: "调整条件" });
  await expect(adjustButton).toHaveAttribute("aria-controls", "data-export-config");
  await expect(adjustButton).not.toHaveAttribute("aria-haspopup", "dialog");
  await adjustButton.click();
  await expect(page.locator("#data-export-config")).toHaveAttribute("role", "complementary");
  await page.getByRole("button", { name: "收起导出配置" }).click();

  await page.getByRole("tab", { name: "本机备份" }).click();
  await page.getByRole("button", { name: /^查看备份详情/ }).first().click();
  await expect(page.locator("#data-backup-details")).toHaveAttribute("role", "complementary");
  await expect(page.locator(".data-backup-table th").last()).toBeHidden();
  const tableFits = await page.locator(".data-backup-list-pane").evaluate((element) =>
    element.scrollWidth <= element.clientWidth,
  );
  expect(tableFits).toBe(true);

  await page.getByRole("button", { name: "关闭备份详情" }).click();
  await page.setViewportSize({ width: 1180, height: 900 });
  await page.getByRole("button", { name: /^查看备份详情/ }).first().click();
  await expect(page.locator("#data-backup-details")).toHaveAttribute("role", "dialog");
  await expect(page.locator("#data-backup-details")).toHaveAttribute("aria-modal", "true");
  await expect(page.locator(".data-drawer-backdrop")).toHaveAttribute("aria-hidden", "true");
});

test("restores tabs through browser history", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto("/#/admin/data?tab=export");
  await page.getByRole("tab", { name: "本机备份" }).click();
  await page.getByRole("tab", { name: "维修回收站" }).click();
  await page.goBack();
  await expect(page.getByRole("tab", { name: "本机备份" })).toHaveAttribute("aria-selected", "true");
  await page.goBack();
  await expect(page.getByRole("tab", { name: "自定义导出" })).toHaveAttribute("aria-selected", "true");
});

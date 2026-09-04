import { expect, test, type Page, type Route } from "@playwright/test";

type Appearance = "EDITORIAL" | "SPATIAL";
type RepairStatus = "REPAIRING" | "COMPLETED" | "CANCELED";

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
}

function repair(id: number, status: RepairStatus) {
  return {
    id,
    caseNo: `CA-2026-${String(8300 + id).padStart(5, "0")}`,
    agreementType: id % 3 === 0 ? "DISCLAIMER" : "REPAIR",
    ownerName: ["陈禹杭", "马昌德", "张津铭", "叶思齐"][id % 4],
    ownerPhone: `1380000${String(id).padStart(4, "0")}`,
    deviceType: ["联想笔记本", "自组装台式机", "显示器", "移动硬盘"][id % 4],
    deviceBrand: "",
    deviceModel: "",
    accessories: id % 2 ? "电源适配器" : "无",
    faultDescription: ["无法进入系统", "开机后无显示", "间歇性闪屏", "无法识别分区"][id % 4],
    serviceDescription: "正在检测硬件并备份必要数据",
    dataBackupConfirmed: true,
    riskAcknowledged: true,
    privacyAcknowledged: true,
    status,
    handlerName: ["叶思齐", "陈禹杭", "马昌德", "张津铭"][id % 4],
    receivedAt: `2026-08-${String(10 + (id % 18)).padStart(2, "0")}T14:20:00`,
    completedAt: status === "REPAIRING" ? null : "2026-09-01T16:40:00",
    updatedAt: "2026-09-02T17:15:00",
    remark: id % 2 ? "已联系送修人" : "",
  };
}

async function installMocks(page: Page, appearance: Appearance) {
  await page.addInitScript(() => {
    localStorage.setItem("ca_attendance_token", "repair-appearance-token");
    localStorage.setItem("ca-admin-section-sidebar-collapsed", "true");
  });
  await page.route("**/api/**", async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname === "/api/public/appearance") return json(route, { appearance, version: 1 });
    if (url.pathname === "/api/access/context") return json(route, { mode: "LOCAL", kioskAvailable: true, allowedRemoteRoles: [] });
    if (url.pathname === "/api/setup/status") return json(route, { initialized: true });
    if (url.pathname === "/api/auth/me") return json(route, { id: 1, studentNo: "visual-admin", name: "视觉验收", role: "ADMIN", mustChangePassword: false });
    if (url.pathname === "/api/repairs/handler-candidates") return json(route, []);
    if (url.pathname === "/api/repairs") {
      const currentStatus = (url.searchParams.get("status") || "REPAIRING") as RepairStatus;
      const totals = { REPAIRING: 8, COMPLETED: 1264, CANCELED: 37 };
      return json(route, {
        items: Array.from({ length: currentStatus === "REPAIRING" ? 8 : 20 }, (_, index) => repair(index + 1, currentStatus)),
        total: totals[currentStatus],
        page: 1,
        pageSize: 20,
        hasMore: totals[currentStatus] > 20,
        statusCounts: totals,
      });
    }
    return json(route, []);
  });
}

async function hasDocumentOverflow(page: Page) {
  return page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);
}

for (const appearance of ["EDITORIAL", "SPATIAL"] as Appearance[]) {
  test(`${appearance} keeps a populated repair ledger usable across widths`, async ({ page }) => {
    await installMocks(page, appearance);
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/#/admin/repairs");

    await expect(page.locator("html")).toHaveAttribute("data-appearance", appearance.toLowerCase());
    await expect(page.getByRole("heading", { name: "维修事务" })).toBeVisible();
    await expect(page.locator(".repair-status-tabs b")).toHaveText(["8", "1,264", "37"]);
    await expect(page.locator(".repair-ledger-row")).toHaveCount(8);
    expect(await hasDocumentOverflow(page)).toBe(false);
    expect(await page.locator(".repair-ledger-row td:last-child").first().evaluate((element) => getComputedStyle(element).position)).toBe("static");

    await page.locator(".repair-ledger-row").first().click();
    await expect(page.locator(".repair-detail-drawer")).toBeVisible();
    await page.getByRole("button", { name: "关闭维修详情" }).click();

    await page.setViewportSize({ width: 960, height: 600 });
    await page.reload();
    await expect(page.locator(".repair-ledger-row")).toHaveCount(8);
    expect(await hasDocumentOverflow(page)).toBe(false);
    expect(await page.locator(".repair-ledger-row td:last-child").first().evaluate((element) => getComputedStyle(element).position)).toBe("sticky");

    await page.getByRole("tab", { name: /已完成/ }).click();
    await expect(page.locator(".repair-ledger-row")).toHaveCount(20);
    await expect(page.locator(".repair-workspace-pagination")).toContainText("共 1264 项");
  });
}

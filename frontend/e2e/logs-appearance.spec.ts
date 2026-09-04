import { expect, test, type Page, type Route } from "@playwright/test";

type Appearance = "EDITORIAL" | "SPATIAL";

const longReason = "虚构操作原因 ".repeat(48).trim();
const longRawData = `fixture-long-raw-${"x".repeat(720)}`;

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({
    status,
    contentType: "application/json",
    body: JSON.stringify(body),
  });
}

function createLog(index: number) {
  const isFirst = index === 1;
  return {
    id: index,
    operatorStudentNo: `fixture-${String(index).padStart(4, "0")}`,
    operatorName: `虚构管理员${index}`,
    actionType: "UPDATE_USER",
    targetType: "users",
    targetId: index,
    beforeData: JSON.stringify(
      isFirst ? { detail: `before-${longRawData}` } : { marker: `before-${index}` },
    ),
    afterData: JSON.stringify(
      isFirst
        ? { detail: longRawData, note: longReason }
        : { marker: `after-${index}`, note: longReason },
    ),
    reason: `${longReason}（第 ${index} 条）`,
    createdAt: `2026-08-${String(1 + ((index - 1) % 20)).padStart(2, "0")}T12:${String(index).padStart(2, "0")}:00`,
  };
}

const logs = Array.from({ length: 21 }, (_, index) => createLog(index + 1));

async function installMocks(page: Page, appearance: Appearance) {
  const state = { cleared: false, deleteRequests: 0 };

  await page.addInitScript(() => {
    localStorage.setItem("ca_attendance_token", "logs-appearance-fixture");
    localStorage.setItem("ca-admin-section-sidebar-collapsed", "true");
  });

  await page.route("**/api/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());

    if (url.pathname === "/api/public/appearance") {
      return json(route, { appearance, version: 1 });
    }
    if (url.pathname === "/api/access/context") {
      return json(route, {
        mode: "LOCAL",
        kioskAvailable: true,
        allowedRemoteRoles: [],
      });
    }
    if (url.pathname === "/api/setup/status") return json(route, { initialized: true });
    if (url.pathname === "/api/auth/me") {
      return json(route, {
        id: 1,
        studentNo: "fixture-admin",
        name: "虚构管理员",
        role: "ADMIN",
        mustChangePassword: false,
      });
    }

    if (url.pathname === "/api/logs") {
      if (request.method() === "DELETE") {
        state.deleteRequests += 1;
        state.cleared = true;
        return route.fulfill({ status: 204 });
      }

      const requestedPage = Number(url.searchParams.get("page") || "1");
      const items = state.cleared
        ? []
        : requestedPage === 2
          ? logs.slice(20)
          : logs.slice(0, 20);
      return json(route, {
        items,
        total: state.cleared ? 0 : logs.length,
        page: requestedPage,
        pageSize: 20,
      });
    }

    return json(route, []);
  });

  return state;
}

async function expectNoDocumentOverflow(page: Page, width: number) {
  const dimensions = await page.evaluate(() => ({
    clientWidth: document.documentElement.clientWidth,
    scrollWidth: document.documentElement.scrollWidth,
  }));
  expect(
    dimensions.scrollWidth,
    `document horizontal overflow at ${width}px`,
  ).toBeLessThanOrEqual(dimensions.clientWidth);
}

function expectLogQuery(url: string, expected: Record<string, string>) {
  expect(Object.fromEntries(new URL(url).searchParams.entries())).toEqual(expected);
}

function isLogsRequest(request: { method(): string; url(): string }) {
  const url = new URL(request.url());
  return request.method() === "GET" && url.pathname === "/api/logs";
}

for (const appearance of ["EDITORIAL", "SPATIAL"] as Appearance[]) {
  test(`${appearance} keeps long operation logs usable and actions correct`, async ({ page }) => {
    const state = await installMocks(page, appearance);
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto("/#/admin/logs");

    await expect(page.locator("html")).toHaveAttribute(
      "data-appearance",
      appearance.toLowerCase(),
    );
    await expect(page.locator(".logs-page")).toBeVisible();
    await expect(page.locator(".timeline-list article")).toHaveCount(20);
    await expect(page.getByText("共 21 条日志", { exact: true })).toBeVisible();
    await expect(page.locator(".logs-page .timeline-list")).toHaveCSS("box-shadow", "none");
    await expect(page.locator(".logs-page .log-main p").first()).toHaveCSS(
      "font-size", appearance === "EDITORIAL" ? "16px" : "17px",
    );
    await expect(page.locator(".logs-page .status-badge").first()).toHaveCSS("font-size", "14px");
    await expect(page.locator(".logs-page .page-actions")).toHaveCSS("background-color", "rgba(0, 0, 0, 0)");

    for (const viewport of [
      { width: 1440, height: 900 },
      { width: 1152, height: 720 },
      { width: 960, height: 600 },
      { width: 390, height: 844 },
    ]) {
      await page.setViewportSize(viewport);
      await expect(page.locator(".timeline-list article")).toHaveCount(20);
      await expectNoDocumentOverflow(page, viewport.width);
    }

    const firstLog = page.locator(".timeline-list article").first();
    await firstLog.getByRole("button", { name: "查看变更详情" }).click();
    const detailDialog = page.getByRole("dialog", { name: "操作详情" });
    await expect(detailDialog.locator(".audit-detail-surface")).toBeVisible();
    await expectNoDocumentOverflow(page, 390);

    const rawDetails = detailDialog.locator("details.audit-raw-details");
    await rawDetails.locator("summary").click();
    await expect(rawDetails).toHaveAttribute("open", "");
    await expect(rawDetails.locator("pre")).toHaveCount(2);
    await expect(rawDetails.locator("pre").first()).toContainText("fixture-long-raw-");
    await expectNoDocumentOverflow(page, 390);
    expect(await detailDialog.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
    await detailDialog.locator(".modal-footer").getByRole("button", { name: "关闭" }).click();

    const filterValues = {
      keyword: "虚构筛选",
      actionType: "UPDATE_USER",
      from: "2026-08-03",
      to: "2026-08-21",
    };
    await page.locator('input[name="logKeyword"]').fill(filterValues.keyword);
    await page.locator('select[name="logActionType"]').selectOption(filterValues.actionType);
    await page.locator('input[name="logFrom"]').fill(filterValues.from);
    await page.locator('input[name="logTo"]').fill(filterValues.to);

    const filterRequestPromise = page.waitForRequest(
      (request) => {
        if (!isLogsRequest(request)) return false;
        const url = new URL(request.url());
        return url.searchParams.get("keyword") === filterValues.keyword;
      },
    );
    await page.getByRole("button", { name: "查询", exact: true }).click();
    const filterRequest = await filterRequestPromise;
    expectLogQuery(filterRequest.url(), {
      ...filterValues,
      page: "1",
      pageSize: "20",
    });
    await expect(page.locator(".timeline-list article")).toHaveCount(20);

    const nextPageRequestPromise = page.waitForRequest(
      (request) => {
        if (!isLogsRequest(request)) return false;
        return new URL(request.url()).searchParams.get("page") === "2";
      },
    );
    await page.getByRole("button", { name: "下一页", exact: true }).click();
    const nextPageRequest = await nextPageRequestPromise;
    expectLogQuery(nextPageRequest.url(), {
      ...filterValues,
      page: "2",
      pageSize: "20",
    });
    await expect(page.locator(".timeline-list article")).toHaveCount(1);

    await page.getByRole("button", { name: "清空日志", exact: true }).click();
    const clearDialog = page.getByRole("dialog", { name: "清空操作日志" });
    await clearDialog.getByRole("button", { name: "取消", exact: true }).click();
    await expect(clearDialog).toHaveCount(0);
    expect(state.deleteRequests).toBe(0);

    await page.getByRole("button", { name: "清空日志", exact: true }).click();
    const confirmedClearDialog = page.getByRole("dialog", { name: "清空操作日志" });
    const deleteResponsePromise = page.waitForResponse((response) => {
      const request = response.request();
      return request.method() === "DELETE" && new URL(request.url()).pathname === "/api/logs";
    });
    await confirmedClearDialog.getByRole("button", { name: "备份并清空", exact: true }).click();
    const deleteResponse = await deleteResponsePromise;
    expect(deleteResponse.status()).toBe(204);
    expect(state.deleteRequests).toBe(1);
    await expect(page.getByText("暂无操作日志", { exact: true })).toBeVisible();
    await expect(page.locator(".timeline-list")).toHaveCount(0);
  });
}

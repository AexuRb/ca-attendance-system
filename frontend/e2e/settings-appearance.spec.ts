import { expect, test, type Page, type Route } from "@playwright/test";

type AppearanceId = "EDITORIAL" | "SPATIAL";
type UserRole = "ADMIN" | "PRESIDENT";

interface WeekdayFixture {
  weekday: number;
  weekday_name: string;
  enabled: boolean;
}

interface PeriodFixture {
  startTime: string;
  endTime: string;
  enabled: boolean;
}

interface PolicyFixture {
  requireDutyDay: boolean;
  requireDutyPeriod: boolean;
}

interface SettingsFixture {
  appearance: AppearanceId;
  role: UserRole;
  weekdays: WeekdayFixture[];
  periods: PeriodFixture[];
  policy: PolicyFixture;
}

const weekdayNames = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"];

function createFixture(appearance: AppearanceId, role: UserRole): SettingsFixture {
  return {
    appearance,
    role,
    weekdays: weekdayNames.map((weekday_name, index) => ({
      weekday: index + 1,
      weekday_name,
      enabled: index < 5,
    })),
    periods: [
      { startTime: "09:00", endTime: "11:00", enabled: false },
      { startTime: "14:00", endTime: "16:00", enabled: true },
      { startTime: "16:00", endTime: "18:00", enabled: true },
    ],
    policy: { requireDutyDay: true, requireDutyPeriod: false },
  };
}

function fulfillJson(route: Route, payload: unknown, status = 200) {
  return route.fulfill({
    status,
    contentType: "application/json",
    body: JSON.stringify(payload),
  });
}

async function installApiMocks(
  page: Page,
  appearance: AppearanceId,
  role: UserRole,
) {
  const state = createFixture(appearance, role);

  await page.addInitScript(() => {
    localStorage.setItem("ca_attendance_token", "settings-appearance-fixture");
    localStorage.setItem("ca-admin-section-sidebar-collapsed", "true");
  });

  await page.route("**/api/**", async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;

    if (path === "/api/public/appearance") {
      return fulfillJson(route, { appearance: state.appearance, version: 1 });
    }
    if (path === "/api/access/context") {
      return fulfillJson(route, {
        mode: "LOCAL",
        kioskAvailable: true,
        allowedRemoteRoles: [],
      });
    }
    if (path === "/api/setup/status") return fulfillJson(route, { initialized: true });
    if (path === "/api/auth/me") {
      return fulfillJson(route, {
        id: 101,
        studentNo: "fixture-settings-user",
        name: role === "ADMIN" ? "演示管理员" : "演示会长",
        role,
        mustChangePassword: false,
      });
    }

    if (path === "/api/settings/weekdays") {
      if (request.method() === "PUT") {
        const body = request.postDataJSON() as { enabledWeekdays: number[] };
        const enabledWeekdays = new Set(body.enabledWeekdays);
        state.weekdays = state.weekdays.map((day) => ({
          ...day,
          enabled: enabledWeekdays.has(day.weekday),
        }));
      }
      return fulfillJson(route, state.weekdays);
    }

    if (path === "/api/settings/duty-periods") {
      if (request.method() === "PUT") {
        const body = request.postDataJSON() as { periods: PeriodFixture[] };
        state.periods = body.periods.map((period) => ({ ...period }));
      }
      return fulfillJson(route, state.periods);
    }

    if (path === "/api/settings/attendance-policy") {
      if (request.method() === "PUT") {
        const body = request.postDataJSON() as PolicyFixture;
        state.policy = {
          requireDutyDay: body.requireDutyDay === true,
          requireDutyPeriod: body.requireDutyPeriod === true,
        };
      }
      return fulfillJson(route, state.policy);
    }

    if (path === "/api/settings/appearance") {
      if (request.method() === "PUT") {
        const body = request.postDataJSON() as { appearance: AppearanceId };
        state.appearance = body.appearance;
      }
      return fulfillJson(route, { appearance: state.appearance, version: 1 });
    }

    return fulfillJson(route, []);
  });

  return state;
}

async function openSettings(page: Page, appearance: AppearanceId) {
  await page.goto("/#/admin/settings");
  await expect(page.locator("html")).toHaveAttribute(
    "data-appearance",
    appearance.toLowerCase(),
  );
  await expect(page.locator(".weekday-calendar-day")).toHaveCount(7);
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

async function expectPut(
  page: Page,
  path: string,
  action: () => Promise<void>,
  expectedBody: unknown,
) {
  const responsePromise = page.waitForResponse((response) => {
    const request = response.request();
    return (
      request.method() === "PUT" &&
      new URL(request.url()).pathname === path
    );
  });

  await action();
  const response = await responsePromise;
  expect(response.ok()).toBe(true);
  expect(response.request().postDataJSON()).toEqual(expectedBody);
}

async function reloadSettings(page: Page, appearance: AppearanceId) {
  await page.reload();
  await expect(page.locator("html")).toHaveAttribute(
    "data-appearance",
    appearance.toLowerCase(),
  );
  await expect(page.locator(".weekday-calendar-day")).toHaveCount(7);
}

for (const appearance of ["EDITORIAL", "SPATIAL"] as const) {
  test.describe(`${appearance} settings`, () => {
    test("keeps the settings workspace within both boundary viewports and persists edits", async ({
      page,
    }) => {
      await installApiMocks(page, appearance, "ADMIN");

      for (const width of [1440, 960, 390]) {
        await page.setViewportSize({ width, height: width === 390 ? 844 : width === 960 ? 600 : 900 });
        await openSettings(page, appearance);
        await expectNoDocumentOverflow(page, width);

        if (appearance === "EDITORIAL" && width === 1440) {
          await expect
            .poll(() =>
              page.locator(".duty-time-side").evaluate(
                (element) => getComputedStyle(element).display,
              ),
            )
            .toBe("contents");
        }

        if (appearance === "SPATIAL" && width === 1440) {
          await expect
            .poll(() =>
              page
                .locator(
                  '.weekday-calendar-day[aria-pressed="true"] .weekday-calendar-leaf',
                )
                .first()
                .evaluate((element) => getComputedStyle(element).backgroundColor),
            )
            .toBe("rgb(0, 102, 204)");
        }
      }

      await page.setViewportSize({ width: 1440, height: 900 });
      await openSettings(page, appearance);

      const saturday = page.locator('.weekday-calendar-day[data-weekday="6"]');
      await expect(saturday).toHaveAttribute("aria-pressed", "false");
      await saturday.click();
      await expect(saturday).toHaveAttribute("aria-pressed", "true");
      await expectPut(
        page,
        "/api/settings/weekdays",
        () => page.getByRole("button", { name: "保存星期" }).click(),
        { enabledWeekdays: [1, 2, 3, 4, 5, 6] },
      );
      await reloadSettings(page, appearance);
      await expect(
        page.locator('.weekday-calendar-day[data-weekday="6"]'),
      ).toHaveAttribute("aria-pressed", "true");

      const requireDutyDay = page.locator('input[name="requireDutyDay"]');
      const requireDutyPeriod = page.locator('input[name="requireDutyPeriod"]');
      await requireDutyDay.uncheck();
      await requireDutyPeriod.check();
      await expectPut(
        page,
        "/api/settings/attendance-policy",
        () => page.getByRole("button", { name: "保存规则" }).click(),
        { requireDutyDay: false, requireDutyPeriod: true },
      );
      await reloadSettings(page, appearance);
      await expect(page.locator('input[name="requireDutyDay"]')).not.toBeChecked();
      await expect(page.locator('input[name="requireDutyPeriod"]')).toBeChecked();

      await page.getByRole("button", { name: "新增时段" }).click();
      await expect(page.locator(".duty-period-tab")).toHaveCount(4);

      const startInput = page.locator('.duty-period-fields input[type="time"]').first();
      const endInput = page.locator('.duty-period-fields input[type="time"]').nth(1);
      await startInput.fill("14:00");
      await endInput.fill("16:00");
      await expect(page.locator(".duty-period-form")).toHaveClass(/invalid/);
      await expect(page.getByRole("alert")).toContainText("值班时间段不能重复");
      await expect(page.locator(".duty-period-tab.conflict")).toHaveCount(2);
      await expect(page.locator(".duty-calendar-block.conflict")).toHaveCount(2);

      const conflictBackground = await page
        .locator(".duty-calendar-block.conflict")
        .first()
        .evaluate((element) => getComputedStyle(element).backgroundColor);
      const normalBackground = await page
        .locator(".duty-calendar-block:not(.conflict):not(.disabled)")
        .first()
        .evaluate((element) => getComputedStyle(element).backgroundColor);
      expect(conflictBackground).not.toBe(normalBackground);
      await expect(page.getByRole("button", { name: "保存时间段" })).toBeDisabled();

      await startInput.fill("20:00");
      await endInput.fill("22:00");
      await expect(page.locator('.duty-period-enabled input')).toBeChecked();
      await page.locator('.duty-period-enabled').click();
      await expect(page.locator('.duty-period-enabled input')).not.toBeChecked();
      await expect(page.locator(".duty-period-form")).not.toHaveClass(/invalid/);
      await expect(page.locator(".duty-period-tab").nth(3)).toHaveClass(/disabled/);
      await expect(page.getByRole("button", { name: "保存时间段" })).toBeEnabled();
      await expectPut(
        page,
        "/api/settings/duty-periods",
        () => page.getByRole("button", { name: "保存时间段" }).click(),
        {
          periods: [
            { startTime: "09:00", endTime: "11:00", enabled: false },
            { startTime: "14:00", endTime: "16:00", enabled: true },
            { startTime: "16:00", endTime: "18:00", enabled: true },
            { startTime: "20:00", endTime: "22:00", enabled: false },
          ],
        },
      );

      await page.getByRole("button", { name: "删除时段" }).click();
      await expect(page.locator(".duty-period-tab")).toHaveCount(3);
      await expect(page.locator(".duty-period-tab").filter({ hasText: "20:00" })).toHaveCount(0);
      await expectPut(
        page,
        "/api/settings/duty-periods",
        () => page.getByRole("button", { name: "保存时间段" }).click(),
        {
          periods: [
            { startTime: "09:00", endTime: "11:00", enabled: false },
            { startTime: "14:00", endTime: "16:00", enabled: true },
            { startTime: "16:00", endTime: "18:00", enabled: true },
          ],
        },
      );
      await reloadSettings(page, appearance);
      await expect(page.locator(".duty-period-tab")).toHaveCount(3);
      await expect(page.locator(".duty-period-tab").filter({ hasText: "20:00" })).toHaveCount(0);
    });

    test("keeps attendance policy controls read-only for PRESIDENT", async ({ page }) => {
      await installApiMocks(page, appearance, "PRESIDENT");
      await page.setViewportSize({ width: 1440, height: 900 });
      await openSettings(page, appearance);

      const policyPanel = page.locator("#settings-policy");
      await expect(policyPanel.locator(".duty-readonly-state")).toHaveText("仅管理员可修改");
      await expect(policyPanel.locator('input[name="requireDutyDay"]')).toBeDisabled();
      await expect(policyPanel.locator('input[name="requireDutyPeriod"]')).toBeDisabled();
      await expect(policyPanel.getByRole("button", { name: "保存规则" })).toHaveCount(0);
    });
  });
}

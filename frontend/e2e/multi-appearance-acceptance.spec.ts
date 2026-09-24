import { expect, test, type Page, type Route } from "@playwright/test";

test.describe.configure({ mode: "parallel" });

type Appearance = "CLASSIC" | "EDITORIAL" | "SPATIAL";
type Role = "MEMBER" | "MINISTER" | "PRESIDENT" | "ADMIN";

const appearances: Appearance[] = ["CLASSIC", "EDITORIAL", "SPATIAL"];
const acceptanceSizes = [
  { width: 1440, height: 900 },
  { width: 1080, height: 720 },
  { width: 1152, height: 720 },
  { width: 960, height: 600 },
  { width: 390, height: 844 },
];

async function capture(page: Page, name: string) {
  if (process.env.CA_VISUAL_CAPTURE === "1") {
    await page.screenshot({ path: `../tasks/stage6-appearance/${name}.png`, animations: "disabled" });
  }
}
const routesByRole: Record<Role, { allowed: string[]; denied: string }> = {
  MEMBER: { allowed: ["profile"], denied: "members" },
  MINISTER: {
    allowed: ["today", "reviews", "attendance", "stats", "profile", "repairs"],
    denied: "schedules",
  },
  PRESIDENT: {
    allowed: [
      "today",
      "reviews",
      "attendance",
      "stats",
      "schedules",
      "members",
      "profile",
      "repairs",
      "trainings",
      "data",
      "settings",
    ],
    denied: "logs",
  },
  ADMIN: {
    allowed: [
      "today",
      "reviews",
      "attendance",
      "stats",
      "schedules",
      "members",
      "profile",
      "repairs",
      "trainings",
      "data",
      "settings",
      "logs",
    ],
    denied: "",
  },
};

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({
    status,
    contentType: "application/json",
    body: JSON.stringify(body),
  });
}

function user(role: Role) {
  return {
    id: 1,
    studentNo: `visual-${role.toLowerCase()}`,
    name: `${role} 视觉验收`,
    role,
    mustChangePassword: false,
  };
}

async function installMocks(
  page: Page,
  options: {
    appearance?: string;
    role?: Role;
    initialized?: boolean;
    expireOn?: string;
    longMembers?: boolean;
    mustChangePassword?: boolean;
    remote?: boolean;
  } = {},
) {
  const {
    appearance = "CLASSIC",
    role = "ADMIN",
    initialized = true,
    expireOn = "",
    longMembers = false,
    mustChangePassword = false,
    remote = false,
  } = options;

  await page.addInitScript(() => {
    localStorage.setItem("ca_attendance_token", "acceptance-token");
    localStorage.setItem("ca-admin-section-sidebar-collapsed", "false");
  });
  await page.route("**/api/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/public/appearance") return json(route, { appearance, version: 1 });
    if (path === "/api/access/context") {
      return json(route, { mode: remote ? "REMOTE" : "LOCAL", kioskAvailable: !remote, allowedRemoteRoles: ["PRESIDENT", "ADMIN"] });
    }
    if (path === "/api/setup/status") return json(route, { initialized });
    if (path === "/api/auth/me") return json(route, { ...user(role), mustChangePassword });
    if (path === expireOn) return json(route, { message: "登录已失效" }, 401);
    if (path === "/api/stats/dashboard") {
      return json(route, {
        pendingReviews: 3,
        openAttendances: 2,
        todayAttendanceCount: 8,
        activeRepairCount: 4,
      });
    }
    if (path === "/api/public/schedules/today") {
      return json(route, { date: "2026-09-03", weekday: 4, slots: [] });
    }
    if (path === "/api/public/schedules/week") return json(route, []);
    if (path === "/api/attendance/reviews/pending") {
      return json(route, { items: [], recordCount: 0, itemCount: 0, truncated: false });
    }
    if (path === "/api/attendance/page") {
      return json(route, { items: [], total: 0, page: 1, pageSize: 20 });
    }
    if (path === "/api/users/selection") {
      return json(route, longMembers ? Array.from({ length: 39 }, (_, index) => index + 2) : []);
    }
    if (path === "/api/users/page") {
      const items = longMembers
        ? Array.from({ length: 40 }, (_, index) => ({
            id: index + 1,
            studentNo: `2026${String(index + 1).padStart(6, "0")}`,
            name: index === 0 ? "视觉验收成员 1" : `长姓名边界验收成员${index + 1}号复合名称`,
            major: "计算机与信息工程学院（虚构的长学院名称边界测试）",
            role: "MEMBER",
            status: "ACTIVE",
          }))
        : [];
      return json(route, { items, total: items.length, page: 1, pageSize: 50 });
    }
    if (path === "/api/logs") {
      return json(route, { items: [], total: 0, page: 1, pageSize: 20 });
    }
    if (path === "/api/repairs") {
      return json(route, {
        items: [], total: 0, page: 1, pageSize: 20, hasMore: false,
        statusCounts: { REPAIRING: 0, COMPLETED: 0, CANCELED: 0 },
      });
    }
    if (path === "/api/trainings/page") {
      return json(route, { items: [], total: 0, page: 1, pageSize: 20, hasMore: false });
    }
    if (path === "/api/stats/summary") return json(route, []);
    if (path === "/api/stats/weekly-detail") return json(route, { days: [], users: [], cells: {} });
    if (path === "/api/schedules") return json(route, []);
    if (path === "/api/schedules/assignee-candidates") return json(route, []);
    if (path === "/api/settings/weekdays") {
      return json(route, [
        { weekday: 1, weekday_name: "星期一", enabled: true },
        { weekday: 2, weekday_name: "星期二", enabled: true },
        { weekday: 3, weekday_name: "星期三", enabled: true },
        { weekday: 4, weekday_name: "星期四", enabled: true },
        { weekday: 5, weekday_name: "星期五", enabled: true },
        { weekday: 6, weekday_name: "星期六", enabled: false },
        { weekday: 7, weekday_name: "星期日", enabled: false },
      ]);
    }
    if (path === "/api/settings/duty-periods") {
      return json(route, [
        { startTime: "08:00", endTime: "10:00", enabled: true },
        { startTime: "14:00", endTime: "16:00", enabled: true },
        { startTime: "19:00", endTime: "21:00", enabled: false },
      ]);
    }
    if (path === "/api/settings/attendance-policy") {
      return json(route, { requireDutyDay: false, requireDutyPeriod: false });
    }
    if (path === "/api/exports/options") return json(route, { sources: [] });
    if (path === "/api/maintenance/summary") {
      return json(route, { datasets: [], backups: { count: 0, totalSize: 0 }, generatedAt: "" });
    }
    if (path === "/api/maintenance/backups") return json(route, []);
    if (path === "/api/repairs/recycle-bin") return json(route, []);
    return json(route, []);
  });
}

async function expectNoHorizontalOverflow(page: Page) {
  await expect.poll(() => page.evaluate(() =>
    document.documentElement.scrollWidth - document.documentElement.clientWidth,
  ), { message: `${page.url()} at ${page.viewportSize()?.width}px overflows` }).toBeLessThanOrEqual(0);
}

for (const appearance of appearances) {
  test(`member table scroll keeps headers visible: ${appearance}`, async ({page}) => {
    await installMocks(page,{appearance,longMembers:true});
    await page.goto('/#/admin/members');
    const scroll = page.locator('.mw-table-scroll');
    await expect(scroll).toBeVisible();
    await scroll.evaluate(el => { el.scrollTop = 650; });
    const header = scroll.locator('thead');
    const headerBox = await header.boundingBox();
    const scrollBox = await scroll.boundingBox();
    expect(headerBox!.y).toBeGreaterThanOrEqual(scrollBox!.y-1);
    expect(headerBox!.y).toBeLessThanOrEqual(scrollBox!.y+1);
    expect(await header.evaluate(el => {
      const r = el.getBoundingClientRect();
      return el.contains(document.elementFromPoint(r.x+60,r.y+r.height/2));
    })).toBe(true);
  });

  test(`member presentation: ${appearance} preserves records, bulk requests and focus`, async ({ page }) => {
    await installMocks(page, { appearance, longMembers: true });
    let submitted: unknown;
    await page.route('**/api/users/bulk-status', async route => {
      submitted = route.request().postDataJSON();
      await json(route, { updated: 39, unchanged: 0, skipped: 0 });
    });
    await page.setViewportSize({ width: 1440, height: 960 });
    await page.goto('/#/admin/members');
    await expect(page.locator('.mw-table tbody tr')).toHaveCount(40);
    await expect(page.locator('input[name="memberSelection-1"]')).toBeDisabled();
    await page.getByRole('checkbox', { name: '选择当前筛选结果中的全部可管理成员' }).check();
    await expect(page.getByText('已选 39 人', { exact: true })).toBeVisible();
    await capture(page, `${appearance}-member-selection-polish-1440`);
    await page.getByRole('button', { name: '批量停用', exact: true }).click();
    const bulk = page.getByRole('dialog', { name: '批量停用账号' });
    await bulk.getByLabel('操作原因').fill('虚构成员批量验收');
    await bulk.getByRole('button', { name: '确认停用', exact: true }).click();
    await expect(bulk).toBeHidden();
    expect(submitted).toEqual({ ids: Array.from({ length: 39 }, (_, i) => i + 2), status: 'DISABLED', reason: '虚构成员批量验收' });
    await page.getByRole('button', { name: '新增成员', exact: true }).click();
    const editor = page.getByRole('dialog', { name: '新增成员', exact: true });
    for (const name of ['studentNo', 'name', 'role', 'status', 'phone', 'major', 'grade', 'qq']) {
      await expect(editor.locator(`[name="${name}"]`)).toBeVisible();
    }
    await editor.getByRole('button', { name: '新增成员', exact: true }).click();
    await expect(editor.locator('[name="studentNo"]')).toBeFocused();
    await expect(editor.locator('[name="studentNo"]')).toHaveAttribute('aria-invalid', 'true');
    await page.keyboard.press('Escape');
    await expect(editor).toBeHidden();
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.evaluate(() => { document.documentElement.style.zoom = '1.5'; });
    await expectNoHorizontalOverflow(page);
    await page.getByRole('button', { name: '新增成员', exact: true }).click();
    await expect(editor).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(editor).toBeHidden();
    await page.evaluate(() => { document.documentElement.style.zoom = ''; });
    await page.setViewportSize({ width: 390, height: 844 });
    await expectNoHorizontalOverflow(page);
    await expect(page.locator('.mw-table tbody tr')).toHaveCount(40);
    const skip = page.getByRole('link', { name: '跳到主要内容', exact: true });
    await skip.focus();
    await page.keyboard.press('Enter');
    await expect(page.locator('#admin-main-content')).toBeFocused();
    await expect(page).toHaveURL(/#\/admin\/members$/);
  });
}

for (const appearance of appearances) {
  test(`${appearance} repair and training workspaces keep the refined interaction contract`, async ({ page }) => {
    await installMocks(page, { appearance });
    for (const size of [{ width: 1440, height: 900 }, { width: 390, height: 844 }]) {
      await page.setViewportSize(size);
      for (const route of ["repairs", "trainings"]) {
        await page.goto(`/?refined-workspace=${appearance}-${size.width}-${route}#/admin/${route}`);
        await page.waitForLoadState("networkidle");
        await expect(page.locator("#admin-main-content > *")).toBeVisible();
        await expect(page.locator('[title="查看详情"]')).toHaveCount(0);
        await expectNoHorizontalOverflow(page);
      }
    }
  });
}

for (const appearance of appearances) {
  for (const role of Object.keys(routesByRole) as Role[]) {
    test(`${appearance} keeps ${role} routes consistent`, async ({ page }) => {
      test.setTimeout(180_000);
      const errors: string[] = [];
      page.on("pageerror", error => errors.push(error.message));
      page.on("console", message => {
        if (message.type() === "error" || message.type() === "warning") errors.push(message.text());
      });
      await installMocks(page, { appearance, role });
      for (const size of acceptanceSizes) {
        await page.setViewportSize(size);
        for (const route of routesByRole[role].allowed) {
          // Each deep link starts a document, separate from SPA navigation tests.
          await page.goto(`/?acceptance=${role}-${size.width}-${route}#/admin/${route}`);
          await page.waitForLoadState("networkidle");
          await expect(page.locator(".route-enter-active, .admin-view-enter-active, .admin-view-leave-active")).toHaveCount(0);
          await expect(page.locator("html")).toHaveAttribute(
            "data-appearance",
            appearance.toLowerCase(),
          );
          await expect(page).toHaveURL(new RegExp(`#\/admin\/${route}(?:\\?|$)`));
          await expect(page.locator("main")).toBeVisible();
          await expect(page.locator("#admin-main-content > *")).toBeVisible();
          if (route === "profile" && appearance === "EDITORIAL") {
            await expect(page.locator(".profile-record-filter .button")).toHaveCSS("white-space", "nowrap");
          }
          await expectNoHorizontalOverflow(page);
          if (role === "ADMIN" && (size.width === 1440 || size.width === 390)) {
            await capture(page, `${appearance}-${route}-${size.width}`);
          }
        }
      }
      const denied = routesByRole[role].denied;
      if (denied) {
        await page.goto(`/?acceptance=${role}-denied#/admin/${denied}`);
        await page.waitForLoadState("networkidle");
        await expect(page).toHaveURL(
          role === "MEMBER" ? /#\/admin\/profile$/ : /#\/admin\/today$/,
        );
      }
      expect(errors).toEqual([]);
    });
  }
}

test("all appearances render initialization and common effective zoom sizes", async ({ page }) => {
  const sizes = [
    { label: "1440x900", width: 1440, height: 900 },
    { label: "1080x720", width: 1080, height: 720 },
    { label: "125-percent", width: 1152, height: 720 },
    { label: "150-percent", width: 960, height: 600 },
    { label: "narrow", width: 390, height: 844 },
  ];

  for (const appearance of appearances) {
    await page.unrouteAll({ behavior: "wait" });
    await installMocks(page, { appearance, initialized: false });
    for (const size of sizes) {
      await page.setViewportSize({ width: size.width, height: size.height });
      await page.goto(`/?appearance=${appearance}#/setup`);
      await expect(page.getByRole("heading", { name: "初始化本机" })).toBeVisible();
      await expectNoHorizontalOverflow(page);
      await capture(page, `${appearance}-setup-${size.width}`);
    }
  }
});

test("an expired authenticated request clears the session and returns to login", async ({ page }) => {
  await installMocks(page, { appearance: "EDITORIAL", expireOn: "/api/users/page" });
  await page.goto("/#/admin/members");
  await expect(page).toHaveURL(/#\/login\?reason=expired/);
  await expect(page.getByRole("heading", { name: "登录后台" })).toBeVisible();
  const token = await page.evaluate(() => localStorage.getItem("ca_attendance_token"));
  expect(token).toBeNull();
});

test("unknown appearance values render the classic interface", async ({ page }) => {
  await installMocks(page, { appearance: "NOT_A_THEME" });
  await page.goto("/#/admin/today");
  await expect(page.locator("html")).toHaveAttribute("data-appearance", "classic");
  await expect(page.locator("main")).toBeVisible();
});

test("long member lists and creation dialogs remain usable in every appearance", async ({ page }) => {
  await page.setViewportSize({ width: 1080, height: 720 });
  for (const appearance of appearances) {
    await page.unrouteAll({ behavior: "wait" });
    await installMocks(page, { appearance, longMembers: true });
    await page.goto(`/?appearance=${appearance}#/admin/members`);
    await expect(page.getByText("视觉验收成员 1", { exact: true })).toBeVisible();
    await expectNoHorizontalOverflow(page);
    await capture(page, `${appearance}-long-members-1080`);
    await page.locator("#admin-main-content").getByRole("button", { name: "新增成员" }).click();
    await expect(page.getByRole("dialog")).toBeVisible();
    const dialogFits = await page.getByRole("dialog").evaluate((element) => {
      const box = element.getBoundingClientRect();
      return box.top >= 0 && box.left >= 0 && box.right <= innerWidth && box.bottom <= innerHeight;
    });
    expect(dialogFits).toBe(true);
    await capture(page, `${appearance}-member-dialog-1080`);
    await page.keyboard.press("Escape");
  }
});

for (const appearance of appearances) {
  test(`${appearance} public pages fit all acceptance sizes`, async ({ page }) => {
    test.setTimeout(60_000);
    await installMocks(page, { appearance });
    await page.addInitScript(() => localStorage.removeItem("ca_attendance_token"));
    const errors: string[] = [];
    page.on("pageerror", error => errors.push(error.message));
    for (const size of acceptanceSizes) {
      await page.setViewportSize(size);
      for (const route of ["/", "/login"]) {
        await page.goto("/#" + route);
        await expect(page.locator("html")).toHaveAttribute("data-appearance", appearance.toLowerCase());
        await expect(page.locator(route === "/" ? ".kiosk-signal-app" : ".auth-layout")).toBeVisible();
        await expectNoHorizontalOverflow(page);
        await capture(page, `${appearance}-${route === "/" ? "kiosk" : "login"}-${size.width}`);
      }
    }
    expect(errors).toEqual([]);
  });
}

test("failed appearance configuration still renders the classic login", async ({ page }) => {
  await installMocks(page);
  await page.addInitScript(() => localStorage.removeItem("ca_attendance_token"));
  await page.route("**/api/public/appearance", route => json(route, { message: "演示配置读取失败" }, 500));
  await page.goto("/#/login");
  await expect(page.locator("html")).toHaveAttribute("data-appearance", "classic");
  await expect(page.getByRole("heading", { name: "登录后台" })).toBeVisible();
});

for (const appearance of ["EDITORIAL", "SPATIAL"]) {
  test(`${appearance} resource failure falls back without a blank page`, async ({ page }) => {
    await installMocks(page, { appearance });
    await page.addInitScript(() => localStorage.removeItem("ca_attendance_token"));
    await page.route(`**/appearances/${appearance.toLowerCase()}/index.ts*`, route => route.abort());
    await page.goto("/#/login");
    await expect(page.locator("html")).toHaveAttribute("data-appearance", "classic");
    await expect(page.getByRole("heading", { name: "登录后台" })).toBeVisible();
    await expectNoHorizontalOverflow(page);
  });
}

for (const appearance of appearances) {
  test(`${appearance} data and settings navigation stays on the selected page`, async ({ page }) => {
    await installMocks(page, { appearance });
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto("/#/admin/data");
    await expect(page.locator(".data-center-page")).toBeVisible();
    for (let attempt = 0; attempt < 3; attempt += 1) {
      await page.getByRole("link", { name: "系统设置", exact: true }).click();
      await page.waitForLoadState("networkidle");
      await expect(page).toHaveURL(/#\/admin\/settings(?:\?|$)/);
      await expect(page.locator("#settings-appearance")).toBeVisible();
      await expect(page.locator(".weekday-calendar-day")).toHaveCount(7);
      await expect(page.locator(".duty-day-rail .duty-rail-block")).toHaveCount(3);
      await expect(page.locator(".duty-period-cards .duty-period-tab")).toHaveCount(3);
      await expectNoHorizontalOverflow(page);
      if (attempt === 0) {
        await capture(page, `${appearance}-settings-polish-1440`);
        await page.setViewportSize({ width: 390, height: 844 });
        await expectNoHorizontalOverflow(page);
        await capture(page, `${appearance}-settings-polish-390`);
        await page.setViewportSize({ width: 1440, height: 900 });
        await page.locator("#settings-periods").scrollIntoViewIfNeeded();
        await capture(page, `${appearance}-settings-timeline-1440`);
        await page.setViewportSize({ width: 390, height: 844 });
        await page.locator("#settings-periods").scrollIntoViewIfNeeded();
        await expectNoHorizontalOverflow(page);
        await capture(page, `${appearance}-settings-timeline-390`);
        await page.setViewportSize({ width: 1440, height: 900 });
      }
      await page.getByRole("link", { name: "数据与备份", exact: true }).click();
      await page.waitForLoadState("networkidle");
      await expect(page).toHaveURL(/#\/admin\/data(?:\?|$)/);
    }
  });

  test(`${appearance} password redirect fits every role and viewport`, async ({ page }) => {
    test.setTimeout(90_000);
    for (const role of Object.keys(routesByRole) as Role[]) {
      await page.unrouteAll({ behavior: "wait" });
      await installMocks(page, { appearance, role, mustChangePassword: true });
      for (const size of acceptanceSizes) {
        await page.setViewportSize(size);
        await page.goto("/#/admin/profile");
        await expect(page).toHaveURL(/#\/password$/);
        await expect(page.getByRole("heading", { name: "设置新密码" })).toBeVisible();
        await expectNoHorizontalOverflow(page);
        if (role === "ADMIN") await capture(page, `${appearance}-password-${size.width}`);
      }
    }
  });

  test(`${appearance} remote entry opens login without the kiosk link`, async ({ page }) => {
    await installMocks(page, { appearance, remote: true });
    await page.addInitScript(() => localStorage.removeItem("ca_attendance_token"));
    await page.goto("/#/");
    await expect(page).toHaveURL(/#\/login$/);
    await expect(page.getByRole("heading", { name: "登录后台" })).toBeVisible();
    await expect(page.locator(".kiosk-signal-app")).toHaveCount(0);
    await expect(page.getByRole("link", { name: /返回签到台/ })).toHaveCount(0);
    await expectNoHorizontalOverflow(page);
  });
}

test("unloaded settings do not block navigation as unsaved edits", async ({ page }) => {
  await installMocks(page, { appearance: "EDITORIAL" });
  let release!: () => void;
  const held = new Promise<void>(resolve => { release = resolve; });
  await page.route("**/api/settings/weekdays", async route => {
    await held;
    await json(route, []);
  });
  try {
    const started = page.waitForRequest("**/api/settings/weekdays");
    await page.goto("/#/admin/settings");
    await started;
    await expect(page.locator("#settings-appearance")).toBeVisible();
    await page.getByRole("link", { name: "数据与备份", exact: true }).click();
    await expect(page).toHaveURL(/#\/admin\/data(?:\?|$)/);
    await expect(page.getByRole("dialog", { name: "放弃未保存修改" })).toHaveCount(0);
  } finally {
    release();
    await page.unrouteAll({ behavior: "wait" });
  }
});

test("edited settings still require confirmation before leaving", async ({ page }) => {
  await installMocks(page, { appearance: "EDITORIAL" });
  await page.goto("/#/admin/settings");
  await page.waitForLoadState("networkidle");
  const policy = page.getByRole("switch", { name: "强制值班日", exact: true });
  await policy.click();
  await expect(policy).toBeChecked();
  await page.getByRole("link", { name: "数据与备份", exact: true }).click();
  const confirmation = page.getByRole("dialog", { name: "放弃未保存修改" });
  await expect(confirmation).toBeVisible();
  await confirmation.getByRole("button", { name: "取消", exact: true }).click();
  await expect(page).toHaveURL(/#\/admin\/settings$/);
  await expect(policy).toBeChecked();
  await page.getByRole("link", { name: "数据与备份", exact: true }).click();
  await confirmation.getByRole("button", { name: "放弃修改", exact: true }).click();
  await expect(page).toHaveURL(/#\/admin\/data(?:\?|$)/);
});

for (const appearance of appearances) {
  test(`review regression: ${appearance} icon padding`, async ({ page }) => {
    await installMocks(page, { appearance, initialized: false });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/#/setup');
    await expect(page.getByRole('heading', { name: '初始化本机' })).toBeVisible();
    await expect.poll(() => page.locator('.input-with-icon').evaluateAll(fields => fields.every(field => {
      const input = field.querySelector('input'), icon = field.querySelector(':scope > svg');
      if (!input || !icon) return true;
      const style = getComputedStyle(input);
      return input.getBoundingClientRect().left + parseFloat(style.borderLeftWidth) + parseFloat(style.paddingLeft) >= icon.getBoundingClientRect().right + 6;
    }))).toBe(true);
  });

  test(`review regression: ${appearance} navigation focus`, async ({ page }) => {
    await installMocks(page, { appearance });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/#/admin/profile');
    await expect(page.locator('.profile-page')).toBeVisible();
    const opener = page.getByRole('button', { name: '打开导航', exact: true });
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');
    await expect(opener).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(page.locator('.nav-open')).toBeVisible();
    await expect.poll(() => page.evaluate(() => Boolean(document.activeElement?.closest('.admin-navigation')))).toBe(true);
    for (let i = 0; i < 15; i++) {
      await page.keyboard.press('Tab');
      expect(await page.evaluate(() => Boolean(document.activeElement?.closest('.admin-navigation')))).toBe(true);
    }
    await page.keyboard.press('Escape');
    await expect(page.locator('.nav-open')).toHaveCount(0);
    await expect(opener).toBeFocused();
    await page.setViewportSize({ width: 1440, height: 900 });
    await expect(page.getByRole('link', { name: '成员名册', exact: true })).toBeVisible();
  });

  test(`review regression: ${appearance} member draft`, async ({ page }) => {
    await installMocks(page, { appearance });
    let saves = 0;
    await page.route('**/api/users', async route => {
      expect(route.request().method()).toBe('POST');
      const payload = route.request().postDataJSON();
      expect(payload.name).toBe('虚构回归成员');
      saves++;
      await json(route, { id: 42, ...payload });
    });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/#/admin/members');
    const opener = page.locator('main').getByRole('button', { name: '新增成员', exact: true });
    await opener.click();
    const editor = page.getByRole('dialog', { name: '新增成员', exact: true });
    const confirmation = page.getByRole('dialog', { name: '放弃未保存修改' });
    await editor.locator('input[name=name]').fill('虚构回归成员');
    await page.keyboard.press('Escape');
    await expect(confirmation).toBeVisible();
    await confirmation.getByRole('button', { name: '取消', exact: true }).click();
    await expect(confirmation).toBeHidden();
    await expect(editor.locator('input[name=name]')).toHaveValue('虚构回归成员');
    await editor.locator('input[name=studentNo]').fill('202609070001');
    await editor.getByRole('button', { name: '新增成员', exact: true }).click();
    await expect(editor).toBeHidden();
    expect(saves).toBe(1);
    await opener.click();
    await editor.locator('input[name=name]').fill('准备放弃的虚构成员');
    await editor.getByRole('button', { name: '关闭', exact: true }).click();
    await confirmation.getByRole('button', { name: '放弃修改', exact: true }).click();
    await expect(editor).toBeHidden();
    await opener.click();
    await expect(editor.locator('input[name=name]')).toHaveValue('');
    await page.keyboard.press('Escape');
    await expect(editor).toBeHidden();
    await expect(confirmation).toBeHidden();
  });
}

for (const [appearance, width] of [['CLASSIC', 1152], ['SPATIAL', 960]] as const) {
  test(`review regression: ${appearance} profile bounds`, async ({ page }) => {
    await installMocks(page, { appearance });
    await page.setViewportSize({ width, height: 900 });
    await page.goto('/#/admin/profile');
    await expect(page.locator('.profile-page')).toBeVisible();
    await expect.poll(() => page.evaluate(() => {
      const panel = document.querySelector('.profile-record-panel')!.getBoundingClientRect();
      const button = document.querySelector('.profile-record-filter .button')!.getBoundingClientRect();
      const summary = document.querySelector('.profile-summary')!.getBoundingClientRect();
      return Math.max(button.right - panel.right, ...[...document.querySelectorAll('.profile-stat')].map(item => item.getBoundingClientRect().right - summary.right));
    })).toBeLessThanOrEqual(0);
  });
}

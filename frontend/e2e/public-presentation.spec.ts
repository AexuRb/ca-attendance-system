import { expect, test, type Page } from '@playwright/test';

async function publicFixture(page: Page, appearance: string, mode = 'login') {
  await page.addInitScript((authenticated) => {
    localStorage.clear();
    if (authenticated) localStorage.setItem('ca_attendance_token', 'fictional-public-token');
  }, mode === 'password');
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname;
    let body: unknown = {};
    if (path === '/api/public/appearance') body = { appearance, version: 1 };
    if (path === '/api/access/context') body = { mode: 'LOCAL', kioskAvailable: true };
    if (path === '/api/setup/status') body = { initialized: mode !== 'setup' };
    if (path === '/api/auth/me') body = { id: 1, name: '演示管理员', role: 'ADMIN', mustChangePassword: mode === 'password' };
    if (path === '/api/public/schedules/today') body = { date: '2026-09-14', weekdayName: '星期一', slots: [{ startTime: '09:00', endTime: '12:00', assignees: [{ name: '示例部长', studentNo: '20260001' }] }] };
    if (path === '/api/public/schedules/week') body = [];
    await route.fulfill({ json: body });
  });
}

for (const appearance of ['CLASSIC', 'EDITORIAL', 'SPATIAL']) {
  test(`${appearance} public forms retain validation and failed input`, async ({ page }) => {
    await publicFixture(page, appearance, 'setup');
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/#/setup');
    await page.getByRole('button', { name: '创建本地系统' }).click();
    await expect(page.locator('#setup-account')).toBeFocused();
    await page.locator('#setup-account').fill('20260001');
    await page.locator('#setup-name').fill('演示管理员');
    await page.locator('#setup-password').fill('fictional-only');
    await page.locator('#setup-confirmation').fill('fictional-only');
    let submissions = 0;
    await page.route('**/api/setup/initialize', async route => {
      submissions++;
      expect(route.request().postDataJSON().account).toBe('20260001');
      await route.fulfill({ status: 409, json: { message: '演示初始化失败，请重试' } });
    });
    await page.getByRole('button', { name: '创建本地系统' }).click();
    await expect(page.getByRole('alert')).toHaveText('演示初始化失败，请重试');
    await expect(page.locator('#setup-name')).toHaveValue('演示管理员');
    expect(submissions).toBe(1);
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBe(0);

    await page.unrouteAll();
    await publicFixture(page, appearance, 'password');
    await page.goto('/#/password');
    await page.reload();
    await page.getByRole('button', { name: '更新密码' }).click();
    await expect(page.locator('input[name=oldPassword]')).toBeFocused();
    await page.locator('input[name=oldPassword]').fill('fictional-old');
    await page.locator('input[name=newPassword]').fill('fictional-new');
    await page.locator('input[name=confirmation]').fill('fictional-new');
    await page.route('**/api/auth/change-password', r => r.fulfill({ json: {} }));
    await page.getByRole('button', { name: '更新密码' }).click();
    await expect(page).toHaveURL(/login\?reason=password-changed/);
    await expect(page.getByRole('status')).toContainText('密码');
    await page.locator('#login-account').fill('20260001');
    await page.locator('#login-password').fill('fictional-only');
    await page.getByRole('button', { name: '显示密码' }).click();
    await expect(page.locator('#login-password')).toHaveAttribute('type', 'text');
    await page.route('**/api/auth/login', r => r.fulfill({ status: 400, json: { message: '演示账号或密码错误' } }));
    await page.getByRole('button', { name: '进入后台' }).click();
    await expect(page.getByRole('alert')).toHaveText('演示账号或密码错误');
    await expect(page.locator('#login-password')).toHaveValue('fictional-only');
  });

  test(`${appearance} kiosk keyboard, cancel, failure retry and consecutive entry`, async ({ page }) => {
    await publicFixture(page, appearance, 'kiosk');
    let lookups = 0, submissions = 0;
    const requests: unknown[] = [];
    await page.route('**/api/public/attendance/lookup?**', async route => {
      lookups++;
      const query = new URL(route.request().url()).searchParams.get('query');
      await route.fulfill({ json: query === '同名' ? { exists: true, matches: [1, 2].map(i => ({ memberToken: `demo-${i}`, name: `演示成员${i}`, maskedStudentNo: `2026***${i}`, grade: '2026级' })) } : { exists: true, memberToken: 'demo-1', name: '演示成员1', maskedStudentNo: '2026***1', action: submissions > 1 ? 'CHECK_OUT' : 'CHECK_IN', message: '可以签到' } });
    });
    await page.route('**/api/public/attendance/submit', async route => {
      submissions++; requests.push(route.request().postDataJSON());
      await route.fulfill(submissions === 1 ? { status: 409, json: { message: '演示提交失败，请重试' } } : { json: { name: '演示成员1', action: submissions > 2 ? 'CHECK_OUT' : 'CHECK_IN', submittedAt: new Date().toISOString() } });
    });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/#/');
    const query = page.getByLabel('学号或姓名', { exact: true });
    await expect(query).toBeFocused();
    await query.fill('同名'); await query.press('Enter');
    const choice = page.getByRole('button', { name: /演示成员1/ });
    await expect(choice).toBeFocused(); await choice.press('Enter');
    await expect(page.getByRole('button', { name: '确认签到', exact: true })).toBeFocused();
    await page.getByRole('button', { name: '重新输入', exact: true }).click();
    await expect(query).toBeFocused(); expect(submissions).toBe(0);
    await query.fill('20260001'); await query.press('Enter');
    await page.getByRole('button', { name: '确认签到', exact: true }).press('Enter');
    await expect(page.getByRole('alert')).toHaveText('演示提交失败，请重试');
    await page.getByRole('button', { name: '确认签到', exact: true }).click();
    await expect(page.locator('.kiosk-signal-success-copy[role=status]')).toContainText('签到成功');
    expect(requests[0]).toEqual(requests[1]);
    await page.getByRole('button', { name: '下一位', exact: true }).click();
    await expect(query).toHaveValue(''); await expect(query).toBeFocused();
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await query.fill('20260001'); await query.press('Enter');
    await page.getByRole('button', { name: '确认签退', exact: true }).click();
    await expect(page.locator('.kiosk-signal-success-copy[role=status]')).toContainText('签退成功');
    expect(submissions).toBe(3); expect(lookups).toBe(4);
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBe(0);
  });
}

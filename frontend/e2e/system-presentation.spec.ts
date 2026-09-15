import { expect, test, type Page } from '@playwright/test';

async function install(page: Page, appearance: string) {
  const writes: Array<{ path: string; body: any }> = [];
  let failPolicy = true;
  let periods = [{ startTime: '09:00', endTime: '12:00', enabled: true }, { startTime: '14:00', endTime: '17:00', enabled: true }];
  let policy = { requireDutyDay: false, requireDutyPeriod: false };
  let weekdays = Array.from({ length: 7 }, (_, i) => ({ weekday: i + 1, weekday_name: `星期${'一二三四五六日'[i]}`, enabled: i < 5 }));
  let backups = [{ filename: 'backup-demo.zip', createdAt: '2026-09-12T09:30:00', size: 2048 }];
  await page.addInitScript(() => localStorage.setItem('ca_attendance_token', 'system-presentation-fixture'));
  await page.route('**/api/**', async route => {
    const req = route.request(), path = new URL(req.url()).pathname;
    const json = (body: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
    if (req.method() !== 'GET') {
      const body = req.postData() ? req.postDataJSON() : null;
      writes.push({ path, body });
      if (path === '/api/settings/weekdays') { weekdays = weekdays.map(d => ({ ...d, enabled: body.enabledWeekdays.includes(d.weekday) })); return json({}); }
      if (path === '/api/settings/duty-periods') { periods = body.periods; return json(periods); }
      if (path === '/api/settings/attendance-policy') {
        if (failPolicy) { failPolicy = false; return json({ message: '模拟规则保存失败' }, 500); }
        policy = body; return json(policy);
      }
      if (path === '/api/maintenance/backups' && req.method() === 'POST') { backups = [...backups, { filename: 'backup-new.zip', createdAt: '2026-09-12T10:30:00', size: 3072 }]; return json({}); }
      if (req.method() === 'DELETE') { backups = backups.filter(b => !path.endsWith(b.filename)); return json({}); }
      return json({});
    }
    if (path === '/api/public/appearance') return json({ appearance, version: 1 });
    if (path === '/api/access/context') return json({ mode: 'LOCAL', kioskAvailable: true, allowedRemoteRoles: [] });
    if (path === '/api/setup/status') return json({ initialized: true });
    if (path === '/api/auth/me') return json({ id: 1, name: '演示管理员', role: 'ADMIN', studentNo: 'visual-admin', mustChangePassword: false });
    if (path === '/api/settings/weekdays') return json(weekdays);
    if (path === '/api/settings/duty-periods') return json(periods);
    if (path === '/api/settings/attendance-policy') return json(policy);
    if (path === '/api/maintenance/backups') return json(backups);
    if (path.startsWith('/api/maintenance/backups/')) return route.fulfill({ contentType: 'application/zip', body: Buffer.from('fictional backup download') });
    if (path === '/api/maintenance/summary') return json({ datasets: [], backups: { count: backups.length, totalSize: backups.reduce((sum,b) => sum + b.size, 0) }, generatedAt: '' });
    if (path === '/api/exports/options') return json({ sources: [] });
    return json([]);
  });
  return writes;
}

for (const appearance of ['CLASSIC', 'EDITORIAL', 'SPATIAL']) {
  test(`${appearance} settings keep explicit saves, retry and period editing`, async ({ page }) => {
    const writes = await install(page, appearance);
    await page.setViewportSize({ width: 1440, height: 960 });
    await page.goto('/#/admin/settings');
    await page.locator('[data-weekday="6"]').click();
    expect(writes).toHaveLength(0);
    await page.getByRole('button', { name: '保存星期', exact: true }).click();
    await expect.poll(() => writes.length).toBe(1);
    expect(writes[0]).toEqual({ path: '/api/settings/weekdays', body: { enabledWeekdays: [1,2,3,4,5,6] } });
    await page.getByRole('switch', { name: '强制值班日', exact: true }).check();
    await page.getByRole('button', { name: '保存规则', exact: true }).click();
    await expect(page.getByText('模拟规则保存失败').first()).toBeVisible();
    await expect(page.getByRole('switch', { name: '强制值班日', exact: true })).toBeChecked();
    await page.getByRole('button', { name: '保存规则', exact: true }).click();
    await expect.poll(() => writes.filter(w => w.path.endsWith('attendance-policy')).length).toBe(2);
    await page.locator('.duty-period-tab').nth(1).click();
    await page.locator('[name="period-2-start"]').fill('18:00');
    await expect(page.getByRole('button', { name: '保存时间段', exact: true })).toBeDisabled();
    await expect(page.locator('.duty-period-error')).toContainText('结束时间');
    await page.locator('[name="period-2-start"]').fill('14:30');
    await page.getByRole('button', { name: '保存时间段', exact: true }).click();
    await expect.poll(() => writes.some(w => w.path.endsWith('duty-periods'))).toBe(true);
    expect(writes.find(w => w.path.endsWith('duty-periods'))?.body.periods[1]).toEqual({ startTime: '14:30', endTime: '17:00', enabled: true });
    await page.reload();
    await page.locator('.duty-period-tab').nth(1).click();
    await expect(page.locator('[name="period-2-start"]')).toHaveValue('14:30');
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.evaluate(() => { document.documentElement.style.zoom = '1.5'; });
    await expect.poll(() => page.evaluate(() => getComputedStyle(document.documentElement).zoom)).toBe('1.5');
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(0);
    await page.evaluate(() => { document.documentElement.style.zoom = ''; });
    await expect.poll(() => page.evaluate(() => getComputedStyle(document.documentElement).zoom)).toBe('1');
    await page.setViewportSize({ width: 390, height: 844 });
    await page.locator('[data-weekday="7"]').focus();
    await page.keyboard.press('Space');
    await expect(page.locator('[data-weekday="7"]')).toHaveAttribute('aria-pressed', 'true');
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(0);
  });

  test(`${appearance} backup actions retain confirmation, download and file restore`, async ({ page }) => {
    const writes = await install(page, appearance);
    await page.setViewportSize({ width: 1440, height: 960 });
    await page.goto('/#/admin/data?tab=backups');
    await page.getByRole('button', { name: '立即备份', exact: true }).click();
    const create = page.getByRole('dialog', { name: '创建本机备份' });
    expect(writes).toHaveLength(0);
    await create.getByRole('button', { name: '取消', exact: true }).click();
    expect(writes).toHaveLength(0);
    await page.getByRole('button', { name: '立即备份', exact: true }).click();
    await create.getByRole('button', { name: '创建备份', exact: true }).click();
    await expect(create).toBeHidden();
    await expect(page.locator('.bw-table tbody tr')).toHaveCount(2);
    const download = page.waitForEvent('download');
    await page.getByRole('button', { name: '下载备份：backup-demo.zip', exact: true }).click();
    expect((await download).suggestedFilename()).toBe('backup-demo.zip');
    await page.getByRole('button', { name: '删除备份：backup-new.zip', exact: true }).click();
    const deletion = page.getByRole('dialog', { name: '删除备份', exact: true });
    await expect(deletion).toContainText('backup-new.zip');
    await deletion.getByRole('button', { name: '删除备份', exact: true }).click();
    await expect(deletion).toBeHidden();
    await expect(page.locator('.bw-table tbody tr')).toHaveCount(1);
    await page.locator('[name="backup-restore-file"]').setInputFiles({ name: 'fixture.zip', mimeType: 'application/zip', buffer: Buffer.from('fixture') });
    const restore = page.getByRole('dialog', { name: '恢复本机备份' });
    await expect(restore.getByRole('button', { name: '确认恢复' })).toBeDisabled();
    await restore.getByLabel('请输入“恢复”确认操作').fill('恢复');
    await expect(restore.getByRole('button', { name: '确认恢复' })).toBeEnabled();
    await restore.getByRole('button', { name: '取消', exact: true }).click();
    expect(writes.some(w => w.path.endsWith('/restore'))).toBe(false);
    await page.setViewportSize({ width: 390, height: 844 });
    await page.getByRole('button', { name: '查看备份详情：backup-demo.zip', exact: true }).click();
    await expect(page.locator('#data-backup-details')).toHaveAttribute('aria-modal', 'true');
    await page.keyboard.press('Escape');
    await expect(page.locator('#data-backup-details')).toBeHidden();
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(0);
  });
}

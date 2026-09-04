const { _electron, expect } = require('../frontend/node_modules/@playwright/test');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const crypto = require('node:crypto');
const net = require('node:net');

const repo = path.resolve(__dirname, '..');
const executablePath = path.resolve(process.argv[2] || path.join(repo, 'desktop/release/win-unpacked/CA-Attendance-System.exe'));
const root = fs.mkdtempSync(path.join(os.tmpdir(), 'ca-packaged-acceptance-'));
const evidence = path.join(repo, 'tasks/packaged-desktop-acceptance');
const account = `9${crypto.randomInt(100000000, 999999999)}`;
const password = crypto.randomBytes(18).toString('base64url');
const origin = 'http://127.0.0.1:8080';
const results = [];
let app;
let page;
let window;
let visit = 0;
const pageErrors = [];
const visualIssues = [];

async function portOpen(port) {
  return new Promise(resolve => {
    const socket = net.connect({ port, host: '127.0.0.1' });
    const done = value => { socket.destroy(); resolve(value); };
    socket.once('connect', () => done(true));
    socket.once('error', () => done(false));
    socket.setTimeout(500, () => done(false));
  });
}

async function released() {
  await expect.poll(async () => (await portOpen(8080)) || (await portOpen(8081)), { timeout: 15000 }).toBe(false);
}

async function launch() {
  const environment = { ...process.env, CA_ATTENDANCE_ROOT: root };
  delete environment.ELECTRON_RUN_AS_NODE;
  app = await _electron.launch({
    executablePath,
    args: [`--user-data-dir=${path.join(root, 'electron')}`],
    env: environment,
    timeout: 45000,
  });
  await expect.poll(() => app.windows().some(candidate => candidate.url().startsWith(origin)), { timeout: 45000 }).toBe(true);
  page = app.windows().find(candidate => candidate.url().startsWith(origin));
  page.setDefaultTimeout(15000);
  page.on('pageerror', error => pageErrors.push(error.message));
  window = await app.browserWindow(page);
  await window.evaluate(win => { win.setContentSize(1440, 900); win.show(); });
  expect(await app.evaluate(({ app }) => app.isPackaged)).toBe(true);
}

async function navigate(route) {
  await page.goto(`${origin}/?desktopAcceptance=${++visit}#${route}`);
  await page.waitForLoadState('networkidle');
}

async function screenshot(name) {
  const encoded = await window.evaluate(async win => (await win.webContents.capturePage()).toPNG().toString('base64'));
  fs.writeFileSync(path.join(evidence, `${name}.png`), Buffer.from(encoded, 'base64'));
}

async function login() {
  await navigate('/login');
  await page.locator('#login-account').fill(account);
  await page.locator('#login-password').fill(password);
  await page.getByRole('button', { name: '进入后台', exact: true }).click();
  await expect(page).toHaveURL(/#\/admin\/today/);
}

async function changeTheme(theme) {
  await navigate('/admin/settings');
  if (await page.locator('html').getAttribute('data-appearance') !== theme) {
    await page.locator(`.appearance-choice.is-${theme}`).click();
    await page.getByRole('button', { name: '应用界面', exact: true }).click();
    await page.getByRole('button', { name: '确认应用', exact: true }).click();
  }
  await expect(page.locator('html')).toHaveAttribute('data-appearance', theme);
  await expect(page.getByRole('dialog')).toHaveCount(0);
}

async function checkZoom(theme) {
  for (const route of ['today', 'profile', 'stats', 'trainings', 'settings']) {
    await navigate(`/admin/${route}`);
    for (const factor of [1, 1.25, 1.5]) {
      await window.evaluate((win, zoom) => win.webContents.setZoomFactor(zoom), factor);
      const contentWidth = await window.evaluate(win => win.getContentSize()[0]);
      await expect.poll(async () => Math.abs((await page.evaluate(() => innerWidth)) - contentWidth / factor)).toBeLessThanOrEqual(2);
      await expect.poll(() => page.evaluate(() => Math.max(document.body.scrollWidth, document.documentElement.scrollWidth) - document.documentElement.clientWidth)).toBeLessThanOrEqual(2);
      if (route === 'profile') {
        const clipping = await page.evaluate(() => {
          const issues = [];
          for (const [containerSelector, childSelector] of [
            ['.profile-summary', '.profile-stat'],
            ['.profile-record-panel', '.profile-record-filter button'],
          ]) {
            const container = document.querySelector(containerSelector);
            const bounds = container.getBoundingClientRect();
            for (const child of container.querySelectorAll(childSelector)) {
              const rect = child.getBoundingClientRect();
              const overflow = Math.max(bounds.left - rect.left, rect.right - bounds.right, 0);
              if (overflow > 2) issues.push({ container: containerSelector, child: childSelector, overflow: Math.round(overflow) });
            }
          }
          return issues;
        });
        visualIssues.push(...clipping.map(issue => ({ theme, route, zoom: factor, ...issue })));
      }
      if (factor === 1.5) await screenshot(`${theme}-${route}-150`);
    }
    await window.evaluate(win => win.webContents.setZoomFactor(1));
  }
  results.push(`${theme}: real Electron zoom 100/125/150 percent`);
}

async function createRepair(theme) {
  const owner = `桌面验收-${theme}`;
  await navigate('/admin/repairs');
  await page.getByRole('button', { name: '新建维修', exact: false }).first().click();
  const dialog = page.getByRole('dialog', { name: '新建维修事务', exact: true });
  await dialog.locator('[name="repair-owner-name"]').fill(owner);
  await dialog.locator('[name="repair-device-type"]').fill('虚构测试设备');
  await dialog.locator('[name="repair-fault-description"]').fill('隔离桌面验收，不是真实维修');
  await dialog.getByRole('button', { name: '下一步', exact: true }).click();
  await dialog.getByRole('option').first().click();
  await dialog.getByRole('button', { name: '保存事务', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await page.reload();
  await expect(page.getByText(owner, { exact: true }).first()).toBeVisible();
  await page.getByText(owner, { exact: true }).first().click();
  await page.getByRole('button', { name: '查看协议', exact: true }).click();
  await expect(page.frameLocator('iframe[title="维修协议内容"]').locator('body')).toContainText(owner);
  await screenshot(`${theme}-agreement`);
  await page.getByRole('button', { name: '关闭预览', exact: true }).click();
  results.push(`${theme}: repair persisted after reload and agreement rendered`);
}

async function createTraining(theme) {
  const title = `桌面验收培训-${theme}`;
  await navigate('/admin/trainings');
  await page.getByRole('button', { name: '新建培训', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '新建培训', exact: true });
  await dialog.locator('[name="training-title"]').fill(title);
  await dialog.locator('[name="training-location"]').fill('隔离测试教室');
  await dialog.getByRole('button', { name: '保存培训', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await page.reload();
  await expect(page.getByText(title, { exact: true }).first()).toBeVisible();
  await screenshot(`${theme}-training`);
  const destination = path.join(root, 'exports', `${theme}.xlsx`);
  await app.evaluate(({ session }, target) => {
    session.defaultSession.once('will-download', (_event, item) => item.setSavePath(target));
  }, destination);
  await page.getByRole('button', { name: '导出统计', exact: true }).click();
  await expect.poll(() => fs.existsSync(destination) ? fs.statSync(destination).size : 0).toBeGreaterThan(100);
  expect(fs.readFileSync(destination).subarray(0, 2).toString()).toBe('PK');
  results.push(`${theme}: training persisted after reload and Excel downloaded`);
}

async function run() {
  if ((await portOpen(8080)) || (await portOpen(8081))) throw new Error('Desktop ports are occupied; no existing process was stopped.');
  fs.mkdirSync(evidence, { recursive: true });
  await launch();
  await expect(page.getByRole('heading', { name: '初始化本机' })).toBeVisible();
  await screenshot('fresh-setup');
  await page.locator('#setup-account').fill(account);
  await page.locator('#setup-name').fill('隔离验收管理员');
  await page.locator('#setup-password').fill(password);
  await page.locator('#setup-confirmation').fill(password);
  await page.getByRole('button', { name: '创建本地系统', exact: true }).click();
  await expect(page).toHaveURL(/#\/admin\/today/);
  results.push('Fresh packaged database initialized through desktop UI');
  for (const theme of ['classic', 'editorial', 'spatial']) {
    await changeTheme(theme);
    await createRepair(theme);
    await createTraining(theme);
    await checkZoom(theme);
    await navigate('/');
    await expect(page.locator('.kiosk-signal-app')).toBeVisible();
    await screenshot(`${theme}-kiosk`);
    await page.evaluate(() => localStorage.removeItem('ca_attendance_token'));
    await navigate('/login');
    await expect(page.getByRole('heading', { name: '登录后台' })).toBeVisible();
    await screenshot(`${theme}-login`);
    await login();
  }
  await window.evaluate(win => win.close());
  expect(await window.evaluate(win => !win.isDestroyed() && !win.isVisible())).toBe(true);
  await window.evaluate(win => win.show());
  expect(await window.evaluate(win => win.isVisible())).toBe(true);
  results.push('Close hides window without destroying it; window restores');
  await app.close();
  app = null;
  await released();
  await launch();
  await login();
  await expect(page.locator('html')).toHaveAttribute('data-appearance', 'spatial');
  await navigate('/admin/repairs');
  await expect(page.getByText('桌面验收-spatial', { exact: true }).first()).toBeVisible();
  await navigate('/admin/trainings');
  await expect(page.getByText('桌面验收培训-spatial', { exact: true }).first()).toBeVisible();
  results.push('Restart retains database and global appearance');
  expect(pageErrors).toEqual([]);
  expect(visualIssues, 'Profile controls must remain inside their visible containers').toEqual([]);
}

(async () => {
  try {
    await run();
    console.log(JSON.stringify({ passed: true, results, evidence }, null, 2));
  } catch (error) {
    console.error(error.message);
    console.log(JSON.stringify({ passed: false, results, visualIssues }, null, 2));
    process.exitCode = 1;
  } finally {
    if (app) await app.close();
    await released();
    const relative = path.relative(os.tmpdir(), root);
    if (!relative.startsWith('..') && !path.isAbsolute(relative) && path.basename(root).startsWith('ca-packaged-acceptance-')) {
      fs.rmSync(root, { recursive: true, force: true, maxRetries: 5, retryDelay: 300 });
    }
  }
})();

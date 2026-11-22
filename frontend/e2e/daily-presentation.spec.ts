import { expect, test, type Page } from '@playwright/test';

async function install(page: Page, appearance: string) {
  const writes: Array<{path: string; body: any}> = [];
  let record = { id: 1, userId: 2, userRole: 'MEMBER', name: '示例成员', studentNo: '20260001', dutyDate: '2026-09-12', checkInTime: '2026-09-12T09:00:00', checkOutTime: '2026-09-12T12:00:00', checkInStatus: 'PENDING', checkOutStatus: 'PENDING', effectiveStatus: 'PENDING', durationMinutes: 180 };
  let removed = false, failReview = true;
  await page.addInitScript(() => localStorage.setItem('ca_attendance_token', 'daily-fictional-token'));
  await page.route('**/api/**', async route => {
    const req = route.request(), path = new URL(req.url()).pathname;
    const json = (body: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
    if (req.method() !== 'GET') {
      const body = req.postData() ? req.postDataJSON() : null;
      writes.push({path, body});
      if (path.endsWith('/manual')) { record = {...record,...body}; return json({}); }
      if (req.method() === 'DELETE') { removed = true; return json({}); }
      if (path.endsWith('/review')) {
        if (failReview) { failReview = false; return json({message:'模拟审核失败，请重试'},500); }
        const key = body.part === 'CHECK_IN' ? 'checkInStatus' : 'checkOutStatus';
        record[key] = body.action === 'APPROVE' ? 'APPROVED' : 'REJECTED';
        return json({});
      }
      if (path.endsWith('/bulk')) { record.checkInStatus = record.checkOutStatus = 'APPROVED'; return json({matched:1,reviewed:2,skipped:0,errors:[]}); }
      return json({});
    }
    if (path === '/api/public/appearance') return json({appearance,version:1});
    if (path === '/api/access/context') return json({mode:'LOCAL',kioskAvailable:true,allowedRemoteRoles:[]});
    if (path === '/api/setup/status') return json({initialized:true});
    if (path === '/api/auth/me') return json({id:9,name:'演示管理员',role:'ADMIN',studentNo:'demo-admin',mustChangePassword:false});
    if (path === '/api/attendance/page') return json({items:removed?[]:[record],total:removed?0:1,page:1,pageSize:20});
    if (path === '/api/attendance/reviews/pending') {
      const count = [record.checkInStatus,record.checkOutStatus].filter(s=>s==='PENDING').length;
      return json({items:count?[record]:[],recordCount:count?1:0,itemCount:count,truncated:false});
    }
    if (path === '/api/public/schedules/today') return json({slots:[]});
    if (path === '/api/stats/dashboard') return json({todayRecordCount:1,todayValidHours:0});
    if (path === '/api/stats/weekly-detail') return json({days:[],users:[],cells:{}});
    if (path === '/api/exports/options') return json({sources:[]});
    if (path === '/api/settings/duty-periods') return json([{id:1,startTime:'14:00',endTime:'16:00',enabled:true}]);
    if (path === '/api/settings/weekdays') return json([
      {weekday:1,weekday_name:'星期一',enabled:true}, {weekday:2,weekday_name:'星期二',enabled:true},
    ]);
    if (path === '/api/repairs') return json({items:[],total:0,page:1,pageSize:12,hasMore:false,
      statusCounts:{REPAIRING:0,COMPLETED:0,CANCELED:0}});
    if (path === '/api/skill-assessments/overview') return json({date:'2026-11-21',items:Array.from({length:30},(_, index) => ({
      id:index+1, name:`示例成员 ${index+1}`, disassemblyAssessment:'PASSED', osReinstallAssessment:'NOT_PASSED',
    }))});
    return json([]);
  });
  return writes;
}

async function recordHorizontalSelection(page: Page) {
  await page.emulateMedia({reducedMotion:'no-preference'});
  await page.addInitScript(() => {
    const animate = Element.prototype.animate;
    Element.prototype.animate = function (frames, options) {
      if (this.hasAttribute('data-horizontal-mark') && Array.isArray(frames)) {
        this.setAttribute('data-tested-horizontal', frames[0]?.transform?.startsWith('translateX') ? 'slide' : 'fade');
      }
      return animate.call(this, frames, options);
    };
  });
}

test('horizontal selection follows schedule views, personal records and repair status', async ({page}) => {
  const writes = await install(page, 'SPATIAL');
  await recordHorizontalSelection(page);
  await page.setViewportSize({width:1440,height:900});

  await page.goto('/#/admin/schedules');
  const views = page.getByRole('group',{name:'排班视图'});
  await expect(views.getByRole('button',{name:'整周概览'})).toHaveAttribute('aria-pressed','true');
  await views.getByRole('button',{name:'按日编辑'}).click();
  await expect(views.locator('[data-horizontal-mark="day"]')).toHaveAttribute('data-tested-horizontal','slide');
  const weekdays = page.getByRole('navigation',{name:'选择排班星期'});
  await expect(weekdays.getByRole('button',{name:/星期一/})).toHaveAttribute('aria-pressed','true');
  await weekdays.getByRole('button',{name:/星期二/}).click();
  const weekdayMark = weekdays.locator('[data-horizontal-mark="2"]');
  await expect(weekdayMark).toHaveAttribute('data-tested-horizontal','slide');
  await expect(weekdayMark).toHaveCSS('opacity','1');

  await page.goto('/#/admin/profile');
  const records = page.getByRole('group',{name:'记录类型'});
  await expect(records.getByRole('button',{name:/值班/})).toHaveAttribute('aria-pressed','true');
  await records.getByRole('button',{name:/培训/}).click();
  await expect(records.locator('[data-horizontal-mark="training"]')).toHaveAttribute('data-tested-horizontal','slide');
  await expect(records.getByRole('button',{name:/培训/})).toHaveCSS('background-color','rgba(0, 0, 0, 0)');
  await page.emulateMedia({reducedMotion:'reduce'});
  await page.setViewportSize({width:390,height:844});
  await records.getByRole('button',{name:/值班/}).click();
  const attendanceMark = records.locator('[data-horizontal-mark="attendance"]');
  await expect(attendanceMark).not.toHaveAttribute('data-tested-horizontal',/slide|fade/);
  await expect(attendanceMark).toHaveCSS('opacity','1');

  await page.emulateMedia({reducedMotion:'no-preference'});
  await page.goto('/#/admin/repairs?status=REPAIRING');
  const statuses = page.getByRole('tablist',{name:'维修事务状态'});
  await statuses.getByRole('tab',{name:/进行中/}).press('ArrowRight');
  const selected = statuses.getByRole('tab',{name:/已完成/});
  await expect(selected).toHaveAttribute('aria-selected','true');
  await expect(selected).toBeFocused();
  const repairMark = statuses.locator('[data-horizontal-mark="COMPLETED"]');
  await expect(repairMark).toHaveAttribute('data-tested-horizontal','slide');
  await expect(repairMark).toHaveCSS('opacity','1');
  await expect(selected).toHaveCSS('background-color','rgba(0, 0, 0, 0)');
  expect(writes).toHaveLength(0);
});

test('horizontal selection follows data center tabs and respects reduced motion', async ({page}) => {
  await install(page, 'EDITORIAL');
  await recordHorizontalSelection(page);
  await page.goto('/#/admin/data?tab=import');
  const modes = page.getByRole('navigation', {name:'数据中心操作模式'});
  const mark = modes.locator('a[aria-current="page"] [data-horizontal-mark]');
  await expect(mark).toHaveAttribute('data-horizontal-mark','import');
  await expect(mark).not.toHaveAttribute('data-tested-horizontal',/slide|fade/);
  await modes.getByRole('link',{name:'数据导出',exact:true}).click();
  await expect(mark).toHaveAttribute('data-horizontal-mark','export');
  await expect(mark).toHaveAttribute('data-tested-horizontal','slide');
  await expect(mark).toHaveCSS('opacity','1');

  await page.emulateMedia({reducedMotion:'reduce'});
  await page.setViewportSize({width:390,height:780});
  await modes.getByRole('link',{name:'数据导入',exact:true}).click();
  await expect(mark).toHaveAttribute('data-horizontal-mark','import');
  await expect(mark).not.toHaveAttribute('data-tested-horizontal',/slide|fade/);
  await expect(mark).toHaveCSS('opacity','1');
});

test('horizontal selection moves immediately and statistics keep the last loaded result during rapid changes', async ({page}) => {
  await install(page, 'SPATIAL');
  await recordHorizontalSelection(page);
  await page.clock.setFixedTime(new Date(2026,10,22,12));
  const weeklyUser = {userId:2,studentNo:'demo-member',name:'本周示例',role:'MEMBER',attendanceHours:2,trainingHours:0,totalHours:2};
  await page.route('**/api/stats/weekly-detail?**', route => route.fulfill({json:{
    days:[{dutyDate:'2026-11-16',weekday:1,weekdayName:'周一'}],users:[weeklyUser],cells:{'2026-11-16':{'2':2}},
  }}));
  let finishMonth = () => {};
  const monthPending = new Promise<void>(resolve => { finishMonth = resolve; });
  await page.route('**/api/stats/summary?**', async route => {
    const from = new URL(route.request().url()).searchParams.get('from');
    if (from === '2026-11-01') {
      await monthPending;
      await route.fulfill({json:[{...weeklyUser,name:'本月示例'}]}).catch(() => undefined);
    } else {
      await route.fulfill({json:[from === '2026-01-01' ? {...weeklyUser,name:'本年示例'} : weeklyUser]});
    }
  });
  try {
    await page.goto('/#/admin/stats?preset=week');
    const weeklyName = page.getByText('本周示例',{exact:true}).filter({visible:true});
    await expect(weeklyName).toBeVisible();
    const filter = page.getByRole('form',{name:'筛选统计日期'});
    const mark = filter.locator('button[aria-pressed="true"] [data-horizontal-mark]');
    await filter.getByRole('button',{name:'本月',exact:true}).click();
    await expect(mark).toHaveAttribute('data-horizontal-mark','month');
    await expect(mark).toHaveAttribute('data-tested-horizontal','slide');
    await expect(weeklyName).toBeVisible();
    await filter.getByRole('button',{name:'本年',exact:true}).click();
    await expect(mark).toHaveAttribute('data-horizontal-mark','year');
    await expect(mark).toHaveAttribute('data-tested-horizontal','slide');
    await expect(page.getByText('本年示例',{exact:true}).filter({visible:true})).toBeVisible();
    finishMonth();
    await expect(page.getByText('本月示例',{exact:true})).toHaveCount(0);
    await expect(page.getByText('本周示例',{exact:true})).toHaveCount(0);
  } finally {
    finishMonth();
  }
});

test('sidebar selection slides within a group and hands off across groups', async ({page}, testInfo) => {
  const writes = await install(page, 'CLASSIC');
  await page.setViewportSize({width:1440,height:900});
  await page.emulateMedia({reducedMotion:'no-preference'});
  await page.addInitScript(() => {
    const animate = Element.prototype.animate;
    Element.prototype.animate = function (keyframes, options) {
      if (this.classList.contains('overview-nav-highlight') && Array.isArray(keyframes)) {
        const first = keyframes[0];
        const last = keyframes.at(-1);
        const kind = first?.opacity === 0 ? 'enter' : last?.opacity === 0 ? 'exit' : 'slide';
        this.setAttribute('data-tested-animation', kind);
        this.setAttribute('data-tested-offset', String(kind === 'exit' ? last?.transform : first?.transform));
      }
      return animate.call(this, keyframes, options);
    };
    const start = document.startViewTransition.bind(document);
    document.startViewTransition = update => {
      const slide = start(update);
      const root = document.documentElement;
      if (root.hasAttribute('data-test-quick-workspace-navigation')) {
        root.removeAttribute('data-test-quick-workspace-navigation');
        queueMicrotask(() => document.querySelector<HTMLAnchorElement>('[data-nav-key="stats"]')?.click());
      }
      void slide.ready.then(() => {
        root.dataset.testedWorkspaceSlide = root.dataset.workspaceSlide;
        root.dataset.testedWorkspaceAnimations = document.getAnimations()
          .filter(animation => animation instanceof CSSAnimation && animation.animationName.startsWith('workspace-slide-'))
          .map(animation => (animation as CSSAnimation).animationName).sort().join(' ');
      }, () => {});
      return slide;
    };
  });
  await page.goto('/#/admin/today');
  const nav = page.getByRole('navigation', {name:'功能菜单'});
  const selectedMark = nav.locator('a[aria-current="page"] .overview-nav-highlight');
  const heading = page.locator('.overview-page-heading');
  const html = page.locator('html');
  await expect(selectedMark).toHaveCount(1);
  await expect(selectedMark).not.toHaveAttribute('data-tested-animation', /slide|enter|exit/);
  await expect(html).not.toHaveAttribute('data-tested-workspace-slide', /forward|backward/);

  await nav.getByRole('link', {name:'签到审核',exact:true}).click();
  await expect(nav.getByRole('link', {name:'签到审核',exact:true})).toHaveAttribute('aria-current','page');
  await expect(selectedMark).toHaveAttribute('data-tested-animation','slide');
  await expect(html).toHaveAttribute('data-tested-workspace-slide','forward');
  await expect(html).toHaveAttribute('data-tested-workspace-animations','workspace-slide-in workspace-slide-out');

  await nav.getByRole('link', {name:'值班记录',exact:true}).click();
  await expect(nav.getByRole('link', {name:'值班记录',exact:true})).toHaveAttribute('aria-current','page');
  await expect(html).toHaveAttribute('data-workspace-slide','forward');
  const attendanceHeading = await heading.elementHandle();
  await page.evaluate(() => { location.hash = '#/admin/attendance?from=2026-09-12&to=2026-09-12'; });
  await expect(page).toHaveURL(/attendance\?from=/);
  expect(await attendanceHeading?.evaluate(el => el.isConnected)).toBe(true);
  const reviewMark = nav.getByRole('link', {name:'签到审核',exact:true}).locator('.overview-nav-highlight');
  await expect(reviewMark).toHaveAttribute('data-tested-animation','exit');
  await expect(reviewMark).toHaveAttribute('data-tested-offset','translateY(4px)');
  await expect(selectedMark).toHaveAttribute('data-tested-animation','enter');
  await expect(selectedMark).toHaveAttribute('data-tested-offset','translateY(-4px)');
  await expect(selectedMark).toHaveCSS('opacity','1');
  await expect(reviewMark).toHaveCSS('opacity','0');
  await nav.screenshot({path:testInfo.outputPath('sidebar.png')});

  await nav.getByRole('link', {name:'签到审核',exact:true}).click();
  const attendanceMark = nav.getByRole('link', {name:'值班记录',exact:true}).locator('.overview-nav-highlight');
  await expect(attendanceMark).toHaveAttribute('data-tested-animation','exit');
  await expect(attendanceMark).toHaveAttribute('data-tested-offset','translateY(-4px)');
  await expect(selectedMark).toHaveAttribute('data-tested-animation','enter');
  await expect(selectedMark).toHaveAttribute('data-tested-offset','translateY(4px)');
  await expect(html).toHaveAttribute('data-tested-workspace-slide','backward');
  await expect(html).not.toHaveAttribute('data-workspace-slide', /./);

  await page.evaluate(() => document.documentElement.setAttribute('data-test-quick-workspace-navigation',''));
  await nav.getByRole('link', {name:'值班记录',exact:true}).click();
  await expect(nav.getByRole('link', {name:'数据统计',exact:true})).toHaveAttribute('aria-current','page');
  await expect(html).toHaveAttribute('data-tested-workspace-slide','forward');
  await expect(page.locator('.overview-content')).toHaveCount(1);

  await page.emulateMedia({reducedMotion:'reduce'});
  await html.evaluate(element => element.removeAttribute('data-tested-workspace-slide'));
  await nav.getByRole('link', {name:'今日概览',exact:true}).click();
  await expect(nav.getByRole('link', {name:'今日概览',exact:true})).toHaveAttribute('aria-current','page');
  await expect(selectedMark).not.toHaveAttribute('data-tested-animation', /slide|enter|exit/);
  await expect(selectedMark).toHaveCSS('opacity','1');
  await expect(html).not.toHaveAttribute('data-tested-workspace-slide', /forward|backward/);
  await expect(html).not.toHaveAttribute('data-workspace-slide', /./);

  await page.setViewportSize({width:390,height:780});
  await page.getByRole('button',{name:'打开功能菜单'}).click();
  await expect(nav.getByRole('link',{name:'今日概览',exact:true})).toHaveAttribute('aria-current','page');
  await page.keyboard.press('Escape');
  await expect(page.getByRole('button',{name:'打开功能菜单'})).toHaveAttribute('aria-expanded','false');
  await page.emulateMedia({reducedMotion:'no-preference'});
  await page.getByRole('button',{name:'打开功能菜单'}).click();
  await nav.getByRole('link',{name:'签到审核',exact:true}).click();
  await expect(html).toHaveAttribute('data-tested-workspace-slide','forward');
  await expect(page.getByRole('button',{name:'打开功能菜单'})).toHaveAttribute('aria-expanded','false');
  expect(writes).toHaveLength(0);
});

test('today layers its motion and keeps unchanged counts and the roster scroll on refresh', async ({page}) => {
  const writes = await install(page,'CLASSIC');
  await page.setViewportSize({width:1440,height:810});
  await page.emulateMedia({reducedMotion:'no-preference'});
  let rosterSize = 30, reviews = 2, repairs = 3;
  let osReinstallAssessment = 'NOT_PASSED';
  await page.route('**/api/stats/dashboard?**', route =>
    route.fulfill({json:{todayPendingCount:reviews,ongoingRepairCount:repairs}}));
  await page.route('**/api/skill-assessments/overview', route => route.fulfill({json:{
    date:'2026-11-21',items:Array.from({length:rosterSize},(_,index) => ({
      id:index+1,name:`示例成员 ${index+1}`,disassemblyAssessment:'PASSED',osReinstallAssessment,
    })),
  }}));
  await page.goto('/#/admin/today');

  const welcome = page.locator('.today-entry-message');
  await expect(welcome).toHaveCSS('animation-name','today-section-arrive');
  expect(await page.locator('.today-welcome').evaluate(element =>
    getComputedStyle(element,'::before').animationName)).toBe('today-welcome-glow');
  const roster = page.getByRole('complementary',{name:'当前在场'});
  const rosterScroll = roster.getByRole('region',{name:'在场人员与考核状态'});
  await expect(rosterScroll).toHaveCSS('animation-name','today-roster-arrive');
  await expect(page.locator('.today-shortcuts')).toHaveCSS('animation-delay','0.14s');
  await expect(page.locator('.today-summary')).toHaveCSS('animation-delay','0.205s');

  const rosterCount = roster.locator('.today-roster-count');
  const reviewCount = page.locator('[data-task="reviews"] .today-task-count');
  await expect(rosterCount).toHaveText('30 人');
  await expect(reviewCount).toHaveText('2');
  for (const element of [rosterCount, reviewCount, rosterScroll]) {
    await element.evaluate(node => node.setAttribute('data-before-refresh',''));
  }
  const scrollTop = await rosterScroll.evaluate(element => {
    element.scrollTop = 350;
    return element.scrollTop;
  });
  expect(scrollTop).toBeGreaterThan(0);
  repairs = 4;
  osReinstallAssessment = 'PASSED';
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  await expect(page.locator('[data-task="repairs"] .today-task-count')).toHaveText('4');
  await expect(roster.getByRole('row',{name:/^示例成员 1 /}).getByText('通过',{exact:true})).toHaveCount(2);
  for (const element of [rosterCount, reviewCount, rosterScroll]) {
    await expect(element).toHaveAttribute('data-before-refresh','');
  }

  rosterSize++;
  reviews++;
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  await expect(rosterCount).toHaveText('31 人');
  await expect(reviewCount).toHaveText('3');
  for (const count of [rosterCount, reviewCount]) {
    await expect(count).not.toHaveAttribute('data-before-refresh','');
    await expect(count).toHaveCSS('animation-name','today-number-arrive');
  }
  await expect(rosterScroll).toHaveAttribute('data-before-refresh','');
  expect(await rosterScroll.evaluate(element => element.scrollTop)).toBe(scrollTop);
  expect(writes).toHaveLength(0);
});

for (const appearance of ['CLASSIC','EDITORIAL','SPATIAL']) {
  test(`${appearance} today keeps the roster full height and scrollable beside the summary`, async ({page}, testInfo) => {
    const writes = await install(page,appearance);
    await page.setViewportSize({width:1920,height:1080});
    await page.emulateMedia({reducedMotion:'reduce'});
    await page.goto('/#/admin/today');
    const summary = page.getByRole('region',{name:'今日信息'});
    await expect(summary.getByText('待审核',{exact:true})).toBeVisible();
    await expect(summary.getByText('进行中维修',{exact:true})).toBeVisible();
    await expect(summary.getByRole('listitem')).toHaveCount(2);
    const roster = page.getByRole('complementary',{name:'当前在场'});
    const rosterScroll = roster.getByRole('region',{name:'在场人员与考核状态'});
    await expect(roster.getByRole('rowheader',{name:'示例成员 1',exact:true})).toBeVisible();
    await expect(roster.getByRole('columnheader',{name:'重装系统'})).toBeVisible();
    await expect(page.locator('.today-entry-message')).toHaveCSS('animation-name','none');
    await expect(rosterScroll).toHaveCSS('animation-name','none');
    await expect(summary.locator('.today-task-count').first()).toHaveCSS('animation-name','none');
    await page.evaluate(() => document.fonts.ready.then(() => undefined));
    const beforeScroll = await page.locator('.today-layout').evaluate(element => {
      const bounds = (selector: string) => element.querySelector(selector)!.getBoundingClientRect();
      return { rosterTop:bounds('.today-roster').top, rosterBottom:bounds('.today-roster').bottom,
        welcomeTop:bounds('.today-welcome').top, lowerBottom:bounds('.today-lower').bottom };
    });
    expect(beforeScroll.rosterTop).toBeCloseTo(beforeScroll.welcomeTop, 0);
    expect(beforeScroll.rosterBottom).toBeCloseTo(beforeScroll.lowerBottom, 0);
    const lower = page.locator('.today-lower');
    const lowerBox = (await lower.boundingBox())!;
    const metrics = (await summary.getByRole('list').boundingBox())!;
    const links = lower.getByRole('link');
    const firstLink = (await links.first().boundingBox())!;
    const lastLink = (await links.last().boundingBox())!;
    const lowerCenter = lowerBox.x + lowerBox.width / 2;
    expect(metrics.x + metrics.width / 2).toBeCloseTo(lowerCenter, 0);
    expect((firstLink.x + lastLink.x + lastLink.width) / 2).toBeCloseTo(lowerCenter, 0);
    await page.screenshot({path:testInfo.outputPath('desktop.png')});
    const heading = roster.getByRole('columnheader',{name:'成员',exact:true});
    const initialHeaderTop = (await heading.boundingBox())!.y;
    await rosterScroll.evaluate(element => { element.scrollTop = 350; });
    await expect.poll(() => rosterScroll.evaluate(element => element.scrollTop)).toBeGreaterThan(0);
    expect((await heading.boundingBox())!.y).toBeCloseTo(initialHeaderTop, 0);
    const lowerAfterScroll = (await page.locator('.today-lower').boundingBox())!;
    expect(lowerAfterScroll.y + lowerAfterScroll.height).toBeCloseTo(beforeScroll.lowerBottom, 0);
    await page.keyboard.press('/');
    await expect(page.locator('.overview-workspace').locator('input,textarea,[role=combobox],[role=log]')).toHaveCount(0);
    for (const viewport of [{width:1440,height:900},{width:1180,height:720},{width:900,height:720},{width:680,height:740},{width:390,height:844}]) {
      await page.setViewportSize(viewport);
      await expect(summary).toBeVisible();
      await expect(roster).toBeVisible();
      await expect.poll(() => page.evaluate(() =>
        document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    }
    const narrow = (await roster.boundingBox())!;
    expect(narrow.y + narrow.height).toBeLessThan((await page.locator('.today-lower').boundingBox())!.y);
    await page.screenshot({path:testInfo.outputPath('narrow.png')});
    await page.getByRole('link',{name:'今日值班记录',exact:true}).click();
    await expect(page).toHaveURL(/attendance\?from=(\d{4}-\d{2}-\d{2})&to=\1/);
    expect(writes).toHaveLength(0);
  });

  test(`${appearance} credits dialog on the profile restores focus`, async ({page}) => {
    await install(page,appearance);
    await page.goto('/#/admin/profile');
    const creditsButton = page.getByRole('button',{name:'查看鸣谢'});
    await creditsButton.click();
    const dialog = page.getByRole('dialog',{name:'鸣谢'});
    await expect(dialog).toBeVisible();
    await expect.poll(() => dialog.evaluate(el => {
      const { x, y, width, height } = el.getBoundingClientRect();
      return el.contains(document.elementFromPoint(x + width / 2, y + height / 2));
    })).toBe(true);

    await page.keyboard.press('Escape');
    await expect(page.getByRole('dialog',{name:'鸣谢'})).toHaveCount(0);
    await expect(creditsButton).toBeFocused();
  });

  test(`${appearance} today navigation and record editing preserve requests and keyboard`, async ({page}) => {
    const writes = await install(page,appearance);
    await page.goto('/#/admin/today');
    await page.getByRole('link',{name:'值班记录',exact:true}).click();
    await expect(page).toHaveURL(/attendance/);
    await expect(page.getByRole('cell',{name:'示例成员',exact:false})).toBeVisible();
    await page.getByRole('button',{name:'编辑',exact:true}).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByRole('radio',{name:'更正签到／签退时间',exact:true}).check();
    await dialog.getByLabel('签退时间',{exact:true}).fill('2026-09-12T12:30');
    await dialog.getByRole('button',{name:'保存',exact:true}).click();
    await expect(dialog).toBeHidden();
    expect(writes[0]).toMatchObject({path:'/api/attendance/1/manual',body:{reason:'更正签到／签退时间',checkOutTime:'2026-09-12T12:30'}});
    await expect(page.locator('time[title^="2026-09-12 12:30"]:visible')).toBeVisible();
    await page.getByRole('button',{name:'删除',exact:true}).click();
    await dialog.getByRole('button',{name:'取消'}).click();
    expect(writes).toHaveLength(1);
    await page.getByRole('button',{name:'删除',exact:true}).click();
    await dialog.getByRole('radio',{name:'重复记录',exact:true}).check();
    await dialog.getByRole('button',{name:'删除记录',exact:true}).click();
    await expect(page.getByRole('cell',{name:'示例成员',exact:false})).toHaveCount(0);
    expect(writes).toHaveLength(2);
  });

  test(`${appearance} review retries, cancellation, responsive layout and reduced motion`, async ({page}) => {
    const writes = await install(page,appearance);
    await page.goto('/#/admin/reviews');
    const approve = page.locator('.review-approve-check-in .review-state-action__approve');
    await approve.click();
    await expect(page.getByText('模拟审核失败，请重试')).toBeVisible();
    await expect(approve).toBeEnabled();
    await approve.click();
    await expect(approve).toHaveCount(0);
    await expect(page.locator('.review-approve-check-in .review-state-action__status')).toHaveText('已通过');
    expect(writes[1]).toMatchObject({path:'/api/attendance/1/review',body:{part:'CHECK_IN',action:'APPROVE'}});
    await page.getByRole('button',{name:'全部通过',exact:true}).click();
    await page.getByRole('dialog').getByRole('button',{name:'取消'}).click();
    expect(writes).toHaveLength(2);
    await page.locator('.review-approve-check-out .review-state-action__reject').click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toContainText('驳回签退');
    await dialog.getByLabel('驳回原因').fill('演示核对原因');
    await page.emulateMedia({reducedMotion:'reduce'});
    await page.setViewportSize({width:390,height:844});
    await expect(dialog).toBeVisible();
    expect(await dialog.evaluate(e=>e.getBoundingClientRect().right)).toBeLessThanOrEqual(390);
    await page.screenshot({path:test.info().outputPath(`${appearance}-reject-390.png`),fullPage:true});
    await dialog.getByRole('button',{name:'确认驳回'}).click();
    await expect(page.getByText('待审核已清空')).toBeVisible();
    expect(writes[2]).toMatchObject({body:{part:'CHECK_OUT',action:'REJECT',reason:'演示核对原因'}});
    for (const route of ['today','attendance','reviews']) {
      await page.goto('/#/admin/'+route);
      await expect(page.locator({ today: '.overview-today', attendance: '.attendance-workspace', reviews: '.review-workspace' }[route]!)).toBeVisible();
      await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(1);
      await page.setViewportSize({width:1440,height:960});
      await page.evaluate(()=>document.documentElement.style.zoom='1.5');
      await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(1);
      await page.evaluate(()=>document.documentElement.style.zoom='');
      await page.setViewportSize({width:390,height:844});
    }
  });
}

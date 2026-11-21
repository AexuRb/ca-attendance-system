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
    if (path === '/api/skill-assessments/overview') return json({date:'2026-11-21',items:Array.from({length:30},(_, index) => ({
      id:index+1, name:`示例成员 ${index+1}`, disassemblyAssessment:'PASSED', osReinstallAssessment:'NOT_PASSED',
    }))});
    return json([]);
  });
  return writes;
}

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

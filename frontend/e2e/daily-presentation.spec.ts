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
    return json([]);
  });
  return writes;
}

for (const appearance of ['CLASSIC','EDITORIAL','SPATIAL']) {
  test(`${appearance} daily command and record editing preserve requests and keyboard`, async ({page}) => {
    const writes = await install(page,appearance);
    await page.goto('/#/admin/today');
    await expect(page.locator('.daily-command')).toBeVisible();
    await page.keyboard.press('/');
    const input = page.getByRole('combobox',{name:'查找后台功能或输入命令'});
    await expect(input).toBeFocused();
    await input.fill('/ 查看 值班记录');
    await input.press('Escape');
    await expect(input).toHaveAttribute('aria-expanded','false');
    await input.press('Enter');
    await expect(page).toHaveURL(/attendance/);
    await expect(page.getByRole('cell',{name:'示例成员',exact:false})).toBeVisible();
    await page.getByRole('button',{name:'编辑',exact:true}).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByLabel('操作原因').fill('演示校正时间');
    await dialog.getByLabel('签退时间',{exact:true}).fill('2026-09-12T12:30');
    await dialog.getByRole('button',{name:'保存',exact:true}).click();
    await expect(dialog).toBeHidden();
    expect(writes[0]).toMatchObject({path:'/api/attendance/1/manual',body:{reason:'演示校正时间',checkOutTime:'2026-09-12T12:30'}});
    await expect(page.getByRole('cell',{name:'2026-09-12 12:30',exact:false})).toBeVisible();
    await page.getByRole('button',{name:'删除',exact:true}).click();
    await dialog.getByRole('button',{name:'取消'}).click();
    expect(writes).toHaveLength(1);
    await page.getByRole('button',{name:'删除',exact:true}).click();
    await dialog.locator('textarea').fill('演示删除原因');
    await dialog.getByRole('button',{name:'删除记录',exact:true}).click();
    await expect(page.getByText('没有符合条件的记录')).toBeVisible();
    expect(writes).toHaveLength(2);
  });

  test(`${appearance} review retries, cancellation, responsive layout and reduced motion`, async ({page}) => {
    const writes = await install(page,appearance);
    await page.goto('/#/admin/reviews');
    const approve = page.locator('.review-approve-check-in');
    await approve.click();
    await expect(page.getByText('模拟审核失败，请重试')).toBeVisible();
    await expect(approve).toBeEnabled();
    await approve.click();
    await expect(approve).toBeDisabled();
    expect(writes[1]).toMatchObject({path:'/api/attendance/1/review',body:{part:'CHECK_IN',action:'APPROVE'}});
    await page.getByRole('button',{name:'全部通过',exact:true}).click();
    await page.getByRole('dialog').getByRole('button',{name:'取消'}).click();
    expect(writes).toHaveLength(2);
    await page.locator('.review-reject').click();
    const dialog = page.getByRole('dialog');
    await dialog.getByLabel('驳回部分').selectOption('CHECK_OUT');
    await dialog.getByLabel('驳回原因').fill('演示核对原因');
    await page.emulateMedia({reducedMotion:'reduce'});
    await page.setViewportSize({width:390,height:844});
    await expect(dialog).toBeVisible();
    expect(await dialog.evaluate(e=>e.getBoundingClientRect().right)).toBeLessThanOrEqual(390);
    await page.screenshot({path:`../tasks/high-frequency-20260912/${appearance}-reject-390.png`,fullPage:true});
    await dialog.getByRole('button',{name:'确认驳回'}).click();
    await expect(page.getByText('待审核已清空')).toBeVisible();
    expect(writes[2]).toMatchObject({body:{part:'CHECK_OUT',action:'REJECT',reason:'演示核对原因'}});
    for (const route of ['today','attendance','reviews']) {
      await page.goto('/#/admin/'+route);
      await expect(page.locator(route === 'reviews' ? '.review-workspace' : `.${route}-workspace`)).toBeVisible();
      await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(1);
      await page.setViewportSize({width:1440,height:960});
      await page.evaluate(()=>document.documentElement.style.zoom='1.5');
      await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(1);
      await page.evaluate(()=>document.documentElement.style.zoom='');
      await page.setViewportSize({width:390,height:844});
    }
  });
}

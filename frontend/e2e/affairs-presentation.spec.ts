import { expect, test, type Page } from '@playwright/test';
async function install(page:Page, appearance:string) {
 const writes:Array<{path:string;body:any}>=[];
 let repair:any={id:1,caseNo:'WX-DEMO-001',agreementType:'REPAIR',ownerName:'示例联系人',ownerPhone:'13800000001',deviceType:'笔记本电脑',faultDescription:'无法正常启动',handlerUserId:8,handlerName:'示例部长',status:'REPAIRING',receivedAt:'2026-09-12T09:00:00',updatedAt:'2026-09-12T10:00:00',dataBackupConfirmed:true,riskAcknowledged:true,privacyAcknowledged:true};
 let session:any={id:1,title:'示例培训',trainingDate:'2026-09-12',startTime:'09:00:00',endTime:'10:00:00',speaker:'示例主讲人',location:'活动室',description:'演示培训内容',status:'ACTIVE',participantCount:1,totalDurationHours:1};
 let participant:any={id:1,sessionId:1,name:'示例成员',studentNo:'20260001',durationHours:1,remark:'完成练习'};
 let fail=true;
 await page.addInitScript(()=>localStorage.setItem('ca_attendance_token','affairs-fictional-token'));
 await page.route('**/api/**',async route=>{
  const req=route.request(),path=new URL(req.url()).pathname;
  const json=(body:unknown,status=200)=>route.fulfill({status,contentType:'application/json',body:JSON.stringify(body)});
  const paged=(items:any[])=>({items,total:items.length,page:1,pageSize:20,hasMore:false});
  if(req.method()!=='GET'){
   const body=req.postData()?req.postDataJSON():null;writes.push({path,body});
   if(path==='/api/repairs/1'){
    if(req.method()==='DELETE'){repair=null;return json({});}
    if(fail){fail=false;return json({message:'模拟保存失败，请重试'},500);}
    repair={...repair,...body};return json(repair);
   }
   if(path==='/api/trainings/1'){session={...session,...body};return json(session);}
   if(path==='/api/trainings/1/participants/1'){if(req.method()==='DELETE')participant=null;else participant={...participant,...body};return json(participant||{});}
   return json({});
  }
  if(path==='/api/public/appearance')return json({appearance,version:1});
  if(path==='/api/access/context')return json({mode:'LOCAL',kioskAvailable:true,allowedRemoteRoles:[]});
  if(path==='/api/setup/status')return json({initialized:true});
  if(path==='/api/auth/me')return json({id:9,name:'演示管理员',role:'ADMIN',studentNo:'demo-admin',mustChangePassword:false});
  if(path==='/api/repairs')return json({...paged(repair?[repair]:[]),statusCounts:{REPAIRING:repair?1:0,COMPLETED:0,CANCELED:0}});
  if(path==='/api/repairs/handler-candidates')return json([{id:8,name:'示例部长',studentNo:'20260008',role:'MINISTER',enabled:true}]);
  if(path==='/api/trainings/page')return json(paged([session]));
  if(path==='/api/trainings/1/participants/page')return json(paged(participant?[participant]:[]));
  return json([]);
 });return writes;
}

test('center dialogs and side drawers animate while nested close restores focus', async ({page}) => {
 const writes = await install(page,'CLASSIC');
 await page.emulateMedia({reducedMotion:'no-preference'});
 await page.setViewportSize({width:1440,height:900});
 await page.route('**/api/repairs/1/agreement', route => route.fulfill({
  contentType:'text/html',body:'<!doctype html><html><body><div class="sheet">示例维修协议</div></body></html>',
 }));
 await page.goto('/#/admin/repairs');

 const create = page.getByRole('button',{name:'新建维修',exact:true});
 await create.click();
 const editor = page.locator('[data-dialog-motion="center"]');
 await expect(editor).toHaveCSS('transition-property','opacity, transform');
 await expect(editor.locator('[data-dialog-content]')).toHaveCSS('transition-delay','0.085s, 0.085s');
 await expect(editor.locator('footer')).toHaveCSS('transition-delay','0.135s, 0.135s');
 await expect(editor.locator('header')).toHaveCSS('animation-name','dialog-heading-light');
 await page.keyboard.press('Escape');
 await expect(create).toBeFocused();
 await expect(editor).toHaveCount(0);

 const opener = page.getByRole('button',{name:'查看 WX-DEMO-001 的详情'});
 await opener.click();
 const detail = page.locator('.repair-detail-drawer');
 await expect(detail).toHaveCSS('transition-property','opacity, transform');
 const preview = detail.getByRole('button',{name:'查看协议',exact:true});
 await preview.click();
 const agreement = page.getByRole('dialog',{name:'协议预览'});
 await expect(agreement).toBeVisible();
 await expect(agreement).toHaveCSS('transform','none');
 await expect(agreement.frameLocator('iframe').getByText('示例维修协议')).toBeVisible();
 await page.keyboard.press('Escape');
 await expect(preview).toBeFocused();
 await expect(detail).toBeVisible();
 await expect(page.locator('.agreement-backdrop')).toHaveCount(0);
 await page.keyboard.press('Escape');
 await expect(opener).toBeFocused();
 await expect(detail).toHaveCount(0);
 expect(writes).toHaveLength(0);
});

for(const appearance of ['CLASSIC','EDITORIAL','SPATIAL']){
 test(`${appearance} repairs preserve two steps, retry, details and delete confirmation`,async({page})=>{
  const writes=await install(page,appearance);await page.goto('/#/admin/repairs');
  await page.getByRole('button',{name:'显示完整电话'}).click();await expect(page.getByText('13800000001',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'查看 WX-DEMO-001 的详情'}).click();await expect(page.getByRole('dialog')).toBeVisible();
  await page.setViewportSize({width:390,height:844});await page.screenshot({path:`../tasks/affairs-20260913/${appearance}-detail-390.png`,fullPage:true});
  await page.getByRole('button',{name:'关闭维修详情'}).click();
  await page.setViewportSize({width:1440,height:960});
  const more = page.getByRole('button',{name:'WX-DEMO-001 更多操作'});
  await more.click();await page.getByRole('menuitem',{name:'编辑事务',exact:true}).click();
  const dialog=page.getByRole('dialog');await dialog.locator('[name="repair-owner-name"]').fill('');await dialog.getByRole('button',{name:'下一步'}).click();
  await expect(dialog.getByText('请填写联系人')).toBeVisible();await expect(dialog.locator('[name="repair-owner-name"]')).toBeFocused();
  await dialog.locator('[name="repair-owner-name"]').fill('示例联系人修改');await dialog.getByRole('button',{name:'下一步'}).click();
  await dialog.getByRole('button',{name:'保存事务'}).click();await expect(dialog.getByRole('alert')).toHaveText('模拟保存失败，请重试');
  await expect(dialog).toBeVisible();await dialog.getByRole('button',{name:'保存事务'}).click();await expect(dialog).toBeHidden();
  expect(writes[1]).toMatchObject({path:'/api/repairs/1',body:{ownerName:'示例联系人修改',handlerUserId:8}});
  await expect(page.getByText('示例联系人修改',{exact:true})).toBeVisible();
  const deleteAction = page.getByRole('menuitem',{name:'移入回收站',exact:true});
  await more.click();await deleteAction.click();await page.getByRole('dialog').getByRole('button',{name:'取消'}).click();expect(writes).toHaveLength(2);
  await more.click();await deleteAction.click();await page.getByRole('dialog').getByRole('button',{name:'移入回收站',exact:true}).click();await expect(page.getByText('当前条件下没有进行中的维修')).toBeVisible();
 });
 test(`${appearance} training edits, participants and responsive dialogs`,async({page})=>{
  const writes=await install(page,appearance);await page.goto('/#/admin/trainings');await page.getByRole('button',{name:'编辑培训',exact:true}).click();
  let dialog=page.getByRole('dialog');await dialog.locator('[name="training-title"]').fill('精修演示培训');
  await page.setViewportSize({width:390,height:844});await page.emulateMedia({reducedMotion:'reduce'});await page.screenshot({path:`../tasks/affairs-20260913/${appearance}-training-editor-390.png`,fullPage:true});
  expect(await dialog.evaluate(e=>e.getBoundingClientRect().right)).toBeLessThanOrEqual(390);
  await dialog.getByRole('button',{name:'保存培训',exact:true}).click();await expect(dialog).toBeHidden();expect(writes[0]).toMatchObject({path:'/api/trainings/1',body:{title:'精修演示培训'}});
  await expect(page.locator('.training-session-heading h2')).toHaveText('精修演示培训');
  await page.getByRole('button',{name:'编辑 示例成员 的参与记录'}).click();dialog=page.getByRole('dialog');await dialog.locator('[name="participant-duration"]').fill('1.5');await dialog.getByRole('button',{name:/保存/}).click();await expect(dialog).toBeHidden();expect(writes[1]).toMatchObject({path:'/api/trainings/1/participants/1',body:{durationHours:1.5}});
  const add = page.getByRole('button',{name:'新建培训',exact:true});
  await add.click();await expect(page.getByRole('dialog')).toBeVisible();
  await expect(page.getByRole('dialog')).toHaveCSS('transform','none');
  await page.keyboard.press('Escape');await expect(add).toBeFocused();await expect(page.getByRole('dialog')).toBeHidden();expect(writes).toHaveLength(2);
  for(const width of [390,960,1440]){await page.setViewportSize({width,height:960});await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(1);}
  await page.evaluate(()=>document.documentElement.style.zoom='1.5');await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(1);
 });
}

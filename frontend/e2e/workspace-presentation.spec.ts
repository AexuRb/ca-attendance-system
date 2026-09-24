import { expect, test, type Page } from '@playwright/test';
async function install(page:Page,appearance:string){
 const writes:Array<{path:string;method:string;body:any}>=[];
 let profile={id:9,name:'示例管理员',studentNo:'demo-admin',role:'ADMIN',phone:'',qq:'',major:'计算机学院',grade:'2026级',mustChangePassword:false};
 let slots:any[]=[{id:1,weekday:1,weekdayName:'周一',startTime:'09:00',endTime:'12:00',title:'日常值班',location:'协会活动室',enabled:true,assignees:[]}];let fail=true;
 await page.addInitScript(()=>localStorage.setItem('ca_attendance_token','workspace-fictional-token'));
 await page.route('**/api/**',async route=>{const req=route.request(),path=new URL(req.url()).pathname;const json=(body:any,status=200)=>route.fulfill({status,contentType:'application/json',body:JSON.stringify(body)});
 if(req.method()!=='GET'){const body=req.postData()?req.postDataJSON():null;writes.push({path,method:req.method(),body});
 if(path==='/api/me/profile'){if(fail){fail=false;return json({message:'模拟保存失败，请重试'},500)}profile={...profile,...body};return json(profile)}
 if(path==='/api/schedules'){slots.push({...body,id:2,assignees:[]});return json({id:2})}
 if(path.startsWith('/api/schedules/')){const id=Number(path.split('/').pop());if(req.method()==='DELETE')slots=slots.filter(s=>s.id!==id);else slots=slots.map(s=>s.id===id?{...s,...body,assignees:[]}:s);return json({id})}
 return json({});}
 if(path==='/api/public/appearance')return json({appearance,version:1});
 if(path==='/api/access/context')return json({mode:'LOCAL',kioskAvailable:true,allowedRemoteRoles:[]});
 if(path==='/api/setup/status')return json({initialized:true});if(path==='/api/auth/me')return json(profile);
 if(path==='/api/schedules')return json(slots);
 if(path==='/api/settings/weekdays')return json(Array.from({length:7},(_,i)=>({weekday:i+1,weekday_name:`星期${'一二三四五六日'[i]}`,enabled:i<5})));
 if(path==='/api/settings/duty-periods')return json([{startTime:'09:00',endTime:'12:00',enabled:true},{startTime:'13:00',endTime:'14:00',enabled:true}]);
 return json([]);
 });return writes;
}
for(const appearance of ['CLASSIC','EDITORIAL','SPATIAL']){
 test(`${appearance} profile preserves input limits, retry and keyboard password cancellation`,async({page})=>{
 const writes=await install(page,appearance);await page.goto('/#/admin/profile');await page.locator('[name=phone]').fill('1'.repeat(70));await expect(page.locator('[name=phone]')).toHaveValue('1'.repeat(64));expect(writes).toHaveLength(0);
 await page.locator('[name=phone]').fill('13800000001');await page.getByRole('button',{name:'保存资料'}).click();await expect(page.getByText('模拟保存失败，请重试',{exact:true})).toBeVisible();await expect(page.locator('[name=phone]')).toHaveValue('13800000001');await page.getByRole('button',{name:'保存资料'}).click();await expect(page.getByText('个人资料已保存',{exact:true})).toBeVisible();expect(writes.filter(w=>w.path==='/api/me/profile')).toHaveLength(2);
 await page.reload();await expect(page.locator('[name=phone]')).toHaveValue('13800000001');await page.getByRole('button',{name:'修改密码',exact:true}).click();await expect(page.getByRole('dialog')).toBeVisible();await page.setViewportSize({width:390,height:844});await page.screenshot({path:`../tasks/workspaces-20260913/${appearance}-password-390.png`,fullPage:true,animations:"disabled"});await page.keyboard.press('Escape');await expect(page.getByRole('dialog')).toHaveCount(0);await expect(page.getByRole('button',{name:'修改密码',exact:true})).toBeFocused();
 await page.emulateMedia({reducedMotion:'reduce'});await page.setViewportSize({width:960,height:720});await page.evaluate(()=>document.body.style.zoom='1.5');expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(1);
 });
 test(`${appearance} schedule saves, edits and archives only after confirmation`,async({page})=>{
 const writes=await install(page,appearance);await page.goto('/#/admin/schedules');await page.getByRole('button',{name:'新增此时段排班',exact:true}).click();const dialog=page.getByRole('dialog');await expect(dialog.locator('[name=scheduleWeekday]')).toHaveValue('1');await expect(dialog.locator('[name=schedulePeriod]')).toHaveValue('13:00-14:00');await dialog.locator('[name=scheduleTitle]').fill('示例新增排班');await dialog.getByRole('button',{name:'保存',exact:true}).click();await expect(dialog).toHaveCount(0);await expect(page.getByText('示例新增排班',{exact:true})).toBeVisible();expect(writes[0].method).toBe('POST');
 await page.getByRole('button',{name:'编辑 示例新增排班',exact:true}).click();await dialog.locator('[name=scheduleLocation]').fill('示例新地点');await page.setViewportSize({width:390,height:844});await page.screenshot({path:`../tasks/workspaces-20260913/${appearance}-schedule-editor-390.png`,fullPage:true,animations:"disabled"});await dialog.getByRole('button',{name:'保存',exact:true}).click();await expect(dialog).toHaveCount(0);await expect(page.getByText('示例新地点',{exact:false})).toBeVisible();expect(writes.some(w=>w.method==='PUT'&&w.body.location==='示例新地点')).toBe(true);
 await page.getByRole('button',{name:'归档 示例新增排班'}).click();await dialog.getByRole('button',{name:'取消',exact:true}).click();expect(writes.filter(w=>w.method==='DELETE')).toHaveLength(0);await page.getByRole('button',{name:'归档 示例新增排班'}).click();await dialog.getByRole('button',{name:'确认归档',exact:true}).click();await expect(page.getByText('示例新增排班',{exact:true})).toHaveCount(0);expect(writes.filter(w=>w.method==='DELETE')).toHaveLength(1);
 });
}

test('schedule import clears a previous valid preview when revalidation fails',async({page})=>{
 await install(page,'CLASSIC');let attempts=0;
 await page.route('**/api/schedules/import/preview',route=>{
  attempts+=1;
  return route.fulfill({status:attempts===1?200:503,contentType:'application/json',body:JSON.stringify(attempts===1?{valid:true,sourceRows:1,groupCount:1,memberCount:1,groups:[],issues:[]}:{message:'模拟校验失败'})});
 });

 await page.goto('/#/admin/schedules');await page.getByRole('button',{name:'导入排班'}).click();await page.getByRole('menuitem',{name:'批量导入'}).click();
 const dialog=page.getByRole('dialog');await dialog.locator('[name=scheduleImportFile]').setInputFiles({name:'排班.xlsx',mimeType:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',buffer:Buffer.from('fictional')});
 await dialog.getByRole('button',{name:'校验预览'}).click();await expect(dialog.getByRole('button',{name:'确认导入'})).toBeEnabled();
 await dialog.getByRole('button',{name:'校验预览'}).click();await expect(dialog.getByRole('alert')).toContainText('模拟校验失败');await expect(dialog.getByRole('button',{name:'确认导入'})).toBeDisabled();
 await expect(dialog.locator('.schedule-import-file-name')).toHaveText('排班.xlsx');
 });

test('schedule reveals the selected weekday on a narrow deep link',async({page})=>{
 await install(page,'EDITORIAL');await page.setViewportSize({width:390,height:844});
 await page.goto('/#/admin/schedules?weekday=5');await expect(page.getByRole('heading',{name:'星期五固定排班'})).toBeVisible();
 await expect.poll(()=>page.locator('.schedule-focus-days').evaluate(nav=>{
  const active=nav.querySelector('.schedule-focus-day.active');if(!active)return false;
  const outer=nav.getBoundingClientRect(),inner=active.getBoundingClientRect();
  return inner.left>=outer.left&&inner.right<=outer.right;
 })).toBe(true);
 await page.setViewportSize({width:320,height:720});
 await expect.poll(()=>page.locator('.mw-primary nav a').evaluateAll(links=>links.every(link=>getComputedStyle(link).whiteSpace==='nowrap'))).toBe(true);
 expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(1);
 });

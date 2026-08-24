# ca-attendance-system 代码审查报告 · 第二批（50 项新发现）

- **审查对象**：[AexuRb/ca-attendance-system](https://github.com/AexuRb/ca-attendance-system) `main` 分支（v2.7.0）
- **与第一批的关系**：第一批报告（55 项）见《代码审查报告-ca-attendance-system-55项发现.md》。本批全部为**新发现，与第一批零重复**，覆盖第一批未深审的区域：维修/培训/自定义导出服务、剩余控制器与设置服务、前端剩余组件与样式系统、脚本与测试代码、并做了**文档-代码交叉核对**。
- **本批方法升级**：除静态审读外，本次在本地**实际构建并运行了后端**（`mvn package` + 端口 18080 起服 + curl 打点），对可疑结论做了运行时实证。标注【实测】的条目均为运行验证过的行为，非推测。
- **严重度统计**：中 9 / 中低 2 / 低 27 / 提示 12。本批最重要的发现是 **#1–#3（Jackson 双世代分裂及其实际后果）**，建议优先处理。

| 分区 | 条目 |
| --- | --- |
| 一、运行时实证发现 | 1–8 |
| 二、后端其余发现 | 9–26 |
| 三、前端 | 27–41 |
| 四、测试与流程 | 42–50 |

---

## 一、运行时实证发现（8 项）

### 1.【中】【实测】双 Jackson 世代并存于同一进程
- **位置**：`backend/src/main/java/com/ca/attendance/config/JacksonConfig.java:10-14`；运行时 classpath 实测同时含 `jackson-databind-3.1.4.jar`（`tools.jackson.*`，Spring Boot 4 / MVC 使用）与 `jackson-databind 2.21.4`（`com.fasterxml.*`，本配置提供的 bean 使用）
- **问题**：Boot 4 的 HTTP 消息转换走 Jackson 3（实测 API 响应中 `Instant`/`LocalDateTime` 均为 ISO 字符串）；而 `JacksonConfig` 定义的裸 Jackson 2 `ObjectMapper` bean 被注入到 `OperationLogService`、`DutyPeriodService`、`TrainingService` 等内部路径。两套序列化规则不同（日期、未知字段严格性、空 Bean 处理），同一条数据在"API 响应"与"落库 JSON"里长得不一样。
- **建议**：删除该配置改用 Boot 管理的构建器，或统一两套行为；长期应收敛到 Jackson 3。

### 2.【中】【实测】operation_logs 快照中的日期被序列化成数组
- **位置**：`backend/src/main/java/com/ca/attendance/log/OperationLogService.java:39-40`（`toJson` 用 #1 的 Jackson 2 mapper）
- **问题**：实测新建培训场次后，日志 `after_data` 落库为 `"trainingDate":[2026,8,16],"startTime":[19,0],"createdAt":[2026,8,17,0,21,5]` —— 所有 `LocalDate/LocalTime/LocalDateTime` 都是数字数组。管理员在日志页看到的全部 before/after 快照（审核、修改、导入记录）日期均不可读，审计价值受损；且这批畸形 JSON 已在库里，前端无法正确渲染历史日志。
- **建议**：随 #1 一并修复；写一个一次性迁移把存量数组形态日期转回字符串。

### 3.【中】【实测】畸形请求体返回 500 + ERROR 堆栈，而非 400
- **位置**：`backend/src/main/java/com/ca/attendance/common/GlobalExceptionHandler.java:36-41`
- **问题**：实测发送非法 UTF-8/畸形 JSON 到 `/api/setup/initialize`，得到 500"服务器内部错误"+ ERROR 级堆栈。`GlobalExceptionHandler` 未处理 `HttpMessageNotReadableException`、`MethodArgumentTypeMismatchException`（如 `?from=notadate`），二者都落入兜底 500。讽刺的是日志模块自己做了友好解析（`OperationLogQueryService.parseDate` 抛 400"日期格式不正确"），而考勤/维修/培训控制器用 `@DateTimeFormat` 绑定、错误时直接 500 —— 同一问题两种口径。
- **建议**：advice 里补两个 handler 映射为 400。

### 4.【中】远程登录限流在隧道场景退化为"共享 IP + 账号"键
- **位置**：`backend/src/main/java/com/ca/attendance/auth/RemoteLoginAttemptGuard.java:46-48`（键 = `clientAddress|account`）；`RemoteAccessPolicy.java:46-49`（`remoteClientAddress` 有意不信任转发头，取 `getRemoteAddr()`）
- **问题**：设计上"隧道在本机终结，不信任 X-Forwarded-For"是对的，但后果是 8081 上所有请求的 `RemoteAddr` 恒为 `127.0.0.1` —— 限流键对**所有远程用户**收敛为 `127.0.0.1|某账号`。攻击者可以故意输错 5 次密码，把真实管理员的该账号锁 10 分钟（账号锁定 DoS）；反过来若隧道支持多路复用，也无法按来源区分。文档建议的"樱花穿透"正是这种拓扑。
- **建议**：至少在文档中明示该限制；或改为账号维度的全局锁定并配更长冷却+告警日志（日志已有），评估是否可让隧道传可信头。

### 5.【中】备份目录无任何保留策略
- **位置**：`backend/src/main/java/com/ca/attendance/maintenance/BackupFileStore.java`（全文无按数量/年龄清理逻辑；仅手动 `delete`）
- **问题**：结合第一批 #15（每删一条考勤记录生成一个全库备份），正常使用几年后 `backups/app/` 会积累成千上万份全库 zip，磁盘写满后 SQLite 写入失败、系统整体瘫痪——而"清理日志/删除记录"恰恰又依赖备份成功（第一批 #9），形成第二重死锁。
- **建议**：保留最近 N 份 + M 天，删除类操作产生的安全备份单独限额。

### 6.【中】值班时段解析失败被静默吞掉，签到判定随之漂移
- **位置**：`backend/src/main/java/com/ca/attendance/settings/DutyPeriodService.java:49-55`
- **问题**：`list()` 读取 `app_settings` 中的 JSON，`catch (Exception ignored) return List.of()`。若该行损坏（手工编辑、断电半写、异常迁移），全部时段静默变空 → `contains()` 恒 false → 若策略开启"必须值班时段"，所有新签到 `withinDutyPeriod=false` 被 recompute 判 INVALID，**无任何日志或界面告警**，用户只看到"签到不计时长"。
- **建议**：解析失败至少 ERROR 日志 + 在设置页显著报错；空时段与"未配置"应区分。

### 7.【中低】维修单创建时"风险确认/隐私确认"缺省为"已确认"
- **位置**：`backend/src/main/java/com/ca/attendance/repair/RepairCaseService.java:560-562`
- **问题**：`request.riskAcknowledged() == null ? fallback == null || fallback.riskAcknowledged() : ...` —— 新建（fallback==null）时 null 归结为 **true**；`privacyAcknowledged` 同理。API 层面未显式传确认位的创建请求会直接落库"风险确认：是/隐私提示：是"，与协议书的纸面承诺（现场签字确认）相悖。对比 `dataBackupConfirmed`（:560 前半）新建缺省为 false，三兄弟口径不一致。
- **建议**：三个确认位新建时缺省 false，强制显式传入。

### 8.【低】两份维修协议全文硬编码在 Java 常量里
- **位置**：`backend/src/main/java/com/ca/attendance/repair/RepairCaseService.java:57-128`（约 70 行文本块）
- **问题**：协议条款（含赔偿比例表）属于业务方会逐年修订的内容，现在改一个字要重新编译发版整个桌面应用。
- **建议**：外置为资源文件或 app_settings，保留版本号。

---

## 二、后端其余发现（18 项）

### 9.【低】维修导出无行数上限且逐列 autoSizeColumn
- **位置**：`RepairCaseService.java:330-347`（exportCases 调用不分页的 `list()`）、`:686-688`（18 列 `autoSizeColumn`，POI 逐单元格测宽，大表极慢）
- **建议**：对齐 CustomExportService 的 5 万行上限；autoSize 改定宽。

### 10.【低】培训导出（名单/统计汇总）无行数上限
- **位置**：`TrainingService.java:375`（`queryParticipants(sessionId, null, null, null)` 全量）、`:387`（`list()` 全量场次）
- **建议**：同上加限。

### 11.【提示】自定义导出 5 万行上限校验发生在全量加载之后
- **位置**：`CustomExportService.java:138-144`（先 `query(...)` 把全部行物化进 List，再查 size 抛错）
- **问题**：防止了工作簿爆炸，防不住查询本身的内存峰值（50 万行也先拉出来再扔）。
- **建议**：SQL 加 `LIMIT MAX_ROWS+1` 探测。

### 12.【低】catch-all 吞异常不打日志的又一组实例
- **位置**：`DutyPeriodService.java:80-82`（"值班时间段保存失败"）、`TrainingService.java:352-354`（"Excel 文件读取失败"）、`UserService.java:140-142`（同前）
- **问题**：与第一批 #17（StatsService.export）同病：根因（编码？损坏文件？数据库锁？）全部丢失。
- **建议**：统一改为记 ERROR 后再抛业务异常。

### 13.【提示】维修单从"已完成"改回"进行中"会清空 completed_at
- **位置**：`RepairCaseService.java:541-545`（非 COMPLETED 状态强制 `completedAt = null`）
- **问题**：误操作状态来回切换后，原始完成时间永久丢失。
- **建议**：回退状态时保留首次完成时间（或记入 remark）。

### 14.【提示】training_participants.attendance_status 是死列
- **位置**：`V1__initial_schema.sql:145`（`CHECK (IN ('PRESENT','ABSENT','LEAVE'))`）vs `TrainingService.java:298,837`（只写 'PRESENT'）、`V10`（把历史值也归一为 PRESENT）
- **问题**：三值枚举列实际只会存一个值，误导后续维护者以为支持缺席/请假统计。
- **建议**：文档标注废弃，或真正实现缺席语义。

### 15.【提示】各模块列表默认时间窗口不一致
- **位置**：`TrainingService.java:129-130`（默认 end = 今天+1 年）vs `RepairCaseService.java:176-177`（end = 今天）vs 日志模块（无默认、可选）
- **问题**：三个列表页"不传日期"时覆盖范围各不相同，用户心智模型混乱；跨年查询培训时还隐式包含未来一年。
- **建议**：统一默认窗口语义（如"本学年"）并文档化。

### 16.【提示】修改成员任意字段都会吊销其全部会话
- **位置**：`UserService.java:200`（update 尾部无条件 `tokens.revokeUser(id)`）
- **问题**：会长帮成员改个联系电话，该成员立刻被踢下线重新登录。作为安全默认可辩护，但对高频的资料修正体验差。
- **建议**：仅在角色/状态/密码变化时吊销。

### 17.【低】培训导入逐行 check-then-upsert
- **位置**：`TrainingService.java:517-523`（`participantExists` 查一次再 INSERT/UPDATE）
- **问题**：与第一批 #11 同根：正确性依赖单连接串行化；每行 2 次查询在 3000 行上限内也是 6000 次往返。
- **建议**：SQLite `INSERT ... ON CONFLICT(session_id, student_no_snapshot) DO UPDATE` 一条搞定（编号生成处已用过该语法）。

### 18.【提示】备份下载将整个 zip 读入内存
- **位置**：`BackupController.java:37-47` + `BackupService.download`
- **问题**：`ResponseEntity<byte[]>` 全量缓冲；数据库接近备份上限（10 万行/表）时 zip 可达数百 MB，内存峰值同步放大。
- **建议**：改 `InputStreamResource`/文件流式响应。

### 19.【低】烟测脚本可能把真实管理员密码改为仓库中的明文
- **位置**：`scripts/full-smoke-test.ps1:299-308`（mustChangePassword 时改密为 `SmokeAdmin-2026`）、`:722-741`（finally 中恢复基线，失败仅 `Write-Warning`）
- **问题**：脚本被用于连接真实数据目录时，若恢复基线失败（后端中途退出、令牌过期），管理员密码就停留在提交在公开仓库里的 `SmokeAdmin-2026`。
- **建议**：恢复失败应非零退出码 + 醒目横幅提示立即改密；或默认只允许连隔离实例（run-isolated-smoke 已有，把 full 冒烟也约束进去）。

### 20.【低】密码策略无复杂度要求，与远程管理建议脱节
- **位置**：`UserInputPolicy.java:48-55`（仅限长度 6–64，`123456` 合法）
- **问题**：README 建议远程管理"设置高强度后台密码"，但后端接受 6 位纯数字；结合 #4 的共享限流键，弱密码 + 暴力尝试的组合风险放大。
- **建议**：至少对 PRESIDENT/ADMIN 角色强制字母+数字组合。

### 21.【提示】成员可自助修改"年级"（统计分组字段）
- **位置**：`UserService.java:145-156`（`updateProfile` 接受 grade）+ `ProfileController.java:15-18`
- **问题**：年级是统计汇总的分组维度（`StatsService.summary` GROUP BY grade），成员自助改年级会改变统计归属，而成员管理里改年级需要会长权限——两处口径不一。
- **建议**：个人资料页隐藏年级字段或设为只读。

### 22.【提示】kiosk 查询两个入口的长度校验不一致
- **位置**：`PublicAttendanceController.java:19-23`（路径变量版：无长度限制、不限字符）vs `:26-30`（query 版：`AttendanceService.lookupByInput:82-84` 超 128 字符报 400）
- **问题**：路径版 `/lookup/{studentNo}` 可传超长任意串直达查询（有限流兜底），行为与 query 版不一致。
- **建议**：路径版同样限长，或统一只留一个入口。

### 23.【提示】/api/health 在远程端口同样无鉴权可探
- **位置**：`AuthInterceptor.java:38`（`/api/health` 全放行，含 8081）
- **问题**：远程入口可被探测指纹（应用名、数据库类型）。对"仅供隧道"的定位算可接受的信息暴露，但未见文档明示。
- **建议**：远程端口的 health 返回最小化，或文档标注。

### 24.【提示】数据中心页 10 个 COUNT 非同一快照
- **位置**：`MaintenanceSummaryService.summary()`（连续 10 次独立 `SELECT COUNT(*)`，无事务包裹）
- **问题**：单连接池下被其他写请求插队时，页面上各表数字来自不同时刻，微小不一致（纯展示影响）。
- **建议**：`@Transactional(readOnly = true)` 包一次。

### 25.【提示】SourceType 枚举零引用，纯死代码
- **位置**：`backend/src/main/java/com/ca/attendance/common/SourceType.java`（`PUBLIC`/`ADMIN_MANUAL` 全库无任何使用，grep 实证）
- **建议**：删除或实现来源标记（签到记录本可区分公开提交/补录来源）。

### 26.【提示】日期参数两种 API 风格并存
- **位置**：`OperationLogController.java:20-22`（`String from/to` + service 手动解析报 400）vs `AttendanceController.java:22-24` 等（`LocalDate` + `@DateTimeFormat`，错误时落入 #3 的 500）
- **问题**：日志模块的健壮做法恰恰证明其他控制器的方式有坑（见 #3）；同一系统两种风格并存。
- **建议**：统一为其中一种并补全局 handler。

---

## 三、前端（15 项）

### 27.【中】账号/排班选择器的搜索关键字在对话框重开时不重置
- **位置**：`frontend/src/features/accounts/AccountPicker.vue:100`、`features/schedule/ScheduleAssigneePicker.vue:66`
- **问题**：两个选择器常驻挂载于 ModalDialog 内，`keyword` 只随输入变化；上次搜索"张"后关闭，下次为别的记录打开时列表仍被过滤。库内其他对话框均以 `watch(open)` 重置表单，唯此两处遗漏。
- **建议**：open 变化时清空 keyword 与结果。

### 28.【中】值班时段前端校验缺失两条后端规则
- **位置**：`frontend/src/features/settings/dutyPeriods.ts:20-37` vs 后端 `DutyPeriodService.java:125-127`（最多 12 个）、`:130-134`（不能重复）
- **问题**：前端允许添加第 13 个时段或重复时段并点保存，服务端拒绝后用户只看到兜底错误、本地列表停在非法状态。
- **建议**：前端补齐两条规则（前后端校验清单应有对照测试）。

### 29.【低】后台顶栏服务状态硬编码为"在线"
- **位置**：`frontend/src/layouts/admin/AdminTopbar.vue:42`（`<ServiceStatus :online="true" compact />`）
- **问题**：签到台接了真实健康轮询（KioskHeader.vue:14-19），后台却恒显"本机服务正常"——后端挂掉时每行请求都失败，徽标仍绿，恰在其设计场景里误导。
- **建议**：复用 kiosk 的健康状态或移除该徽标。

### 30.【中】设计令牌重复定义，整块样式成为死代码
- **位置**：`frontend/src/styles/admin-details.css:2-6`（`--admin-canvas/--admin-line/--admin-line-soft`）与 `admin-theme.css:3-9` 在**同一选择器** `.refined-admin-layout` 上重定义不同值；`main.ts:16` 先导入 details、`:19` 后导入 theme，theme 恒胜 —— details 的令牌块整体失效，但其后约 20 处 `var(--admin-line)` 引用（:92,137,188,445,683,940,1029,1161…）实际渲染的是另一文件的调色板。宽度令牌同样两套（`admin-shell.css:2-3` vs `admin-theme.css:3-4`），侧栏折叠过渡也被覆盖（`admin-shell.css:12` 0.3s vs `admin-theme.css:18` 0.42s）。
- **问题**：改"错"的那份文件毫无效果，两套调色板让真实意图不可考。
- **建议**：合并为单一 tokens.css，删除影子定义。

### 31.【低】路由过渡动画规则整组重复
- **位置**：`admin-details.css:1433-1452` 与 `admin-theme.css:496-514` 都定义 `.admin-view-enter/leave-*`，import 顺序使前者全部失活。
- **建议**：删除一份（与 #30 同根，可一并清理）。

### 32.【低】登录页跳转未校验 `next` 查询参数
- **位置**：`frontend/src/pages/auth/LoginPage.vue:133`
- **问题**：`/login?next=//evil.com` 会在登录成功后执行 `router.replace('//evil.com')`，底层 `history.pushState` 抛跨域 SecurityError，被 catch 当作普通错误展示——用户明明登录成功却看到报错卡住。虽非可利用的开放重定向（Vue Router 拦截了），体验与语义都错。
- **建议**：仅接受以单个 `/` 开头的路径。

### 33.【低】签到台把整个交互区包进一个 aria-live 区域
- **位置**：`frontend/src/pages/kiosk/KioskAttendanceCourt.vue:3`（`aria-live="polite"` 包住输入框、选择按钮、确认票、错误文本）；`KioskSchedulePanel.vue:5` 类似
- **问题**：每次步骤切换读屏器播报整棵新子树，且可聚焦控件位于会突变的 live 区内是 ARIA 反模式。
- **建议**：live 只包裹状态文本节点。

### 34.【低】listbox 语义无键盘模型支撑
- **位置**：`AccountPicker.vue:36-48`（`role="listbox"/"option"` + `aria-selected`，但选项是普通 Tab 序按钮，无方向键/Home/End）
- **问题**：声明了 listbox 就背上了 ARIA 交互契约，读屏用户会预期方向键导航。
- **建议**：实现 roving tabindex 或降级为普通 group/list。

### 35.【低】维修状态 tablist 无箭头导航、无 tabpanel 关联
- **位置**：`frontend/src/features/repairs/RepairStatusTabs.vue:2-11`（`role="tablist"/"tab"` 无左右方向键、无 `aria-controls`，对应内容区也无 `role="tabpanel"`）
- **建议**：同 #34，补契约或降级。

### 36.【低】维修历史行仅 Enter 可激活
- **位置**：`frontend/src/features/repairs/RepairHistoryTable.vue:16-23`（`<tr tabindex="0" @click @keydown.enter>`）
- **问题**：Space 这个标准激活键无效，行本身也无"可激活"角色提示。
- **建议**：补 `@keydown.space.prevent`，或让行内详情按钮承担激活。

### 37.【低】tsconfig 严格度缺口 + 测试全局类型注入生产检查
- **位置**：`frontend/tsconfig.json:2-16`
- **问题**：缺 `noUncheckedIndexedAccess`（代码里大量 `value.split(":")[下标]` 裸解构，如 KioskSchedulePanel.vue:106）、`noUnusedLocals/Parameters`、`noFallthroughCasesInSwitch`；`"types": ["vite/client","vitest/globals"]` 使生产代码类型检查也能通过 `describe/expect` 引用。
- **建议**：app 与 test 的 tsconfig 拆分，`types` 仅留在 test 配置。

### 38.【低】favicon 用纯白 logo，浅色标签页不可见
- **位置**：`frontend/index.html:6`（`/brand/ca-logo-white.png`）
- **问题**：默认浅色浏览器主题下标签页图标是空白；黑色版资源已存在（`ca-logo-black.png`）。
- **建议**：换黑色版或双色 SVG。

### 39.【低】AdminLayout 组件 setup 直接访问 localStorage 未捕获异常
- **位置**：`frontend/src/layouts/AdminLayout.vue:79-81`（setup 期间 `getItem`）、`:120,:125`
- **问题**：存储被禁用（cookie 屏蔽策略、部分内嵌 webview）时 setup 抛 SecurityError，整个后台布局渲染失败，而不只是侧栏折叠态失效。
- **建议**：try/catch 包裹并回退默认值。

### 40.【提示】focusFirstInvalid 存在共享实现，培训编辑器却各自用全局 querySelector 复制
- **位置**：`TrainingSessionEditorDialog.vue:92`、`TrainingParticipantEditorDialog.vue:81`（`document.querySelector('#xxx [aria-invalid=true]')`）vs 共享 `shared/validation/userInput.ts` 的 `focusFirstInvalid`（SetupPage、PasswordPage、MemberEditorDialog 等在用）
- **建议**：两处改用共享实现。

### 41.【提示】prefers-reduced-motion 全局覆盖存在 7 份重复（其中一份文件里 2 份）
- **位置**：`base.css:128-137`（`*` 选择器已全覆盖）之下，`kiosk.css:963-971`、`kiosk-theme.css:685-694` **及** `:1676-1685`（同文件两份）、`auth.css:556-562`、`admin-theme.css:766-773`、`admin-shell.css:404-412` 全部冗余（约 60 行死重）
- **建议**：保留 base.css 一份即可。

---

## 四、测试与流程（9 项）

### 42.【低】8 个后端类零测试引用（含暗藏 bug 的 JdbcTime）
- **位置**：grep 实证 `backend/src/test` 中零引用：`JdbcTime`、`JacksonConfig`、`InitialAdminInitializer`、`DesktopControlService`、`MaintenanceSummaryService`、`AccessController`、`HealthController`、`CustomExportController`
- **问题**：第一批 #16（`JdbcTime.localTime` 捕错异常类型）与本批 #1/#2（Jackson 分裂）恰好都落在无测试的类上——不是巧合，是测试网的盲区地图。
- **建议**：至少为 JdbcTime 与序列化行为补单测。

### 43.【低】全部测试没有任何日期字段的 JSON 形态断言
- **位置**：grep `jsonPath.*dutyDate|submittedAt|checkInTime` 在 `backend/src/test` 零命中
- **问题**：双 Jackson 分裂（#1/#2）因此能穿过 188 个测试和发布检查表。
- **建议**：补一条 MockMvc 断言 `"$.submittedAt", matchesPattern(ISO)` 之类的契约测试。

### 44.【提示】无 dependabot/renovate 依赖更新自动化
- **位置**：`.github/` 下仅 `workflows/ci.yml`
- **问题**：依赖更新全靠手动 `npm audit` + overrides 钉版（已钉 brace-expansion/minimatch），无 PR 级提醒。
- **建议**：加 dependabot.yml（maven + npm 分组、周更）。

### 45.【提示】frontend 与 desktop 两个 package.json 各维护一份 overrides
- **位置**：`frontend/package.json`（`brace-expansion: 5.0.9`、`minimatch: 10.2.5`）与 `desktop/package.json`（`brace-expansion: 5.0.9`）
- **问题**：同一供应链钉版策略两处手工同步，未来只改一处即漂移。
- **建议**：以注释互指 + 发布检查表核对项固化。

### 46.【提示】CI 后端 job 不验证可打包性
- **位置**：`.github/workflows/ci.yml` backend job 仅 `mvn --batch-mode test`
- **问题**：repackage/资源打包类失败（如 static 目录异常）要到手动触发的 desktop-package 阶段才暴露。
- **建议**：backend job 末尾加 `mvn -q -DskipTests package`。

### 47.【提示】/api/setup/status 向本地无认证暴露用户数
- **位置**：`AuthInterceptor.java:41-43`（`/api/setup/` 本地放行）+ `SetupService.status()`（返回 userCount）
- **问题**：本机任何进程可探知系统是否初始化、用户规模。本地信任模型下影响很小，远程端口已封，但属未文档化的信息暴露。
- **建议**：已初始化后该接口收敛为只返回布尔值。

### 48.【中低】批量状态修改支持"按当前筛选全量匹配"，无服务端确认机制
- **位置**：`UserService.java:274-276`（ids 为空时按 keyword/role/statusFilter/grade 反查全量执行）
- **问题**：会长在成员页带着模糊关键字误点"全部停用"，影响面 = 当前筛选命中的所有人；后端仅要求一个可选 reason，无类似维修永久删除的"输入编号确认"（purge 有 caseNo 确认，对比鲜明）。
- **建议**：空 ids 模式要求显式 confirm 令牌（如返回受影响人数二次确认）。

### 49.【提示】维修负责人解析：姓名留空时静默指派为当前操作者
- **位置**：`RepairCaseService.java:586-592`（`requestedName == null || equals(current.name)` → self）
- **问题**：创建维修单时负责人一栏留空，落库负责人=创建人，无任何提示；若实际维修人是别人，统计归属错误。
- **建议**：留空时要求显式选择或返回警告。

### 50.【提示】值班时段 contains() 每次调用都做字符串解析
- **位置**：`DutyPeriodService.java:89-93`（每次 `LocalTime.parse(period.startTime())`）
- **问题**：签到台每次 lookup/submit 都会走这条路径；字符串格式在 normalize 时已保证，重复解析纯浪费（微性能，但也是"时段以字符串存储"这一建模的税）。
- **建议**：list 后缓存解析结果，或字段直接存 LocalTime。

---

## 附：第二批修复优先级建议

1. **立即**：#1 + #2（Jackson 双世代与日志快照畸形，含存量数据迁移）、#3（500→400，两行 handler）、#7（确认位缺省 true，一行）。
2. **短期**：#4（限流键文档化/调整）、#5（备份保留策略，与第一批 #9/#15 同盘棋）、#6（设置解析失败告警）、#28/#30（前端校验与样式令牌）。
3. **随迭代**：其余低/提示项。

> 统计：中 9（#1、#2、#3、#4、#5、#6、#27、#28、#30）；中低 2（#7、#48）；低 27；提示 12。合计 50 项。
> 两批累计 105 项发现。

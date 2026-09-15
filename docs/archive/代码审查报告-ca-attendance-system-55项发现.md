# ca-attendance-system 多轮代码审查报告（55 项发现）

- **审查对象**：[AexuRb/ca-attendance-system](https://github.com/AexuRb/ca-attendance-system) `main` 分支（v2.7.0）
- **审查方式**：共 4 轮 —— 第 1 轮整体审查（安全架构 + 数据层 + 工程流程）；第 2 轮后端服务层逐文件深审（Attendance/Stats/Schedule/JdbcTime/迁移/初始化）；第 3 轮前端页面与共享组件深审；第 4 轮桌面端 runtime / 安装器 / 便携版脚本深审。所有条目均给出文件与行号，行号以本次审查的工作副本为准。
- **总体结论**：项目工程质量显著高于同类规模系统的平均水平 —— 全库参数化 SQL 无注入、备份/恢复对 zip 炸弹与路径穿越防护完善、Electron 安全配置到位、188 个后端测试跑真实 SQLite、文档与代码一致性经得起抽查。**未发现"高危"级漏洞**；下列 55 项以中低严重度为主，集中在三类主题：**单连接 SQLite 上的运维上限、前端交互竞态、发布流程一致性**。
- **严重度统计**：中 14 项 / 低 33 项 / 提示 8 项。

| 分区 | 条目 |
| --- | --- |
| 一、安全 | 1–7 |
| 二、后端：数据与事务 | 8–20 |
| 三、后端：业务一致性 | 21–26 |
| 四、前端：交互与健壮性 | 27–42 |
| 五、桌面端与发布 | 43–50 |
| 六、工程流程 | 51–55 |

---

## 一、安全（7 项）

### 1.【中】本地登录接口无速率限制，签到台同源可暴力猜解
- **位置**：`backend/src/main/java/com/ca/attendance/auth/AuthController.java:21-24`、`AuthService.java:41-44`
- **问题**：`RemoteLoginAttemptGuard` 只在 `context.remote()`（8081 入口）时生效；本地入口 `/api/auth/login`（127.0.0.1:8080）完全不限流。公开签到台与登录接口同源，任何能操作协会电脑的人（或本地恶意进程）可无限次尝试管理员密码。
- **建议**：对本地登录也施加宽松限流（如每分钟 30 次/地址），或连续失败锁定短时间。

### 2.【中】attendance_records 缺少 (user_id, duty_date) 唯一约束
- **位置**：`backend/src/main/resources/db/sqlite/V1__initial_schema.sql:79`（仅非唯一索引 `idx_attendance_user_date`）
- **问题**：防止同一成员同一天重复签到完全依赖应用层 check-then-act；对比 `training_participants`（V1:153）有 `UNIQUE (session_id, student_no_snapshot)`。配合第 11 条，防重语义全靠单连接池串行化维持。
- **建议**：增加部分唯一索引（SQLite 支持带 WHERE 的唯一索引，如排除已删除记录），把不变量下沉到 schema。

### 3.【低】PublicSubmissionRepository.save 未处理重复键冲突
- **位置**：`backend/src/main/java/com/ca/attendance/attendance/PublicSubmissionRepository.java:42-57`
- **问题**：`request_id` 是主键，但 `save` 是普通 INSERT，无 `DuplicateKeyException` 处理（对比 `UserService.create:107-109` 有处理）。一旦并发窗口打开（见第 11 条），重复提交会以 500 收场而不是幂等返回。
- **建议**：捕获重复键后回读收据，与 `findByRequestId` 路径合并。

### 4.【低】InitialAdminInitializer 可静默提升已有成员为管理员，且不留审计
- **位置**：`backend/src/main/java/com/ca/attendance/config/InitialAdminInitializer.java:37-48`
- **问题**：环境变量 `INITIAL_ADMIN_ACCOUNT` 指定的学号若已存在普通成员，将被直接改为 `role='ADMIN'` 并重置密码；该学号也未经过 `UserInputPolicy` 格式校验；整个提升过程不写 `operation_logs`。作为恢复通道可以理解，但属于无痕提权路径。
- **建议**：至少补一条 `INITIALIZE_SYSTEM`/`PROMOTE_ADMIN` 日志；学号走同一校验。

### 5.【低】AgreementDialog iframe sandbox 含 allow-same-origin
- **位置**：`frontend/src/shared/ui/AgreementDialog.vue:53`
- **问题**：`sandbox="allow-same-origin allow-modals"` 渲染服务端下发的协议 HTML。当前没有 `allow-scripts` 是安全的，但 `allow-same-origin` 意味着未来谁加上脚本类属性，协议内容即获得应用同源能力。
- **建议**：若打印功能允许，去掉 `allow-same-origin`；或为 srcdoc 附加 CSP。

### 6.【提示】安装器对数据目录授予 Users 组修改权限
- **位置**：`desktop/build/installer.nsh:10-18`（icacls `/grant *S-1-5-32-545:(OI)(CI)M`）
- **问题**：任何本地 Windows 账户都能改写 `data/backups/exports/logs`（含 attendance.db 与备份）。单用户协会机器上这是让非提权进程可写的务实选择，但与 README"按成员隐私数据妥善保管"的定位存在落差，且文档未说明。
- **建议**：在"数据安全"文档中明示此权限模型；或改为仅授予当前安装用户。

### 7.【提示】登录存在时序性学号枚举
- **位置**：`backend/src/main/java/com/ca/attendance/auth/AuthService.java:47-53`
- **问题**：学号不存在时跳过 BCrypt（约 100ms）直接返回，存在时执行哈希比较；文案一致但响应时间可区分，可用于枚举有效学号。本地内网威胁下风险很低。
- **建议**：学号不存在时也做一次假哈希比较，抹平时序。

---

## 二、后端：数据与事务（13 项）

### 8.【中】成员 Excel 导入无文件大小/行数上限
- **位置**：`backend/src/main/java/com/ca/attendance/user/UserService.java:115-143`（仅检查 `file.isEmpty()`）、`:379-460`（导入循环）；`application.yml:10`（multipart 上限 128MB）
- **问题**：`WorkbookFactory.create` 全量 DOM 加载，恶意/超大 xlsx 可致桌面应用 OOM；即便 1 万行的合法导入，也是 1 万次 BCrypt（每次约 100ms）+ 每行 2–3 次查询跑在单个事务里 —— 单连接池下**包括公开签到台在内的整个应用被阻塞数分钟**。同库的兄弟导入都做了上限：排班 5MB/1000 行（`DutyScheduleImportService.java:48-49`）、培训 3000 行（`TrainingService.java:39`），唯独成员导入漏了。
- **建议**：对齐兄弟导入的上限；行级校验前置、BCrypt 改为分批提交。

### 9.【中】备份行数上限会形成"自锁"死局
- **位置**：`backend/src/main/java/com/ca/attendance/maintenance/BackupArchiveWriter.java:128-141`（单表 >10 万行即抛异常）、`BackupArchiveLimits.java:8-9`；依赖方：`AttendanceService.java:541-543`（删记录前必须备份成功）、`UserService.java:250`、`:318-320`、`OperationLogQueryService.java:86-91`（清日志前还要备份）
- **问题**：`operation_logs`（每次变更/导出都写）与 `public_attendance_submissions`（只增不删，全库无 DELETE）都是只增表；任一超过 10 万行后 `createBackup` 必然失败，导致**手动备份、删除考勤记录、删除成员、批量停用、甚至清空日志功能本身**全部不可用 —— 系统进入只能手工改库才能解脱的状态。
- **建议**：给两张表加保留期/归档策略；或删除、清空类流程不再硬依赖备份成功（降级为警告）。

### 10.【中】恢复备份中"列合法但值畸形"的数据返回 500
- **位置**：`backend/src/main/java/com/ca/attendance/maintenance/DatabaseRestoreExecutor.java:130-184`；`restoreTable` 只捕获 `ApiException`/`IOException`（`:83-87`）
- **问题**：`Timestamp.valueOf` 抛 `IllegalArgumentException`、`LocalDate.parse`/`LocalTime.parse` 抛 `DateTimeParseException`，均未被捕获，最终落入 `GlobalExceptionHandler.handleOther` 变成 500"服务器内部错误"+ ERROR 堆栈，而不是 400"备份文件已损坏"。事务本身会干净回滚，不损坏数据。
- **建议**：在解析处捕获并转 `ApiException.badRequest`。

### 11.【低】公共签到幂等与防重的并发安全完全依赖 `maximumPoolSize = 1`
- **位置**：`backend/src/main/java/com/ca/attendance/config/SQLiteDataSourceConfiguration.java:32`；被保护的是 `AttendanceService.java:174-219` 的 check-then-act
- **问题**：当前单连接串行化一切事务，语义正确；但这份正确性距失效只差一行配置改动，且无注释/断言固化该前提（schema 层缺口见第 2、3 条）。
- **建议**：连接池配置处写明"并发正确性依赖此值=1"；或补 schema 约束后解除耦合。

### 12.【低】LIKE 过滤未转义通配符、枚举字段未做等值校验
- **位置**：`AttendanceRepository.java:353-367`、`UserRepository.java:175-186`、`CustomExportService.java:704-766`、`DutyScheduleService.java:75`
- **问题**：均为绑定参数（无注入），但 `status=%`、关键字含 `%`/`_` 会意外匹配；枚举型过滤用 LIKE 而非白名单等值。
- **建议**：枚举字段白名单校验 + `=`；文本加 `ESCAPE`。

### 13.【低】日志导出与考勤搜索无 LIMIT、全内存构建工作簿
- **位置**：`OperationLogQueryService.java:94-118`（导出无 LIMIT + XSSF 全内存，行内含完整 before/after JSON）；`AttendanceRepository.java:171-218`（非分页 search/me 无 LIMIT）
- **问题**：自定义导出有 5 万行上限（`CustomExportService.java:39,386-389`），这两处没有；日志表增大后导出是一次无顶内存尖峰。
- **建议**：导出加上限 + SXSSF 流式写；非分页接口加保护性上限。

### 14.【低】数据库迁移在 Web 服务器开始接收请求之后才执行
- **位置**：`backend/src/main/java/com/ca/attendance/config/DatabaseMigrator.java:17-27`（CommandLineRunner，Tomcat 先起）
- **问题**：升级迁移（V5/V9 为全表 UPDATE）期间早到的请求阻塞 10 秒后 500。本地单用户窗口很小。
- **建议**：迁移前移到容器启动前（如 `ApplicationRunner` 之前自定义 Bootstrap），或接受并文档化。

### 15.【低】每删一条考勤记录都在删除事务内做一次全库备份
- **位置**：`AttendanceService.java:541-546`；备份文件发布 `BackupFileStore` 的 `files.publish` 非事务
- **问题**：连删 20 条坏记录 = 20 次全库 zip，每次阻塞全部请求；且外层事务回滚时安全备份文件已入列表无法收回（残留无害但易误导）。
- **建议**：连续删除合并为一次备份；或接受现状但记录清理策略。

### 16.【中】JdbcTime.localTime 捕获了错误的异常类型（实际 bug）
- **位置**：`backend/src/main/java/com/ca/attendance/common/JdbcTime.java:63-66`
- **问题**：`catch (IllegalArgumentException)` 捕不住 `LocalTime.parse` 抛出的 `DateTimeParseException`（后者直接继承 RuntimeException）。库内出现畸形时间字符串时将以未处理异常 500 失败，而不是设计中的 SQLException 语义。同文件 `localDateTime:44` 的 catch 是正确的（`Timestamp.valueOf` 确实抛 IllegalArgumentException）。
- **建议**：改为 `catch (RuntimeException)` 或显式捕获 `DateTimeParseException`。

### 17.【低】StatsService.export 吞掉异常且不打日志
- **位置**：`backend/src/main/java/com/ca/attendance/stats/StatsService.java:324-326`
- **问题**：`catch (Exception ex) { throw ApiException.badRequest("导出 Excel 失败"); }` —— 根因（POI 限制、OOM 前兆等）完全丢失，无法排障。
- **建议**：catch 中先 `log.error("...", ex)` 再抛。

### 18.【低】统计导出/周视图内存构建无行/列上限
- **位置**：`StatsService.java:244-331`（366 天 × 全员 XSSFWorkbook 全内存）、`:111-123`（weeklyDetail 的 cells 为 O(天数×成员) 的 LinkedHashMap）
- **问题**：协会规模下无虞，但没有像导出服务那样的顶格保护；与第 13 条同主题。
- **建议**：设定导出人数/天数的组合上限。

### 19.【中】手动补录可为同一成员同一天无限叠加记录
- **位置**：`backend/src/main/java/com/ca/attendance/attendance/AttendanceService.java:488-526`
- **问题**：`manualCreate` 不检查该成员当日已有记录/未签退记录（公共签到路径 `:196-209` 严格防重），同一成员同日可补多条，AUTO_APPROVED 状态下时长统计直接重复累计。
- **建议**：补录前检查当日已有记录并要求确认或拒绝。

### 20.【低】手动补录/修改不拒绝未来时间
- **位置**：`AttendanceService.java:450-503`（manualUpdate/manualCreate 均无与当前时间的比较）
- **问题**：checkInTime/checkOutTime 可为任意未来时刻，直接产生未来日期的 AUTO_APPROVED/VALID 记录，污染当期与年度统计。
- **建议**：拒绝晚于当前时刻的时间输入（或至少给出警告）。

---

## 三、后端：业务一致性（6 项）

### 21.【低】manualCreate 用"当前"值班日设置约束历史补录，且与 manualUpdate 口径相反
- **位置**：`AttendanceService.java:510-513`（create 拒绝非当前值班日）vs `:456-480`（update 允许任意日期，靠 recompute 判 INVALID）
- **问题**：为过去的值班日补录时，若该星期后来被关闭，会得到"所选日期不是当前设置的值班日"的困惑报错；而修改反而可以把记录挪到非值班日。两个入口规则不统一。
- **建议**：统一为"记录当时快照 + recompute 判定"，或两处都用历史快照。

### 22.【低】submitPublic(String) 是无人调用的公开 API，且绕过成员令牌绑定
- **位置**：`AttendanceService.java:162-164`
- **问题**：生产代码无调用方（仅测试使用）；它跳过 `selections.bindForSubmission` 的令牌校验直接按学号提交签到。虽未被暴露，但留在 public 表面是隐患。
- **建议**：删除或收窄为包私有。

### 23.【低】trimToNull 静默截断超长输入
- **位置**：`backend/src/main/java/com/ca/attendance/schedule/DutyScheduleService.java:408-414`
- **问题**：500 字备注只存前 500 字，用户无任何感知；与 `UserInputPolicy`"超长即报错"的全局口径不一致（排班标题/位置/备注均受影响）。
- **建议**：超长时报 400 提示，而非静默截断。

### 24.【提示】重置密码默认为学号后六位
- **位置**：`backend/src/main/java/com/ca/attendance/user/UserInputPolicy.java:57-65`
- **问题**：可预测；已有 `must_change_password=1` 强制改密与恢复后吊销全部 token 兜底（`BackupService.java:130`），风险受控。
- **建议**：保持现状可接受；交接文档（已有）继续强调立即改密。

### 25.【提示】统计排序用 double 比较 BigDecimal
- **位置**：`StatsService.java:87`、`:160`（`decimal(...).doubleValue()` 参与 comparator）
- **问题**：存在理论精度边界（同值不同序），实际小时数值域内无影响。
- **建议**：改用 `compareTo`。

### 26.【低】本地登录成败无审计记录
- **位置**：`AuthService.java:65-71`（仅远程登录落 operation_logs）
- **问题**：管理员在本地入口的登录成功/失败完全不记录，事后无法追溯"谁在何时登录过"。
- **建议**：本地登录至少记成功事件（失败可选），复用 `logRemoteAuthentication` 的结构。

---

## 四、前端：交互与健壮性（16 项）

### 27.【中】MembersPage 分页竞态：按钮不禁用、响应无版本控制
- **位置**：`frontend/src/pages/admin/MembersPage.vue:141-160`（仅按页码禁用）、`:326-342`（load 直接覆盖）
- **问题**：快速连点"下一页"并发两个 `load()`，后完成的旧响应覆盖新响应 —— 表格显示第 3 页而 `page=4`；且 `LoadingBlock` 仅在 `busy && !members.length` 时渲染，已有数据时分页零反馈。修复模式库内已有（`useRepairWorkspace.ts:113-150` 的版本号 + AbortController）。
- **建议**：分页按钮加 busy 禁用；load 响应带版本号丢弃过期结果。

### 28.【中】LogsPage 同样的分页竞态
- **位置**：`frontend/src/pages/admin/LogsPage.vue:69-87`
- **问题**：同第 27 条，分页按钮无 busy 禁用。
- **建议**：同上。

### 29.【中】LogsPage 导出失败完全静默
- **位置**：`LogsPage.vue:213-218`
- **问题**：`exportLogs()` 裸 `await get(...)` 未包 `run()` —— 失败既无 toast 也无错误态，按钮无 pending；对比 `RepairsPage.vue:453-461` 的正确姿势。
- **建议**：包进 `run()` 并禁用按钮。

### 30.【中】onMounted 裸 await 无全局错误兜底
- **位置**：`DataPage.vue:404-408,439-465,470-475,509-512`、`ProfilePage.vue:265-279`、`SettingsPage.vue:233-245`；`main.ts` 未注册 `app.config.errorHandler`/`unhandledrejection`
- **问题**：这些请求失败时零用户反馈。最坏是 ProfilePage：首个 `/api/auth/me` 失败则资料表单与个人记录全部静默空白。
- **建议**：注册全局 errorHandler；关键 onMounted 补错误分支。

### 31.【中】考勤手动补录对话框可双击重复提交
- **位置**：`frontend/src/pages/admin/AttendancePage.vue:171-183`（按钮只校验表单不校验 busy）、`:310-329`（save 无重入保护）
- **问题**：双击"保存"发出两次 POST（配合后端第 19 条，直接产生重复记录）；ESC/背景关闭可在请求途中隐藏对话框诱发再次提交。同类对话框（维修/培训/成员）都有 pending/disabled，独此遗漏。
- **建议**：补 `pending` 禁用与重入保护。

### 32.【中】ReviewsPage 逐行审核按钮忙时不禁用，useAsyncTask 非重入
- **位置**：`ReviewsPage.vue:27-29,64-85`；根因之一 `frontend/src/shared/composables/useAsyncTask.ts:8-25`
- **问题**：快速点击对同一条记录发出多次 review；`run()` 无在飞计数，并发 run 的 `finally` 互清 busy/error。
- **建议**：逐行禁用（参照维修页 `usePendingActions`）；useAsyncTask 加计数。

### 33.【低】ModalDialog 无背景滚动锁定，而两个抽屉各自复制了一份
- **位置**：`frontend/src/shared/ui/ModalDialog.vue`（全文件无 overflow 处理）；对照 `RepairDetailDrawer.vue:217-237`、`TrainingSessionDrawer.vue:84-104`
- **问题**：使用最广的模态框打开时背景页面仍可滚动；两个抽屉的滚动锁定代码是复制粘贴。
- **建议**：下沉到 `useDialogFocus.ts` 统一实现。

### 34.【低】ActionMenu 经触发器关闭时泄漏三个全局监听器
- **位置**：`frontend/src/shared/ui/ActionMenu.vue:70-79`
- **问题**：`toggle()` 关闭分支提前 `return`，不移除 `pointerdown`/`resize`/`scroll`（capture）监听；泄漏的 scroll 监听在此后每次页面滚动都执行 `positionMenu`，直到某次外点点击触发 `close()` 或组件卸载。
- **建议**：关闭分支统一走 `close()`。

### 35.【低】工具函数大面积重复，正主版本被忽视
- **位置**：`localDate()` ×5（`RepairsPage.vue:479-481`、`TodayPage.vue:99-101`、`TrainingPage.vue:458-460`、`AttendancePage.vue:377-379`、`KioskPage.vue:77`）；`sameQuery()` ×2（`RepairsPage.vue:485-496`、`TrainingPage.vue:466-477`）；`roleLabel()` ×3（`MembersPage.vue:522-530`、`StatsPage.vue:183-190`、`ProfilePage.vue:337-345`，而 `adminNavigation.ts:152` 已有正主且 AdminLayout 在用）；`bytes()`（`DataPage.vue:529-533`）vs `fileSize()`（`TrainingImportDialog.vue:101-104`，只到 KB，5MB 显示 5120.0KB）
- **问题**：改一处忘三处的维护风险已现实存在（文件大小显示即为实例）。
- **建议**：抽 `shared/format.ts`。

### 36.【低】19 处手写 slice 日期解析与 Intl 混用，隐含时区假设
- **位置**：`LogsPage.vue:233-234`、`ReviewsPage.vue:265`、`ProfilePage.vue:329-330`、`AttendancePage.vue:362-363`、`RepairsPage.vue:471`、`SchedulePage.vue:332-334` 等；对照 `DataPage.vue:527-528`、`AdminLayout.vue:128-136` 的 Intl
- **问题**：slice 假定无时区 ISO 串（后端以服务器本地时间序列化，`JdbcTime.java:82-83`）；桌面场景浏览器与服务器同机同时区所以成立，远程跨时区访问时两种风格会互相矛盾。
- **建议**：统一一个日期工具模块，明确"服务器本地时间"约定。

### 37.【低】导入/恢复上传无客户端文件校验
- **位置**：`MembersPage.vue:475-479`、`TrainingImportDialog.vue:86-88`、`DataPage.vue:489-494`
- **问题**：`accept` 只是选择器提示；超大/错类型文件全量上传后才被后端拒绝（配合后端第 8 条放大代价；恢复上传还是破坏性操作）。
- **建议**：提交前校验扩展名与大小上限。

### 38.【低】破坏性操作失败后确认框仍然关闭
- **位置**：`LogsPage.vue:219-223`、`DataPage.vue:476-488,513-526`
- **问题**：`await run(...)` 失败返回 undefined 未检查就关闭对话框并刷新列表；对清空日志、清除回收站这类操作，失败后仅剩转瞬即逝的 toast。对照 `MembersPage.vue:513-517` 的正确检查。
- **建议**：失败时保持对话框打开。

### 39.【低】年级选项含未来两年，编辑器对不匹配值静默清空
- **位置**：`MembersPage.vue:304-307`（`getFullYear() + 2`）、`MemberEditorDialog.vue:86-93`
- **问题**：下拉里出现尚未入学的年级；存量成员的遗留年级值（如自定义文本）不在 30 个选项内时 select 回显"暂不填写"，一保存就清空原值。
- **建议**：跨度收敛到合理区间；编辑时把存量值动态并入选项。

### 40.【低】恢复成功提示立即被 reload 冲掉，对话框绑定页面级 busy
- **位置**：`DataPage.vue:495-508`
- **问题**：toast"请重新登录"发出后同一 tick 链内 `window.location.reload()`，4.2 秒的提示毫秒级被销毁，只剩登录页 `reason=restored` 兜底；`RestoreBackupDialog` 复用页面共享 `busy`（`:368`），无关在飞任务也会禁用其按钮。
- **建议**：改为 reload 前由登录页显著展示恢复提示；对话框改用局部 pending。

### 41.【低】toast 与 ActionMenu 同用 z-index 120，层级关系未编码
- **位置**：`frontend/src/styles/admin-interactions.css:6-8`（menu 120）与 `styles/components.css:563-565`（toast 120），modal 背景 90（`:481-483`）
- **问题**：菜单开着时弹 toast 会盖住菜单项 4.2 秒（拦截点击）；菜单在模态框打开前弹出则浮在遮罩之上。toast > menu > modal 的次序只是巧合成立。
- **建议**：用 CSS 变量显式分层。

### 42.【低】SettingsPage 保存按钮忙时不禁用、无未保存离开守卫
- **位置**：`frontend/src/pages/admin/SettingsPage.vue:14-16,38-44,178-187`
- **问题**：可双击重复 PUT；切走路由静默丢失改动（维修/培训页装了 `useUnsavedChanges`，此页没有）—— 该页恰恰是"签到时保存快照、事后修改不改历史"的高价值设置。
- **建议**：补 busy 禁用与路由守卫（参照 `RepairsPage.vue:282-284,462-467`）。

---

## 五、桌面端与发布（8 项）

### 43.【低】启动冲突检测探测不到"非 HTTP"的 8080 占用者
- **位置**：`desktop/runtime.cjs:186-200`
- **问题**：`detectStartupConflict` 用 HTTP 健康探测判断 8080；若占用者是纯 TCP 服务，探测失败 → `reachable=false` → 判定无冲突 → spawn 后端 → Java 绑定失败 → 用户看到的是"后端服务意外退出"而非专属提示。`isLoopbackPortInUse(8080)` 这个现成函数没被用在这条路径上。
- **建议**：HTTP 探测失败后补一次原始端口探测区分场景。

### 44.【低】凭据文件写入非原子，0o600 在 Windows 上无效
- **位置**：`desktop/credential-store.cjs:41`
- **问题**：`writeFileSync` 直写无 temp+rename，写入中途崩溃/断电即文件损坏（load 会清除，表现为"记住的登录"静默丢失）；`mode: 0o600` 仅新建时生效且 Windows 上 POSIX mode 语义不同（实际靠安装器 ACL，见第 6 条）。
- **建议**：temp 文件 + rename 原子替换。

### 45.【提示】requestJson 对超 1MB 响应静默截断
- **位置**：`desktop/runtime.cjs:122-138`
- **问题**：只收集前 1MB 分块但不中止请求，超出部分丢弃后 `JSON.parse` 失败得 `parsed=null`，调用方拿到"成功但无 body"而非明确错误。当前健康端点体量极小，实际不可触发。
- **建议**：超限时 `request.destroy(error)`。

### 46.【提示】splash.html 无 CSP
- **位置**：`desktop/splash.html`
- **问题**：`loadFile` 的 file:// 页面不经过后端 `SecurityHeadersFilter` 的 CSP 头。当前纯静态无脚本，风险为零；一行 meta 可封死未来演化。
- **建议**：补 `<meta http-equiv="Content-Security-Policy" content="default-src 'self'; img-src 'self' data:">`。

### 47.【低】.bat 文件为 LF 行尾，.gitattributes 无 EOL 基线
- **位置**：`desktop/portable/启动管理系统.bat`、根 `start.bat`（od 确认 `\n` 行尾）；`.gitattributes` 仅 2 行
- **问题**：cmd.exe 对 LF-only 批处理在 `goto`/标签场景有已知解析怪癖；且不同 `core.autocrlf` 的开发者检出结果不一致。
- **建议**：`.gitattributes` 增加 `*.bat text eol=crlf`、`*.ps1 text eol=crlf`。

### 48.【低】THIRD-PARTY-NOTICES 只覆盖 Temurin 与 Electron
- **位置**：`desktop/THIRD-PARTY-NOTICES.txt`（共 10 行）
- **问题**：发行包内嵌的 Spring Boot fat jar 含 Spring/Tomcat/Jackson/Apache POI/sqlite-jdbc/pdfbox 等几十个 OSS 组件，均未列出（大部分为 Apache-2.0/MIT，义务是保留声明）。
- **建议**：由 `mvn` 依赖树生成 THIRD-PARTY 清单并入包。

### 49.【提示】installer preInit 向 HKLM/HKCU 双注册表视图硬编码 InstallLocation
- **位置**：`desktop/build/installer.nsh:1-8`
- **问题**：在用户选择目录之前就把 `C:\CAAttendance\app` 写入四处注册表；自定义安装目录时该值与实际不符（后续安装步骤会覆盖，但 preInit 的写入时机与意图令人费解）。
- **建议**：确认 electron-builder 依赖此 hack 的必要性并加注释，或移除。

### 50.【提示】冲突检测与后端绑定之间存在 TOCTOU 窗口
- **位置**：`desktop/main.cjs:404-410`
- **问题**：`detectStartupConflict` 通过后到 `startBackend()` 的 Java 完成绑定之间，第三方程序仍可抢注 8080；此时只能靠后端崩溃 + 错误对话框兜底。单实例锁只防本应用二开。
- **建议**：接受现状（本地场景概率极低），在后端启动失败的错误信息里区分"端口被占"。

---

## 六、工程流程（5 项）

### 51.【中】提交到仓库的前端构建产物缺少漂移检查
- **位置**：`backend/src/main/resources/static/`（跟踪入库）+ `.github/workflows/ci.yml` frontend job
- **问题**：`vite build` 会清空重写该目录，CI 构建后没有 `git diff --exit-code backend/src/main/resources/static` 校验 —— 仅改前端的合并可以在带着过期 bundle 的情况下通过 CI，而后端 job 测试的恰是旧资产。发版路径安全（`desktop-package` 会先重建）。
- **建议**：frontend job 末尾加一行 diff 检查。

### 52.【低】scripts/ 下 14 个纯函数 Python 测试未进 CI
- **位置**：`scripts/test_performance_baseline.py`（9 例）、`test_large_dataset_visual.py`（3）、`test_large_dataset_validation.py`（2）
- **问题**：毫秒级即可运行、无 GUI/DB 依赖，却只是发布检查表的手工项，会随时间腐烂。
- **建议**：CI 加一个 `python -m unittest discover scripts` 步骤。

### 53.【低】冒烟/稳定性/性能三层验证全为手动清单
- **位置**：`scripts/full-smoke-test.ps1`（40KB API 冒烟含备份恢复）、`test-desktop-stability.ps1`、`run-performance-baseaseline.ps1` 等
- **问题**：需要 Windows + 真实 Electron，手动化可以理解；但 PR 级自动验证止步于单测/集成测，没有自动化触达运行中的 jar。
- **建议**：至少把 API 冒烟（纯 HTTP 部分）接入 CI 的 job。

### 54.【低】前后端均无 lint / 静态分析
- **位置**：`frontend/package.json`（无 lint 脚本与 ESLint 配置）、`backend/pom.xml`（无 Checkstyle/SpotBugs/ErrorProne）
- **问题**：仅有 `vue-tsc` 类型检查；第 16、34 条这类"错误异常类型/泄漏监听"恰是静态分析的长项。
- **建议**：引入 ESLint（含 vue 插件）与 SpotBugs，CI 跑 warning 阈值。

### 55.【低】plan.md（70KB 开发过程稿）提交在仓库根部且无引用
- **位置**：根目录 `plan.md`
- **问题**：内容为开发规划过程记录，文档索引无一处引用，属于不应随仓库分发的内部稿。
- **建议**：删除或移入私有归档。

---

## 附：修复优先级建议

1. **先修（影响数据正确性/可致系统不可用）**：#9（备份自锁）、#19（补录重复）、#16（异常类型 bug）、#8（导入上限）、#1（本地限流）。
2. **次修（交互缺陷，用户可感知）**：#27–#32 前端竞态与静默失败一组、#37、#38。
3. **低成本高收益的单行级修复**：#12、#17、#22、#29、#34、#46、#47、#51、#52。
4. **其余**按迭代节奏消化即可。

> 统计：中 14（#1、#2、#8、#9、#10、#16、#19、#27、#28、#29、#30、#31、#32、#51）；低 33；提示 8。合计 55 项，达成"≥50 点"目标。

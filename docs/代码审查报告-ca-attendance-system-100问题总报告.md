# ca-attendance-system 代码审查总报告 —— 155 个问题（含解决方案 v3）

- **审查对象**：[AexuRb/ca-attendance-system](https://github.com/AexuRb/ca-attendance-system) `main` 分支（v2.7.0）
- **审查历程**：四轮审查。第一轮（55 项）、第二轮（50 项）各自成文；第三轮新增约 20 项（文档-代码交叉核对 + 一次实际构建并运行后端的 API 级实证）；**第四轮新增 55 项（问题 101–155）**：两个子代理通读全部剩余后端/前端源码，本地跑通全部测试套件（后端 192/192、前端 149/149、桌面 17/17，均通过）与前端构建漂移比对（无漂移），并进行第二轮运行时探针（掩码/时间倒序/文件名/限流等）。
- **诚实说明**：第四轮目标为"再找 100 项"，经系统性挖掘（死端点/死 CSS/死方法审计、测试与脚本深读、文档逐条比对）后确认的**高质量新发现为 55 项**。前三轮已覆盖绝大多数高价值目标，剩余空间若继续强挖将大量产出琐碎风格项，违背审查质量原则，故如实交付 55 项并以"155 项总计"收口。累计实测复现 24 组。
- **本版更新**：每个问题提供 **1–3 种解决方案**（方案 A/B/C），标注取舍，✅ 为推荐。一般规律：A = 最小改动止血，B = 彻底修复，C = 可选加固。
- **更正声明**：① 第二批第 22 项（"路径版 lookup 无长度限制"）经实测为**误报**（路径版同样走 128 字符校验），已撤销；② 第四轮调查中曾撤销"GET /api/repairs 存在不分页死端点"的初步判断（该端点本身即分页实现）。
- **总体评价**：工程质量远超其规模定位（全参数化 SQL、备份链路多重加固、192 个真实 SQLite 测试、诚实的文档）。155 个问题中**无高危**。统计：第 1–100 批 中 23 / 中低 8 / 低 54 / 提示 15；**第 101–155 批 中 13 / 低 30 / 提示 12**。

| 分区 | 条目 |
| --- | --- |
| 一、安全与权限 | 1–12 |
| 二、后端：数据与事务 | 13–35 |
| 三、后端：业务逻辑与一致性 | 36–55 |
| 四、前端：交互与健壮性 | 56–80 |
| 五、桌面端与发布 | 81–88 |
| 六、测试质量 | 89–94 |
| 七、文档与代码失真 | 95–99 |
| 八、工程流程 | 100 |
| **九、第四批：数据正确性与导入导出** | **101–110** |
| **十、第四批：健壮性与配置边界** | **111–116** |
| **十一、第四批：死代码与死端点** | **117–123** |
| **十二、第四批：后端一致性细节** | **124–130** |
| **十三、第四批：前端交互补漏** | **131–142** |
| **十四、第四批：样式与桌面细节** | **143–148** |
| **十五、第四批：测试与流程补漏** | **149–155** |

---

## 一、安全与权限（12 项）

### 1.【中】本地登录接口无速率限制，签到台同源可暴力猜解【实测】
- **位置**：`backend/.../auth/AuthController.java:21-24`、`AuthService.java:41-44`
- **详解**：`RemoteLoginAttemptGuard` 只在远程入口（8081）生效；本地 `/api/auth/login` 完全不限流。公开签到台与登录接口同源，协会电脑前的任何人或本地恶意进程可无限次尝试管理员密码。
- **复现**：循环 `curl -X POST :8080/api/auth/login -d '{"studentNo":"x","password":"guess"}'`，无 429、无锁定。
- **解决方案**：
  - 方案 A（最小改动）：本地登录复用 `RemoteLoginAttemptGuard`，阈值放宽（如 30 次/分/账号）。改动一个 if，防住暴力猜解。
  - 方案 B（账号锁定）：连续失败 N 次锁定账号 5 分钟，登录页给出提示。更强，但本地场景可能被恶意锁定他人账号（与 #2 同款副作用）。
  - 方案 C（无状态延迟）：失败 3 次后每次登录人为延迟 +500ms。无锁定副作用，但对并发暴力不够强。
  - ✅ 推荐 A：与既有远程守卫代码同构，副作用最小。

### 2.【中】远程限流在隧道拓扑下退化为共享键，可被用于锁定拒绝
- **位置**：`backend/.../auth/RemoteLoginAttemptGuard.java:46-48`；`RemoteAccessPolicy.java:46-49`
- **详解**：限流键 = `clientAddress|account`；隧道在本机终结使 8081 的 `RemoteAddr` 恒为 `127.0.0.1`——所有远程用户共享同一 IP 键。攻击者故意输错 5 次即可把真实管理员的账号锁 10 分钟（锁定 DoS）。
- **解决方案**：
  - 方案 A（零代码）：在 README「远程管理」一节明示该限制，并将 `REMOTE_LOGIN_FAILURE` 日志纳入交接巡检。
  - 方案 B（语义修正）：键改为纯账号维度（去掉 IP），锁定冷却延长至 30 分钟 + 本地入口提供解锁命令/页面。语义清晰，但 DoS 面不变、恢复更慢。
  - 方案 C（精确来源）：若所用隧道（樱花 frp）可注入自定义可信头，配置可信代理名单后采用该头作为来源。最精确，依赖隧道能力与配置纪律。
  - ✅ 推荐 A 先行，具备条件后升级 C。

### 3.【中】attendance_records 缺少 (user_id, duty_date) 唯一约束
- **位置**：`V1__initial_schema.sql:79`（仅非唯一索引）；对比 `training_participants`（V1:153）有唯一约束
- **解决方案**：
  - 方案 A（schema 兜底）：V11 迁移加 `CREATE UNIQUE INDEX uniq_att_user_date ON attendance_records(user_id, duty_date)`。**前置条件**：先清理存量重复（配合 #36 的检测查询）；**取舍**：将"一人一天一条"固化为产品语义——若产品允许同日两段值班，应改为 `(user_id, duty_date, check_in_time)`。
  - 方案 B（原子写入）：`INSERT INTO ... SELECT ... WHERE NOT EXISTS (开放记录)` 单语句完成检查+插入。不动 schema，但每个调用点都要改。
  - 方案 C（维持现状）：仅加注释与回归测试固化"依赖单连接串行"（同 #35）。
  - ✅ 推荐 A（语义确认后），与 #17 互补。

### 4.【低】PublicSubmissionRepository.save 不处理重复键
- **位置**：`PublicSubmissionRepository.java:42-57`
- **解决方案**：
  - 方案 A：catch `DuplicateKeyException` → 转走 `findByRequestId` 幂等回读（`UserService.create:107-109` 有现成范式）。
  - 方案 B：改 `INSERT OR IGNORE` + 回读（SQLite 方言，性能略优但可移植性差）。
  - ✅ 推荐 A。

### 5.【中低】维修单创建时"风险/隐私确认"缺省为"已确认"【实测】
- **位置**：`RepairCaseService.java:560-562`
- **复现**：`POST /api/repairs` 不带确认位 → `"riskAcknowledged":true,"privacyAcknowledged":true`。
- **解决方案**：
  - 方案 A（一行级）：新建分支（fallback==null）三处缺省统一为 false。
  - 方案 B（强约束）：`RepairCaseRequest` 的三个确认位加 `@NotNull`，强制显式传入。更严，但旧版本前端会立即 400，需同步发版。
  - 方案 C（双保险）：A + 前端表单强制勾选才能保存。
  - ✅ 推荐 A（前端随后补 C）。

### 6.【中低】批量停用波及全部筛选命中者且缺管理员连续性保护【实测】
- **位置**：`UserService.java:274-276`（空 ids 全量反查）、`:301-305`
- **复现**：`PUT /api/users/bulk-status {"ids":[],"status":"DISABLED"}` → 除操作者外全体被停用。
- **解决方案**：
  - 方案 A（两阶段确认）：空 ids 模式首次调用仅返回影响人数与名单预览，需带 `confirm:true` 二次调用才执行（purge 的 caseNo 确认是库内现成范式）。
  - 方案 B（禁止隐式全量）：空 ids 直接 400，要求前端先按筛选查出 ids 显式提交。最保守，前端需改造。
  - 方案 C（补连续性）：批量路径对 ADMIN 目标调用 `protectAdminContinuity`（单条路径已有，:614-634）。
  - ✅ 推荐 A + C 组合。

### 7.【低】InitialAdminInitializer 可静默提升已有成员为管理员且不留审计
- **位置**：`InitialAdminInitializer.java:37-48`
- **解决方案**：
  - 方案 A：保留恢复通道，但学号过 `UserInputPolicy.newStudentNo` 校验 + 提权后写一条 `INITIALIZE_SYSTEM`/`PROMOTE_ADMIN` 日志。
  - 方案 B：删除该初始化器，恢复管理员改走文档化的离线 SQL 步骤。攻击面最小，但失去自助恢复能力。
  - ✅ 推荐 A。

### 8.【低】登录存在时序性学号枚举
- **位置**：`AuthService.java:47-53`
- **解决方案**：
  - 方案 A：学号不存在时对固定假哈希执行一次 `passwordEncoder.matches(...)` 抹平时序（一行 + 一个常量哈希）。
  - ✅ 推荐 A。

### 9.【低】AgreementDialog iframe sandbox 含 allow-same-origin
- **位置**：`frontend/src/shared/ui/AgreementDialog.vue:53`
- **解决方案**：
  - 方案 A：直接去掉 `allow-same-origin`（打印按钮走 `window.print()` 不需要同源；需回归验证打印）。
  - 方案 B：保留但在 srcdoc 注入 CSP meta + 代码注释警示"勿加脚本类 sandbox 属性"。
  - ✅ 推荐 A（验证打印后）。

### 10.【提示】安装器对数据目录授予 Users 组修改权限
- **位置**：`desktop/build/installer.nsh:10-18`
- **解决方案**：
  - 方案 A（文档化）：README「数据与安全」明示"任何本机账户可读写数据目录"的权限模型。
  - 方案 B（收紧 ACL）：改授 `*S-1-3-4`（当前所有者）或安装用户——多用户切换的协会电脑上后端将无权写库，需评估使用场景。
  - 方案 C（迁移数据目录）：数据迁至 `%LOCALAPPDATA%` 或 `%ProgramData%` 并按方案 B 收紧。改动最大，涉及目录结构文档。
  - ✅ 推荐 A（B/C 仅在有多用户隔离需求时）。

### 11.【低】/api/setup/status 向本地无认证暴露用户数【实测】
- **位置**：`AuthInterceptor.java:41-43` + `SetupService.status()`
- **复现**：`curl :8080/api/setup/status` → `{"initialized":true,"userCount":1}`（无令牌）。
- **解决方案**：
  - 方案 A：已初始化后仅返回 `{"initialized":true}`（userCount 只在未初始化时返回 0）。
  - 方案 B：接口整体要求 LOCAL 且已初始化时返回 404。
  - ✅ 推荐 A（前端 SetupPage 只依赖 initialized 布尔）。

### 12.【提示】/api/health 在远程端口同样可探指纹
- **位置**：`AuthInterceptor.java:38`
- **解决方案**：
  - 方案 A：`HealthController` 内用 `RemoteAccessPolicy.isRemote(request)` 判断，远程端口仅返回 `{"status":"ok"}`（桌面端 `isAttendanceHealth` 指纹校验只打本地口，不受影响——需确认 runtime.cjs 探测走 8080 ✓）。
  - 方案 B：文档标注可接受。
  - ✅ 推荐 A。

---

## 二、后端：数据与事务（23 项）

### 13.【中】双 Jackson 世代并存，根源是 pom 显式声明 Jackson 2【实测】
- **位置**：`backend/pom.xml:40-45` + `config/JacksonConfig.java:10-14`；classpath 同时有 2.21.4（com.fasterxml）与 3.1.4（tools.jackson）
- **详解**：MVC 走 Jackson 3（ISO 字符串）；裸 Jackson 2 bean 用于日志/设置/备份内部路径，行为分裂（见 #14）。
- **解决方案**：
  - 方案 A（止血）：保留双库，但在 `JacksonConfig` 内显式 `disable(WRITE_DATES_AS_TIMESTAMPS)`、`disable(FAIL_ON_UNKNOWN_PROPERTIES)`，对齐 Boot 默认。半小时工作量，立刻消除 #14 的数组问题。
  - 方案 B（根治）：删除 pom 的 Jackson 2 依赖与 `JacksonConfig`，全部服务注入 Boot 管理的 ObjectMapper（Jackson 3，import 换 `tools.jackson.*`）。涉及 6-8 个文件 import 与 API 微调（Jackson 3 的 `writeValueAsString` 签名基本兼容）。
  - 方案 C（保守根治）：同 B 但保留 Jackson 2 仅在备份归档读写（格式已稳定），其余路径统一 Jackson 3。
  - ✅ 推荐 A 立即做，B 在下个版本完成（两步走风险最低）。

### 14.【中】operation_logs 快照中的日期被序列化成数字数组【实测】
- **位置**：`OperationLogService.java:39-40`
- **复现**：建培训后 `GET /api/logs` → `afterData` 中 `"trainingDate":[2026,8,16]`。
- **解决方案**：
  - 方案 A（存量迁移）：修 #13 后新增 V11 迁移，扫描 `operation_logs` 两列 JSON，把 `[y,m,d,...]` 数组按字段类型转回 ISO 字符串（仅处理日期类字段白名单）。
  - 方案 B（读取端容错）：前端 `logDisplay.ts` 解析 diff 值时识别数组形态并格式化为日期显示。治标，但零迁移风险。
  - ✅ 推荐 A（新数据靠 #13 修复，存量靠迁移）；若怕迁移风险则 B 兜底。

### 15.【中】畸形请求体/坏参数返回 500 + ERROR 堆栈【实测】
- **位置**：`GlobalExceptionHandler.java:36-41`
- **复现**：畸形 JSON → 500；`?from=notadate` → 500。
- **解决方案**：
  - 方案 A：advice 补两个 handler：`HttpMessageNotReadableException`、`MethodArgumentTypeMismatchException` → 400「请求格式不正确」。两个方法十行内。
  - 方案 B：连带把所有控制器日期参数统一为日志模块的 String+手动解析模式（`OperationLogQueryService.parseDate` 范式）——彻底但改动面大。
  - ✅ 推荐 A（B 由 #50 统一时再议）。

### 16.【中】备份行数上限构成"自锁"死局
- **位置**：`BackupArchiveWriter.java:128-141`、`BackupArchiveLimits.java:8-9`；依赖方 `AttendanceService.java:541`、`UserService.java:250,318`、`OperationLogQueryService.java:86-91`
- **解决方案**：
  - 方案 A（只增表治理）：给 `operation_logs` 与 `public_attendance_submissions` 加保留期（如 12 个月）+ 定期清理任务；数据中心页显示两表行数与上限余量（>80% 预警）。根治触发源。
  - 方案 B（降级策略）：删除/清空流程在备份失败时降级为「警告并继续」（reason 记录"安全备份失败"）。保可用性但牺牲安全网，需产品确认。
  - 方案 C（上限提升）：`MAX_ROWS_PER_TABLE` 提至 50 万并同步 `MAX_ARCHIVE_BYTES`。只是延后死局。
  - ✅ 推荐 A（C 作为过渡，B 仅在产品明确取舍后）。

### 17.【低】公共签到幂等与防重的并发安全完全依赖连接池=1
- **位置**：`SQLiteDataSourceConfiguration.java:32`
- **解决方案**：
  - 方案 A：配置类加醒目注释 + `@PostConstruct` 断言 `maximumPoolSize == 1`，否则启动失败并提示"并发正确性依赖单连接"。防未来误改。
  - 方案 B：补齐 #3/#4 schema 约束后，允许将池调大（SQLite 写仍串行，读可并发）。
  - ✅ 推荐 A 立即，B 作为长期项。

### 18.【中】备份目录无任何保留策略【实测】
- **位置**：`BackupFileStore.java`（无清理逻辑）
- **复现**：3 个探针操作即产生 4 份备份。
- **解决方案**：
  - 方案 A（自动清理）：`publish` 成功后执行保留策略——手动备份保留最近 50 份且 180 天内，安全备份（系统前缀）单独限额 20 份；清理动作写 operation_logs。
  - 方案 B（手动管理）：数据中心页加"清理旧备份"（按年龄/数量），默认不自动删。保守，依赖运营纪律。
  - ✅ 推荐 A（上限宽松到不影响审计）+ 页面提示。

### 19.【低】LIKE 过滤未转义通配符、枚举未做等值校验【实测】
- **位置**：`AttendanceRepository.java:353-367`、`UserRepository.java:175-186`、`CustomExportService.java:704-766`、`DutyScheduleService.java:75`、`RepairCaseService.java:502-519`、`TrainingService.java:141-155`
- **复现**：`?keyword=%25` 返回全部；`?status=%25` 被接受。
- **解决方案**：
  - 方案 A：枚举/角色/状态参数先白名单校验（非法即 400）再等值拼接；文本参数统一走 `escapeLike()`（转义 `%`/`_`/`\`）+ `ESCAPE '\'`。
  - 方案 B：仅前端收紧选项——治标，API 层仍可绕过。
  - ✅ 推荐 A（抽一个静态工具两个调用点全覆盖）。

### 20.【低】无 LIMIT 的查询与导出（多模块）
- **位置**：`OperationLogQueryService.java:94-118`、`AttendanceRepository.java:171-218`、`RepairCaseService.java:330-347`、`TrainingService.java:375,387`
- **解决方案**：
  - 方案 A：各路径补 `LIMIT 50001`，超限报"结果过多请缩小范围"（CustomExport 范式）。
  - 方案 B：非分页接口全部下线，导出改分页拉取 + SXSSF 流式写。
  - ✅ 推荐 A 先行（一行/处），B 随导出重构。

### 21.【提示】CustomExport 上限校验在全量加载之后
- **位置**：`CustomExportService.java:138-144`
- **解决方案**：
  - 方案 A：SQL 追加 `LIMIT 50001`，取回 50001 即判定超限，避免百万行物化。
  - ✅ 推荐 A。

### 22.【低】统计导出内存工作簿且 autoSizeColumn 全列测量
- **位置**：`StatsService.java:244-331`、`RepairCaseService.java:686-688`
- **解决方案**：
  - 方案 A：autoSizeColumn 改固定列宽（常量已备）；保留 XSSF。
  - 方案 B：全面改 SXSSF 流式 + 定宽，支持任意行数。
  - ✅ 推荐 A（协会规模足够，B 与 #20-B 合并做）。

### 23.【中】值班时段解析失败被静默吞掉，签到判定随之漂移
- **位置**：`DutyPeriodService.java:49-55`
- **解决方案**：
  - 方案 A：解析失败时 ERROR 日志 + 抛出启动期异常（settings 是核心依赖，损坏就该 fail fast），设置页与签到台显示"配置损坏请联系管理员"。
  - 方案 B：回退内置默认时段并在数据中心页红色告警。可用性优先，但静默口径仍在。
  - ✅ 推荐 A（本地单管理场景 fail fast 更安全）。

### 24.【低】每删一条记录都做一次全库备份，且备份文件发布非事务
- **位置**：`AttendanceService.java:541-546`
- **解决方案**：
  - 方案 A（批量合并）：前端删除改为勾选批量，后端一个事务一次备份删多条（reason 汇总）。
  - 方案 B（复用退避）：10 分钟内已有安全备份且期间无写操作时，复用该备份文件名记录于日志，不再重复生成。注意需校验"无写操作"否则破坏先备份语义。
  - 方案 C（改为删除后备份）：先删后备——违背"安全网在破坏前"的设计初衷，不推荐。
  - ✅ 推荐 A（顺带改善批量删除 UX）。

### 25.【低】数据库迁移在 Web 服务器开始接客之后才执行
- **位置**：`DatabaseMigrator.java:17-27`
- **解决方案**：
  - 方案 A：迁移逻辑前移——`SpringApplication` 构造时 `addInitializers`（或 `EnvironmentPostProcessor` 后手动建 DataSource 跑迁移），再起 Web。
  - 方案 B：保持现状，但迁移期间由 Filter 对 `/api/**` 返回 503 + Retry-After（一个 AtomicBoolean 标志）。
  - ✅ 推荐 A（与现有迁移器逻辑解耦小、测试可复用）。

### 26.【中】JdbcTime.localTime 捕获了错误的异常类型（实际 bug）
- **位置**：`JdbcTime.java:63-66`
- **解决方案**：
  - 方案 A：`catch (DateTimeParseException | IllegalArgumentException)` 或直接 `catch (RuntimeException)` 统一转 SQLException。
  - 方案 B：A + 补单测（畸形时间值 → SQLException 而非 500）。
  - ✅ 推荐 B（一行修复 + 防回归）。

### 27.【低】catch-all 吞异常且不打日志（5 处实例）
- **位置**：`StatsService.java:324-326`、`DutyPeriodService.java:80-82`、`TrainingService.java:352-354`、`UserService.java:140-142`、`DutyScheduleImportService.java:148-149`
- **解决方案**：
  - 方案 A：每处 catch 内补 `log.error("...", ex)` 再抛业务异常（5 处各一行）。
  - 方案 B：抽 `ExcelParseExceptions.wrap(...)` 工具统一"记日志+转 badRequest"。
  - ✅ 推荐 A（改动最小），B 随下一次重构。

### 28.【中】成员 Excel 导入无文件大小/行数上限
- **位置**：`UserService.java:115-143,379-460`；`application.yml:10`
- **解决方案**：
  - 方案 A（对齐兄弟模块）：5MB 文件上限 + 3000 行上限，超限整文件拒绝（`DutyScheduleImportService.java:48-49` 范式）；行级校验前置到 BCrypt 之前，失败早退。
  - 方案 B（分批提交）：每 200 行一个子事务分批提交，避免长事务阻塞签到台；失败可断点续导。改动较大。
  - 方案 C（前端预检）：选择文件即校验大小/扩展名，超限直接提示（配合 #68）。
  - ✅ 推荐 A + C（B 在导入量真实变大后再做）。

### 29.【中低】恢复备份中"列合法但值畸形"返回 500
- **位置**：`DatabaseRestoreExecutor.java:130-184`
- **解决方案**：
  - 方案 A：`toTimestamp/toSqlDate/toSqlTime` 各自 catch 解析异常 → `ApiException.badRequest("备份文件包含无效的日期值：" + 原值)`。
  - ✅ 推荐 A。

### 30.【提示】备份下载整包读入内存
- **位置**：`BackupController.java:37-47`
- **解决方案**：
  - 方案 A：返回 `FileSystemResource`（或 `InputStreamResource`）流式响应，设置 Content-Length。
  - ✅ 推荐 A。

### 31.【提示】导出文件名不含操作者标识
- **位置**：各导出 Controller
- **解决方案**：
  - 方案 A：文件名追加导出时间戳已有——可选追加操作者学号后缀便于多人环境区分。
  - ✅ 可选 A（仅多人共用机器场景有价值）。

### 32.【低】培训导入逐行 check-then-upsert
- **位置**：`TrainingService.java:517-523`
- **解决方案**：
  - 方案 A：改 `INSERT ... ON CONFLICT(session_id, student_no_snapshot) DO UPDATE SET ...` 单语句（`nextCaseNo` 已示范该语法）。
  - ✅ 推荐 A（3000 行内即省 3000 次查询）。

### 33.【提示】数据中心页 10 个 COUNT 非同一快照
- **位置**：`MaintenanceSummaryService.summary()`
- **解决方案**：
  - 方案 A：方法加 `@Transactional(readOnly = true)`（WAL 下读快照一致）。
  - ✅ 推荐 A。

### 34.【提示】requestJson 响应超 1MB 静默截断
- **位置**：`desktop/runtime.cjs:122-138`
- **解决方案**：
  - 方案 A：`size` 超 1MB 时 `request.destroy(new Error('响应过大'))` 显式失败。
  - ✅ 推荐 A。

### 35.【低】并发正确性前提（pool=1）无固化手段
- **位置**：`SQLiteDataSourceConfiguration.java`
- **解决方案**：
  - 方案 A：注释 + `@PostConstruct` 断言（同 #17-A，两条合并实现一次即可）。
  - ✅ 推荐 A。

---

## 三、后端：业务逻辑与一致性（20 项）

### 36.【中】手动补录可为同一成员同一天无限叠加记录【实测】
- **位置**：`AttendanceService.java:488-526`
- **复现**：同日两次 `POST /api/attendance/manual`（同学号不同时段）→ 两个 200，时长重复累计。
- **解决方案**：
  - 方案 A（确认制）：manualCreate 先查当日已有记录，存在则 409 返回已有记录摘要；带 `allowDuplicate:true` 显式参数方可创建第二条（用于上下午两段的合法场景）。
  - 方案 B（产品禁令）：直接拒绝同日第二条（若协会规则就是一天一条）。最简单，但堵死合法双段场景。
  - 方案 C（前端辅助）：补录选人后即时显示"该成员当日已有记录"警告条 + 引导去编辑。
  - ✅ 推荐 A + C。

### 37.【低】手动补录/修改不拒绝未来时间【实测】
- **位置**：`AttendanceService.java:450-503`
- **复现**：`checkInTime:"2026-08-24T14:00:00"`（未来一周）→ 200。
- **解决方案**：
  - 方案 A：校验 `checkInTime/checkOutTime` 不得晚于 `now + 5 分钟` 容差，否则 400。
  - 方案 B：允许录入但未来时间记录状态强制 PENDING（进审核队列），不自动通过。
  - ✅ 推荐 A（B 引入审核噪音）。

### 38.【低中】manualUpdate 用"当前"设置改写历史快照，违反文档承诺【实测】
- **位置**：`AttendanceService.java:460-463`；文档 `docs/签到与时长状态矩阵.md:8`
- **复现**：关星期后编辑同记录 → `dutyDay:true→false`。
- **解决方案**：
  - 方案 A（保快照）：manualUpdate 持久化 `before.dutyDay()/before.withinDutyPeriod()`（与 require_* 一致），仅当编辑的是**当日**记录时才允许按当前设置重算。
  - 方案 B（显式重算）：保留重算行为，但编辑非当日记录且设置已变化时，要求请求带 `recomputeSnapshot:true` 并在响应中提示差异。灵活但复杂。
  - ✅ 推荐 A（与文档承诺一致）。

### 39.【低】manualCreate 与 manualUpdate 值班日口径相反
- **位置**：`AttendanceService.java:510-513` vs `:456-480`
- **解决方案**：
  - 方案 A（对齐到 update）：create 不再校验当前值班日，允许补录过去 N 天（如 30 天）内任意日期，靠 recompute 判 INVALID；提示语告知后果。
  - 方案 B（对齐到 create）：update 挪到非值班日也拒绝。会限制合法修正场景。
  - ✅ 推荐 A。

### 40.【验证性记录】令牌/角色远程门禁实现正确
- **位置**：文档需求说明书 124 行
- **说明**：实测远程口令门禁/角色检查符合文档（成员远程登录 403、kiosk 远程 403、本地令牌远程仅限长/管角色）。**无需修复**；建议将本条复现步骤固化为回归测试（`AuthSecurityTest` 已部分覆盖）。

### 41.【低】维修状态 COMPLETED→进行中会清空 completed_at
- **位置**：`RepairCaseService.java:541-545`
- **解决方案**：
  - 方案 A：非 COMPLETED 状态保留 `completed_at` 原值（仅展示层按状态隐藏），需要时写入 remark 说明。
  - ✅ 推荐 A。

### 42.【提示】维修负责人留空时静默指派为当前操作者【实测】
- **位置**：`RepairCaseService.java:586-592`
- **解决方案**：
  - 方案 A：创建时 handler 留空 → 400「请选择负责人」；更新时留空保持原值。
  - 方案 B：前端负责人必填下拉（配合 #61 的选择器修复）。
  - ✅ 推荐 A + B。

### 43.【低】烟测脚本可能把真实管理员密码改为仓库明文
- **位置**：`scripts/full-smoke-test.ps1:299-308,722-741`
- **解决方案**：
  - 方案 A：基线恢复失败时以非零退出码结束 + 控制台醒目横幅「管理员密码可能已改为 SmokeAdmin-2026，请立即修改」。
  - 方案 B：脚本启动时校验 `APP_ROOT`/目标非隔离实例（拒绝指向真实数据目录），强制走 `run-isolated-smoke.ps1`。
  - ✅ 推荐 A + B。

### 44.【低】run-isolated-smoke.ps1 默认弱口令 "123456"
- **位置**：`scripts/run-isolated-smoke.ps1:5-6`
- **解决方案**：
  - 方案 A：去掉默认值，与 full-smoke 一致强制传入（env 或参数）。
  - ✅ 推荐 A。

### 45.【低】密码策略无复杂度要求，与远程管理建议脱节【实测】
- **位置**：`UserInputPolicy.java:48-55`
- **复现**：重置密码 `123456` → 登录成功。
- **解决方案**：
  - 方案 A（分级）：`password(value, role)` 增加角色参数——PRESIDENT/ADMIN 强制至少字母+数字；成员维持长度约束。平衡安全与老人习惯。
  - 方案 B（全面强制）：所有角色统一复杂度。最严，换届导入期体验下降。
  - ✅ 推荐 A。

### 46.【提示】默认密码为学号后六位（有缓解，验证无误）【实测】
- **位置**：`UserInputPolicy.java:57-65`
- **说明**：实测 `"20260005"` 后六位 `"260005"` 登录成功 ✓（审查中曾误判为 bug，经库内哈希 BCrypt 验证确认逻辑无误，记录防再误报）。已有 must_change_password + 恢复吊销令牌兜底。
- **解决方案**：
  - 方案 A：维持现状，交接文档继续强调（已具备）。
  - 方案 B（可选加强）：批量导入时生成随机六位初始密码并随导入报告导出给管理员分发。
  - ✅ 推荐 A。

### 47.【低】本地登录成败无审计
- **位置**：`AuthService.java:65-71`
- **解决方案**：
  - 方案 A：本地登录成功复用 `logRemoteAuthentication` 结构写 `LOGIN_SUCCESS`（失败可选，避免日志膨胀——失败仅连续≥3 次时记录）。
  - ✅ 推荐 A。

### 48.【低中】前后端年级范围不匹配，前端无范围校验【实测】
- **位置**：后端 `UserService.java:653-655`（2007–2057）；前端 `MembersPage.vue:304-307`（1999–2028）+ `userInput.ts`（仅长度）
- **复现**：`{"grade":"2099"}` → 400；前端选"1999级"同样必 400。
- **解决方案**：
  - 方案 A（后端动态化）：范围改为 `[当前年-30, 当前年+2]` 动态计算，错误消息带当前区间。
  - 方案 B（共享常量）：前后端范围抽为共享常量/接口下发（后端暴露 `GET /api/users/grade-range`，前端据此生成下拉与校验）。
  - ✅ 推荐 A + 前端补范围校验（B 在多端场景再做）。

### 49.【提示】成员可自助修改年级（统计分组字段）
- **位置**：`UserService.java:145-156` + `ProfileController.java:15-18`
- **解决方案**：
  - 方案 A：`ProfileRequest` 移除 grade 字段（成员端只读展示），年级变更走管理端。
  - 方案 B：保留自助修改但记 operation_logs（当前未记录成员自助资料变更——顺带补）。
  - ✅ 推荐 A + B 中的日志补全。

### 50.【提示】各模块默认时间窗与日期参数风格不一致
- **位置**：`TrainingService.java:129-130` vs `RepairCaseService.java:176-177` vs 日志模块；参数风格见 #15
- **解决方案**：
  - 方案 A：统一默认窗口为「本学年」（9 月 1 日起）并抽公共工具；日期参数统一 ISO 绑定（错误路径已由 #15-A 兜住）。
  - ✅ 推荐 A。

### 51.【提示】training_participants.attendance_status 是死列
- **位置**：`V1:145` vs `TrainingService.java:298,837`
- **解决方案**：
  - 方案 A：schema 注释标注 deprecated + `database/README.md` 说明，下个大版本迁移移除。
  - 方案 B：实现缺席/请假语义（产品功能，需需求确认）。
  - ✅ 推荐 A。

### 52.【提示】DutyPeriodService.contains() 每次调用解析字符串
- **位置**：`DutyPeriodService.java:89-93`
- **解决方案**：
  - 方案 A：`list()` 返回后按 setting 版本缓存解析结果（app_settings updated_at 变化即失效）。
  - 方案 B：`DutyPeriodItem` 直接携带 `LocalTime` 字段（序列化另出 DTO）。
  - ✅ 推荐 A（微改）。

### 53.【提示】FixedScheduleCalendarService.week() N+1 查询
- **位置**：`FixedScheduleCalendarService.java:30-37`
- **解决方案**：
  - 方案 A：`DutyScheduleService` 增加按周批量查询（一次取 7 天 slots + 一次 IN 批量取 assignees），周视图 14 查询 → 2 查询。
  - ✅ 推荐 A。

### 54.【提示】`submitPublic(String)` 与 `lookup(String)` 是无人调用的公开 API
- **位置**：`AttendanceService.java:162-164,61-71`
- **解决方案**：
  - 方案 A：删除两方法（测试改调 `submitPublic(studentNo, requestId)`/`lookupByInput`）。
  - 方案 B：收窄为包私有并加注释。
  - ✅ 推荐 A。

### 55.【低】shared/application 端口层 6 个文件零消费
- **位置**：`shared/application/{AuditLogPort,CurrentActor,SafetyBackupPort}.java` + 3 个 Adapter
- **解决方案**：
  - 方案 A：删除 6 个文件（grep 确认零引用后直接删）。
  - 方案 B：反向推进——调用方全部改走端口（大重构，无近期收益）。
  - ✅ 推荐 A。

---

## 四、前端：交互与健壮性（25 项）

### 56.【中】MembersPage/LogsPage 分页竞态
- **位置**：`MembersPage.vue:141-160,326-342`、`LogsPage.vue:69-87`
- **解决方案**：
  - 方案 A（就地修）：分页按钮加 `:disabled="busy"`；`load()` 引入请求版本号，过期响应直接丢弃（`useRepairWorkspace.ts:113-150` 现成范式）。
  - 方案 B（彻底）：两页迁移到 `useTrainingWorkspace` 式的专用 composable（AbortController + 版本号 + 空页回退全套）。
  - ✅ 推荐 A 先止血，B 随页面重构。

### 57.【中】LogsPage 导出失败完全静默
- **位置**：`LogsPage.vue:213-218`
- **解决方案**：
  - 方案 A：`exportLogs` 包进 `run()` + 导出按钮 `:disabled="busy"`（`RepairsPage.vue:453-461` 范式）。
  - ✅ 推荐 A。

### 58.【中】onMounted 裸 await 无全局错误兜底
- **位置**：`DataPage/ProfilePage/SettingsPage` 多处；`main.ts`
- **解决方案**：
  - 方案 A：`main.ts` 注册 `app.config.errorHandler` 与 `window.addEventListener('unhandledrejection')`，统一 toast「页面加载失败」。
  - 方案 B：关键 onMounted（ProfilePage 等）补 try/catch 错误态 + 重试按钮。
  - ✅ 推荐 A + B（A 兜全局，B 给关键页）。

### 59.【中】考勤手动补录对话框可双击重复提交
- **位置**：`AttendancePage.vue:171-183,310-329`
- **解决方案**：
  - 方案 A：保存按钮 `:disabled="busy || 表单无效"`；`save()` 加重入保护（`if (busy) return`）。
  - ✅ 推荐 A。

### 60.【中】ReviewsPage 逐行审核忙时不禁用，useAsyncTask 非重入
- **位置**：`ReviewsPage.vue:27-29,64-85`；`useAsyncTask.ts:8-25`
- **解决方案**：
  - 方案 A：`useAsyncTask.run` 改在飞计数（`pendingCount`），busy = count>0；逐行按钮 `disabled=busy||pendingKeys.has(id)`（`usePendingActions` 范式）。
  - ✅ 推荐 A。

### 61.【低】选择器搜索关键字在对话框重开时不重置
- **位置**：`AccountPicker.vue:100`、`ScheduleAssigneePicker.vue:66`
- **解决方案**：
  - 方案 A：组件内 `watch(() => props.open, v => { if (v) { keyword=''; results=[] } })`。
  - ✅ 推荐 A。

### 62.【中】值班时段前端校验缺失两条后端规则
- **位置**：`dutyPeriods.ts:20-37` vs `DutyPeriodService.java:125-134`
- **解决方案**：
  - 方案 A：`validateDutyPeriods` 补「≤12 个」「不得重复」两条（消息与后端一致），并在 `dutyPeriods.test.ts` 加对照用例。
  - ✅ 推荐 A。

### 63.【低】后台顶栏服务状态硬编码为"在线"
- **位置**：`AdminTopbar.vue:42`
- **解决方案**：
  - 方案 A：抽 `useServiceHealth()` composable（kiosk 的轮询逻辑上移共享），顶栏接入。
  - 方案 B：直接移除徽标（宁缺毋滥）。
  - ✅ 推荐 A。

### 64.【低】LoginPage 跳转未校验 next 参数
- **位置**：`LoginPage.vue:133`
- **解决方案**：
  - 方案 A：`const target = String(route.query.next||''); if (/^\/(?!\/)/.test(target)) await router.replace(target) else router.replace(defaultHome)`。
  - ✅ 推荐 A。

### 65.【中】设计令牌/样式规则大面积重复，整块死代码
- **位置**：`admin-details.css:2-6` vs `admin-theme.css:3-9` 等（详见报告正文）
- **解决方案**：
  - 方案 A：合并为单一 `tokens.css`（一层变量），删除 admin-details/admin-shell/admin-theme 中重复定义与死规则、7 份 reduced-motion 只留 base.css 一份；靠视觉回归截图验证。
  - 方案 B：引入 `@layer` 显式分层（tokens < components < themes），保留多文件但顺序受控。
  - ✅ 推荐 A（一次性清理，行数立减数百）。

### 66.【低】ModalDialog 无背景滚动锁定
- **位置**：`ModalDialog.vue`；对照两个 Drawer 的复制实现
- **解决方案**：
  - 方案 A：`useDialogFocus.ts` 统一加 scroll-lock（打开计数 >0 时锁 body），ModalDialog 与两个 Drawer 共用，删除复制代码。
  - ✅ 推荐 A。

### 67.【低】ActionMenu 经触发器关闭时泄漏监听器
- **位置**：`ActionMenu.vue:70-79`
- **解决方案**：
  - 方案 A：`toggle()` 关闭分支改为调用 `close()`（唯一出口移除监听）。
  - ✅ 推荐 A（两行）。

### 68.【低】导入/恢复上传无客户端文件校验
- **位置**：`MembersPage.vue:475-479`、`TrainingImportDialog.vue:86-88`、`DataPage.vue:489-494`
- **解决方案**：
  - 方案 A：选择文件即校验扩展名（.xlsx/.zip）与大小上限（与后端一致 5MB；恢复 zip 放宽至后端上限），不合法直接红字提示。
  - ✅ 推荐 A。

### 69.【低】破坏性操作失败后确认框仍关闭
- **位置**：`LogsPage.vue:219-223`、`DataPage.vue:476-488,513-526`
- **解决方案**：
  - 方案 A：统一 `const ok = await run(...); if (ok === undefined) return;` 保持对话框打开（`MembersPage.vue:513-517` 范式）。
  - ✅ 推荐 A。

### 70.【低】年级编辑器对不匹配值静默清空
- **位置**：`MemberEditorDialog.vue:86-93`
- **解决方案**：
  - 方案 A：编辑态把存量 grade 值动态并入 `options`（`options = union(gradeChoices, [form.grade])`）。
  - ✅ 推荐 A。

### 71.【低】恢复成功提示立即被 reload 冲掉；对话框绑定页面级 busy
- **位置**：`DataPage.vue:495-508,368`
- **解决方案**：
  - 方案 A：恢复成功先 `localStorage.setItem('ca_restore_notice','1')` 再 reload，登录页 onMounted 读取并显著展示「数据已恢复，请重新登录」后清除。
  - 方案 B：简单延迟——toast 显示 3 秒后再 reload（体验一般但零状态传递）。
  - ✅ 推荐 A。

### 72.【低】toast 与 ActionMenu 同用 z-index 120
- **位置**：`admin-interactions.css:6-8`、`components.css:563-565`
- **解决方案**：
  - 方案 A：tokens 定义 `--z-modal:90; --z-menu:110; --z-toast:130` 并替换硬编码。
  - ✅ 推荐 A。

### 73.【低】SettingsPage 保存忙时不禁用、无未保存守卫
- **位置**：`SettingsPage.vue:14-16,38-44,178-187`
- **解决方案**：
  - 方案 A：三组保存按钮加 busy 禁用；页面接入 `useUnsavedChanges` + `onBeforeRouteLeave`（`RepairsPage.vue:282-284,462-467` 范式）。
  - ✅ 推荐 A。

### 74.【低】tsconfig 严格度缺口 + 测试全局类型注入生产检查
- **位置**：`frontend/tsconfig.json:2-16`
- **解决方案**：
  - 方案 A：拆 `tsconfig.app.json`（无 vitest types、开 `noUnusedLocals/Parameters`）与 `tsconfig.test.json`；`noUncheckedIndexedAccess` 先开并修存量索引访问。
  - ✅ 推荐 A（分两步提交）。

### 75.【低】白色 favicon 浅色标签页不可见
- **位置**：`frontend/index.html:6`
- **解决方案**：
  - 方案 A：换 `ca-logo-black.png`。
  - 方案 B：SVG 双色 + `<meta name="theme-color">`/`prefers-color-scheme` 适配。
  - ✅ 推荐 A（B 锦上添花）。

### 76.【低】AdminLayout setup 直接访问 localStorage 未捕获
- **位置**：`AdminLayout.vue:79-81,120,125`
- **解决方案**：
  - 方案 A：抽 `safeStorageGet/Set`（try/catch 回退内存 Map），三处替换。
  - ✅ 推荐 A。

### 77.【低】签到台整场包在一个 aria-live 区域
- **位置**：`KioskAttendanceCourt.vue:3`、`KioskSchedulePanel.vue:5`
- **解决方案**：
  - 方案 A：live 区只包错误/状态文本节点；步骤容器移出 live。
  - ✅ 推荐 A。

### 78.【低】listbox/tablist 语义无键盘契约（三处）
- **位置**：`AccountPicker.vue:36-48`、`RepairStatusTabs.vue:2-11`、`RepairHistoryTable.vue:16-23`
- **解决方案**：
  - 方案 A：补契约——listbox 实现 roving tabindex + 上下键；tablist 实现左右键 + `aria-controls`/`tabpanel`；表格行补 `@keydown.space.prevent`。
  - 方案 B：降级语义——去掉 listbox/tablist 角色改普通 group/导航按钮（屏幕阅读器预期即消失）。
  - ✅ 推荐 tablist 用 A、listbox 二选一（用得少可 B）、表格行 A。

### 79.【提示】focusFirstInvalid 存在共享实现，培训编辑器各自复制
- **位置**：`TrainingSessionEditorDialog.vue:92`、`TrainingParticipantEditorDialog.vue:81`
- **解决方案**：
  - 方案 A：两处改调 `focusFirstInvalid(formRef.value, errors)`。
  - ✅ 推荐 A。

### 80.【低】日志页动作标签缺失 5 个固定类型 + CUSTOM_* 族
- **位置**：`logDisplay.ts`（actionLabels）
- **解决方案**：
  - 方案 A：补 `BULK_REVIEW_ATTENDANCE/ATTENDANCE_STATS/REPAIR_CASES/TRAINING_SESSION/TRAINING_SUMMARY` 五个标签；`actionLabel()` 加 default 分支——未知代码显示「业务操作」，`CUSTOM_` 前缀统一映射「自定义导出」。
  - ✅ 推荐 A。

---

## 五、桌面端与发布（8 项）

### 81.【低】启动冲突检测探测不到"非 HTTP"的 8080 占用者
- **位置**：`desktop/runtime.cjs:186-200`
- **解决方案**：
  - 方案 A：HTTP 探测失败后追加 `isLoopbackPortInUse(8080)` 原始探测，命中则返回 `LOCAL_PORT_OCCUPIED`（友好提示现成）。
  - ✅ 推荐 A。

### 82.【低】凭据文件写入非原子，0o600 在 Windows 无效
- **位置**：`desktop/credential-store.cjs:41`
- **解决方案**：
  - 方案 A：写 `remembered-login.bin.tmp` → `fs.renameSync` 原子替换；Windows 权限交由安装器 ACL（见 #10）并在注释说明。
  - ✅ 推荐 A。

### 83.【低】.bat 文件为 LF 行尾且 .gitattributes 无基线
- **位置**：`启动管理系统.bat`、`start.bat`；`.gitattributes`
- **解决方案**：
  - 方案 A：`.gitattributes` 增加 `*.bat text eol=crlf`、`*.ps1 text eol=crlf`，`git add --renormalize .` 一次到位。
  - ✅ 推荐 A。

### 84.【低】THIRD-PARTY-NOTICES 只覆盖 Temurin 与 Electron
- **位置**：`desktop/THIRD-PARTY-NOTICES.txt`
- **解决方案**：
  - 方案 A：引入 license-maven-plugin（或 `mvn dependency:list` + 手工整理）生成后端 THIRD-PARTY 清单，build-desktop.ps1 打包时并入 NOTICE。
  - ✅ 推荐 A。

### 85.【提示】installer preInit 向注册表四处硬编码 InstallLocation
- **位置**：`desktop/build/installer.nsh:1-8`
- **解决方案**：
  - 方案 A：加注释说明该 hack 的用途（electron-builder 默认目录引导）；升级 electron-builder 版本时验证是否可删。
  - ✅ 推荐 A。

### 86.【提示】detectStartupConflict 与后端绑定之间 TOCTOU 窗口
- **位置**：`desktop/main.cjs:404-410`
- **解决方案**：
  - 方案 A：接受现状（概率极低）；在后端意外退出的错误对话框文案中区分「端口被占用」场景（读 backend.log 中的 BindException）。
  - ✅ 推荐 A。

### 87.【提示】splash.html 无 CSP meta
- **位置**：`desktop/splash.html`
- **解决方案**：
  - 方案 A：补 `<meta http-equiv="Content-Security-Policy" content="default-src 'self'; img-src 'self' data:; style-src 'unsafe-inline'">`。
  - ✅ 推荐 A。

### 88.【低】UI 冒烟脚本使用固定 sleep 与脆弱选择器
- **位置**：`scripts/ui-smoke-test.py:38,42` 等
- **解决方案**：
  - 方案 A：固定 `wait_for_timeout` 全部改为 `expect(...).to_be_visible/to_have_text` 自动等待；关键 CSS 选择器集中为模块级常量便于重构同步。
  - ✅ 推荐 A。

---

## 六、测试质量（6 项）

### 89.【低】8 个后端类零测试引用（含暗藏 bug 的 JdbcTime）
- **位置**：JdbcTime、JacksonConfig、InitialAdminInitializer、DesktopControlService、MaintenanceSummaryService、AccessController、HealthController、CustomExportController
- **解决方案**：
  - 方案 A（优先补三件）：`JdbcTimeTest`（畸形值→SQLException，锁 #26）、序列化契约测试（锁 #13/#14，见 #90）、`InitialAdminInitializerTest`（提权路径写日志）。
  - 方案 B：按盲区清单全部补齐（含 MaintenanceSummary/AccessController 烟测）。
  - ✅ 推荐 A 先行，B 列入迭代。

### 90.【低】全部测试没有任何日期字段的 JSON 形态断言
- **位置**：grep `jsonPath.*(dutyDate|submittedAt|checkInTime)` 零命中
- **解决方案**：
  - 方案 A：新增一条 MockMvc 契约测试——登录后 `GET /api/attendance/...` 断言 `$.checkInTime` 匹配 `^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}`，永久锁住双 Jackson 分裂复发。
  - ✅ 推荐 A。

### 91.【低】AttendanceServiceTest 部长周界测试跨周日午夜会假失败
- **位置**：`AttendanceServiceTest.java:180-232`
- **解决方案**：
  - 方案 A：服务引入可注入 `Clock`（`AttendanceService` 构造器默认 `Clock.systemDefaultZone()`），测试固定时刻构造"本周"数据。
  - ✅ 推荐 A（顺带服务端时间可测化）。

### 92.【低】DutyPeriodServiceTest sortOrder 测试无法检验排序
- **位置**：`DutyPeriodServiceTest.java:38-48`
- **解决方案**：
  - 方案 A：夹具改为 3 个乱序时段，断言 normalize 后按开始时间重排且 sortOrder 为 0,1,2。
  - ✅ 推荐 A。

### 93.【低】router.test.ts 断言配置回声而非守卫行为
- **位置**：`router.test.ts:9-15,29-31`
- **解决方案**：
  - 方案 A：删除配置回声断言，扩展 `resolveRouteAccess` 用例覆盖全部角色×关键路由矩阵（含 mustChangePassword、远程 kiosk 分支）。
  - ✅ 推荐 A。

### 94.【低】前端测试挂载/定时器清理缺陷两处
- **位置**：`MemberEditorDialog.test.ts:69-101`、`useKioskAttendance.test.ts:44-81`
- **解决方案**：
  - 方案 A：挂载用例改 `try { ... } finally { wrapper.unmount() }`；`useKioskAttendance` 用例在断言前先行 unmount 或 `afterEach` 统一清理 + 断言改为按端点过滤计数。
  - ✅ 推荐 A。

---

## 七、文档与代码失真（5 项）

### 95.【中】用户手册的维修编号格式写错【实测】
- **位置**：`系统使用说明.md:301-305`（`WX202607070001`）vs 代码（`JXWXyyyyMMdd-0001`）与需求说明书 484 行（正确）
- **复现**：实测编号 `JXWX20260817-0001`；按手册格式做 purge 确认 → 永远"编号不匹配"。
- **解决方案**：
  - 方案 A：修正手册两处示例为 `JXWX20260817-0001` 格式。
  - 方案 B：purge 确认输入框旁直接显示完整编号供复制（消灭"照手册抄"的可能）。
  - ✅ 推荐 A + B（B 同时是通用防错改进）。

### 96.【中】备份硬上限三份手册零文档
- **位置**：`BackupArchiveLimits.java:4-9` vs `系统使用说明.md:363`、`本地运行说明.md:87`、需求说明书
- **解决方案**：
  - 方案 A：三份文档补「备份上限」小节（10 万行/表、25 万行总量、128MB），附自查 SQL：`SELECT COUNT(*) FROM operation_logs;` 与预警阈值。
  - 方案 B（配合 #16/#18）：数据中心页显示两表余量百分比，>80% 黄色预警——文档只需引用界面说明。
  - ✅ 推荐 A + B。

### 97.【低中】权限矩阵两处失真
- **位置**：`docs/角色权限矩阵.md:100`（措辞与 `GET /api/settings/duty-periods|weekdays` 部长可读矛盾）；矩阵漏维修协议端点行
- **解决方案**：
  - 方案 A（改文档）：矩阵补「维修协议预览/打印（部长可用）」行；措辞改"部长不能进行系统设置**管理**操作（只读时段/星期除外）"。
  - 方案 B（改代码）：两个读取端点提权到 `DUTY_SETTINGS_MANAGE`（与 attendance-policy 读取一致），矩阵措辞即成立——需确认部长工作台是否依赖该只读数据。
  - ✅ 推荐 A（B 需产品确认依赖后）。

### 98.【低中】"设置修改不改历史"承诺被 manualUpdate 破坏
- **位置**：三份文档 vs `AttendanceService.java:460-463`
- **解决方案**：
  - 方案 A：随 #38-A 修代码（保快照），文档不动。
  - 方案 B（短期）：文档措辞先改为"审核开关保留签到时快照；值班日/时段事实字段以最后一次人工修正时的设置重算"，待代码修复后改回。
  - ✅ 推荐 A 为主，修复合入前用 B 过渡。

### 99.【低】CHANGELOG 测试计数口径无固化
- **位置**：`CHANGELOG.md`（"前端 149 / 后端 193 / 脚本 14 / 桌面端 17"）
- **解决方案**：
  - 方案 A：CI 各 job 末尾输出用例数（surefire 汇总 / `vitest run --reporter=json` 统计 / `node --test` 汇总），发布时从 CI 摘抄。
  - ✅ 推荐 A。

---

## 八、工程流程（1 项）

### 100.【中】综合工程流程缺口集
- **解决方案**（按子项，均为独立小改动，可逐条领取）：
  - 构建产物漂移：CI frontend job 末尾 `git diff --exit-code backend/src/main/resources/static`（一行）；
  - Python 测试入 CI：新增 `scripts-test` job 跑 `python -m unittest discover scripts -p "test_*.py"`（毫秒级）；
  - HTTP 冒烟入 CI：`full-smoke-test.ps1` 的纯 API 部分拆出 `api-smoke` 目标在 ubuntu job 对临时 jar 执行；
  - 静态分析：前端 ESLint（vue 插件）+ 后端 SpotBugs，CI 先 warning-only；
  - 依赖更新：`.github/dependabot.yml`（maven/npm 分组、周更）；
  - overrides 防漂移：两份 package.json 的 overrides 处加注释互指 + 发布检查表核对项；
  - 仓库清理：删除 `plan.md`、`common/SourceType.java`、`EffectiveStatus` 双轨收敛（枚举落地使用或删除）。
  - ✅ 推荐顺序：漂移检查 → Python 测试 → dependabot → lint → 其余。

---

## 九、第四批：数据正确性与导入导出（101–110）

### 101.【中】签到台学号掩码形同虚设：8 位学号全部数字可见【实测】
- **位置**：`backend/.../attendance/AttendanceService.java:153-160`（maskStudentNo）
- **详解**：掩码算法为"前 min(4, len-4) 位 + `****` + 后 4 位"。8 位学号 → 前 4 + 后 4 = **8 位全部泄露**（实测 `20260005` → `2026****0005`）；10 位学号泄露 8/10。该字段出现在公开签到台的确认票与同名选择列表中，任何在签到台前的人都可收集成员学号。
- **复现**：`GET /api/public/attendance/lookup?query=<8位学号>` → 响应 `maskedStudentNo` 含全部数字。
- **方案**：A. 改为固定保留后 2 位 + 前缀星号（`******05`），信息量足够区分同名者即可；B. 保留前 2 后 2（`20****05`）；C. 对确认票完全不显示学号，仅显示姓名+年级+头像字。✅ 推荐 A（一行改动，隐私与可用性平衡）。

### 102.【中】恢复旧版本备份会静默清空培训/排班/维修等可选表
- **位置**：`maintenance/DatabaseRestoreExecutor.java:37-41,100-106`；`BackupSchema.java:40-49`（OPTIONAL_RESTORE_TABLES）；`BackupRestoreValidator.java:103-118`（schemaVersion 1–4 均放行）
- **详解**：恢复流程对 CLEAR_TABLE_ORDER 中所有表执行清空，仅当 `app_settings` 缺失时豁免；但校验器允许恢复**旧结构版本**的备份（其 zip 中合法地不含 training/schedule/repair 表文件）。恢复一个 v1 时代的备份会先把现库的培训、排班、维修数据全部 DELETE，再什么都不补——静默的数据清空。`app_settings` 的豁免证明"缺失=保留"才是本意。
- **复现**：手工构造仅含 users/attendance 等必需表、metadata 标 schemaVersion=1 的 zip → `POST /api/maintenance/backups/restore` → 培训/维修数据消失。
- **方案**：A. 所有 OPTIONAL_RESTORE_TABLES 缺失时一律不清空（与 app_settings 同语义）；B. 恢复前返回"该备份将丢失以下数据表"确认清单（两阶段确认）；C. 拒绝恢复低于当前 SCHEMA_VERSION 的备份并提示先升级程序。✅ 推荐 A + B。

### 103.【低】备份 metadata.json 校验薄弱
- **位置**：`BackupRestoreValidator.java:93-105`
- **详解**：schemaVersion 缺失/null 直接放行；`tables` 列表只要求包含必需名，垃圾表名、与实际文件不对应、operator/createdAt 全都不校验。配合 #102，手工编辑的归档可保留一个"看起来可信"的元数据块。
- **方案**：A. schemaVersion 必填且为整数；tables 必须与实际条目集合完全一致；B. metadata 摘要写入文件名/签名防篡改（本地场景过度）。✅ 推荐 A。

### 104.【中】考勤查询 ORDER BY 无 id 决胜列，分页可重复/丢行
- **位置**：`AttendanceRepository.java:179,203,216,325-326`（`ORDER BY duty_date DESC, check_in_time DESC` 无 `ar.id`）
- **详解**：同一秒内的批量/补录记录排序不稳定，`LIMIT/OFFSET` 翻页时同一行可能出现在两页或被跳过。同库的 `CustomExportService.java:274` 与 `TrainingService.java:729` 都正确补了 `id DESC`。
- **方案**：A. 四处 ORDER BY 追加 `, ar.id DESC`；B. 改键集分页（keyset pagination）。✅ 推荐 A（一行 ×4）。

### 105.【中】成员重导入会用空白覆盖已有成员的电话/学院/年级
- **位置**：`user/UserService.java:424-437`（更新路径 `SET name=?, phone=?, major=?, grade=?, qq=COALESCE(?,qq)`）
- **详解**：`UserInputPolicy.phone/college/grade` 对空输入返回 null；导入文件缺列或留空时，已有成员的这三项被置 NULL。`qq` 用 `COALESCE` 保护了，恰恰证明"留空保旧值"是本意——另三列漏了。
- **复现**：对已有成员的库导入一份只含"学号+姓名"两列的表 → 成员资料三项被清空。
- **方案**：A. phone/major/grade 同样改为 `COALESCE(?, 原列)`；B. 前端导入模板强制全列。✅ 推荐 A（与 qq 对齐，一行 SQL）。

### 106.【中】12 位以上数字学号在 Excel 导入中被科学计数法损坏
- **位置**：`user/UserService.java:552-557`、`training/TrainingService.java:990-995`（`DataFormatter.formatCellValue` 无数字处理）
- **详解**：实测反汇编 POI 5.5.1：General 格式对 ≥1E11 的数值套 `0.#####E0`，数字单元格学号 `202301012345` 读出为 `"2.02301012345E11"`，随后被 `\d{6,32}` 正则拒绝——12 位学号（国内高校常见）整批报"学号必须为 6 至 32 位纯数字"，误导性强。
- **方案**：A. 读取时判断 `cell.getCellType()==NUMERIC` → `BigDecimal.toPlainString()`（两处导入共用一个工具）；B. 模板把学号列预设为文本格式。✅ 推荐 A + B。

### 107.【中】培训标题无长度上限且直接拼入下载文件名【实测】
- **位置**：`training/TrainingService.java:1156-1161`（required() 不限长）、`:367,376`（拼文件名）、`:1179-1182`（cleanFilename 不封顶不去控制字符）
- **复现**：实测创建 300 字符标题的培训后 `GET /api/trainings/{id}/export` → Content-Disposition 携带 300+ 字符文件名（Windows 255 上限，下载失败/截断）。
- **方案**：A. 标题上限 100 字符（与其他字段对齐）；B. cleanFilename 统一委托 `CustomExportService.filename()`（去控制字符 + 80 字符封顶）。✅ 推荐 A + B。

### 108.【低】维修完成时间早于受理时间无任何校验【实测】
- **位置**：`repair/RepairCaseService.java:536-544`（repairValues）；前端 `repairForms.ts:13-39` 亦未校验
- **复现**：实测 `POST /api/repairs` 带 `receivedAt:2026-08-17T10:00`、`completedAt:2026-08-16T09:00`、status=COMPLETED → 200 落库，形成负时长维修单。
- **方案**：A. 后端校验 `completedAt > receivedAt` 否则 400；B. 前端表单同步校验并禁提交。✅ 推荐 A + B。

### 109.【低】培训时长解析的"单位剥离"作用于任意位置
- **位置**：`training/TrainingService.java:1014-1024`（`replace("h","")` 等）
- **详解**：`"1H30"` → `"130"` → 记录 130 小时（上限内静默通过）；`"2课时"` → `"2课"` → 报"应填写数字"（误导）。
- **方案**：A. 单位只允许作为后缀（正则 `^(\d+(?:\.\d+)?)(?:\s*(?:h|H|小时|时))?$`）；B. 顺带拒绝 >999.99 之外的异常值提示复核。✅ 推荐 A。

### 110.【低】恢复流程无条件把 attendance_status 改写为 PRESENT
- **位置**：`maintenance/DatabaseRestoreExecutor.java:109-111`
- **详解**：schema 允许 PRESENT/ABSENT/LEAVE 三值，恢复时对 v4 新备份也强制覆写为 PRESENT——备份里若有 ABSENT/LEAVE 值会被静默改写。
- **方案**：A. 删除该覆写（V10 归一已是历史一次性动作）；B. 保留但仅对 schemaVersion<10 的备份执行。✅ 推荐 A。

## 十、第四批：健壮性与配置边界（111–116）

### 111.【低】updateReview 对未知 part 静默路由到签退列
- **位置**：`AttendanceRepository.java:261-277`（`if ("CHECK_IN".equals(part)) ... else { 按签退更新 }`）
- **详解**：仓储层把一切非 CHECK_IN 值当 CHECK_OUT 处理；目前靠上层 normalize 挡住，但这是典型的静默错路由陷阱。
- **方案**：A. else 分支改为显式 `"CHECK_OUT".equals(part)`，否则抛异常。✅ 推荐 A。

### 112.【低】备份列表在并发删除时整体失败
- **位置**：`maintenance/BackupFileStore.java:64-70,86-93,95-105`
- **详解**：`list()` 遍历时 `describe()` 遇到刚被另一管理员删除的文件抛 `读取备份文件失败`，整个列表 400；`delete()` 未与 `list()` 同步（仅 `prepare()` 有 synchronized）。
- **方案**：A. describe 单文件失败时跳过该文件（记 warn）；B. list 也 synchronized。✅ 推荐 A（跳过比锁更正确）。

### 113.【提示】LocalDate 裸绑定依赖驱动 toString 巧合
- **位置**：`training/TrainingService.java:698`（from/to 直接作 SQL 参数）
- **详解**：反汇编 sqlite-jdbc 3.53 证实未知类型走 `toString()`；`LocalDate.toString()` 恰好等于存储的 `yyyy-MM-dd` 所以能跑。同文件其他位置都用 `databaseDate()`。LocalTime 若同样裸绑（`10:00` vs 存储 `10:00:00`）会静默比较失败。
- **方案**：A. 改用 `databaseDate()` 对齐；B. 团队规约：java.time 一律经 JdbcTime 转换。✅ 推荐 A。

### 114.【低】cleanFilename 不去控制字符、不封长度（维修侧）
- **位置**：`repair/RepairCaseService.java:1278-1281`
- **详解**：与 #107 同类（维修侧输入是服务端生成的编号，风险低），但两个同名工具实现不一致本身就是漂移源。
- **方案**：A. 两处 cleanFilename 合并为一个工具（采用 CustomExportService 的实现）。✅ 推荐 A。

### 115.【低】escape() 不转义单引号
- **位置**：`repair/RepairCaseService.java:1283-1292`
- **详解**：覆盖 `& < > "` 但不含 `'`。当前生成的属性全用双引号所以无利用路径，但一次引号风格改动即可破防。
- **方案**：A. 补 `'` → `&#39;`；B. 改用 OWASP encoder 或 HtmlUtils.htmlEscape。✅ 推荐 A。

### 116.【低】app.remote.port == server.port 时整站被当作远程入口
- **位置**：`config/RemoteAccessConfiguration.java:23-25`（相等时静默不加连接器）+ `RemoteAccessPolicy.java:21`（`getLocalPort()==remotePort` 判远程）
- **详解**：直接 `java -jar --app.remote.port=8080`（绕过 start.bat 的校验）时，8080 上所有请求被判为 REMOTE：签到台 403、成员/部长全被拒——系统"活着但瘫痪"且无任何启动告警。
- **方案**：A. 启动时校验两端口相等即 fail fast；B. RemoteAccessPolicy 改用"请求来自附加连接器"的显式标记（如 attribute）。✅ 推荐 A。

## 十一、第四批：死代码与死端点（117–123）

### 117.【低】四个无调用方的 API 端点
- **位置**：`GET /api/attendance/open`（AttendanceController.java:63-68）、`GET /api/public/attendance/lookup/{studentNo}`（PublicAttendanceController.java:19-23）、`GET /api/trainings/me/hours`（TrainingController.java:60）、`GET /api/trainings/import-template`（TrainingController.java:113-115）
- **详解**：grep 全部前端源码（含模板字符串）零调用；前两个连子代理级消费者都没有。`/api/attendance/open` 的"未签退记录"功能完整实现（服务+权限）却无人使用。
- **方案**：A. 删除端点及对应服务方法（openRecords、lookup(String)、myHours、exportImportTemplate 通用版）；B. 保留但标注 @Deprecated 并列入文档。✅ 推荐 A（减小攻击面）。

### 118.【提示】Role 枚举的五个助手谓词是死代码且与权限策略存在口径分歧
- **位置**：`common/Role.java:9-28`（atLeastManager/canManageUsers/canExport/canSetDutyWeekdays/canViewOperationLogs 零调用）
- **详解**：授权全部走 `RolePermissionPolicy`；死副本中 `canExport()` 含 MINISTER——与 CUSTOM_EXPORT/REPAIR_EXPORT（仅会长以上）不一致，留着必然误导。
- **方案**：A. 删除五个方法。✅ 推荐 A。

### 119.【低】components.css 含 26 个死类
- **位置**：`frontend/src/styles/components.css`：`.add-slot .compact-metrics .detail-heading .detail-panel .effective-date .effective-day .inline-date .lane-main .list-heading .modal-lg .modal-sm .modal-xl .person-chips .record-date .record-list .record-list-item .schedule-lanes .schedule-panel-wide .selected-slot .settlement-steps .settlement-summary .split-workspace .subnav-row .table-select .week-board .week-day-slots`
- **详解**：逐一 grep `.vue/.ts` 零引用（自动审计 + 抽样人工复核）；多为旧版数据中心/排班页遗留。
- **方案**：A. 删除死类；B. 建立 CI 死选择器检查（如 unused-css 工具）。✅ 推荐 A。

### 120.【低】kiosk-focus-* 旧皮肤在两个样式文件中双份死代码（约 29 类）
- **位置**：`kiosk.css:86-737` 与 `kiosk-theme.css`（`.kiosk-focus-schedule/-shift/-timeline/-week/-workspace/-admin/-collapse...`）
- **详解**：签到台已改用 `kiosk-signal-*` 体系；旧类在两个文件各留一份，部分同名类定义互相覆盖，`--shift-index` 变量的原始消费者已死。
- **方案**：A. 删除两文件中全部 kiosk-focus-* 死块（约 350 行）。✅ 推荐 A（与 #119 一起一次清理）。

### 121.【低】layouts.css 含 44 个死类（旧版整体布局系统）
- **位置**：`frontend/src/styles/layouts.css`：`.admin-nav .admin-sidebar .kiosk-page .kiosk-login .kiosk-success .kiosk-timeline .sidebar-brand .sidebar-user .week-strip .timeline-* .slot-* ...`
- **详解**：旧版 admin/kiosk 双布局的完整遗骸（grep 零引用）；现布局在 admin-shell.css/kiosk-theme.css。
- **方案**：A. 整文件评估后删除或仅保留仍被引用的类；B. 与 #119/#120 合并为一次"样式清理"提交。✅ 推荐 B。

### 122.【提示】自定义导出 preview 全量物化才取 12 行；Boolean 导出分支为死代码
- **位置**：`export/CustomExportService.java:98-117,138-144,433-443`
- **详解**：预览拉满 5 万行才 limit(12)，且大表会直接触发"超 5 万行"400——仅为预览却付出全量代价；`setCellValue` 的 Boolean→"是/否" 分支永不触发（sqlite-jdbc 不返回 Boolean，未映射的 0/1 会裸数字导出）。
- **方案**：A. 预览走 `LIMIT PREVIEW_ROWS+1` + COUNT；B. Boolean 分支改为处理 Number 0/1。✅ 推荐 A + B。

### 123.【提示】BackupArchiveReader 在非预期 RuntimeException 时泄漏临时目录
- **位置**：`maintenance/BackupArchiveReader.java:29-50`
- **详解**：仅 ApiException/IOException 触发清理；其他 RuntimeException（如多部件流中途异常）导致 java.io.tmpdir 残留解压目录。
- **方案**：A. finally 中清理（ExtractedBackupArchive 已是 Closeable，包一层 try-with-resources）。✅ 推荐 A。

## 十二、第四批：后端一致性细节（124–130）

### 124.【提示】batchUpdateEffective 是唯一不写 updated_by 的写操作
- **位置**：`AttendanceRepository.java:141-155`
- **详解**：批量审核重算有效状态时只更新 updated_at；其余写路径都记录操作者，审计链在此断开。
- **方案**：A. 方法加 reviewerId 参数并写入。✅ 推荐 A。

### 125.【低】用户分页超界返回空页，与考勤分页的"回退末页"行为不一致
- **位置**：`UserRepository.java:121-136`（page=99 时 items 空）vs `AttendanceRepository.java:192-193`（Math.min(page,lastPage) 回退）
- **复现**：实测 `GET /api/users/page?page=99&pageSize=5` → `{"items":[],"page":99}`；考勤同等请求回退到末页。
- **方案**：A. UserRepository 补 lastPage 回退，两处一致。✅ 推荐 A。

### 126.【提示】分页默认值 20 与 30 混用
- **位置**：`RepairCaseController.java:39`、`TrainingController.java:86`（默认 30）vs 其余控制器（默认 20）
- **方案**：A. 统一为常量（如 20），控制器引用同一常量。✅ 推荐 A。

### 127.【提示】39 处 now() 散落，无 Clock 注入
- **位置**：后端全库 `LocalDateTime.now()/LocalDate.now()/LocalTime.now()` 共 39 处（grep 计数）
- **详解**：与 #91（测试午夜竞态）同根：时间不可测使周界/过期逻辑只能靠真实时钟撞。
- **方案**：A. 核心服务（AttendanceService/AuthInterceptor/TokenService）注入 `Clock`；B. 全量注入（工作量大）。✅ 推荐 A。

### 128.【提示】@Transactional(readOnly=true) 仅 2/35
- **位置**：grep 全库：35 个 @Transactional 中仅 2 个标注只读
- **详解**：只读路径开写事务，在单连接 SQLite 上无功能问题，但语义与快照一致性（见 #33）都受损。
- **方案**：A. 查询型方法补 readOnly（巡检清单式提交）。✅ 推荐 A。

### 129.【提示】TokenService 过期令牌仅在再次访问时清除
- **位置**：`auth/TokenService.java:29-38`
- **详解**：过期条目要等同一 token 再被 require() 才移除；长期运行+频繁登录下 map 缓慢增长（12h 过期+重启清空，量级无害）。
- **方案**：A. 定时清扫（如每小时 removeIf(expired)）；B. 改用带过期的缓存结构。✅ 推荐 A（几行）。

### 130.【提示】日志查询 actionType 不校验，任意值静默空结果
- **位置**：`log/OperationLogQueryService.java:170-173`
- **复现**：实测 `GET /api/logs?actionType=NOT_EXIST` → 200 空列表，无"未知动作类型"提示。
- **方案**：A. 与前端 actionLabels（#80）共享合法类型集校验。✅ 推荐 A（顺带修 #80）。

## 十三、第四批：前端交互补漏（131–142）

### 131.【中】TodayPage 首次加载失败渲染出"一切正常"的假仪表盘
- **位置**：`pages/admin/TodayPage.vue:7-22,78-97`（`void load().catch(() => undefined)`，无错误态）
- **详解**：后端不可达时页面显示"今天没有待处理异常/今日暂无排班"等真实感十足的空态，无错误无重试——值班者据此跳过审核。签到台对同一端点有正确的 error/online 处理可参照。
- **方案**：A. load 失败设置 error 态 + 重试按钮（复用 kiosk 模式）；B. 仅全局 errorHandler 兜底。✅ 推荐 A。

### 132.【中】StatsPage 重叠加载竞态：旧响应覆盖新预设
- **位置**：`pages/admin/StatsPage.vue:143-174`（无版本号/Abort）
- **详解**：快速切换"本周→本月"时后完成者胜出，分段控件显示本月而表格是周数据——统计页静默出错。
- **方案**：A. 请求版本号丢弃过期响应（useRepairWorkspace 范式）；B. busy 期间禁用预设切换。✅ 推荐 A。

### 133.【中】StatsPage 导出/统计按钮忙时不禁用
- **位置**：`StatsPage.vue:6-11,26,175-180`
- **详解**：双击导出发两个下载；与 #132 复合。
- **方案**：A. `:disabled="busy"`。✅ 推荐 A。

### 134.【中】10 个 CSS 自定义属性被引用但从未定义（可见 UI 降级）
- **位置**：`settings.css:103`（`var(--muted)` 开关旋钮透明）、`components.css:1353-1405`（`--blue-200/--blue-50/--danger` 账号选择器无边框/悬停/红色）、`auth.css/training-workspace.css/admin-details.css`（`--ink-400/700/900/850`、`--primary`、`--blue-800`）
- **详解**：全量比对 `var(--x)` 引用与 `--x:` 定义得出；无效声明静默退化为透明/继承色——设置页开关、选择器选中态、停用账号红色等具体视觉破损。
- **方案**：A. tokens.css 补齐缺失变量或改引用现有近义 token；B. CI 加"未定义变量"检查（stylelint）。✅ 推荐 A + B。

### 135.【低】ReviewsPage 已驳回的签到显示成功绿色
- **位置**：`pages/admin/ReviewsPage.vue:47-61`
- **详解**：签到徽章 tone 为 `PENDING?warning:success`——REJECTED 也是绿；签退列则一律 neutral，两列口径还互相矛盾。审核者易把驳回当有效。
- **方案**：A. 增加 REJECTED→danger 映射，两列统一状态→tone 函数。✅ 推荐 A。

### 136.【低】BulkMemberStatusDialog 请求期间可取消且无 pending 反馈
- **位置**：`features/members/BulkMemberStatusDialog.vue:28-41`（取消按钮无 busy 守卫；对照 ConfirmDialog.vue:12-15,46-47 的正确做法）
- **方案**：A. 对齐 ConfirmDialog：pending 时双按钮禁用 + 确认钮转圈。✅ 推荐 A。

### 137.【低】TodayPage 每 60 秒及每次聚焦全量拉取当日考勤
- **位置**：`pages/admin/TodayPage.vue:67-71,87-93`（`GET /api/attendance?from=to=today` 无分页，仅展示 slice(0,8)）
- **详解**：几百成员时每分钟一次全量列表负载；常开页面累积可观。
- **方案**：A. 改用 `/api/attendance/page?pageSize=8` + dashboard 计数接口；B. 轮询间隔自适应（页面隐藏时暂停）。✅ 推荐 A + B。

### 138.【低】StatsPage 自定义区间不校验即提交
- **位置**：`StatsPage.vue:24-27,171-174`
- **详解**：清空日期直接发空 from/to → 后端 400 只剩兜底 toast；无 from≤to 检查。
- **方案**：A. 提交前校验非空 + from≤to，行内错误提示。✅ 推荐 A。

### 139.【低】修改密码成功后落地裸登录页无任何确认反馈
- **位置**：`pages/auth/PasswordPage.vue:93-97`（replace 到 login 无 query）
- **详解**：页面文案承诺"完成后会重新登录"，实际无 reason 参数，LoginPage 只认 restored/expired——用户以为改密失败，可能反复输旧密码。
- **方案**：A. `router.replace({name:'login',query:{reason:'password-changed'}})` 并在 LoginPage 增加对应文案。✅ 推荐 A。

### 140.【低】Toast 无堆叠上限、不可暂停、整体可点击关闭
- **位置**：`shared/composables/useToast.ts:13-17`、`shared/ui/ToastHost.vue:4-19`
- **详解**：无 max 限制（未来按条目通知的场景会顶出屏幕）；手动 dismiss 不清定时器；长错误 4.2s 即逝且无悬停暂停；整条 toast 是单一 button，点文本即关（X 图标形同虚设）。
- **方案**：A. 上限 3 条 + 悬停暂停 + 关闭按钮独立；B. 错误级 toast 时长延长/需手动关。✅ 推荐 A。

### 141.【低】签到台周条在加载失败时显示"本周无排班"式中性占位
- **位置**：`pages/kiosk/KioskWeekStrip.vue:13-18`
- **详解**：`weekSchedule` 为空（含**失败**）时渲染"—"占位；错误只在上方面板体现，底部周条看起来像"真没排班"。
- **方案**：A. 区分 loading/error/empty 三态，error 时显示"排班暂时不可用"。✅ 推荐 A。

### 142.【提示】成员筛选"清除全部"不清关键字，且关键字不显示为筛选片
- **位置**：`features/members/MemberFilters.vue:76-83,87-103,169-175`
- **方案**：A. clearAll 一并清 keyword 并纳入筛选片展示。✅ 推荐 A。

## 十四、第四批：样式与桌面细节（143–148）

### 143.【低】大量硬编码色值绕过设计令牌
- **位置**：`today.css:21-33,56,93,183,198-202,252-264`、`kiosk.css`（19 处）、`auth.css:14,276-278,327-344`、`schedule.css:4-53`
- **详解**：均为现有 token（--surface/--line*/--blue-*）的近似值；调主题时这些表面不跟随。
- **方案**：A. 逐个替换为 token；B. stylelint 规则禁止裸 hex。✅ 推荐 A + B。

### 144.【提示】同一"成功绿"语义存在三种色值
- **位置**：`auth.css:129`（#35b492）、`kiosk.css:76,458,498`（#36a283）vs token `--green-700`
- **方案**：A. 统一走 --green-* 令牌。✅ 推荐 A。

### 145.【提示】KioskHeader 时间元素的 datetime 属性是 UTC 而可见时钟是本地时间
- **位置**：`pages/kiosk/KioskHeader.vue:48`（`toISOString()` 带 Z 后缀）
- **详解**：机器可读时间与渲染文本差 8 小时（CST），语义不一致。
- **方案**：A. 用本地时间串或带偏移的 ISO。✅ 推荐 A。

### 146.【低】桌面窗口未锁定缩放，Ctrl+滚轮即可破坏签到台布局
- **位置**：`desktop/main.cjs`（无 `setVisualZoomLevelLimits`/zoomFactor 守卫，grep 证实）
- **复现**：打包版窗口内 Ctrl+滚轮放大——公开签到台字体/布局错乱且无重置入口（重启才恢复）。
- **方案**：A. `webContents.setVisualZoomLevelLimits(1,1)` + `zoomFactor` 钉 1；B. 仅对签到台路由锁定。✅ 推荐 A。

### 147.【提示】窗口尺寸/位置不记忆
- **位置**：`desktop/main.cjs:209-229`（每次启动固定 1440×900）
- **方案**：A. 保存/恢复 bounds（electron-store 或简单 JSON）；B. 接受现状。✅ 推荐 A（体验小改）。

### 148.【低】性能工具种子用户全部复用管理员密码哈希且直写数据库文件
- **位置**：`scripts/performance_baseline.py:566-615`（seed_database 直连 sqlite 写入；取 admin password_hash 给全部种子用户）
- **详解**：有"仅限单用户库"护栏（≠1 即拒），但种子库中**任何成员都能用管理员密码登录**——若该库被误用于演示/恢复，权限即泄露；README 未警示"种子库不可复用"。
- **方案**：A. 种子用户用独立固定哈希（如 "Perf-Seed-2026"）并在种子 JSON 里写明；B. 文档显著警告种子库一次性使用。✅ 推荐 A + B。

## 十五、第四批：测试与流程补漏（149–155）

### 149.【低】CHANGELOG 后端测试计数 193 与实际 192 漂移【实测】
- **位置**：`CHANGELOG.md`（"后端 193"）；本地全量 `mvn test` 实测 `Tests run: 192`
- **详解**：前端 149/脚本 14/桌面 17 均与实测一致，唯后端差 1——手工计数的必然漂移。
- **方案**：A. CI 各 job 末尾输出用例数（surefire/vitest 汇总），发布时从 CI 摘抄（同 #99）。✅ 推荐 A。

### 150.【低】vitest 全局 jsdom 环境拖慢纯逻辑测试
- **位置**：`frontend/vite.config.js`（`test.environment: 'jsdom'` 全局生效）
- **详解**：实测 149 用例 24.2s，其中 environment 累计 413s（jsdom 每文件冷启）；绝大多数 .test.ts 是纯逻辑，不需要 DOM。
- **方案**：A. 默认 environment 改 'node'，DOM 用例文件头 `// @vitest-environment jsdom`；B. environmentMatchGlobs 按路径分流。✅ 推荐 A（预计提速 3-5 倍）。

### 151.【中低】"UI 核心工作流测试"是纯 Mock 渲染测试，从不触达后端
- **位置**：`scripts/ui-core-workflows-test.py:248-340`（page.route + fulfill_json 全拦截）
- **详解**：所有 API 被路由拦截返回硬编码夹具——它验证的是"前端对着夹具能渲染"，后端契约任何回归都测不出；而发布检查表把它当作 UI 集成验证步骤。与 ui-role-regression.py（真实后端）形成反差。
- **方案**：A. 明确重命名/标注为"前端渲染冒烟"，发布表另列真实后端 UI 步骤（ui-role-regression 已有）；B. 增加少量直连后端的用例。✅ 推荐 A。

### 152.【提示】UI 夹具字段与现行类型漂移
- **位置**：`scripts/ui-core-workflows-test.py:30-45`（`"validHours": 2`、`"source": "PUBLIC"`）
- **详解**：前端 `AttendanceRecordItem`（attendanceRecords.ts）无这两个字段；source 对应的 SourceType 在后端已死（#100）——夹具仍在喂养已删除的字段。
- **方案**：A. 清理夹具对齐现行类型（随 #151 一起）。✅ 推荐 A。

### 153.【提示】两个并发测试使用 Thread.sleep(200) 做时序断言
- **位置**：`AttendanceBulkReviewIntegrationTest.java:155`、`UserDeletionHistoryIntegrationTest.java:259`
- **详解**：`sleep(200)` 后断言"任务未完成"——依赖调度时序的负断言，慢机器上语义仍成立但本质是竞态测试的脆弱写法。
- **方案**：A. 改轮询条件（awaitility 风格：500ms 内持续未完成即通过）。✅ 推荐 A。

### 154.【提示】10 个测试类各自新建 Spring 上下文
- **位置**：`@DynamicPropertySource`（临时目录属性）×10、`@DirtiesContext` ×10（grep 计数）
- **详解**：每类独立上下文使缓存失效（测试日志中 Spring banner 反复打印）；套件 17.9s 尚可，但随用例增长线性变慢。
- **方案**：A. 统一一个上下文定制器 + 按类隔离数据库名；B. 接受现状。✅ 推荐 A（测试基建一次性投入）。

### 155.【低】性能基线脚本无回归门禁
- **位置**：`scripts/run-performance-baseline.ps1`（全文无 compare/threshold/fail 逻辑，仅产出 JSON）
- **详解**：`docs/大数据量与性能测试记录.md` 的数字靠人工比对历史 JSON；脚本本身永不因性能退化而失败。另：该脚本默认管理员口令也是 "123456"（与 #44 同款，两个脚本）。
- **方案**：A. 增加 `--baseline <json>` 参数，P95 超阈值即非零退出；B. CI 定期跑并把 JSON 存为 artifact 供 diff。✅ 推荐 A。

## 附 A：实测复现速查（24 组）

后端以 `java -jar attendance-backend.jar --server.port=18080 --app.remote.port=18081` 起服后：

| # | 对应问题 | 命令/步骤 | 预期缺陷表现 |
| --- | --- | --- | --- |
| 1 | #11 | `curl :18080/api/setup/status` | 无令牌返回 userCount |
| 2 | #12 | `curl :18081/api/health` | 远程端口指纹可读 |
| 3 | #1 | 循环错误口令打 :18080/api/auth/login | 无限流 |
| 4 | #5 | `POST /api/repairs` 缺确认位 | risk/privacy=true |
| 5 | #6 | `PUT /api/users/bulk-status {"ids":[],"status":"DISABLED"}` | 全体非自身被停用 |
| 6 | #15 | 畸形 JSON / `?from=notadate` | 500+ERROR 堆栈 |
| 7 | #14 | 建培训后 `GET /api/logs` | afterData 日期为数组 |
| 8 | #19 | `GET /api/users?keyword=%25` | 返回全部 |
| 9 | #36 | 同日两次 manual | 两条 200 |
| 10 | #37 | manual 带未来日期 | 200 |
| 11 | #38 | 关闭星期后 manual 同记录 | dutyDay 翻转 |
| 12 | #45 | 重置密码为 123456 后登录 | 成功 |
| 13 | #46 | 新建用户用学号后六位登录 | 成功（正向验证） |
| 14 | #48 | `PUT /api/me/profile {"grade":"2099"}` | 400 与前端范围不符 |
| 15 | #18 | 连续三个管理操作 | 产生 4 份备份文件 |
| 16 | #95 | 创建维修单看编号 | JXWX 前缀（手册写 WX） |
| 17 | #101 | `GET /api/public/attendance/lookup?query=<8位学号>` | maskedStudentNo 含全部 8 位数字 |
| 18 | #107 | 300 字符标题培训后 `GET /api/trainings/{id}/export` 看 Content-Disposition | 300+ 字符文件名 |
| 19 | #108 | `POST /api/repairs` 带 completedAt 早于 receivedAt、status=COMPLETED | 200 落库负时长 |
| 20 | #125 | `GET /api/users/page?page=99&pageSize=5` | items 空且 page=99（考勤同参回退末页） |
| 21 | #130 | `GET /api/logs?actionType=NOT_EXIST` | 200 静默空结果 |
| 22 | —（正向） | 8081 连续 6 次错误口令 | 第 6 次 429（远程限流生效） |
| 23 | —（正向） | 前端 `npm run build` 后 diff `backend/.../static` | 无漂移（CI 防漂移建议仍成立） |
| 24 | #149 | 本地 `mvn test` 读 surefire 汇总 | 192 ≠ CHANGELOG 的 193 |

## 附 B：修复优先级 Top 10（含方案引用）

1. **#13+#14**（Jackson 双世代）→ 先 13-A 止血，再 13-B 根治 + 14-A 存量迁移
2. **#16+#18+#96**（备份死锁三连）→ 16-A 只增表治理 + 18-A 自动保留 + 96-A 文档
3. **#15**（500→400）→ 15-A 两个 handler
4. **#36+#37**（补录重复与未来时间）→ 36-A/C + 37-A
5. **#5**（确认位缺省）→ 5-A 一行
6. **#6**（批量停用）→ 6-A+C 两阶段确认与连续性
7. **#1+#2**（限流双缺口）→ 1-A + 2-A/C
8. **#28**（成员导入上限）→ 28-A+C 对齐兄弟模块
9. **#26**（JdbcTime 异常类型）→ 26-B 一行+单测
10. **#95/#97/#98**（文档失真）→ 95-A+B、97-A、98-A

## 附 B-2：第四批（101–155）修复优先级补充

1. **#101**（掩码泄露）→ 101-A 一行改动，隐私收益最高
2. **#102+#103**（恢复清空可选表）→ 102-A+B、103-A，数据安全级
3. **#105+#106**（导入覆盖与科学计数法）→ 成员导入数据完整性的两个静默破坏源
4. **#104**（ORDER BY 决胜列）→ 四处一行，分页正确性
5. **#134**（未定义 CSS 变量）→ 补 token，修复可见视觉破损
6. **#131+#132+#133**（Today/Stats 页三连）→ 错误态 + 竞态 + 禁用一次提交修复
7. **#117–#121**（死端点与死 CSS 约 100 类）→ 一次清理提交，减攻击面与维护噪音
8. **#146**（缩放锁定）→ 两行，防签到台布局被破坏
9. **#151**（纯 Mock UI 测试定位失真）→ 重命名与发布表更正，防虚假信心
10. **#107/#108/#109**（导入导出输入校验三连）→ 一并提交

> 总统计：**155 个问题**（第 1–100 批：中 23 / 中低 8 / 低 54 / 提示 15；第 101–155 批：中 13 / 低 30 / 提示 12）；**24 组运行时实测复现**；每项提供 1–3 个带取舍说明的解决方案，✅ 为推荐方案。

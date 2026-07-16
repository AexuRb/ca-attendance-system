# 学期化重构规格

版本：3.0 草案  
状态：已确认，进入实施  
适用范围：本地 SQLite 后端、Electron 桌面端、Vue 管理端与签到台

## 1. 目标与边界

本次只新增两类业务能力：

1. 学期结算与数据封存。
2. 排班例外与直接调班。

同时完成两项工程改造：

- 后端保持单进程、单 SQLite 的模块化单体，但拆分控制器、应用服务、领域规则、数据库访问和文件适配器。
- 旧前端源码与历史样式全部删除，使用 Vue 3、TypeScript 和 Vue Router 从零重建全部页面。

不新增微服务、在线依赖、部长调班申请、培训负责人、可配置角色系统、SQLCipher、安装包或 GitHub 发布。

## 2. 不可变业务规则

### 2.1 学期状态

状态流转：`DRAFT -> ACTIVE -> SETTLING -> SEALED`。

- 系统同时最多存在一个 `ACTIVE` 学期。
- 学期日期范围不得重叠，名称与编码唯一。
- 没有活动学期时，公共签到查询可用，但提交签到或签退必须被拒绝。
- `ACTIVE`：按现有权限正常读写。
- `SETTLING`：公共签到停止；成员和部长只读；会长、管理员可处理审核、未签退、培训、排班与维修遗留问题。
- `SEALED`：该学期业务数据只读，不能删除学期或修改其业务记录。
- 会长可发起结算、查看预检与结算预览；只有管理员可以封存和重新打开。
- 管理员重新打开必须填写原因，系统先自动备份；旧结算版本永久保留，重新封存产生新版本。

### 2.2 封存预检

以下任一条件成立时禁止封存：

- 存在待审核签到或签退。
- 存在未签退记录。
- 存在状态为 `REPAIRING` 的维修事务。
- 存在不属于任何学期、日期越界或外键失效的业务数据。

封存事务必须按顺序执行：预检、自动备份、生成不可变结算快照、更新学期状态、写操作日志。任一步失败时，数据库变更整体回滚；已经生成的安全备份允许保留。

### 2.3 结算口径

结算范围包含签到、培训、固定排班、排班例外、调班、维修事务和最终统计。

- 成员结算明细保存学号、姓名、角色、签到次数、签到分钟数、培训次数、培训时长和合计时长快照。
- 学期汇总保存审核状态、未签退数、维修状态分布、排班与例外数量、成员总数等快照。
- 快照不依赖后续成员改名、停用或业务数据重开后的变化。
- 封存版本从 1 开始递增；旧版本不可覆盖和删除。

### 2.4 历史数据迁移

- V6 迁移创建一个系统生成的“历史学期”，日期覆盖现有业务数据的最小和最大日期。
- 现有签到、培训、排班和维修数据全部归入历史学期。
- 历史学期默认进入 `SETTLING`，由管理员处理进行中维修等遗留项后手动封存。
- 迁移不清空现有数据，不自动创建当前学期。

### 2.5 排班例外与调班

固定排班属于一个学期。某日最终排班只能由统一解析器生成：

`固定周排班 -> 当日例外 -> 直接调班 -> 最终排班`

支持四种例外：

- 全天取消。
- 指定时段取消。
- 临时增加时段与人员。
- 指定时段人员替换。

会长和管理员可以直接创建、修改、删除例外与调班，必须填写原因。部长不能进入排班管理，仅可查看最终排班。调班保存原人员、替换人员、时间段和操作者快照。

排班只用于展示和管理，不作为签到有效性的条件。公共签到台、今日页、周排班页和导出必须调用同一个最终排班解析器。

## 3. 模块边界

后端保持 `com.ca.attendance` 根包，按业务拆分：

| 模块 | 职责 | 禁止事项 |
| --- | --- | --- |
| `identity` | 登录、会话、角色、成员资料 | 不直接写业务表 |
| `term` | 学期状态机、写入策略、结算与快照 | 不生成 Excel 或 HTML |
| `attendance` | 签到、签退、审核、记录 | 不直接依赖备份实现 |
| `schedule` | 固定排班、例外、调班、最终排班解析 | 不决定签到有效性 |
| `training` | 培训与参与时长 | 不自行判断学期写入状态 |
| `repair` | 维修事务与协议 | 不直接依赖备份实现 |
| `reporting` | 统计、预览、自定义导出 | 不修改业务数据 |
| `maintenance` | 备份、恢复、健康检查 | 不承载业务规则 |
| `audit` | 操作日志 | 不反向依赖业务模块 |
| `platform` | SQLite、路径、桌面控制、远程访问 | 不暴露业务接口 |

每个较大模块使用 `api/application/domain/infrastructure` 四层。跨模块协作只依赖小型端口接口，例如 `SafetyBackupPort`、`AuditLogPort`、`CurrentActor` 和 `TermWritePolicy`，禁止业务服务直接引用具体 `BackupService` 或静态认证上下文。

## 4. 数据模型

### 4.1 核心表

`academic_terms`

- `id`, `term_code`, `term_name`, `start_date`, `end_date`
- `status`, `legacy`
- `settling_started_at/by`, `sealed_at/by`
- `reopened_at/by`, `reopen_reason`
- `created_at/by`, `updated_at/by`

`term_settlements`

- `id`, `term_id`, `version`, `status`
- `summary_json`, `source_digest`
- `prepared_at/by`, `sealed_at/by`
- `superseded_at`, `reopen_reason`
- 唯一键：`(term_id, version)`

`term_member_settlements`

- `settlement_id`, `user_id`
- `student_no_snapshot`, `name_snapshot`, `role_snapshot`
- `attendance_count`, `attendance_minutes`
- `training_count`, `training_minutes`, `total_minutes`

`duty_schedule_exceptions`

- `id`, `term_id`, `exception_date`, `exception_type`
- `source_slot_id`, `start_time`, `end_time`, `title`, `location`
- `reason`, `created_at/by`, `updated_at/by`

`duty_schedule_exception_assignees`

- `exception_id`, `user_id`, `student_no_snapshot`, `name_snapshot`, `sort_order`

`duty_shift_reassignments`

- `id`, `term_id`, `duty_date`, `source_slot_id`
- `start_time`, `end_time`
- `original_user_id`, `original_student_no_snapshot`, `original_name_snapshot`
- `replacement_user_id`, `replacement_student_no_snapshot`, `replacement_name_snapshot`
- `reason`, `created_at/by`, `updated_at/by`

### 4.2 现有表变更

为 `attendance_records`、`training_sessions`、`duty_schedule_slots` 和 `repair_cases` 增加非空 `term_id`。SQLite 迁移先以可空列回填历史学期，再通过触发器和应用校验阻止新增空值；后续大版本可重建表收紧物理约束。

`public_attendance_submissions` 通过 `record_id` 归属学期，无需重复保存 `term_id`。

## 5. API 契约

现有接口尽量保持路径和响应兼容，新接口统一返回 `{ data, message, traceId? }` 的现有响应格式。

### 5.1 学期

| 方法 | 路径 | 权限 | 用途 |
| --- | --- | --- | --- |
| GET | `/api/terms` | 登录用户 | 学期列表与当前学期 |
| POST | `/api/terms` | 会长、管理员 | 创建草稿学期 |
| PUT | `/api/terms/{id}` | 会长、管理员 | 修改草稿学期 |
| POST | `/api/terms/{id}/activate` | 会长、管理员 | 激活学期，可选复制前一学期固定排班 |
| POST | `/api/terms/{id}/settling` | 会长、管理员 | 进入结算期 |
| GET | `/api/terms/{id}/settlement/preflight` | 会长、管理员 | 封存阻塞项 |
| POST | `/api/terms/{id}/settlement/preview` | 会长、管理员 | 生成或刷新结算预览 |
| POST | `/api/terms/{id}/seal` | 管理员 | 自动备份并封存 |
| POST | `/api/terms/{id}/reopen` | 管理员 | 填写原因、自动备份并重开 |
| GET | `/api/terms/{id}/settlements` | 会长、管理员 | 历史结算版本 |

所有列表与统计接口增加可选 `termId`；省略时后台默认当前学期，公共接口只使用当前活动学期。请求不存在或无当前学期时返回明确的 `409` 业务错误。

### 5.2 排班

| 方法 | 路径 | 权限 | 用途 |
| --- | --- | --- | --- |
| GET | `/api/schedules/effective?date=` | 登录用户 | 查询某日最终排班 |
| GET/POST/PUT/DELETE | `/api/schedules/exceptions...` | 会长、管理员 | 管理例外 |
| GET/POST/PUT/DELETE | `/api/schedules/reassignments...` | 会长、管理员 | 管理直接调班 |
| GET | `/api/public/schedules/today` | 本机公开 | 今日最终排班 |
| GET | `/api/public/schedules/week` | 本机公开 | 本周最终排班 |

固定排班写接口继续使用 `/api/schedules`，新增 `termId` 参数；部长访问所有排班写接口返回 `403`。

## 6. 前端信息架构

三套独立布局：

- `KioskLayout`：本机签到台，输入始终优先，自动聚焦、成功后 4 秒复位、同名只显示学号后四位。
- `AuthLayout`：后台登录、首次初始化、修改初始密码。
- `AdminLayout`：左侧紧凑导航、顶部学期切换和状态，不设右侧栏。

后台一级导航：

- 值班：今日、待审核、值班记录、统计、排班。
- 人员：成员、我的资料。
- 事务：培训、维修。
- 系统：学期与结算、数据与备份、设置、日志、鸣谢。

页面使用真实路由，筛选、分页和活动学期写入 URL。业务区使用干净白底与清晰分隔，淡蓝色用于导航和主操作；橙色仅表示结算警告，红色仅表示危险操作。动画限于路由过渡、列表进入、状态切换和操作反馈，并尊重 `prefers-reduced-motion`。

## 7. 兼容与验收

- V1-V5 数据库可直接升级到 V6，原数据数量不变且全部归入历史学期。
- V6 备份包含全部新表和 `term_id`；V1-V5 旧备份仍可恢复，恢复后自动归入历史学期。
- 桌面端、8080 本机签到台、8081 受限远程后台行为保持不变。
- 后端对状态机、权限、迁移、封存阻塞、快照版本和最终排班解析具备自动化测试。
- 前端对路由守卫、角色导航、学期状态、签到自动复位和核心表单具备自动化测试。
- 1440x900、1024x768、390x844 三个视口无溢出、遮挡或不可访问控件。
- 本轮不生成安装包、不上传 GitHub、不发布 Release。

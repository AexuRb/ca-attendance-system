# 计算机协会本地管理系统后端

Spring Boot 单体后端，提供静态前端、REST API、SQLite 持久化、Excel/PDF 处理、备份恢复和桌面端本机控制。

## 环境

- Java 21
- Maven 3.9+
- SQLite 由 JDBC 驱动内嵌，不需要安装数据库服务

## 本地运行

```powershell
mvn spring-boot:run
```

默认配置：

```text
地址：http://127.0.0.1:8080
根目录：当前工作目录
数据库：data/attendance.db
备份：backups/app
```

可通过环境变量设置独立测试根目录：

```powershell
$env:APP_ROOT = "$env:TEMP\ca-attendance-dev"
mvn spring-boot:run
```

启动时 `DatabaseMigrator` 会按照 `PRAGMA user_version` 创建或升级结构，并执行 SQLite `quick_check` 与外键检查。首次空库通过 `/api/setup/initialize` 创建首位管理员。

## 构建与测试

```powershell
mvn test
mvn package
```

最终文件固定为：

```text
target/attendance-backend.jar
```

集成测试使用临时 SQLite 文件，覆盖结构迁移、核心业务写入、备份恢复、首次初始化和桌面管理员恢复，不依赖本机真实数据。

## 认证

登录接口：

```http
POST /api/auth/login
```

登录成功后，受保护接口使用：

```http
Authorization: Bearer <token>
```

## 主要接口

公开与初始化：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/health` | 服务、应用身份和 SQLite 健康检查 |
| `GET` | `/api/setup/status` | 查询是否需要首次初始化 |
| `POST` | `/api/setup/initialize` | 仅空库、仅本机创建首位管理员 |
| `GET` | `/api/public/attendance/lookup/{keyword}` | 按学号或姓名查询签到人 |
| `POST` | `/api/public/attendance/submit` | 提交签到或签退 |
| `GET` | `/api/public/schedules/today` | 今日部长排班 |
| `GET` | `/api/public/schedules/week` | 本周排班概览 |

登录与个人：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/auth/login` | 账号密码登录 |
| `GET` | `/api/auth/me` | 当前登录用户 |
| `POST` | `/api/auth/change-password` | 修改本人密码 |
| `POST` | `/api/auth/logout` | 退出登录 |
| `PUT` | `/api/me/profile` | 修改个人资料 |
| `GET` | `/api/attendance/me` | 本人值班和培训时长 |

签到与统计：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/attendance` | 查询签到签退记录 |
| `GET` | `/api/attendance/open` | 查询未签退记录 |
| `GET` | `/api/attendance/reviews/pending` | 查询待审核记录 |
| `POST` | `/api/attendance/{id}/review` | 审核单条记录 |
| `POST` | `/api/attendance/reviews/bulk` | 批量通过待审核记录 |
| `POST` | `/api/attendance/manual` | 会长或管理员补录记录 |
| `PUT` | `/api/attendance/{id}/manual` | 管理员修改记录 |
| `DELETE` | `/api/attendance/{id}` | 会长或管理员删除并自动备份 |
| `GET` | `/api/stats/dashboard` | 后台概览统计 |
| `GET` | `/api/stats/summary` | 值班时长汇总 |
| `GET` | `/api/stats/export` | 会长或管理员导出统计 |

成员、培训、排班与维修：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET/POST` | `/api/users` | 查询或新增成员 |
| `PUT` | `/api/users/{id}` | 修改成员资料、角色和状态 |
| `POST` | `/api/users/{id}/reset-password` | 重置成员密码 |
| `GET/POST` | `/api/trainings` | 会长或管理员查询、新增培训 |
| `PUT/DELETE` | `/api/trainings/{id}` | 修改或归档培训 |
| `POST` | `/api/trainings/{id}/participants/import` | 导入培训名单 Excel |
| `GET` | `/api/trainings/export` | 导出培训统计 |
| `GET/POST` | `/api/schedules` | 查询或新增排班 |
| `PUT/DELETE` | `/api/schedules/{id}` | 修改或归档排班 |
| `GET` | `/api/schedules/import-template` | 下载当前星期和时段对应的排班模板 |
| `POST` | `/api/schedules/import/preview` | 全量校验并预览排班文件，不写入数据 |
| `POST` | `/api/schedules/import` | 校验通过后按星期和时段分组覆盖导入 |
| `GET/POST` | `/api/repairs` | 部长及以上查询、新增维修事务 |
| `PUT` | `/api/repairs/{id}` | 修改维修事务 |
| `DELETE` | `/api/repairs/{id}` | 会长或管理员移入维修回收站 |
| `GET` | `/api/repairs/recycle-bin` | 管理员查看维修回收站 |
| `POST` | `/api/repairs/{id}/restore` | 管理员恢复维修事务 |
| `POST` | `/api/repairs/{id}/purge` | 管理员确认编号、自动备份后永久删除 |
| `GET` | `/api/repairs/export` | 会长或管理员导出维修事务 |
| `GET` | `/api/repairs/{id}/agreement` | 打开可打印协议 |
| `GET` | `/api/exports/options` | 获取当前角色可用的数据源、筛选项和字段 |
| `POST` | `/api/exports/excel` | 按单一数据源、字段顺序和筛选条件导出 Excel |

设置、日志与维护：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET/PUT` | `/api/settings/weekdays` | 查询或修改值班星期 |
| `GET/PUT` | `/api/settings/duty-periods` | 查询或修改值班时间段 |
| `GET` | `/api/logs` | 管理员查询操作日志 |
| `GET` | `/api/logs/export` | 管理员导出日志 |
| `DELETE` | `/api/logs` | 管理员清空日志 |
| `GET` | `/api/maintenance/summary` | 数据中心摘要 |
| `GET/POST` | `/api/maintenance/backups` | 查询或创建备份 |
| `POST` | `/api/maintenance/backups/restore` | 管理员恢复备份 |
| `GET/DELETE` | `/api/maintenance/backups/{filename}` | 下载或删除备份 |

## 桌面控制接口

`/api/desktop/shutdown` 只供 Electron 主进程使用，并同时要求：

- 请求来源为本机回环地址。
- 携带桌面进程启动时随机生成的控制令牌。
- 控制令牌不写入数据库、配置文件或前端页面。

普通浏览器不能使用该接口。

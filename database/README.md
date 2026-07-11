# SQLite 数据库

系统使用嵌入式 SQLite，不需要安装数据库服务、创建数据库账号或执行初始化命令。

首次启动时，后端会自动完成以下操作：

1. 在应用根目录创建 `data/attendance.db`。
2. 执行版本化结构脚本。
3. 初始化星期配置。
4. 引导用户在页面中创建首位管理员。

数据库结构的运行时来源是：

```text
backend/src/main/resources/db/sqlite/V1__initial_schema.sql
backend/src/main/resources/db/sqlite/V2__repair_recycle_bin.sql
backend/src/main/resources/db/sqlite/V3__attendance_duty_period.sql
```

本目录下的 `schema.sql` 是同一结构的便于审阅版本，不需要手动执行。

## 数据目录

```text
应用根目录/
├─ data/attendance.db
├─ backups/app/
├─ exports/
└─ logs/
```

`data/`、`backups/`、`exports/` 和 `logs/` 均不会提交到 GitHub。

## 迁移与备份

- 迁移整套系统前，应先关闭桌面应用，再复制完整应用根目录。
- 应用运行期间不要直接复制 `attendance.db`，应使用后台的一键备份或完整迁移包。
- 更新程序不会覆盖 `data/` 和 `backups/`。
- 数据库当前结构版本为 3；旧数据库启动时会自动增加维修回收站字段和值班时段资格快照字段。
- SQLite 文件未加密，请妥善保管数据库与备份文件。

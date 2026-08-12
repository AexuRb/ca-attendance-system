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
backend/src/main/resources/db/sqlite/V4__public_submission_idempotency.sql
backend/src/main/resources/db/sqlite/V5__minister_attendance_auto_approval.sql
backend/src/main/resources/db/sqlite/V6__reserved.sql
backend/src/main/resources/db/sqlite/V7__remove_schedule_adjustments.sql
backend/src/main/resources/db/sqlite/V8__repair_case_sequences.sql
backend/src/main/resources/db/sqlite/V9__attendance_eligibility_policy.sql
backend/src/main/resources/db/sqlite/V10__normalize_training_participants.sql
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
- 数据库当前结构版本为 10；旧数据库启动时会依次补齐维修回收站、值班资格快照、公共签到幂等、部长自动审核、有效时长限制快照和培训时长口径迁移。
- V7 会删除旧版本曾使用的排班例外和调班表，固定周表数据不受影响。
- V8 使用持久化每日序列生成维修编号，永久删除维修记录后也不会复用编号。
- V9 为值班记录增加两个限制快照，并按当前默认的审核判定规则统一重算旧记录。
- V10 将历史培训参与状态归一为内部兼容值并移除状态索引，保留每条记录原有时长；培训业务只按 `duration_hours` 统计。
- SQLite 文件未加密，请妥善保管数据库与备份文件。

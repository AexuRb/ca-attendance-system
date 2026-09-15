# SQLite 数据库

SQLite 由后端内嵌使用，不需要安装数据库服务或手工执行 SQL。数据库位于 `APP_ROOT/data/attendance.db`，首次空库通过页面创建首位管理员。

## 结构来源

运行时结构以 [版本迁移目录](../backend/src/main/resources/db/sqlite/) 和 [DatabaseMigrator](../backend/src/main/java/com/ca/attendance/config/DatabaseMigrator.java) 为准。启动按 `PRAGMA user_version` 依次升级，并执行完整性检查；当前版本为 11。

- V7 移除旧排班例外和调班表，保留固定周表。
- V8 引入持久化维修编号序列，永久删除后不复用编号。
- V9 增加有效时长限制快照并重算旧记录。
- V10 归一历史培训参与状态，保留原有时长，培训按 `duration_hours` 统计。
- V11 添加全局外观设置 `UI_APPEARANCE`，默认 `CLASSIC`，不清空业务数据。

[schema.sql](schema.sql) 是供阅读和脚本测试使用的表结构副本，不是运行时迁移入口，也不替代迁移中的设置数据初始化。不要手工执行它来升级既有数据库。

## 数据保护

- 开发测试设置唯一的临时 APP_ROOT，禁止操作正式数据。
- 运行中的数据库通过后台备份；迁移电脑时从托盘完全退出后复制完整应用根目录，包括 data、backups、exports、logs。
- 更新程序保留业务数据；结构变更新增迁移并验证空库及升级，不改写已安装数据库。
- 数据库和备份未加密，不得提交 Git。恢复步骤见 [本地运行说明](../本地运行说明.md)，验证方法见 [数据安全演练记录](../docs/verification/数据安全演练记录.md)。

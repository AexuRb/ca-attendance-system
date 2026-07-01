# 数据库说明

本目录用于保存计算机协会值班签到签退系统的 MySQL 数据库脚本。

## 文件说明

| 文件 | 作用 |
| --- | --- |
| `schema.sql` | 创建数据库和核心表结构 |
| `seed.sql` | 写入默认值班星期和系统配置 |
| `init-db.bat` | Windows 一键初始化数据库 |
| `import_members.py` | 从成员信息 Excel 导入 `users` 表 |

## 初始化顺序

推荐方式：先在项目根目录复制并修改 `local-config.bat`，然后双击项目根目录的：

```text
init-db.bat
```

也可以双击本目录下的：

```text
database\init-db.bat
```

脚本会根据自身所在目录自动定位 `schema.sql` 和 `seed.sql`，所以项目移动到其他路径或新电脑后不需要修改 SQL 文件路径。

如果 `mysql` 命令没有加入 PATH，可以在 `local-config.bat` 中额外添加：

```bat
set "MYSQL_CLIENT=full-path-to-mysql.exe"
```

备用手动方式：在 MySQL 中依次执行：

```sql
SOURCE database/schema.sql;
SOURCE database/seed.sql;
```

如果在服务器终端执行，可使用：

```bash
mysql -u root -p < database/schema.sql
mysql -u root -p ca_attendance < database/seed.sql
```

## 核心表

| 表名 | 说明 |
| --- | --- |
| `users` | 用户表，保存成员、部长、会长、管理员 |
| `duty_weekday_settings` | 值班星期配置表 |
| `attendance_records` | 签到签退记录表 |
| `operation_logs` | 操作日志表 |
| `app_settings` | 系统配置表 |

## 角色值

`users.role` 可选值：

- `MEMBER`：成员
- `MINISTER`：部长
- `PRESIDENT`：会长
- `ADMIN`：管理员

## 账号状态

`users.status` 可选值：

- `ACTIVE`：启用
- `DISABLED`：停用

停用账号不能登录、不能签到签退，但历史记录保留。

## 审核状态

`attendance_records.check_in_status` 和 `attendance_records.check_out_status` 可选值：

- `NOT_SUBMITTED`：未提交，主要用于未签退状态
- `PENDING`：待审核
- `APPROVED`：审核通过
- `REJECTED`：审核驳回
- `AUTO_APPROVED`：自动通过，会长和管理员使用

## 有效状态

`attendance_records.effective_status` 可选值：

- `PENDING`：待处理
- `VALID`：有效，计入统计
- `INVALID`：无效，不计入统计
- `INCOMPLETE`：未完成，例如忘记签退

## 有效时长

`attendance_records.valid_hours` 保存有效时长，单位为小时。

计算规则：

- 有效时长 = 签退时间 - 签到时间
- 按小时四舍五入取整数
- 1 小时 15 分钟记为 1 小时
- 2 小时 34 分钟记为 3 小时
- 记录未通过审核、缺少签退时间或被驳回时，有效时长为 0

后端负责在审核通过、签退提交、管理员补改记录时重新计算 `duration_minutes` 和 `valid_hours`。

## 初始管理员

初始管理员账号：

```text
cugbcacyh
```

初始管理员密码不写入 SQL 明文。后端开发阶段会实现初始化逻辑：

1. 首次启动时检查 `users` 表是否存在管理员。
2. 如果不存在，则读取初始化配置。
3. 使用 BCrypt 加密初始密码。
4. 创建管理员账号。
5. 设置 `must_change_password = 1`，要求首次登录后修改密码。

## 默认值班星期

`seed.sql` 默认启用星期一到星期日，方便开发和测试。

正式使用时，会长或管理员可以在后台调整实际值班星期。

历史记录按提交当天的值班星期配置判断是否有效，后续调整值班星期不反向修改历史记录。

## 导入成员信息表

脚本不再内置本机成员表路径。使用时请通过 `--excel` 指定当前电脑上的 Excel 文件，或设置 `MEMBER_EXCEL` 环境变量。

导入字段映射：

| Excel 列 | 数据库字段 |
| --- | --- |
| 姓名 | `users.name` |
| 学号 | `users.student_no` |
| 联系方式 | `users.phone` |
| 学院 | `users.major` |
| 年级 | `users.grade` |
| QQ | 暂无对应 Excel 列，导入为空 |

导入前先 dry-run：

```powershell
$env:PYTHONIOENCODING = "utf-8"
$env:MYSQL_PWD = "你的 MySQL 密码"
python database/import_members.py --excel ".\成员信息表.xlsx" --dry-run
Remove-Item Env:MYSQL_PWD
Remove-Item Env:PYTHONIOENCODING
```

正式导入：

```powershell
$env:PYTHONIOENCODING = "utf-8"
$env:MYSQL_PWD = "你的 MySQL 密码"
python database/import_members.py --excel ".\成员信息表.xlsx"
Remove-Item Env:MYSQL_PWD
Remove-Item Env:PYTHONIOENCODING
```

导入规则：

- 所有导入用户默认为 `MEMBER`。
- 默认账号状态为 `ACTIVE`。
- 初始密码为学号后六位，并以 BCrypt 哈希写入。
- `must_change_password = 1`，后端登录后应要求首次修改密码。
- 学号重复时更新姓名、手机号、学院、年级，不覆盖角色、状态和已有密码。

脚本依赖：

```powershell
python -m pip install openpyxl bcrypt mysql-connector-python
```

## 已验证环境

当前脚本已在本机 MySQL 9.7.1 中导入并通过基础烟雾测试：

- 成功创建 `ca_attendance` 数据库。
- 成功创建 5 张核心表。
- 成功写入 7 条默认值班星期配置。
- 成功写入系统配置。
- 临时用户和临时签到记录可以插入，并可事务回滚。

## 数据备份

系统不再使用外部备份脚本。管理员或会长可在后台“维护”页面执行一键备份，备份文件保存到项目根目录的 `backups/app`。
备份包含 `users`、`duty_weekday_settings`、`app_settings`、`attendance_records`、`operation_logs` 五张核心表。

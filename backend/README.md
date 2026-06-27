# 计算机协会值班签到签退系统后端

## 环境

- Java 21
- Maven 3.9+
- MySQL 9.7+

## 本地运行

先确认数据库已初始化，并设置数据库密码环境变量：

```powershell
$env:DB_PASSWORD = "你的 MySQL 密码"
mvn spring-boot:run
```

服务启动后访问：

```text
http://localhost:8080/api/health
```

也可以直接在 IntelliJ IDEA 中打开 `backend` 目录，等待 Maven 同步完成后运行：

```text
com.ca.attendance.AttendanceApplication
```

运行前需要在 IDEA 的 Run Configuration 里添加环境变量：

```text
DB_PASSWORD=你的 MySQL 密码
```

## 认证

登录接口：

```http
POST /api/auth/login
```

登录成功后，后续接口使用：

```http
Authorization: Bearer <token>
```

## 已实现接口

公开接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/health` | 健康检查 |
| `GET` | `/api/public/attendance/lookup/{studentNo}` | 公开页按学号查询姓名和当前操作 |
| `POST` | `/api/public/attendance/submit` | 公开页提交签到或签退 |

登录与个人：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/auth/login` | 学号密码登录 |
| `GET` | `/api/auth/me` | 当前登录用户 |
| `POST` | `/api/auth/change-password` | 修改密码 |
| `POST` | `/api/auth/logout` | 退出登录 |
| `PUT` | `/api/me/profile` | 修改本人年级 |
| `GET` | `/api/attendance/me` | 查看本人值班记录 |

后台管理：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/attendance` | 查询全部签到签退记录 |
| `GET` | `/api/attendance/open` | 查看未签退记录 |
| `GET` | `/api/attendance/reviews/pending` | 查看待审核记录 |
| `POST` | `/api/attendance/{id}/review` | 审核签到或签退 |
| `POST` | `/api/attendance/reviews/bulk` | 批量通过待审核记录 |
| `POST` | `/api/attendance/manual` | 会长或管理员新增签到记录 |
| `PUT` | `/api/attendance/{id}/manual` | 管理员手动修改记录 |
| `DELETE` | `/api/attendance/{id}` | 管理员删除签到记录 |
| `GET` | `/api/users` | 成员查询 |
| `POST` | `/api/users` | 新增成员 |
| `PUT` | `/api/users/{id}` | 修改成员、状态、角色 |
| `POST` | `/api/users/{id}/reset-password` | 重置密码 |
| `GET` | `/api/settings/weekdays` | 查看值班星期 |
| `PUT` | `/api/settings/weekdays` | 修改值班星期 |
| `GET` | `/api/stats/dashboard` | 后台概览统计 |
| `GET` | `/api/stats/summary` | 统计汇总 |
| `GET` | `/api/stats/export` | 导出 Excel |
| `GET` | `/api/logs` | 管理员查看操作日志 |
| `GET` | `/api/logs/export` | 管理员导出操作日志 |
| `DELETE` | `/api/logs` | 管理员清空操作日志 |
| `GET` | `/api/maintenance/backups` | 会长或管理员查看备份 |
| `POST` | `/api/maintenance/backups` | 会长或管理员创建备份 |
| `GET` | `/api/maintenance/backups/{filename}` | 会长或管理员下载备份 |
| `DELETE` | `/api/maintenance/backups/{filename}` | 管理员删除备份 |

## 验证情况

已完成本地验证：

- Maven 编译通过。
- Maven 打包通过。
- 后端可连接本机 MySQL。
- `/api/health` 返回正常。
- 默认初始管理员账号为 `cugbcacyh`。
- 公开学号查询可识别姓名。
- 临时成员签到、签退、审核闭环通过，测试数据已清理。

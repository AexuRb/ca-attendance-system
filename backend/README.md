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
| `GET` | `/api/public/schedules/today` | 公开页查看今日排班 |
| `GET` | `/api/public/schedules/week` | 公开页查看本周排班 |

登录与个人：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/auth/login` | 学号密码登录 |
| `GET` | `/api/auth/me` | 当前登录用户 |
| `POST` | `/api/auth/change-password` | 修改密码 |
| `POST` | `/api/auth/logout` | 退出登录 |
| `PUT` | `/api/me/profile` | 修改本人手机号、学院、QQ |
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
| `GET` | `/api/trainings` | 查看培训场次 |
| `POST` | `/api/trainings` | 会长或管理员新增培训 |
| `PUT` | `/api/trainings/{id}` | 会长或管理员修改培训 |
| `DELETE` | `/api/trainings/{id}` | 会长或管理员归档培训 |
| `GET` | `/api/trainings/{id}/participants` | 查看培训参与名单 |
| `POST` | `/api/trainings/{id}/participants` | 会长或管理员新增参与记录 |
| `PUT` | `/api/trainings/{id}/participants/{participantId}` | 会长或管理员修改参与记录 |
| `DELETE` | `/api/trainings/{id}/participants/{participantId}` | 会长或管理员删除参与记录 |
| `POST` | `/api/trainings/{id}/participants/import` | 会长或管理员导入培训名单 Excel |
| `GET` | `/api/trainings/{id}/export` | 会长或管理员导出单场培训名单 |
| `GET` | `/api/trainings/export` | 会长或管理员导出培训统计 |
| `GET` | `/api/schedules` | 查看后台排班 |
| `POST` | `/api/schedules` | 会长或管理员新增排班 |
| `PUT` | `/api/schedules/{id}` | 会长或管理员修改排班 |
| `DELETE` | `/api/schedules/{id}` | 会长或管理员归档排班 |
| `GET` | `/api/repairs` | 部长、会长或管理员查看维修事务 |
| `POST` | `/api/repairs` | 部长、会长或管理员新增维修事务 |
| `PUT` | `/api/repairs/{id}` | 部长、会长或管理员修改维修事务 |
| `GET` | `/api/repairs/export` | 部长、会长或管理员导出维修事务 Excel |
| `GET` | `/api/repairs/{id}/agreement` | 部长、会长或管理员打开可打印维修协议 |
| `GET` | `/api/logs` | 管理员查看操作日志 |
| `GET` | `/api/logs/export` | 管理员导出操作日志 |
| `DELETE` | `/api/logs` | 管理员清空操作日志 |
| `GET` | `/api/maintenance/summary` | 会长或管理员查看数据中心汇总 |
| `GET` | `/api/maintenance/backups` | 会长或管理员查看备份 |
| `POST` | `/api/maintenance/backups` | 会长或管理员创建备份 |
| `POST` | `/api/maintenance/backups/restore` | 管理员上传系统备份并恢复数据 |
| `GET` | `/api/maintenance/backups/{filename}` | 会长或管理员下载备份 |
| `DELETE` | `/api/maintenance/backups/{filename}` | 管理员删除备份 |

## 验证情况

已完成本地验证：

- Maven 编译通过。
- Maven 打包通过。
- 后端可连接本机 MySQL。
- `/api/health` 返回正常。
- 培训管理可创建场次、导入名单、导出单场名单和统计表。
- 维修事务可创建、修改状态、导出 Excel，并生成可打印协议 HTML。
- 默认初始管理员账号为 `cugbcacyh`。
- 公开学号查询可识别姓名。
- 临时成员签到、签退、审核闭环通过，测试数据已清理。

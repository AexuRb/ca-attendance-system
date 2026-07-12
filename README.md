# 计算机协会本地离线管理系统

面向计算机协会日常工作的 Windows 本地离线系统。原生桌面应用会在本机启动 Spring Boot 服务，并通过 Electron 展示公开签到台和分角色管理后台；运行时不需要互联网、MySQL、Java 或浏览器插件。

## 主要功能

- 公开签到、签退、重名成员选择和成功确认
- 与后台设置实时同步的今日部长排班和本周概览
- 签到审核、手动补录、记录查询、删除与时长统计
- 成员管理、角色调整、批量启停和 Excel 导入
- 培训场次、主讲人、参与名单、培训时长和 Excel 导入导出
- 每周排班、Excel 批量导入、值班星期和值班时间段设置
- 维修事务、维修协议、免责协议、维修回收站和记录导出
- 自定义 Excel 数据中心、操作日志、完整备份与恢复
- 首次启动向导、响应式签到台和分角色后台

## 角色权限

| 角色 | 主要权限 |
| --- | --- |
| 成员 | 公开签到签退，查看个人记录，维护个人资料和密码 |
| 部长 | 审核记录、查看统计、管理维修事务；不能进入培训或成员管理，不能导出维修事务 |
| 会长 | 管理成员、培训、排班和维修，导入导出业务数据，新增或删除签到记录，执行备份 |
| 管理员 | 拥有会长业务权限，并可维护管理员账号、修改签到记录、查看日志、删除或恢复备份 |

## 技术方案

- 桌面端：Electron 43
- 前端：Vue 3、Vite 8、Lucide Icons
- 后端：Spring Boot 4、Spring JDBC
- 数据库：SQLite，单文件存储
- 运行时：随应用打包的 Eclipse Temurin JRE 21
- 支持系统：Windows 10/11 x64
- 本机地址：`http://127.0.0.1:8080`

## 安装与使用

Release 提供两种无需额外环境的离线包：

- `CA-Attendance-System-Setup-2.1.0.exe`：安装版，默认安装到 `C:\CAAttendance\app`。
- `CA-Attendance-System-Portable-2.1.0.zip`：便携版，解压后运行根目录的 `启动管理系统.bat`。

首次启动会自动创建 SQLite 数据库，并要求现场创建首位管理员，不预置公开账号或密码。应用运行期间也可以在同一台电脑的浏览器访问 `http://127.0.0.1:8080`。点击桌面窗口右上角关闭按钮后，系统会继续在 Windows 托盘运行；需要停止本机服务时，从托盘菜单选择“完全退出”。

当前安装包未使用代码签名证书，Windows 首次运行时可能显示未知发布者或信誉提示。发布页同时提供 `SHA256SUMS.txt` 供校验文件完整性。

完整操作见 [本地运行说明.md](本地运行说明.md) 和 [系统使用说明.md](系统使用说明.md)。

## 数据目录

程序文件与业务数据分离，安装版和便携版使用相同结构：

```text
CAAttendance/
├─ app/                     桌面程序、后端和内置 Java
├─ data/attendance.db       SQLite 主数据库
├─ backups/app/             后台生成的备份
├─ exports/                 导出目录
└─ logs/                    桌面端和后端日志
```

迁移电脑前先退出应用，再复制整个 `CAAttendance` 文件夹。SQLite 文件和备份未加密，应按成员隐私数据妥善保管。

## 源码开发

源码开发需要 JDK 21、Maven 3.9+ 和 Node.js 22.12+。不需要安装数据库服务。

```powershell
cd frontend
npm ci
npm run build

cd ..\backend
mvn package
```

运行源码版可双击根目录 `start.bat`。构建完整 Windows 发行包：

```powershell
.\scripts\build-desktop.ps1
```

脚本会校验并准备固定版本 Temurin、运行前后端测试、构建安装版和便携版，并将最终文件写入 `release-artifacts/`。

## 项目结构

```text
backend/        Spring Boot 后端、SQLite 迁移和集成测试
database/       SQLite 结构说明和可读架构副本
desktop/        Electron 主进程、预加载桥接和打包配置
docs/           Excel 模板及项目补充文档
frontend/       Vue 前端
scripts/        构建、业务冒烟和浏览器界面测试
```

## 文档

- [系统使用说明.md](系统使用说明.md)：角色权限和日常操作
- [本地运行说明.md](本地运行说明.md)：安装、迁移、备份、源码构建和故障排查
- [计算机协会值班签到签退系统需求说明书.md](计算机协会值班签到签退系统需求说明书.md)：业务规则和验收标准
- [database/README.md](database/README.md)：SQLite 数据库与版本迁移

## 数据与安全

数据库、备份、导出、日志、材料、运行时、构建产物、本机配置和测试截图均已加入 `.gitignore`。不要把真实数据库、成员隐私数据、协会内部材料或本机凭据提交到公开仓库。

# 计算机协会值班签到签退系统

用于计算机协会日常值班的本地网页系统，支持公开签到签退、后台审核、成员管理、批量导入、统计查询、Excel 导出和数据库备份。

## 技术栈

- 前端：Vue 3 + Vite
- 后端：Spring Boot
- 数据库：MySQL
- 运行方式：Windows 本地单服务，默认端口 `8080`

## 本地启动

1. 安装 JDK 21、Maven、Node.js、MySQL。
2. 创建数据库并导入 `database/schema.sql`，需要初始数据时再导入 `database/seed.sql`。
3. 复制配置模板：

```bat
copy local-config.example.bat local-config.bat
```

4. 修改 `local-config.bat` 里的 MySQL 密码和初始管理员密码。
5. 双击 `start.bat` 启动网站。

启动后访问：

```text
http://127.0.0.1:8080
```

## 前端重新构建

如果修改了 `frontend/src` 下的前端代码：

```bash
cd frontend
npm install
npm run build
```

构建产物会写入 `backend/src/main/resources/static`，再通过后端服务统一访问。

## 文档

- `系统使用说明.md`：面向日常使用者的操作说明
- `计算机协会值班签到签退系统需求说明书.md`：需求和权限规则
- `本地运行说明.md`：启动和后台备份说明
- `database/README.md`：数据库结构和初始化说明

## 安全说明

`local-config.bat`、数据库备份、运行日志、依赖目录和构建缓存不会上传到 GitHub。请不要把真实数据库密码、管理员密码或成员隐私数据提交到仓库。

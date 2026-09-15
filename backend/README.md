# 后端开发

Java 21、Spring Boot 4、Spring JDBC、SQLite 模块化单体，提供 REST API、静态前端、Excel/协议生成和备份恢复。需要 JDK 21 与 Maven 3.9+，不需要独立数据库服务。

## 隔离运行

在 `backend/` 中运行，使用每次唯一的临时数据目录：

```powershell
$env:APP_ROOT = Join-Path $env:TEMP ('ca-attendance-dev-' + [guid]::NewGuid().ToString('N'))
mvn spring-boot:run
```

服务默认绑定 `127.0.0.1:8080`；远程管理连接器为 `127.0.0.1:8081`，只接受会长和管理员，禁止初始化、公开签到及桌面控制。源码环境可以用 `APP_PORT`、`APP_REMOTE_PORT` 隔离端口；桌面发行版保持固定端口。测试结束停止该后端，不复用正式 APP_ROOT。

若需后端直接提供前端页面，先在 `frontend/` 执行 `npm ci`、`npm run build`。输出写入后端的 `src/main/resources/static/`，不要手工编辑生成文件。

## 构建与测试

```powershell
mvn -q test
mvn package
```

输出为 `target/attendance-backend.jar`。集成测试使用临时 SQLite；迁移机制见 [数据库说明](../database/README.md)，备份与回滚验证见 [数据安全演练记录](../docs/verification/数据安全演练记录.md)。

## 接口与权限

接口清单和授权规则统一维护在 [角色权限矩阵](../docs/角色权限矩阵.md)，避免在本文件重复维护整份路由表。具体请求结构查看对应 Controller、DTO 与测试。

- 登录使用 `POST /api/auth/login`，受保护请求携带 Bearer 令牌；远程入口在登录和每次鉴权时都检查角色。
- 全新空库通过 `/api/setup/initialize` 创建首位管理员；不提供绕过登录的管理员恢复入口。
- `/api/desktop/shutdown` 仅供 Electron 主进程控制，同时要求回环来源与启动时随机生成的控制令牌，普通页面不能调用。
- Controller 负责映射、转换和响应，Service 负责权限、事务与业务编排；业务写入与审计保持一致。

配置入口为 [application.yml](src/main/resources/application.yml)，开发规则见 [AGENTS.md](../AGENTS.md)。

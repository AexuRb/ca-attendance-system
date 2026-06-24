@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

call "%~dp0local-config.bat"

set "ROOT_DIR=%~dp0"
set "BACKEND_DIR=%ROOT_DIR%backend"
set "JAR=%BACKEND_DIR%\target\attendance-backend-0.0.1-SNAPSHOT.jar"

echo.
echo ========================================
echo  计算机协会签到签退系统 - 本地启动
echo ========================================
echo.

where java >nul 2>nul
if errorlevel 1 (
  echo [错误] 未找到 java。请先安装 JDK，并把 java 加入 PATH。
  pause
  exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -Command "if (Get-NetTCPConnection -LocalPort %APP_PORT% -State Listen -ErrorAction SilentlyContinue) { exit 10 }"
if "%ERRORLEVEL%"=="10" (
  echo [提示] %APP_PORT% 端口已经有程序在运行。
  echo 如果网站已经启动，浏览器会直接打开访问地址。
  start "" "http://127.0.0.1:%APP_PORT%"
  pause
  exit /b 0
)

if not exist "%JAR%" (
  echo [提示] 未找到后端 jar，正在尝试自动打包...
  where mvn >nul 2>nul
  if errorlevel 1 (
    echo [错误] 未找到 Maven。请先安装 Maven，或在 IntelliJ IDEA 中执行 package。
    pause
    exit /b 1
  )
  pushd "%BACKEND_DIR%"
  call mvn -q -DskipTests package
  if errorlevel 1 (
    popd
    echo [错误] 后端打包失败。
    pause
    exit /b 1
  )
  popd
)

echo [启动] 正在打开后端服务窗口...
start "计算机协会签到签退系统" /D "%BACKEND_DIR%" cmd /k java -jar target\attendance-backend-0.0.1-SNAPSHOT.jar

echo [等待] 正在等待服务启动...
for /l %%i in (1,1,20) do (
  powershell -NoProfile -ExecutionPolicy Bypass -Command "try { $r = Invoke-RestMethod -Uri 'http://127.0.0.1:%APP_PORT%/api/health' -TimeoutSec 2; if ($r.status -eq 'ok') { exit 0 } } catch { exit 1 }"
  if not errorlevel 1 goto started
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Sleep -Seconds 1"
)

echo [警告] 服务可能还没有完全启动，请查看新打开的后端窗口。
pause
exit /b 1

:started
echo [完成] 服务已启动。
echo [访问] http://127.0.0.1:%APP_PORT%
start "" "http://127.0.0.1:%APP_PORT%"
echo.
echo 后端运行在新打开的窗口中。需要停止时，关闭那个后端窗口即可。
pause

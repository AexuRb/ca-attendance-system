@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

if not defined APP_HOST set "APP_HOST=127.0.0.1"
if not defined APP_PORT set "APP_PORT=8080"
set "APP_ROOT=%~dp0"
set "BACKEND_DIR=%~dp0backend"
set "JAR=%BACKEND_DIR%\target\attendance-backend.jar"

echo.
echo ========================================
echo  CA Attendance System - SQLite Start
echo ========================================
echo.

where java >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Java was not found.
  echo Install JDK 21 for source development, or use the desktop package.
  pause
  exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -Command "try { [void][uint16]'%APP_PORT%'; exit 0 } catch { exit 1 }"
if errorlevel 1 (
  echo [ERROR] APP_PORT is invalid: %APP_PORT%
  pause
  exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\check-port.ps1" "%APP_PORT%"
set "PORT_CHECK=%ERRORLEVEL%"
if "%PORT_CHECK%"=="10" (
  echo [INFO] The local service is already running.
  start "" "http://127.0.0.1:%APP_PORT%"
  pause
  exit /b 0
)
if "%PORT_CHECK%"=="11" (
  echo [ERROR] Port %APP_PORT% is occupied by another program.
  echo Close that program or set APP_PORT before running this script.
  pause
  exit /b 1
)

if not exist "%JAR%" (
  echo [INFO] Backend jar not found. Building it now...
  where mvn >nul 2>nul
  if errorlevel 1 (
    echo [ERROR] Maven was not found.
    pause
    exit /b 1
  )
  pushd "%BACKEND_DIR%"
  call mvn -q -DskipTests package
  if errorlevel 1 (
    popd
    echo [ERROR] Backend package failed.
    pause
    exit /b 1
  )
  popd
)

echo [START] Opening local SQLite service...
start "CA Attendance System" /D "%~dp0" cmd /k java -jar "backend\target\attendance-backend.jar" --server.address=%APP_HOST% --server.port=%APP_PORT% --app.storage.root="%APP_ROOT%"

echo [WAIT] Waiting for service startup...
for /l %%i in (1,1,30) do (
  powershell -NoProfile -ExecutionPolicy Bypass -Command "try { $r = Invoke-RestMethod -Uri 'http://127.0.0.1:%APP_PORT%/api/health' -TimeoutSec 2; if ($r.status -eq 'ok') { exit 0 } } catch { exit 1 }"
  if not errorlevel 1 goto started
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Sleep -Seconds 1"
)

echo [WARN] Service did not become ready. Check the backend window.
pause
exit /b 1

:started
echo [DONE] Service started with SQLite.
echo [DATA] %APP_ROOT%data\attendance.db
echo [URL] http://127.0.0.1:%APP_PORT%
start "" "http://127.0.0.1:%APP_PORT%"
echo.
echo Close the backend window to stop the service.
pause

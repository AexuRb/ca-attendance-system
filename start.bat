@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

if exist "%~dp0local-config.bat" (
  call "%~dp0local-config.bat"
) else (
  echo [ERROR] local-config.bat not found.
  if exist "%~dp0local-config.example.bat" (
    copy "%~dp0local-config.example.bat" "%~dp0local-config.bat" >nul
    echo local-config.bat has been created.
  )
  echo Please edit local-config.bat and set DB_PASSWORD and INITIAL_ADMIN_PASSWORD.
  pause
  exit /b 1
)

if not defined APP_PORT set "APP_PORT=8080"
if not defined DB_HOST set "DB_HOST=127.0.0.1"
if not defined DB_PORT set "DB_PORT=3306"
if not defined DB_NAME set "DB_NAME=ca_attendance"
if not defined DB_USER set "DB_USER=root"

if not defined DB_PASSWORD (
  echo [ERROR] DB_PASSWORD is not set.
  echo Please edit local-config.bat and set your MySQL password.
  pause
  exit /b 1
)

if "%DB_PASSWORD%"=="change-me" (
  echo [ERROR] DB_PASSWORD is still change-me.
  echo Please edit local-config.bat and set your MySQL password.
  pause
  exit /b 1
)

if "%INITIAL_ADMIN_PASSWORD%"=="change-me" (
  echo [ERROR] INITIAL_ADMIN_PASSWORD is still change-me.
  echo Please edit local-config.bat and set the initial admin password.
  pause
  exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -Command "try { [void][uint16]'%APP_PORT%'; exit 0 } catch { exit 1 }"
if errorlevel 1 (
  echo [ERROR] APP_PORT is invalid: %APP_PORT%
  echo Please set APP_PORT in local-config.bat, for example: set "APP_PORT=8080"
  pause
  exit /b 1
)

set "ROOT_DIR=%~dp0"
set "BACKEND_DIR=%ROOT_DIR%backend"
set "JAR=%BACKEND_DIR%\target\attendance-backend-0.0.1-SNAPSHOT.jar"

echo.
echo ========================================
echo  CA Attendance System - Local Start
echo ========================================
echo.

where java >nul 2>nul
if errorlevel 1 (
  echo [ERROR] java was not found. Install JDK and add java to PATH.
  pause
  exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\check-port.ps1" "%APP_PORT%"
set "PORT_CHECK=%ERRORLEVEL%"
if "%PORT_CHECK%"=="10" (
  echo [INFO] Port %APP_PORT% already has a listening service.
  echo If the website is running, the browser will open it now.
  start "" "http://127.0.0.1:%APP_PORT%"
  pause
  exit /b 0
)
if "%PORT_CHECK%"=="11" (
  echo [ERROR] Port %APP_PORT% is occupied but is not a reachable website service.
  echo Please edit local-config.bat and set APP_PORT to another port, for example 8081.
  pause
  exit /b 1
)

if not exist "%JAR%" (
  echo [INFO] Backend jar not found. Trying to build it now...
  where mvn >nul 2>nul
  if errorlevel 1 (
    echo [ERROR] Maven was not found. Install Maven or run package in IntelliJ IDEA.
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

echo [START] Opening backend service window...
start "CA Attendance System" /D "%BACKEND_DIR%" cmd /k java -jar target\attendance-backend-0.0.1-SNAPSHOT.jar --server.port=%APP_PORT%

echo [WAIT] Waiting for service startup...
for /l %%i in (1,1,20) do (
  powershell -NoProfile -ExecutionPolicy Bypass -Command "try { $r = Invoke-RestMethod -Uri 'http://127.0.0.1:%APP_PORT%/api/health' -TimeoutSec 2; if ($r.status -eq 'ok') { exit 0 } } catch { exit 1 }"
  if not errorlevel 1 goto started
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Sleep -Seconds 1"
)

echo [WARN] Service may not be fully started. Check the new backend window.
pause
exit /b 1

:started
echo [DONE] Service started.
echo [URL] http://127.0.0.1:%APP_PORT%
start "" "http://127.0.0.1:%APP_PORT%"
echo.
echo Backend is running in the new window. Close that window to stop it.
pause

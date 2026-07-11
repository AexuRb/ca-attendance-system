@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

if not defined APP_HOST set "APP_HOST=127.0.0.1"
if not defined APP_PORT set "APP_PORT=8080"
if not defined APP_ROOT set "APP_ROOT=%~dp0"
for %%I in ("%APP_ROOT%") do set "APP_ROOT=%%~fI"
if "%APP_ROOT:~-1%"=="\" if not "%APP_ROOT:~1%"==":\" set "APP_ROOT=%APP_ROOT:~0,-1%"
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

set "NEED_BUILD=0"
if not exist "%JAR%" (
  set "NEED_BUILD=1"
) else (
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$jarTime = (Get-Item -LiteralPath $env:JAR).LastWriteTimeUtc; $newer = Get-ChildItem -LiteralPath (Join-Path $env:BACKEND_DIR 'src\main'), (Join-Path $env:BACKEND_DIR 'pom.xml') -Recurse -File | Where-Object { $_.LastWriteTimeUtc -gt $jarTime } | Select-Object -First 1; if ($newer) { exit 10 } else { exit 0 }"
  if errorlevel 10 (
    set "NEED_BUILD=1"
  ) else if errorlevel 1 (
    echo [WARN] Could not verify backend jar freshness. Rebuilding it for safety...
    set "NEED_BUILD=1"
  )
)

if "%NEED_BUILD%"=="1" (
  echo [INFO] Backend jar is missing or older than the current source. Building it now...
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
echo [WAIT] The browser will open after the service is ready.
echo [DATA] %APP_ROOT%\data\attendance.db
echo [URL] http://127.0.0.1:%APP_PORT%
echo.
echo Close this window or press Ctrl+C to stop the service.

set "WAIT_BROWSER_ARG="
if defined CA_START_NO_BROWSER set "WAIT_BROWSER_ARG=-NoBrowser"
start "" /b powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\wait-open-service.ps1" -Address "%APP_HOST%" -Port "%APP_PORT%" %WAIT_BROWSER_ARG%

java -jar "%JAR%" --server.address="%APP_HOST%" --server.port="%APP_PORT%"
set "BACKEND_EXIT=%ERRORLEVEL%"

echo.
if "%BACKEND_EXIT%"=="0" (
  echo [STOP] Local service stopped normally.
) else (
  echo [ERROR] Local service exited with code %BACKEND_EXIT%.
)
if defined CA_START_NO_PAUSE exit /b %BACKEND_EXIT%
pause
exit /b %BACKEND_EXIT%

@echo off
chcp 65001 >nul
setlocal EnableExtensions

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "ROOT_DIR=%%~fI"
set "SCHEMA_SQL=%SCRIPT_DIR%schema.sql"
set "SEED_SQL=%SCRIPT_DIR%seed.sql"

if exist "%ROOT_DIR%\local-config.bat" (
  call "%ROOT_DIR%\local-config.bat"
) else (
  echo [INFO] local-config.bat not found. Default database settings will be used.
)

if not defined DB_HOST set "DB_HOST=127.0.0.1"
if not defined DB_PORT set "DB_PORT=3306"
if not defined DB_USER set "DB_USER=root"
if not defined MYSQL_CLIENT set "MYSQL_CLIENT=mysql"

if not exist "%SCHEMA_SQL%" (
  echo [ERROR] schema.sql not found:
  echo %SCHEMA_SQL%
  if not "%NO_PAUSE%"=="1" pause
  exit /b 1
)

if not exist "%SEED_SQL%" (
  echo [ERROR] seed.sql not found:
  echo %SEED_SQL%
  if not "%NO_PAUSE%"=="1" pause
  exit /b 1
)

"%MYSQL_CLIENT%" --version >nul 2>nul
if errorlevel 1 (
  echo [ERROR] mysql client not found.
  echo Add MySQL bin to PATH, or add this line to local-config.bat:
  echo set "MYSQL_CLIENT=full-path-to-mysql.exe"
  if not "%NO_PAUSE%"=="1" pause
  exit /b 1
)

if "%DB_PASSWORD%"=="change-me" set "DB_PASSWORD="
if not defined DB_PASSWORD (
  echo [INFO] DB_PASSWORD is empty.
  set /p "DB_PASSWORD=Input MySQL password, leave empty if no password: "
)

if defined DB_PASSWORD set "MYSQL_PWD=%DB_PASSWORD%"

echo.
echo ========================================
echo  CA Attendance System - Database Init
echo ========================================
echo [DIR] %SCRIPT_DIR%
echo [DB] %DB_USER%@%DB_HOST%:%DB_PORT%
echo.

echo [1/2] Importing schema.sql ...
"%MYSQL_CLIENT%" -h "%DB_HOST%" -P "%DB_PORT%" -u "%DB_USER%" --default-character-set=utf8mb4 < "%SCHEMA_SQL%"
if errorlevel 1 (
  echo [ERROR] Failed to import schema.sql. Check MySQL password, port, and permissions.
  if not "%NO_PAUSE%"=="1" pause
  exit /b 1
)

echo [2/2] Importing seed.sql ...
"%MYSQL_CLIENT%" -h "%DB_HOST%" -P "%DB_PORT%" -u "%DB_USER%" --default-character-set=utf8mb4 < "%SEED_SQL%"
if errorlevel 1 (
  echo [ERROR] Failed to import seed.sql. Check whether ca_attendance was created.
  if not "%NO_PAUSE%"=="1" pause
  exit /b 1
)

echo.
echo [DONE] Database init finished.
echo You can run start.bat now.
if not "%NO_PAUSE%"=="1" pause

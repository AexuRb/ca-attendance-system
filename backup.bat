@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

call "%~dp0local-config.bat"

set "BACKUP_DIR=%~dp0backups"
if not exist "%BACKUP_DIR%" mkdir "%BACKUP_DIR%"

echo.
echo ========================================
echo  计算机协会签到签退系统 - 数据库备份
echo ========================================
echo.

where mysqldump >nul 2>nul
if errorlevel 1 (
  echo [错误] 未找到 mysqldump。请确认 MySQL bin 目录已加入 PATH。
  pause
  exit /b 1
)

for /f %%i in ('powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-Date -Format yyyyMMdd_HHmmss"') do set "TS=%%i"
set "OUT_FILE=%BACKUP_DIR%\%DB_NAME%_%TS%.sql"
set "MYSQL_PWD=%DB_PASSWORD%"

echo [备份] 正在导出数据库 %DB_NAME% ...
mysqldump --host=%DB_HOST% --port=%DB_PORT% --user=%DB_USER% --default-character-set=utf8mb4 --single-transaction --set-gtid-purged=OFF --routines --triggers --events --databases %DB_NAME% --result-file="%OUT_FILE%"
if errorlevel 1 (
  echo [错误] 数据库备份失败。
  pause
  exit /b 1
)

echo [完成] 备份文件已生成：
echo %OUT_FILE%
pause

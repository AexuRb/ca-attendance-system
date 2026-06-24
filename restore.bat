@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

call "%~dp0local-config.bat"

echo.
echo ========================================
echo  计算机协会签到签退系统 - 数据库恢复
echo ========================================
echo.

where mysql >nul 2>nul
if errorlevel 1 (
  echo [错误] 未找到 mysql。请确认 MySQL bin 目录已加入 PATH。
  pause
  exit /b 1
)

set "BACKUP_FILE=%~1"
if "%BACKUP_FILE%"=="" (
  echo 可以把 .sql 备份文件拖到本脚本上，或在下方输入文件路径。
  set /p "BACKUP_FILE=请输入要恢复的 .sql 文件路径："
)
set "BACKUP_FILE=%BACKUP_FILE:"=%"

if not exist "%BACKUP_FILE%" (
  echo [错误] 备份文件不存在：
  echo %BACKUP_FILE%
  pause
  exit /b 1
)

echo [注意] 即将把备份文件恢复到本地 MySQL。
echo [文件] %BACKUP_FILE%
echo [数据库] %DB_NAME%
echo [建议] 恢复前请先运行 backup.bat，给当前数据再留一份备份。
choice /M "确认继续恢复"
if errorlevel 2 (
  echo 已取消。
  pause
  exit /b 0
)

set "MYSQL_PWD=%DB_PASSWORD%"
echo [准备] 正在确保数据库存在...
mysql --host=%DB_HOST% --port=%DB_PORT% --user=%DB_USER% --default-character-set=utf8mb4 --execute="CREATE DATABASE IF NOT EXISTS %DB_NAME% CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
if errorlevel 1 (
  echo [错误] 创建或检查数据库失败。
  pause
  exit /b 1
)

echo [恢复] 正在导入备份...
mysql --host=%DB_HOST% --port=%DB_PORT% --user=%DB_USER% --default-character-set=utf8mb4 %DB_NAME% < "%BACKUP_FILE%"
if errorlevel 1 (
  echo [错误] 数据库恢复失败。
  pause
  exit /b 1
)

echo [完成] 数据库恢复完成。
pause

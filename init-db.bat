@echo off
chcp 65001 >nul
setlocal EnableExtensions

call "%~dp0database\init-db.bat"
exit /b %ERRORLEVEL%

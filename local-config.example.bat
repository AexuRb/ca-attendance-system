@echo off
rem Copy this file to local-config.bat, then edit the values for your machine.

rem The system is intended to run on this offline computer only.
set "APP_HOST=127.0.0.1"
set "APP_PORT=8080"

set "DB_HOST=127.0.0.1"
set "DB_PORT=3306"
set "DB_NAME=ca_attendance"
set "DB_USER=root"
set "DB_PASSWORD=change-me"

rem Optional: set this if mysql.exe is not in PATH.
rem set "MYSQL_CLIENT=full-path-to-mysql.exe"

set "INITIAL_ADMIN_ACCOUNT=cugbcacyh"
set "INITIAL_ADMIN_PASSWORD=change-me"

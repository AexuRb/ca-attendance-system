param(
  [string]$MysqlClient = "mysql",
  [string]$DbHost = "127.0.0.1",
  [int]$DbPort = 3306,
  [string]$DbUser = "root",
  [string]$DbName = "ca_attendance"
)

$requiredTables = @(
  "users",
  "duty_weekday_settings",
  "attendance_records",
  "operation_logs",
  "app_settings"
)

try {
  $tables = & $MysqlClient -h $DbHost -P $DbPort -u $DbUser -N -B $DbName -e "SHOW TABLES;" 2>$null
} catch {
  exit 2
}

if ($LASTEXITCODE -ne 0) {
  exit 2
}

$missing = @($requiredTables | Where-Object { $tables -notcontains $_ })
if ($missing.Count -gt 0) {
  Write-Output ("Missing tables: " + ($missing -join ", "))
  exit 1
}

exit 0

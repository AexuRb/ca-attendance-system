[CmdletBinding()]
param(
    [switch]$KeepData
)

$ErrorActionPreference = 'Stop'
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$desktopRoot = Join-Path $repoRoot 'desktop'
$electron = Join-Path $desktopRoot 'node_modules\electron\dist\electron.exe'
$backendJar = Join-Path $repoRoot 'backend\target\attendance-backend.jar'
$sessionRoot = Join-Path $env:TEMP ("ca-attendance-desktop-stability-{0}" -f [guid]::NewGuid().ToString('N'))
$startedProcesses = [System.Collections.Generic.List[System.Diagnostics.Process]]::new()
$results = [System.Collections.Generic.List[object]]::new()
$smokeEnvironmentNames = @(
    'CA_ATTENDANCE_SMOKE_TRAY_MS',
    'CA_ATTENDANCE_SMOKE_BACKEND_CRASH_MS',
    'CA_ATTENDANCE_SMOKE_EXIT_MS'
)

function Assert-True([bool]$condition, [string]$message) {
    if (-not $condition) {
        throw $message
    }
}

function Assert-PortsAvailable {
    foreach ($port in 8080, 8081) {
        $listener = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
        Assert-True (-not $listener) "测试前端口 $port 已被占用"
    }
}

function Wait-PortsReleased([int]$timeoutSeconds = 12) {
    $deadline = (Get-Date).AddSeconds($timeoutSeconds)
    do {
        $occupied = @(
            foreach ($port in 8080, 8081) {
                Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
            }
        )
        if ($occupied.Count -eq 0) {
            return
        }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $deadline)
    throw '桌面进程退出后 8080 或 8081 仍未释放'
}

function Wait-ApplicationHealth(
    [System.Diagnostics.Process]$process,
    [int]$timeoutSeconds = 25
) {
    $deadline = (Get-Date).AddSeconds($timeoutSeconds)
    do {
        if ($process.HasExited) {
            throw "桌面进程在服务就绪前退出，退出码 $($process.ExitCode)"
        }
        try {
            $health = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/health' -TimeoutSec 1
            if ($health.status -eq 'ok' -and
                $health.application -eq 'ca-attendance-system' -and
                $health.databaseType -eq 'SQLite') {
                return $health
            }
        } catch {
            # The backend is still starting.
        }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $deadline)
    throw '桌面后端未在限定时间内就绪'
}

function Start-IsolatedDesktop(
    [string]$appRoot,
    [string]$userData,
    [hashtable]$smoke = @{}
) {
    New-Item -ItemType Directory -Force -Path $appRoot, $userData | Out-Null
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $electron
    $startInfo.WorkingDirectory = $desktopRoot
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.ArgumentList.Add('.')
    $startInfo.ArgumentList.Add("--user-data-dir=$userData")
    $startInfo.Environment['CA_ATTENDANCE_ROOT'] = $appRoot
    foreach ($name in $smokeEnvironmentNames) {
        [void]$startInfo.Environment.Remove($name)
    }
    foreach ($entry in $smoke.GetEnumerator()) {
        $startInfo.Environment[$entry.Key] = [string]$entry.Value
    }

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    Assert-True $process.Start() '无法启动 Electron 测试进程'
    $startedProcesses.Add($process)
    return $process
}

function Wait-DesktopExit(
    [System.Diagnostics.Process]$process,
    [int]$timeoutSeconds = 35
) {
    Assert-True $process.WaitForExit($timeoutSeconds * 1000) '桌面测试进程未按时退出'
    $process.Refresh()
    Assert-True ($process.ExitCode -eq 0) "桌面测试进程退出码为 $($process.ExitCode)"
}

function Get-DesktopLog([string]$appRoot) {
    $path = Join-Path $appRoot 'logs\desktop.log'
    Assert-True (Test-Path -LiteralPath $path -PathType Leaf) "缺少桌面日志：$path"
    return Get-Content -LiteralPath $path -Raw
}

function Assert-StorageLayout([string]$appRoot) {
    foreach ($directory in 'data', 'backups', 'exports', 'logs') {
        Assert-True (Test-Path -LiteralPath (Join-Path $appRoot $directory) -PathType Container) `
            "缺少持久化目录：$directory"
    }
    Assert-True (Test-Path -LiteralPath (Join-Path $appRoot 'data\attendance.db') -PathType Leaf) `
        '桌面端没有创建 SQLite 数据库'
}

function Test-TrayLifecycle {
    Write-Host '[1/4] Testing tray hide, restore and full exit...'
    $appRoot = Join-Path $sessionRoot 'roots\tray'
    $userData = Join-Path $sessionRoot 'user-data\tray'
    $process = Start-IsolatedDesktop $appRoot $userData @{
        CA_ATTENDANCE_SMOKE_TRAY_MS = 1800
    }
    Wait-DesktopExit $process
    Wait-PortsReleased
    Assert-StorageLayout $appRoot
    $log = Get-DesktopLog $appRoot
    Assert-True $log.Contains('main window hidden to tray') '窗口没有成功隐藏到托盘'
    Assert-True $log.Contains('tray smoke-test hidden=true tray=true restored=true') '托盘恢复烟测失败'
    Assert-True $log.Contains('backend exited code=0') '完全退出时后端没有正常结束'
    $results.Add([pscustomobject]@{ Scenario = '托盘隐藏、恢复与完全退出'; Result = '通过' })
}

function Test-SingleInstanceAndBinding {
    Write-Host '[2/4] Testing single instance and loopback bindings...'
    $appRoot = Join-Path $sessionRoot 'roots\single-instance'
    $userData = Join-Path $sessionRoot 'user-data\single-instance'
    $primary = Start-IsolatedDesktop $appRoot $userData @{
        CA_ATTENDANCE_SMOKE_EXIT_MS = 9000
    }
    [void](Wait-ApplicationHealth $primary)

    foreach ($port in 8080, 8081) {
        $listeners = @(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction Stop)
        Assert-True ($listeners.Count -ge 1) "端口 $port 没有监听"
        Assert-True (($listeners | Where-Object LocalAddress -ne '127.0.0.1').Count -eq 0) `
            "端口 $port 未严格绑定到 127.0.0.1"
    }

    $secondary = Start-IsolatedDesktop $appRoot $userData
    Wait-DesktopExit $secondary 8
    Start-Sleep -Milliseconds 350
    $log = Get-DesktopLog $appRoot
    $startCount = ([regex]::Matches($log, 'desktop starting version=')).Count
    Assert-True ($startCount -eq 1) '第二个实例重复初始化了桌面生命周期'

    Wait-DesktopExit $primary 20
    Wait-PortsReleased
    $results.Add([pscustomobject]@{ Scenario = '单实例与回环端口绑定'; Result = '通过' })
}

function Test-UnexpectedBackendExit {
    Write-Host '[3/4] Testing unexpected backend exit detection...'
    $appRoot = Join-Path $sessionRoot 'roots\backend-crash'
    $userData = Join-Path $sessionRoot 'user-data\backend-crash'
    $process = Start-IsolatedDesktop $appRoot $userData @{
        CA_ATTENDANCE_SMOKE_BACKEND_CRASH_MS = 1800
    }
    Wait-DesktopExit $process
    Wait-PortsReleased
    $log = Get-DesktopLog $appRoot
    Assert-True $log.Contains('backend crash smoke-test terminating pid=') '异常停止烟测没有终止测试后端'
    Assert-True $log.Contains('backend crash smoke-test detected the unexpected exit') '桌面端没有识别后端异常退出'
    $results.Add([pscustomobject]@{ Scenario = '后端异常退出检测与日志'; Result = '通过' })
}

function Test-DirectoryMigration {
    Write-Host '[4/4] Testing whole-directory migration...'
    $sourceRoot = Join-Path $sessionRoot 'roots\migration-source'
    $targetRoot = Join-Path $sessionRoot 'roots\migration-target'
    $sourceUserData = Join-Path $sessionRoot 'user-data\migration-source'
    $targetUserData = Join-Path $sessionRoot 'user-data\migration-target'

    $source = Start-IsolatedDesktop $sourceRoot $sourceUserData @{
        CA_ATTENDANCE_SMOKE_EXIT_MS = 1500
    }
    Wait-DesktopExit $source
    Wait-PortsReleased
    Assert-StorageLayout $sourceRoot
    $marker = Join-Path $sourceRoot 'exports\migration-marker.txt'
    Set-Content -LiteralPath $marker -Value 'desktop migration smoke test' -Encoding ascii

    Copy-Item -LiteralPath $sourceRoot -Destination $targetRoot -Recurse
    Assert-True (Test-Path -LiteralPath (Join-Path $targetRoot 'exports\migration-marker.txt')) `
        '复制后的目录缺少迁移标记文件'

    $target = Start-IsolatedDesktop $targetRoot $targetUserData @{
        CA_ATTENDANCE_SMOKE_EXIT_MS = 2200
    }
    [void](Wait-ApplicationHealth $target)
    Wait-DesktopExit $target
    Wait-PortsReleased
    Assert-StorageLayout $targetRoot
    $targetLog = Get-DesktopLog $targetRoot
    Assert-True $targetLog.Contains("root=$targetRoot") '迁移后应用没有使用新的根目录'
    $results.Add([pscustomobject]@{ Scenario = '整目录复制与迁移后启动'; Result = '通过' })
}

function Remove-IsolatedSession {
    if (-not (Test-Path -LiteralPath $sessionRoot)) {
        return
    }
    $resolvedSession = [System.IO.Path]::GetFullPath($sessionRoot)
    $resolvedTemp = [System.IO.Path]::GetFullPath($env:TEMP).TrimEnd('\') + '\'
    $leaf = Split-Path -Leaf $resolvedSession
    Assert-True ($resolvedSession.StartsWith($resolvedTemp, [System.StringComparison]::OrdinalIgnoreCase)) `
        '拒绝清理不在临时目录中的路径'
    Assert-True ($leaf.StartsWith('ca-attendance-desktop-stability-')) '拒绝清理名称不匹配的临时目录'
    foreach ($attempt in 1..5) {
        try {
            Remove-Item -LiteralPath $resolvedSession -Recurse -Force
            return
        } catch {
            if ($attempt -eq 5) {
                throw
            }
            Start-Sleep -Milliseconds 300
        }
    }
}

if (-not (Test-Path -LiteralPath $electron -PathType Leaf)) {
    throw "缺少 Electron 运行时，请先在 desktop 目录执行 npm ci：$electron"
}
if (-not (Test-Path -LiteralPath $backendJar -PathType Leaf)) {
    throw "缺少桌面烟测所需的后端 JAR：$backendJar"
}

New-Item -ItemType Directory -Force -Path $sessionRoot | Out-Null
Write-Host "Desktop stability test root: $sessionRoot"

try {
    Assert-PortsAvailable
    Test-TrayLifecycle
    Test-SingleInstanceAndBinding
    Test-UnexpectedBackendExit
    Test-DirectoryMigration

    Write-Host ''
    Write-Host 'Desktop stability scenarios:'
    $results | Format-Table -AutoSize
    Write-Host 'All desktop stability scenarios passed.'
} finally {
    foreach ($process in $startedProcesses) {
        if (-not $process.HasExited) {
            $process.Kill($true)
            [void]$process.WaitForExit(5000)
        }
        $process.Dispose()
    }
    try {
        Wait-PortsReleased 8
    } catch {
        Write-Warning $_.Exception.Message
    }
    if ($KeepData) {
        Write-Host "Kept desktop stability data: $sessionRoot"
    } else {
        Remove-IsolatedSession
    }
}

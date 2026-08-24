[CmdletBinding()]
param(
    [int]$Port = 18080,
    [int]$RemotePort = 18081,
    [string]$AdminStudentNo = $env:CA_TEST_ADMIN_STUDENT_NO,
    [string]$AdminPassword = $env:CA_TEST_ADMIN_PASSWORD,
    [switch]$IncludeUi
)

$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($AdminStudentNo)) {
    $AdminStudentNo = "9{0:D9}" -f (Get-Random -Minimum 0 -Maximum 1000000000)
}
if ([string]::IsNullOrWhiteSpace($AdminPassword)) {
    $AdminPassword = "Aa1!" + [Guid]::NewGuid().ToString("N")
}

$workspace = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$jar = Join-Path $workspace "backend\target\attendance-backend.jar"
if (-not (Test-Path -LiteralPath $jar)) {
    throw "未找到后端 JAR，请先在 backend 目录执行 mvn package。"
}

$separator = [IO.Path]::DirectorySeparatorChar
$tempBase = [IO.Path]::GetFullPath($env:TEMP).TrimEnd([char[]]@($separator)) + $separator
$smokeRoot = [IO.Path]::GetFullPath(
    (Join-Path $env:TEMP ("ca-attendance-smoke-" + [Guid]::NewGuid().ToString("N")))
)
if (-not $smokeRoot.StartsWith($tempBase, [StringComparison]::OrdinalIgnoreCase)) {
    throw "临时目录不在系统 TEMP 内：$smokeRoot"
}

New-Item -ItemType Directory -Path $smokeRoot -Force | Out-Null
$stdout = Join-Path $smokeRoot "backend.stdout.log"
$stderr = Join-Path $smokeRoot "backend.stderr.log"
$previousRoot = $env:APP_ROOT
$previousPort = $env:APP_PORT
$previousRemotePort = $env:APP_REMOTE_PORT
$backendProcess = $null
$backendProcessId = 0

try {
    $env:APP_ROOT = $smokeRoot
    $env:APP_PORT = [string]$Port
    $env:APP_REMOTE_PORT = [string]$RemotePort
    $backendProcess = Start-Process `
        -FilePath "java" `
        -ArgumentList @("-jar", $jar) `
        -PassThru `
        -WindowStyle Hidden `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr
    $backendProcessId = $backendProcess.Id

    $baseUrl = "http://127.0.0.1:$Port"
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        try {
            $health = Invoke-RestMethod -Uri "$baseUrl/api/health" -TimeoutSec 2
            if ($health.status -eq "ok") {
                $ready = $true
                break
            }
            Start-Sleep -Milliseconds 500
        } catch {
            Start-Sleep -Milliseconds 500
        }
    }
    if (-not $ready) {
        Get-Content -LiteralPath $stdout -Tail 80 -ErrorAction SilentlyContinue
        Get-Content -LiteralPath $stderr -Tail 80 -ErrorAction SilentlyContinue
        throw "隔离测试服务启动超时。"
    }

    $setupBody = @{
        account = $AdminStudentNo
        name = "隔离测试管理员"
        password = $AdminPassword
    } | ConvertTo-Json
    Invoke-RestMethod `
        -Uri "$baseUrl/api/setup/initialize" `
        -Method Post `
        -ContentType "application/json; charset=utf-8" `
        -Body $setupBody | Out-Null

    & (Join-Path $PSScriptRoot "full-smoke-test.ps1") `
        -BaseUrl $baseUrl `
        -AdminStudentNo $AdminStudentNo `
        -AdminPassword $AdminPassword
    if ($IncludeUi) {
        & python (Join-Path $PSScriptRoot "ui-core-workflows-test.py") `
            --base-url $baseUrl `
            --admin-student-no $AdminStudentNo `
            --admin-password $AdminPassword `
            --screenshot-dir (Join-Path $smokeRoot "ui-check")
        if ($LASTEXITCODE -ne 0) {
            throw "核心页面浏览器验收失败，退出码：$LASTEXITCODE"
        }
    }
} catch {
    Write-Host "隔离测试失败，后端日志："
    Get-Content -LiteralPath $stdout -Tail 100 -ErrorAction SilentlyContinue
    Get-Content -LiteralPath $stderr -Tail 100 -ErrorAction SilentlyContinue
    throw
} finally {
    if ($backendProcessId -gt 0 -and (Get-Process -Id $backendProcessId -ErrorAction SilentlyContinue)) {
        Stop-Process -Id $backendProcessId -Force -ErrorAction SilentlyContinue
        Wait-Process -Id $backendProcessId -Timeout 5 -ErrorAction SilentlyContinue
    }
    $listenerOwners = @(
        Get-NetTCPConnection -State Listen -LocalPort $Port, $RemotePort -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty OwningProcess -Unique
    )
    foreach ($listenerOwner in $listenerOwners) {
        $listenerProcess = Get-CimInstance Win32_Process -Filter "ProcessId = $listenerOwner"
        if (
            $listenerProcess.Name -eq "java.exe" -and
            $listenerProcess.CommandLine -like "*$jar*"
        ) {
            Stop-Process -Id $listenerOwner -Force -ErrorAction SilentlyContinue
            Wait-Process -Id $listenerOwner -Timeout 5 -ErrorAction SilentlyContinue
        } else {
            Write-Warning "未停止占用隔离测试端口的未知进程：$listenerOwner"
        }
    }
    if ($backendProcess) {
        $backendProcess.Dispose()
    }
    $env:APP_ROOT = $previousRoot
    $env:APP_PORT = $previousPort
    $env:APP_REMOTE_PORT = $previousRemotePort

    if (Test-Path -LiteralPath $smokeRoot) {
        $verifiedRoot = [IO.Path]::GetFullPath($smokeRoot)
        if ($verifiedRoot.StartsWith($tempBase, [StringComparison]::OrdinalIgnoreCase)) {
            for ($attempt = 0; $attempt -lt 5; $attempt++) {
                try {
                    Remove-Item -LiteralPath $verifiedRoot -Recurse -Force -ErrorAction Stop
                    break
                } catch {
                    Start-Sleep -Milliseconds 250
                }
            }
            if (Test-Path -LiteralPath $verifiedRoot) {
                Write-Warning "隔离测试临时目录暂未删除：$verifiedRoot"
            }
        }
    }
}

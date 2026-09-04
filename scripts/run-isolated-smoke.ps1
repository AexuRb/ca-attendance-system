[CmdletBinding()]
param(
    [int]$Port = 18080,
    [int]$RemotePort = 18081,
    [string]$AdminStudentNo = $env:CA_TEST_ADMIN_STUDENT_NO,
    [string]$AdminPassword = $env:CA_TEST_ADMIN_PASSWORD,
    [switch]$IncludeRemoteAccess,
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

function Invoke-IsolatedRequest {
    param(
        [int]$TargetPort,
        [string]$Method,
        [string]$Path,
        $Body = $null,
        [string]$Token = $null
    )

    $parameters = @{
        Uri = "http://127.0.0.1:$TargetPort$Path"
        Method = $Method
        SkipHttpErrorCheck = $true
        TimeoutSec = 5
        Headers = @{}
    }
    if ($Token) {
        $parameters.Headers.Authorization = "Bearer $Token"
    }
    if ($null -ne $Body) {
        $parameters.ContentType = "application/json; charset=utf-8"
        $parameters.Body = $Body | ConvertTo-Json -Depth 10
    }
    Invoke-WebRequest @parameters
}

function Assert-IsolatedStatus {
    param($Response, [int]$Expected, [string]$Label)
    if ([int]$Response.StatusCode -ne $Expected) {
        throw "$Label 期望状态 $Expected，实际 $($Response.StatusCode)：$($Response.Content)"
    }
}

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

    if ($IncludeRemoteAccess) {
        $localFailures = @(
            1..7 | ForEach-Object {
                (Invoke-IsolatedRequest $Port POST "/api/auth/login" @{
                    studentNo = $AdminStudentNo
                    password = "invalid-password"
                }).StatusCode
            }
        )
        if (@($localFailures | Where-Object { $_ -ne 401 }).Count) {
            throw "本机登录失败不应触发限流：$($localFailures -join ',')"
        }
        Assert-IsolatedStatus `
            (Invoke-IsolatedRequest $Port POST "/api/auth/login" @{
                studentNo = $AdminStudentNo
                password = $AdminPassword
            }) 200 "本机连续失败后的正确登录"

        $localHealth = (Invoke-IsolatedRequest $Port GET "/api/health").Content | ConvertFrom-Json
        $remoteHealth = (Invoke-IsolatedRequest $RemotePort GET "/api/health").Content | ConvertFrom-Json
        if (-not $localHealth.databaseType -or $remoteHealth.PSObject.Properties.Name -contains "databaseType") {
            throw "远程健康检查信息脱敏不符合预期"
        }
        Assert-IsolatedStatus `
            (Invoke-IsolatedRequest $RemotePort GET "/api/public/attendance/lookup?query=$AdminStudentNo") `
            403 "远程签到台访问"

        $remoteLogin = Invoke-IsolatedRequest $RemotePort POST "/api/auth/login" @{
            studentNo = $AdminStudentNo
            password = $AdminPassword
        }
        Assert-IsolatedStatus $remoteLogin 200 "远程管理员登录"
        $remoteToken = ($remoteLogin.Content | ConvertFrom-Json).token
        Assert-IsolatedStatus `
            (Invoke-IsolatedRequest $RemotePort GET "/api/auth/me" -Token $remoteToken) `
            200 "远程后台接口"

        $accountFailures = @(
            1..5 | ForEach-Object {
                (Invoke-IsolatedRequest $RemotePort POST "/api/auth/login" @{
                    studentNo = $AdminStudentNo
                    password = "invalid-password"
                }).StatusCode
            }
        )
        if (@($accountFailures | Where-Object { $_ -ne 401 }).Count) {
            throw "远程账号失败序列不符合预期：$($accountFailures -join ',')"
        }
        Assert-IsolatedStatus `
            (Invoke-IsolatedRequest $RemotePort POST "/api/auth/login" @{
                studentNo = $AdminStudentNo
                password = $AdminPassword
            }) 429 "远程账号锁定"
        Assert-IsolatedStatus `
            (Invoke-IsolatedRequest $Port POST "/api/auth/login" @{
                studentNo = $AdminStudentNo
                password = $AdminPassword
            }) 200 "本机登录恢复远程账号锁"
        Assert-IsolatedStatus `
            (Invoke-IsolatedRequest $RemotePort POST "/api/auth/login" @{
                studentNo = $AdminStudentNo
                password = $AdminPassword
            }) 200 "远程账号恢复"

        1..25 | ForEach-Object {
            Assert-IsolatedStatus `
                (Invoke-IsolatedRequest $RemotePort POST "/api/auth/login" @{
                    studentNo = "unknown-$_"
                    password = "invalid-password"
                }) 401 "远程全局限流计数 $_"
        }
        Assert-IsolatedStatus `
            (Invoke-IsolatedRequest $RemotePort POST "/api/auth/login" @{
                studentNo = $AdminStudentNo
                password = $AdminPassword
            }) 429 "远程全局锁定"
        Assert-IsolatedStatus `
            (Invoke-IsolatedRequest $Port POST "/api/auth/login" @{
                studentNo = $AdminStudentNo
                password = $AdminPassword
            }) 200 "远程全局锁定期间的本机登录"
        Write-Host "REMOTE_ACCESS_ACCEPTANCE_OK local=unlimited account=5 global=30 kiosk=blocked health=sanitized"
    }

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

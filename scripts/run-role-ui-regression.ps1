[CmdletBinding()]
param(
    [int]$Port = 18082,
    [int]$RemotePort = 18083,
    [switch]$KeepData
)

$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$jar = Join-Path $repoRoot 'backend\target\attendance-backend.jar'
$python = (Get-Command python -ErrorAction Stop).Source
$runner = Join-Path $PSScriptRoot 'ui-role-regression.py'
$runRoot = Join-Path $env:TEMP ('ca-attendance-role-ui-' + [guid]::NewGuid().ToString('N'))
$stdout = Join-Path $runRoot 'backend.stdout.log'
$stderr = Join-Path $runRoot 'backend.stderr.log'
$process = $null
$backendProcessId = 0
$previousRoot = $env:APP_ROOT
$previousPort = $env:APP_PORT
$previousRemotePort = $env:APP_REMOTE_PORT

function Invoke-Json {
    param(
        [string]$BaseUrl,
        [string]$Method,
        [string]$Path,
        [object]$Body,
        [string]$Token = ''
    )
    $parameters = @{
        Uri = "$BaseUrl$Path"
        Method = $Method
        ContentType = 'application/json; charset=utf-8'
    }
    if ($null -ne $Body) {
        $parameters.Body = $Body | ConvertTo-Json -Depth 8
    }
    if ($Token) {
        $parameters.Headers = @{ Authorization = "Bearer $Token" }
    }
    Invoke-RestMethod @parameters
}

function Set-TestPassword {
    param(
        [string]$BaseUrl,
        [string]$Account,
        [string]$NewPassword
    )
    $defaultPassword = $Account.Substring($Account.Length - 6)
    $login = Invoke-Json $BaseUrl POST '/api/auth/login' @{
        studentNo = $Account
        password = $defaultPassword
    }
    Invoke-Json $BaseUrl POST '/api/auth/change-password' @{
        oldPassword = $defaultPassword
        newPassword = $NewPassword
    } $login.token | Out-Null
}

if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
    throw '未找到后端 JAR，请先在 backend 目录执行 mvn package。'
}
foreach ($candidatePort in $Port, $RemotePort) {
    if (Get-NetTCPConnection -State Listen -LocalPort $candidatePort -ErrorAction SilentlyContinue) {
        throw "隔离测试端口已被占用：$candidatePort"
    }
}

New-Item -ItemType Directory -Path $runRoot -Force | Out-Null
try {
    $env:APP_ROOT = $runRoot
    $env:APP_PORT = [string]$Port
    $env:APP_REMOTE_PORT = [string]$RemotePort
    $process = Start-Process `
        -FilePath 'java' `
        -ArgumentList @('-jar', $jar) `
        -PassThru `
        -WindowStyle Hidden `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr
    $backendProcessId = $process.Id

    $baseUrl = "http://127.0.0.1:$Port"
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        try {
            if ((Invoke-RestMethod "$baseUrl/api/health" -TimeoutSec 2).status -eq 'ok') {
                $ready = $true
                break
            }
        } catch {
            Start-Sleep -Milliseconds 500
        }
    }
    if (-not $ready) {
        throw '角色 UI 回归服务启动超时。'
    }

    $suffix = Get-Date -Format 'MMddHHmmss'
    $admin = "97$suffix"
    $president = "96$suffix"
    $minister = "95$suffix"
    $member = "94$suffix"
    $adminPassword = "Admin!$suffix"
    $presidentPassword = "President!$suffix"
    $ministerPassword = "Minister!$suffix"
    $memberPassword = "Member!$suffix"

    Invoke-Json $baseUrl POST '/api/setup/initialize' @{
        account = $admin
        name = '角色回归管理员'
        password = $adminPassword
    } | Out-Null
    $adminLogin = Invoke-Json $baseUrl POST '/api/auth/login' @{
        studentNo = $admin
        password = $adminPassword
    }
    foreach ($user in @(
        @{ studentNo = $president; name = '角色回归会长'; role = 'PRESIDENT' },
        @{ studentNo = $minister; name = '角色回归部长'; role = 'MINISTER' },
        @{ studentNo = $member; name = '角色回归成员'; role = 'MEMBER' }
    )) {
        Invoke-Json $baseUrl POST '/api/users' $user $adminLogin.token | Out-Null
    }

    $currentCheckOut = Get-Date
    $currentCheckIn = $currentCheckOut.AddMinutes(-5)
    $historicalCheckIn = $currentCheckIn.AddDays(-7)
    $historicalCheckOut = $currentCheckOut.AddDays(-7)
    foreach ($period in @(
        @{ checkIn = $currentCheckIn; checkOut = $currentCheckOut; reason = '角色回归本周记录' },
        @{ checkIn = $historicalCheckIn; checkOut = $historicalCheckOut; reason = '角色回归历史记录' }
    )) {
        Invoke-Json $baseUrl POST '/api/attendance/manual' @{
            studentNo = $minister
            checkInTime = $period.checkIn.ToString('yyyy-MM-ddTHH:mm:ss')
            checkOutTime = $period.checkOut.ToString('yyyy-MM-ddTHH:mm:ss')
            reason = $period.reason
        } $adminLogin.token | Out-Null
    }

    Set-TestPassword $baseUrl $president $presidentPassword
    Set-TestPassword $baseUrl $minister $ministerPassword
    Set-TestPassword $baseUrl $member $memberPassword

    & $python $runner `
        --base-url $baseUrl `
        --admin-account $admin `
        --admin-password $adminPassword `
        --president-account $president `
        --president-password $presidentPassword `
        --minister-account $minister `
        --minister-password $ministerPassword `
        --member-account $member `
        --member-password $memberPassword `
        --screenshot-dir (Join-Path $runRoot 'screenshots')
    if ($LASTEXITCODE -ne 0) {
        throw "角色 UI 回归失败，退出码：$LASTEXITCODE"
    }
} catch {
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
        if ($listenerProcess.Name -eq 'java.exe' -and $listenerProcess.CommandLine -like "*$jar*") {
            Stop-Process -Id $listenerOwner -Force -ErrorAction SilentlyContinue
            Wait-Process -Id $listenerOwner -Timeout 5 -ErrorAction SilentlyContinue
        } else {
            Write-Warning "未停止占用隔离测试端口的未知进程：$listenerOwner"
        }
    }
    if ($process) {
        $process.Dispose()
    }
    $env:APP_ROOT = $previousRoot
    $env:APP_PORT = $previousPort
    $env:APP_REMOTE_PORT = $previousRemotePort
    if (-not $KeepData -and (Test-Path -LiteralPath $runRoot)) {
        Remove-Item -LiteralPath $runRoot -Recurse -Force -ErrorAction SilentlyContinue
    } elseif ($KeepData) {
        Write-Host "角色 UI 回归数据保留在：$runRoot"
    }
}

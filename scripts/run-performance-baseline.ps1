[CmdletBinding()]
param(
    [int]$Port = 18080,
    [int]$RemotePort = 18081,
    [string]$AdminStudentNo = "1004231224",
    [string]$AdminPassword = "123456",
    [int]$Iterations = 12,
    [int]$BrowserIterations = 3,
    [string]$OutputPath = "",
    [switch]$KeepData
)

$ErrorActionPreference = "Stop"
$workspace = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$backendDirectory = Join-Path $workspace "backend"
$performanceTool = Join-Path $PSScriptRoot "performance_baseline.py"
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
if (-not $OutputPath) {
    $OutputPath = Join-Path $env:TEMP "ca-attendance-performance-$timestamp.json"
}
$OutputPath = [IO.Path]::GetFullPath($OutputPath)

$separator = [IO.Path]::DirectorySeparatorChar
$tempBase = [IO.Path]::GetFullPath($env:TEMP).TrimEnd([char[]]@($separator)) + $separator
$runRoot = [IO.Path]::GetFullPath(
    (Join-Path $env:TEMP ("ca-attendance-performance-" + [Guid]::NewGuid().ToString("N")))
)
if (-not $runRoot.StartsWith($tempBase, [StringComparison]::OrdinalIgnoreCase)) {
    throw "临时目录不在系统 TEMP 内：$runRoot"
}

New-Item -ItemType Directory -Path $runRoot -Force | Out-Null
$stdout = Join-Path $runRoot "backend.stdout.log"
$stderr = Join-Path $runRoot "backend.stderr.log"
$seedOutput = Join-Path $runRoot "seed.json"
$inspectOutput = Join-Path $runRoot "database.json"
$apiOutput = Join-Path $runRoot "api.json"
$browserOutput = Join-Path $runRoot "browser.json"
$validationOutput = Join-Path $runRoot "large-validation.json"
$visualOutput = Join-Path $runRoot "large-visual.json"
$screenshotDirectory = Join-Path `
    (Split-Path -Parent $OutputPath) `
    (([IO.Path]::GetFileNameWithoutExtension($OutputPath)) + "-screenshots")
$database = Join-Path $runRoot "data\attendance.db"
$baseUrl = "http://127.0.0.1:$Port"
$maven = (Get-Command mvn).Source
$python = (Get-Command python).Source

$previousRoot = $env:APP_ROOT
$previousPort = $env:APP_PORT
$previousRemotePort = $env:APP_REMOTE_PORT
$backendProcess = $null

function Invoke-PerformanceTool {
    param([string[]]$Arguments)
    & $python $performanceTool @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "性能工具执行失败，退出码：$LASTEXITCODE"
    }
}

try {
    $env:APP_ROOT = $runRoot
    $env:APP_PORT = [string]$Port
    $env:APP_REMOTE_PORT = [string]$RemotePort
    $backendProcess = Start-Process `
        -FilePath $maven `
        -ArgumentList @("-q", "-DskipTests", "spring-boot:run") `
        -WorkingDirectory $backendDirectory `
        -PassThru `
        -WindowStyle Hidden `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr

    $ready = $false
    for ($attempt = 0; $attempt -lt 120; $attempt++) {
        try {
            $health = Invoke-RestMethod -Uri "$baseUrl/api/health" -TimeoutSec 2
            if ($health.status -eq "ok") {
                $ready = $true
                break
            }
        } catch {
            # Startup is expected to refuse connections until Spring is ready.
        }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) {
        Get-Content -LiteralPath $stdout -Tail 80 -ErrorAction SilentlyContinue
        Get-Content -LiteralPath $stderr -Tail 80 -ErrorAction SilentlyContinue
        throw "隔离性能测试服务启动超时。"
    }

    $setupBody = @{
        account = $AdminStudentNo
        name = "性能测试管理员"
        password = $AdminPassword
    } | ConvertTo-Json
    Invoke-RestMethod `
        -Uri "$baseUrl/api/setup/initialize" `
        -Method Post `
        -ContentType "application/json; charset=utf-8" `
        -Body $setupBody | Out-Null

    Invoke-PerformanceTool @(
        "seed", "--database", $database,
        "--users", "500",
        "--attendance", "10000",
        "--trainings", "200",
        "--participants-per-training", "10",
        "--training-participant-counts", "0,1,30,72,3000",
        "--repairs", "1100",
        "--repair-status-counts", "8,1050,42",
        "--logs", "5000",
        "--output", $seedOutput
    )
    Invoke-PerformanceTool @(
        "inspect", "--database", $database,
        "--output", $inspectOutput
    )

    $seed = Get-Content -LiteralPath $seedOutput -Raw | ConvertFrom-Json
    $toDate = [DateTime]::ParseExact($seed.date_range[1], "yyyy-MM-dd", $null)
    $fromDate = $toDate.AddDays(-364).ToString("yyyy-MM-dd")
    Invoke-PerformanceTool @(
        "benchmark", "--base-url", $baseUrl,
        "--student-no", $AdminStudentNo,
        "--password", $AdminPassword,
        "--iterations", [string]$Iterations,
        "--from-date", $fromDate,
        "--to-date", $toDate.ToString("yyyy-MM-dd"),
        "--output", $apiOutput
    )
    Invoke-PerformanceTool @(
        "browser", "--base-url", $baseUrl,
        "--student-no", $AdminStudentNo,
        "--password", $AdminPassword,
        "--iterations", [string]$BrowserIterations,
        "--output", $browserOutput
    )
    & $python (Join-Path $PSScriptRoot "large_dataset_visual.py") `
        --base-url $baseUrl `
        --student-no $AdminStudentNo `
        --password $AdminPassword `
        --from-date $fromDate `
        --to-date $toDate.ToString("yyyy-MM-dd") `
        --screenshots $screenshotDirectory `
        --output $visualOutput
    if ($LASTEXITCODE -ne 0) {
        throw "大数据视觉验收失败，退出码：$LASTEXITCODE"
    }
    & $python (Join-Path $PSScriptRoot "large_dataset_validation.py") `
        --base-url $baseUrl `
        --student-no $AdminStudentNo `
        --password $AdminPassword `
        --from-date $fromDate `
        --to-date $toDate.ToString("yyyy-MM-dd") `
        --output $validationOutput
    if ($LASTEXITCODE -ne 0) {
        throw "大数据功能验收失败，退出码：$LASTEXITCODE"
    }

    $listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop |
        Select-Object -First 1
    $javaProcess = Get-Process -Id $listener.OwningProcess
    $assets = Get-ChildItem `
        -LiteralPath (Join-Path $backendDirectory "src\main\resources\static\assets") `
        -File `
        -ErrorAction SilentlyContinue
    $backupFiles = Get-ChildItem -LiteralPath (Join-Path $runRoot "backups") `
        -Filter "*.zip" -Recurse -File -ErrorAction SilentlyContinue

    $report = [ordered]@{
        generatedAt = (Get-Date).ToString("s")
        isolatedRoot = if ($KeepData) { $runRoot } else { "removed after run" }
        scale = $seed.counts
        seed = $seed
        database = Get-Content -LiteralPath $inspectOutput -Raw | ConvertFrom-Json
        api = Get-Content -LiteralPath $apiOutput -Raw | ConvertFrom-Json
        browser = Get-Content -LiteralPath $browserOutput -Raw | ConvertFrom-Json
        visual = Get-Content -LiteralPath $visualOutput -Raw | ConvertFrom-Json
        validation = Get-Content -LiteralPath $validationOutput -Raw | ConvertFrom-Json
        process = [ordered]@{
            workingSetBytes = $javaProcess.WorkingSet64
            privateMemoryBytes = $javaProcess.PrivateMemorySize64
        }
        artifacts = [ordered]@{
            javascriptBytes = ($assets | Where-Object Extension -eq ".js" | Measure-Object Length -Sum).Sum
            cssBytes = ($assets | Where-Object Extension -eq ".css" | Measure-Object Length -Sum).Sum
            backupBytes = ($backupFiles | Measure-Object Length -Sum).Sum
        }
    }
    $outputDirectory = Split-Path -Parent $OutputPath
    if ($outputDirectory) {
        New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
    }
    $report | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $OutputPath -Encoding utf8
    Write-Host "性能基线完成：$OutputPath"
} catch {
    Write-Host "性能基线失败，后端日志："
    Get-Content -LiteralPath $stdout -Tail 100 -ErrorAction SilentlyContinue
    Get-Content -LiteralPath $stderr -Tail 100 -ErrorAction SilentlyContinue
    throw
} finally {
    if ($backendProcess -and -not $backendProcess.HasExited) {
        Stop-Process -Id $backendProcess.Id -Force -ErrorAction SilentlyContinue
        Wait-Process -Id $backendProcess.Id -Timeout 5 -ErrorAction SilentlyContinue
    }
    $listenerOwners = @(
        Get-NetTCPConnection -State Listen -LocalPort $Port, $RemotePort -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty OwningProcess -Unique
    )
    foreach ($listenerOwner in $listenerOwners) {
        $listenerProcess = Get-CimInstance Win32_Process -Filter "ProcessId = $listenerOwner"
        if (
            $listenerProcess.Name -eq "java.exe" -and
            $listenerProcess.CommandLine -like "*com.ca.attendance.AttendanceApplication*"
        ) {
            Stop-Process -Id $listenerOwner -Force -ErrorAction SilentlyContinue
            Wait-Process -Id $listenerOwner -Timeout 5 -ErrorAction SilentlyContinue
        } else {
            Write-Warning "未停止占用隔离性能端口的未知进程：$listenerOwner"
        }
    }
    $env:APP_ROOT = $previousRoot
    $env:APP_PORT = $previousPort
    $env:APP_REMOTE_PORT = $previousRemotePort
    if ($backendProcess) {
        $backendProcess.Dispose()
    }

    if (-not $KeepData -and (Test-Path -LiteralPath $runRoot)) {
        $verifiedRoot = [IO.Path]::GetFullPath($runRoot)
        if ($verifiedRoot.StartsWith($tempBase, [StringComparison]::OrdinalIgnoreCase)) {
            for ($attempt = 0; $attempt -lt 8; $attempt++) {
                try {
                    Remove-Item -LiteralPath $verifiedRoot -Recurse -Force -ErrorAction Stop
                    break
                } catch {
                    Start-Sleep -Milliseconds 300
                }
            }
            if (Test-Path -LiteralPath $verifiedRoot) {
                Write-Warning "隔离性能目录暂未删除：$verifiedRoot"
            }
        }
    }
}

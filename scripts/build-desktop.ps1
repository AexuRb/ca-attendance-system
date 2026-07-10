[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$frontendRoot = Join-Path $repoRoot 'frontend'
$backendRoot = Join-Path $repoRoot 'backend'
$desktopRoot = Join-Path $repoRoot 'desktop'
$desktopRelease = Join-Path $desktopRoot 'release'
$artifactRoot = Join-Path $repoRoot 'release-artifacts'

function Invoke-Checked([string]$workingDirectory, [string]$command, [string[]]$arguments) {
    Push-Location $workingDirectory
    try {
        & $command @arguments
        if ($LASTEXITCODE -ne 0) {
            throw "$command failed with exit code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
}

function Remove-BuildDirectory([string]$candidate, [string]$allowedParent) {
    $parentFull = [System.IO.Path]::GetFullPath($allowedParent).TrimEnd('\') + '\'
    $candidateFull = [System.IO.Path]::GetFullPath($candidate)
    if (-not $candidateFull.StartsWith($parentFull, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to remove a path outside $allowedParent"
    }
    if (Test-Path -LiteralPath $candidateFull) {
        Remove-Item -LiteralPath $candidateFull -Recurse -Force
    }
}

foreach ($command in 'npm.cmd', 'mvn.cmd') {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "Required build command was not found: $command"
    }
}

& (Join-Path $PSScriptRoot 'prepare-temurin-runtime.ps1')

Write-Host 'Building frontend...'
Invoke-Checked $frontendRoot 'npm.cmd' @('ci')
Invoke-Checked $frontendRoot 'npm.cmd' @('run', 'build')

Write-Host 'Testing and packaging backend...'
Invoke-Checked $backendRoot 'mvn.cmd' @('-q', 'package')

Write-Host 'Testing desktop runtime...'
Invoke-Checked $desktopRoot 'npm.cmd' @('ci')
Invoke-Checked $desktopRoot 'npm.cmd' @('test')

Remove-BuildDirectory $desktopRelease $desktopRoot
$previousSigningSetting = $env:CSC_IDENTITY_AUTO_DISCOVERY
$env:CSC_IDENTITY_AUTO_DISCOVERY = 'false'
try {
    Write-Host 'Building unsigned Windows installer...'
    Invoke-Checked $desktopRoot 'npm.cmd' @('run', 'dist')
} finally {
    $env:CSC_IDENTITY_AUTO_DISCOVERY = $previousSigningSetting
}

$package = Get-Content -Raw -LiteralPath (Join-Path $desktopRoot 'package.json') | ConvertFrom-Json
$version = $package.version
$installer = Join-Path $desktopRelease "CA-Attendance-System-Setup-$version.exe"
$unpackedApp = Join-Path $desktopRelease 'win-unpacked'
if (-not (Test-Path -LiteralPath $installer -PathType Leaf)) {
    throw "Installer was not created: $installer"
}
if (-not (Test-Path -LiteralPath $unpackedApp -PathType Container)) {
    throw "Unpacked desktop application was not created: $unpackedApp"
}

Remove-BuildDirectory $artifactRoot $repoRoot
New-Item -ItemType Directory -Force -Path $artifactRoot | Out-Null
Copy-Item -LiteralPath $installer -Destination $artifactRoot

$portableStage = Join-Path $desktopRelease 'portable'
$portableRoot = Join-Path $portableStage 'CAAttendance'
$portableApp = Join-Path $portableRoot 'app'
Remove-BuildDirectory $portableStage $desktopRelease
New-Item -ItemType Directory -Force -Path $portableApp | Out-Null
Copy-Item -Path (Join-Path $unpackedApp '*') -Destination $portableApp -Recurse -Force
foreach ($directory in 'data', 'backups', 'exports', 'logs') {
    New-Item -ItemType Directory -Force -Path (Join-Path $portableRoot $directory) | Out-Null
}
Copy-Item -LiteralPath (Join-Path $desktopRoot 'portable\启动管理系统.bat') -Destination $portableRoot
Copy-Item -LiteralPath (Join-Path $desktopRoot 'portable\使用说明.txt') -Destination $portableRoot

$portableZip = Join-Path $artifactRoot "CA-Attendance-System-Portable-$version.zip"
Compress-Archive -LiteralPath $portableRoot -DestinationPath $portableZip -CompressionLevel Optimal

$checksumLines = Get-ChildItem -LiteralPath $artifactRoot -File |
    Sort-Object Name |
    ForEach-Object { "{0}  {1}" -f (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant(), $_.Name }
Set-Content -LiteralPath (Join-Path $artifactRoot 'SHA256SUMS.txt') -Value $checksumLines -Encoding ascii

Write-Host "Desktop release artifacts are ready: $artifactRoot"
Get-ChildItem -LiteralPath $artifactRoot -File | Select-Object Name, Length

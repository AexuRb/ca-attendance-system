[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$runtimeRoot = Join-Path $repoRoot 'runtime'
$destination = Join-Path $runtimeRoot 'temurin-21'
$downloadRoot = Join-Path $runtimeRoot 'downloads'
$archiveName = 'OpenJDK21U-jre_x64_windows_hotspot_21.0.11_10.zip'
$archivePath = Join-Path $downloadRoot $archiveName
$downloadUrl = 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.11%2B10/OpenJDK21U-jre_x64_windows_hotspot_21.0.11_10.zip'
$expectedSha256 = 'be26677aaa20b39a62edcaab4c8857a8b76673b0f45abc0b6143b142b62717e4'
$expectedVersionMarker = 'Temurin-21.0.11+10'

function Assert-PathUnderRuntime([string]$candidate) {
    $runtimeFull = [System.IO.Path]::GetFullPath($runtimeRoot).TrimEnd('\') + '\'
    $candidateFull = [System.IO.Path]::GetFullPath($candidate)
    if (-not $candidateFull.StartsWith($runtimeFull, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to modify a path outside the runtime directory: $candidateFull"
    }
    return $candidateFull
}

function Test-ExpectedRuntime([string]$path) {
    $java = Join-Path $path 'bin\java.exe'
    $release = Join-Path $path 'release'
    if (-not (Test-Path -LiteralPath $java -PathType Leaf) -or -not (Test-Path -LiteralPath $release -PathType Leaf)) {
        return $false
    }
    return (Get-Content -Raw -LiteralPath $release).Contains($expectedVersionMarker)
}

New-Item -ItemType Directory -Force -Path $runtimeRoot, $downloadRoot | Out-Null
if (Test-ExpectedRuntime $destination) {
    Write-Host "Temurin runtime is ready: $destination"
    exit 0
}

if (Test-Path -LiteralPath $destination) {
    $verifiedDestination = Assert-PathUnderRuntime $destination
    Remove-Item -LiteralPath $verifiedDestination -Recurse -Force
}

$downloadRequired = $true
if (Test-Path -LiteralPath $archivePath -PathType Leaf) {
    $downloadRequired = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expectedSha256
}
if ($downloadRequired) {
    Write-Host "Downloading Eclipse Temurin JRE 21.0.11+10..."
    Invoke-WebRequest -Uri $downloadUrl -OutFile $archivePath -Headers @{ 'User-Agent' = 'ca-attendance-build' }
}

$actualSha256 = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualSha256 -ne $expectedSha256) {
    throw "Temurin archive checksum mismatch. Expected $expectedSha256, got $actualSha256"
}

$extractRoot = Assert-PathUnderRuntime (Join-Path $runtimeRoot ".extract-$PID")
if (Test-Path -LiteralPath $extractRoot) {
    Remove-Item -LiteralPath $extractRoot -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $extractRoot | Out-Null

try {
    Expand-Archive -LiteralPath $archivePath -DestinationPath $extractRoot -Force
    $directories = @(Get-ChildItem -LiteralPath $extractRoot -Directory)
    if ($directories.Count -ne 1) {
        throw 'Unexpected Temurin archive layout'
    }
    Move-Item -LiteralPath $directories[0].FullName -Destination $destination
} finally {
    if (Test-Path -LiteralPath $extractRoot) {
        Remove-Item -LiteralPath $extractRoot -Recurse -Force
    }
}

if (-not (Test-ExpectedRuntime $destination)) {
    throw 'Temurin runtime verification failed after extraction'
}

Write-Host "Temurin runtime prepared: $destination"

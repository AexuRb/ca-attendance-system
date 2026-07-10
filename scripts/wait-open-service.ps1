param(
    [string]$Address = '127.0.0.1',
    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 65535)]
    [int]$Port,
    [switch]$NoBrowser
)

$uri = "http://${Address}:${Port}"
$healthUri = "$uri/api/health"
$deadline = (Get-Date).AddSeconds(45)

do {
    try {
        $health = Invoke-RestMethod -Uri $healthUri -TimeoutSec 2
        if ($health.status -eq 'ok' -and $health.application -eq 'ca-attendance-system') {
            if (-not $NoBrowser) {
                Start-Process $uri
            }
            exit 0
        }
    } catch {
        # The service is still starting.
    }
    Start-Sleep -Milliseconds 500
} while ((Get-Date) -lt $deadline)

Write-Warning "The local service did not become ready at $healthUri"
exit 1

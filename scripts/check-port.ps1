param(
    [Parameter(Mandatory = $true)]
    [int]$Port
)

try {
    $connections = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue
    foreach ($connection in $connections) {
        if ($connection.State -eq 'Listen' -or $connection.State -eq 'Bound') {
            $processName = 'unknown'
            try {
                $processName = (Get-Process -Id $connection.OwningProcess -ErrorAction Stop).ProcessName
            } catch {
                $processName = 'unknown'
            }

            Write-Host "PORT_USED State=$($connection.State) PID=$($connection.OwningProcess) Process=$processName"
            if ($connection.State -eq 'Listen') {
                exit 10
            }
            exit 11
        }
    }
    exit 0
} catch {
    Write-Host "PORT_CHECK_ERROR $($_.Exception.Message)"
    exit 2
}

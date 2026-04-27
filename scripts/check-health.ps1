param(
    [string]$DiscoveryUrl = "http://localhost:8761/actuator/health",
    [string]$UserServiceUrl = "http://localhost:8081/actuator/health",
    [string]$AccountServiceUrl = "http://localhost:8082/actuator/health",
    [string]$TransactionServiceUrl = "http://localhost:8083/actuator/health",
    [string]$LoanServiceUrl = "http://localhost:8084/actuator/health"
)

$checks = @(
    @{ Name = "discovery-server"; Url = $DiscoveryUrl },
    @{ Name = "user-service"; Url = $UserServiceUrl },
    @{ Name = "account-service"; Url = $AccountServiceUrl },
    @{ Name = "transaction-service"; Url = $TransactionServiceUrl },
    @{ Name = "loan-service"; Url = $LoanServiceUrl }
)

$results = @()
foreach ($check in $checks) {
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $response = Invoke-RestMethod -Method Get -Uri $check.Url -TimeoutSec 10
        $stopwatch.Stop()

        $results += [PSCustomObject]@{
            Service = $check.Name
            Status = $response.status
            DurationMs = [math]::Round($stopwatch.Elapsed.TotalMilliseconds, 2)
            Url = $check.Url
        }
    }
    catch {
        $stopwatch.Stop()
        $results += [PSCustomObject]@{
            Service = $check.Name
            Status = "UNREACHABLE"
            DurationMs = [math]::Round($stopwatch.Elapsed.TotalMilliseconds, 2)
            Url = $check.Url
        }
    }
}

$results | Format-Table -AutoSize

$failed = $results | Where-Object { $_.Status -ne "UP" }
if ($failed) {
    Write-Error "One or more services are unhealthy or unreachable."
    exit 1
}

Write-Host "All health checks are UP."

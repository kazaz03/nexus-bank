# ─────────────────────────────────────────────────────────────────────────────
# measure-transaction-lb.ps1
#
# Exercises the @LoadBalanced RestTemplate used by Transaction Service during
# F11 (transfer) — the same mechanism that resolves account-service through
# Eureka for the production transfer flow. Sends N requests in two modes:
#
#   • direct  — bypasses Eureka and hits a fixed account-service URL.
#   • lb      — goes through Eureka + Spring Cloud LoadBalancer.
#
# Reports the per-instance distribution and average latencies so you can
# include the numbers in section 9.5 of the documentation.
#
# Prerequisite: discovery-server (8761), account-service running on at least
# two ports, and transaction-service registered with Eureka.
# ─────────────────────────────────────────────────────────────────────────────

param(
    [string]$TransactionServiceBaseUrl = "http://localhost:8083",
    [string]$DirectAccountServiceBaseUrl = "http://localhost:8082",
    [int]$RequestCount = 100,
    [string]$OutputPath = "./scripts/transaction-lb-report.json"
)

if ($RequestCount -lt 1) { throw "RequestCount must be >= 1" }

function Invoke-Probe {
    param([string]$Mode, [string]$DirectBaseUrl)

    $url = "$TransactionServiceBaseUrl/api/transactions/probe/account-instance?mode=$Mode"
    if ($Mode -eq "direct") {
        $encoded = [System.Uri]::EscapeDataString($DirectBaseUrl)
        $url = "$url&directBaseUrl=$encoded"
    }

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $response = Invoke-RestMethod -Method Get -Uri $url -TimeoutSec 15
    $sw.Stop()

    $instanceId = "unknown"
    if ($null -ne $response.downstream -and $response.downstream.instanceId) {
        $instanceId = [string]$response.downstream.instanceId
    }

    [PSCustomObject]@{
        Mode = $Mode
        InstanceId = $instanceId
        EndToEndMs = [double]$sw.Elapsed.TotalMilliseconds
        DownstreamMs = [double]$response.durationMs
    }
}

Write-Host "[direct] Sending $RequestCount requests bypassing Eureka..."
$directResults = for ($i = 1; $i -le $RequestCount; $i++) {
    Invoke-Probe -Mode "direct" -DirectBaseUrl $DirectAccountServiceBaseUrl
}

Write-Host "[lb]     Sending $RequestCount requests through Eureka + LoadBalancer..."
$lbResults = for ($i = 1; $i -le $RequestCount; $i++) {
    Invoke-Probe -Mode "lb" -DirectBaseUrl $DirectAccountServiceBaseUrl
}

$directAvg = ($directResults | Measure-Object -Property EndToEndMs -Average).Average
$lbAvg     = ($lbResults     | Measure-Object -Property EndToEndMs -Average).Average

$distribution = $lbResults |
    Group-Object -Property InstanceId |
    Sort-Object -Property Count -Descending |
    ForEach-Object {
        [PSCustomObject]@{
            InstanceId = $_.Name
            Requests = $_.Count
            Percentage = [math]::Round(($_.Count / $RequestCount) * 100, 2)
        }
    }

Write-Host ""
Write-Host "Distribution across account-service instances (lb mode):"
$distribution | Format-Table -AutoSize

Write-Host ""
Write-Host "Average end-to-end latency (ms):"
[PSCustomObject]@{
    direct = [math]::Round($directAvg, 2)
    lb     = [math]::Round($lbAvg, 2)
} | Format-List

$summary = [PSCustomObject]@{
    requestCount = $RequestCount
    direct = [PSCustomObject]@{ averageEndToEndMs = [math]::Round($directAvg, 2) }
    loadBalanced = [PSCustomObject]@{
        averageEndToEndMs = [math]::Round($lbAvg, 2)
        distribution = $distribution
    }
}

$dir = Split-Path -Parent $OutputPath
if ($dir -and !(Test-Path $dir)) { New-Item -Path $dir -ItemType Directory | Out-Null }
$summary | ConvertTo-Json -Depth 8 | Set-Content -Path $OutputPath -Encoding UTF8
Write-Host "Saved report to $OutputPath"

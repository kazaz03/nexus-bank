param(
    [string]$LoanServiceBaseUrl = "http://localhost:8084",
    [string]$DirectAccountServiceBaseUrl = "http://localhost:8082",
    [int]$RequestCount = 100,
    [string]$OutputPath = "./scripts/load-balancing-report.json"
)

if ($RequestCount -lt 1) {
    throw "RequestCount must be >= 1"
}

function Invoke-Probe {
    param(
        [string]$Mode,
        [string]$DirectBaseUrl
    )

    $url = "$LoanServiceBaseUrl/api/loans/probe/account-instance?mode=$Mode"
    if ($Mode -eq "direct") {
        $encoded = [System.Uri]::EscapeDataString($DirectBaseUrl)
        $url = "$url&directBaseUrl=$encoded"
    }

    $outer = [System.Diagnostics.Stopwatch]::StartNew()
    $response = Invoke-RestMethod -Method Get -Uri $url -TimeoutSec 15
    $outer.Stop()

    $instanceId = "unknown"
    if ($null -ne $response.downstream -and $response.downstream.instanceId) {
        $instanceId = [string]$response.downstream.instanceId
    }

    [PSCustomObject]@{
        Mode = $Mode
        InstanceId = $instanceId
        EndToEndMs = [double]$outer.Elapsed.TotalMilliseconds
        DownstreamMs = [double]$response.durationMs
        Target = [string]$response.target
    }
}

Write-Host "Running $RequestCount direct-mode requests..."
$directResults = for ($i = 1; $i -le $RequestCount; $i++) {
    Invoke-Probe -Mode "direct" -DirectBaseUrl $DirectAccountServiceBaseUrl
}

Write-Host "Running $RequestCount load-balanced requests..."
$lbResults = for ($i = 1; $i -le $RequestCount; $i++) {
    Invoke-Probe -Mode "lb" -DirectBaseUrl $DirectAccountServiceBaseUrl
}

$directAvgEndToEnd = ($directResults | Measure-Object -Property EndToEndMs -Average).Average
$directAvgDownstream = ($directResults | Measure-Object -Property DownstreamMs -Average).Average
$lbAvgEndToEnd = ($lbResults | Measure-Object -Property EndToEndMs -Average).Average
$lbAvgDownstream = ($lbResults | Measure-Object -Property DownstreamMs -Average).Average

$lbDistribution = $lbResults |
    Group-Object -Property InstanceId |
    Sort-Object -Property Count -Descending |
    ForEach-Object {
        [PSCustomObject]@{
            InstanceId = $_.Name
            Requests = $_.Count
            Percentage = [math]::Round(($_.Count / $RequestCount) * 100, 2)
        }
    }

$summary = [PSCustomObject]@{
    requestCount = $RequestCount
    direct = [PSCustomObject]@{
        averageEndToEndMs = [math]::Round($directAvgEndToEnd, 2)
        averageDownstreamMs = [math]::Round($directAvgDownstream, 2)
    }
    loadBalanced = [PSCustomObject]@{
        averageEndToEndMs = [math]::Round($lbAvgEndToEnd, 2)
        averageDownstreamMs = [math]::Round($lbAvgDownstream, 2)
        distribution = $lbDistribution
    }
}

Write-Host ""
Write-Host "Distribution across account-service instances (LB mode):"
$lbDistribution | Format-Table -AutoSize

Write-Host ""
Write-Host "Average timings (ms):"
[PSCustomObject]@{
    directAverageEndToEndMs = [math]::Round($directAvgEndToEnd, 2)
    directAverageDownstreamMs = [math]::Round($directAvgDownstream, 2)
    lbAverageEndToEndMs = [math]::Round($lbAvgEndToEnd, 2)
    lbAverageDownstreamMs = [math]::Round($lbAvgDownstream, 2)
} | Format-List

$directory = Split-Path -Parent $OutputPath
if ($directory -and !(Test-Path $directory)) {
    New-Item -Path $directory -ItemType Directory | Out-Null
}

$summary | ConvertTo-Json -Depth 8 | Set-Content -Path $OutputPath -Encoding UTF8
Write-Host "Saved report to $OutputPath"

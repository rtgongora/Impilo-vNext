param(
  [string]$ReportPath = "docs/operations/RUNTIME_SMOKE_REPORT.md"
)

$ErrorActionPreference = "Stop"

function Test-TcpPort {
  param([string]$TargetHost, [int]$Port)
  try {
    $client = New-Object System.Net.Sockets.TcpClient
    $iar = $client.BeginConnect($TargetHost, $Port, $null, $null)
    $ok = $iar.AsyncWaitHandle.WaitOne(1500, $false)
    if (-not $ok) { $client.Close(); return $false }
    $client.EndConnect($iar) | Out-Null
    $client.Close()
    return $true
  } catch {
    return $false
  }
}

function Get-HealthStatus {
  param([string]$BaseUrl)
  try {
    $resp = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -TimeoutSec 5 -Method Get
    if ($resp.status) { return [string]$resp.status }
    return "UNKNOWN"
  } catch {
    return "UNREACHABLE"
  }
}

$checks = [System.Collections.Generic.List[object]]::new()
function Add-Check {
  param([string]$Name, [bool]$Pass, [string]$Detail)
  $checks.Add([pscustomobject]@{
    Name = $Name
    Status = if ($Pass) { "PASS" } else { "FAIL" }
    Detail = $Detail
  })
}

Add-Check -Name "PostgreSQL :5432" -Pass (Test-TcpPort "localhost" 5432) -Detail "Core database reachability"
Add-Check -Name "Redis :6379" -Pass (Test-TcpPort "localhost" 6379) -Detail "Cache/session reachability"
Add-Check -Name "Kafka :9092" -Pass (Test-TcpPort "localhost" 9092) -Detail "Event bus reachability"

$serviceMap = @{
  # Match host ports exposed by docker-compose.runtime.yml
  "TSHEPO" = "http://localhost:18081"
  "TSHEPO-AUTHZ" = "http://localhost:18082"
  "VITO" = "http://localhost:8092"
  "VARAPI" = "http://localhost:8083"
  "TUSO" = "http://localhost:8084"
  "ZIBO" = "http://localhost:8085"
  "PCT" = "http://localhost:8088"
  "OROS" = "http://localhost:18089"
  "Guidance" = "http://localhost:8260"
  "Clinical-Knowledge-Platform" = "http://localhost:8270"
  "Experience-BFF" = "http://localhost:8160"
  "Nhume" = "http://localhost:8340"
  "Ndila" = "http://localhost:8330"
}
foreach ($svc in $serviceMap.GetEnumerator()) {
  $status = Get-HealthStatus $svc.Value
  Add-Check -Name "$($svc.Key) actuator/health" -Pass ($status -eq "UP") -Detail "Status=$status"
}

$pass = @($checks | Where-Object { $_.Status -eq "PASS" }).Count
$fail = @($checks | Where-Object { $_.Status -eq "FAIL" }).Count

$reportDir = Split-Path -Parent $ReportPath
if ($reportDir -and -not (Test-Path $reportDir)) {
  New-Item -ItemType Directory -Path $reportDir -Force | Out-Null
}

$lines = @()
$lines += "# Runtime Smoke Report"
$lines += ""
$lines += "Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')"
$lines += ""
$lines += "| Check | Status | Detail |"
$lines += "|---|---|---|"
foreach ($c in $checks) {
  $lines += "| $($c.Name) | $($c.Status) | $($c.Detail) |"
}
$lines += ""
$lines += "- Pass: $pass"
$lines += "- Fail: $fail"

Set-Content -Path $ReportPath -Value $lines -Encoding UTF8
Write-Host "Runtime smoke report written: $ReportPath"
Write-Host "Pass=$pass Fail=$fail"

if ($fail -gt 0) { exit 1 }

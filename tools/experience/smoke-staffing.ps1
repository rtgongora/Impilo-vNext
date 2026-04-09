# Impilo vNext — manual smoke checks for staffing APIs after compose/experience is up.
# Prerequisites: experience-bff healthy on http://localhost:8160 (see compose/experience/docker-compose.yml)
#
# Usage: pwsh -File tools/experience/smoke-staffing.ps1

$ErrorActionPreference = "Stop"
$base = if ($env:BFF_URL) { $env:BFF_URL.TrimEnd('/') } else { "http://localhost:8160" }

try {
  Invoke-WebRequest -Uri "$base/actuator/health" -UseBasicParsing -TimeoutSec 3 | Out-Null
} catch {
  Write-Host "BFF not reachable at $base (start: docker compose -f compose/experience/docker-compose.yml up -d experience-db experience-bff)."
  Write-Host "Automated equivalent: mvn -pl services/experience-bff test -Dtest=StaffingApiIntegrationTest"
  exit 0
}
$tenant = if ($env:X_TENANT_ID) { $env:X_TENANT_ID } else { "tenant-moh-zw" }
$facility = "a1b2c3d4-0001-4000-8000-000000000001"
$week = "2026-04-06"
$headers = @{
  "X-Tenant-ID"       = $tenant
  "X-Pod-ID"          = "national-spine"
  "X-Request-ID"      = [guid]::NewGuid().ToString()
  "X-Correlation-ID"  = [guid]::NewGuid().ToString()
}

Write-Host "GET roster-week..."
Invoke-RestMethod -Uri "$base/internal/v1/staffing/roster-week?facility_id=$facility&week_start=$week" -Headers $headers | ConvertTo-Json -Depth 6

$headers["X-Request-ID"] = [guid]::NewGuid().ToString()
$headers["X-Correlation-ID"] = [guid]::NewGuid().ToString()
Write-Host "GET on-call..."
Invoke-RestMethod -Uri "$base/internal/v1/staffing/on-call?facility_id=$facility&week_start=$week" -Headers $headers | ConvertTo-Json -Depth 6

$headers["X-Request-ID"] = [guid]::NewGuid().ToString()
$headers["X-Correlation-ID"] = [guid]::NewGuid().ToString()
Write-Host "GET swaps..."
Invoke-RestMethod -Uri "$base/internal/v1/staffing/on-call/swaps?facility_id=$facility" -Headers $headers | ConvertTo-Json -Depth 6

Write-Host "Done."

##
## Impilo vNext - Start Experience Platform (Windows / PowerShell)
## Builds wellness-service + inpatient-service + experience-bff JARs (Maven), then docker compose up.
##
## Usage:
##   .\tools\dev\up.ps1
##   .\tools\dev\up.ps1 -Build          # rebuild Docker images (UI + Java services)
##   .\tools\dev\up.ps1 -SkipMaven
##   .\tools\dev\up.ps1 -ShowHostProfile  # print optional host sovereign port matrix
##   .\tools\dev\up.ps1 -SovereignHost     # experience + pharmacy + MusheX + Costa + dispatch in compose
##
param(
    [switch] $Build,
    [switch] $SkipMaven,
    [switch] $ShowHostProfile,
    [switch] $SovereignHost
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$ComposeFile = Join-Path $Root "compose\experience\docker-compose.yml"
$ServicesDir = Join-Path $Root "services"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Impilo vNext - Experience Platform" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

if (-not $SkipMaven) {
    Write-Host ""
    $mavenModules = "wellness-service,inpatient-service,pct-service,integration-hub,experience-bff"
    if ($SovereignHost) {
        $mavenModules += ",pharmacy-service,mushex-service,dispatch-service,costing-engine-service,surveillance-service,ndila-service,nhume-service"
    }
    Write-Host "[INFO] Building $mavenModules (Maven)..." -ForegroundColor Yellow
    Push-Location $ServicesDir
    try {
        mvn -B -pl $mavenModules -am -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw "Maven build failed with exit code $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
    Write-Host "[OK] Java service JARs ready" -ForegroundColor Green
}

$composeFiles = @("-f", $ComposeFile)
if ($SovereignHost) {
    $composeFiles += "-f", (Join-Path $Root "compose\experience\docker-compose.sovereign.yml")
}

$upArgs = @("up", "-d")
if ($Build) { $upArgs += "--build" }

Write-Host ""
if ($SovereignHost) {
    Write-Host "[INFO] Starting Docker Compose (experience + sovereign Rx-path overlay)..." -ForegroundColor Yellow
} else {
    Write-Host "[INFO] Starting Docker Compose..." -ForegroundColor Yellow
}
docker compose @composeFiles @upArgs
if ($LASTEXITCODE -ne 0) { throw "docker compose failed with exit code $LASTEXITCODE" }

function Wait-HttpOk {
    param([string] $Url, [int] $MaxSeconds = 120)
    $deadline = (Get-Date).AddSeconds($MaxSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3 | Out-Null
            return $true
        } catch {
            Start-Sleep -Seconds 2
            Write-Host "  waiting for $Url ..." -ForegroundColor DarkGray
        }
    }
    return $false
}

Write-Host ""
Write-Host "[INFO] Waiting for BFF actuator health (max 120s)..." -ForegroundColor Yellow
$bffOk = Wait-HttpOk "http://localhost:8160/actuator/health"

Write-Host ""
Write-Host "[INFO] Waiting for inpatient-service health (max 120s)..." -ForegroundColor Yellow
$inpatientOk = Wait-HttpOk "http://localhost:8121/actuator/health"

if ($SovereignHost -and $bffOk) {
    Write-Host ""
    Write-Host "[INFO] Waiting for sovereign overlay health (max 180s)..." -ForegroundColor Yellow
    node (Join-Path $Root "scripts\production-readiness\wait-sovereign-health.mjs")
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[WARN] Some sovereign services did not report healthy in time." -ForegroundColor Yellow
    }
}

Write-Host ""
if ($bffOk) {
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "  Experience Platform is running" -ForegroundColor Green
    Write-Host "  UI:        http://localhost:3000  (use -Build after Wave 20 UI changes)" -ForegroundColor Green
    Write-Host "  BFF:       http://localhost:8160" -ForegroundColor Green
    Write-Host "  Inpatient: http://localhost:8121" -ForegroundColor Green
    Write-Host "  PCT:       http://localhost:8088" -ForegroundColor Green
    Write-Host "  Hub:       http://localhost:8110" -ForegroundColor Green
    if ($SovereignHost) {
        Write-Host "  Pharmacy:  http://localhost:8096" -ForegroundColor Green
        Write-Host "  Costa:     http://localhost:8101" -ForegroundColor Green
        Write-Host "  MusheX:    http://localhost:8102" -ForegroundColor Green
        Write-Host "  Dispatch:  http://localhost:8320" -ForegroundColor Green
        Write-Host "  Surveillance: http://localhost:8180" -ForegroundColor Green
        Write-Host "  Ndila:     http://localhost:8155" -ForegroundColor Green
        Write-Host "  Nhume:     http://localhost:8210" -ForegroundColor Green
    }
    Write-Host "  DB:        localhost:5433" -ForegroundColor Green
    Write-Host "  Redis:     localhost:6379" -ForegroundColor Green
    Write-Host "  Kafka:     localhost:9092" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Verify: node scripts/production-readiness/verify-demo-journeys.mjs" -ForegroundColor Cyan
    if ($SovereignHost) {
        Write-Host "Sovereign verify: node scripts/production-readiness/verify-demo-journeys.mjs --sovereign" -ForegroundColor Cyan
    }
    Write-Host "Parity: node scripts/production-readiness/generate-wave20-parity-matrix.mjs" -ForegroundColor Cyan
    if ($ShowHostProfile) {
        Write-Host ""
        Write-Host "Optional host sovereigns (green Rx-path probes):" -ForegroundColor Yellow
        Write-Host "  See docs/production-readiness/wave20/SOVEREIGN_HOST_PORT_MATRIX.md" -ForegroundColor Yellow
        Write-Host "  pharmacy :8096  MusheX :8102  dispatch :8320  nhume :8210  Ndila :8155" -ForegroundColor DarkGray
    }
} else {
    Write-Host "[WARN] BFF health check timed out. Logs:" -ForegroundColor Yellow
    Write-Host '  docker compose -f compose/experience/docker-compose.yml logs experience-bff' -ForegroundColor Yellow
}

if (-not $inpatientOk) {
    Write-Host "[WARN] inpatient-service health check timed out." -ForegroundColor Yellow
}

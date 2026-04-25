##
## Impilo vNext — Start Experience Platform (Windows / PowerShell)
## Builds wellness-service + experience-bff JARs (Maven), then docker compose up.
##
## Usage:
##   .\tools\dev\up.ps1
##   .\tools\dev\up.ps1 -Build        # docker compose --build
##   .\tools\dev\up.ps1 -SkipMaven   # only compose (JAR must already exist)
##
param(
    [switch] $Build,
    [switch] $SkipMaven
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$ComposeFile = Join-Path $Root "compose\experience\docker-compose.yml"
$ServicesDir = Join-Path $Root "services"

Write-Host "========================================"  -ForegroundColor Cyan
Write-Host "  Impilo vNext — Experience Platform" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

if (-not $SkipMaven) {
    Write-Host "`n[INFO] Building wellness-service + experience-bff (Maven)…" -ForegroundColor Yellow
    Push-Location $ServicesDir
    try {
        mvn -B `
            -pl wellness-service,experience-bff `
            -am -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw "Maven build failed with exit code $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
    Write-Host "[OK] wellness-service + experience-bff JARs ready" -ForegroundColor Green
}

$buildArg = @()
if ($Build) { $buildArg = @("--build") }

Write-Host "`n[INFO] Starting Docker Compose…" -ForegroundColor Yellow
docker compose -f $ComposeFile @buildArg up -d
if ($LASTEXITCODE -ne 0) { throw "docker compose failed with exit code $LASTEXITCODE" }

Write-Host "`n[INFO] Waiting for BFF actuator health (max 120s)…" -ForegroundColor Yellow
$deadline = (Get-Date).AddSeconds(120)
$ok = $false
while ((Get-Date) -lt $deadline) {
    try {
        Invoke-WebRequest -Uri "http://localhost:8160/actuator/health" -UseBasicParsing -TimeoutSec 3 | Out-Null
        $ok = $true
        break
    } catch {
        Start-Sleep -Seconds 2
        Write-Host "  waiting…" -ForegroundColor DarkGray
    }
}

Write-Host ""
if ($ok) {
    Write-Host "========================================"  -ForegroundColor Green
    Write-Host "  Experience Platform is running" -ForegroundColor Green
    Write-Host "  UI:  http://localhost:3020" -ForegroundColor Green
    Write-Host "  BFF: http://localhost:8160" -ForegroundColor Green
    Write-Host "  DB:    localhost:5433" -ForegroundColor Green
    Write-Host "  Redis: localhost:6379" -ForegroundColor Green
    Write-Host "  Kafka: localhost:9092" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
} else {
    Write-Host "[WARN] BFF health check timed out. Logs:" -ForegroundColor Yellow
    Write-Host "  docker compose -f compose/experience/docker-compose.yml logs experience-bff" -ForegroundColor Yellow
}

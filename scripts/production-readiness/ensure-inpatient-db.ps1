# Ensure inpatient database exists on experience-db (idempotent).
param(
    [string] $ComposeFile = (Join-Path (Resolve-Path (Join-Path $PSScriptRoot "..\..")) "compose\experience\docker-compose.yml")
)

$exists = docker compose -f $ComposeFile exec -T experience-db `
    psql -U impilo -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = 'inpatient'" 2>$null

if ($exists -ne "1") {
    Write-Host "[INFO] Creating database inpatient on experience-db..." -ForegroundColor Yellow
    docker compose -f $ComposeFile exec -T experience-db `
        psql -U impilo -d postgres -c "CREATE DATABASE inpatient OWNER impilo;"
    if ($LASTEXITCODE -ne 0) { throw "Failed to create inpatient database" }
    Write-Host "[OK] Database inpatient created" -ForegroundColor Green
} else {
    Write-Host "[OK] Database inpatient already exists" -ForegroundColor Green
}

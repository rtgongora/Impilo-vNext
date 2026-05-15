$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$composeFile = Join-Path $root "compose/registry/docker-compose.registry-e2e.yml"
$projectName = if ($env:REGISTRY_E2E_COMPOSE_PROJECT) { $env:REGISTRY_E2E_COMPOSE_PROJECT } else { "registry-e2e-ci" }
$artifactDir = if ($env:REGISTRY_E2E_ARTIFACT_DIR) { $env:REGISTRY_E2E_ARTIFACT_DIR } else { Join-Path $root ".tmp/registry-e2e-artifacts" }
$workDir = if ($env:REGISTRY_E2E_WORK_DIR) { $env:REGISTRY_E2E_WORK_DIR } else { Join-Path $root ".tmp/registry-e2e-work" }
$keepStack = if ($env:REGISTRY_E2E_KEEP_STACK) { $env:REGISTRY_E2E_KEEP_STACK } else { "0" }
$startupTimeoutSeconds = if ($env:REGISTRY_E2E_STARTUP_TIMEOUT_SECONDS) { [int]$env:REGISTRY_E2E_STARTUP_TIMEOUT_SECONDS } else { 480 }

$failed = $false
New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null
New-Item -ItemType Directory -Force -Path $workDir | Out-Null

function Invoke-Compose {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
    docker compose -p $projectName -f $composeFile @Args
}

function Assert-Command {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "[registry-e2e] Missing required command: $Name"
    }
}

function Wait-ServiceHealthy {
    param([string]$ServiceName, [int]$TimeoutSeconds = 480)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $cid = (Invoke-Compose ps -q $ServiceName).Trim()
        if ($cid) {
            $health = (docker inspect --format "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}" $cid 2>$null).Trim()
            if ($health -eq "healthy" -or $health -eq "running") {
                Write-Host "[registry-e2e] Service $ServiceName is $health"
                return
            }
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "[registry-e2e] Timed out waiting for service: $ServiceName"
}

function Invoke-HealthWait {
    param([string]$Name, [string]$Url, [int]$TimeoutSeconds = 240)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            $resp = Invoke-RestMethod -Uri $Url -Method Get
            if ($resp.status -eq "UP") {
                Write-Host "[registry-e2e] Health endpoint UP: $Name ($Url)"
                return
            }
        } catch {
            # retry
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "[registry-e2e] Timed out waiting for health endpoint: $Name ($Url)"
}

function Dump-Diagnostics {
    try { Invoke-Compose ps | Out-File -FilePath (Join-Path $artifactDir "compose-ps.txt") -Encoding utf8 } catch {}
    try { Invoke-Compose logs --no-color | Out-File -FilePath (Join-Path $artifactDir "compose-logs.txt") -Encoding utf8 } catch {}
    foreach ($service in @("postgres","redis","kafka","keycloak","tshepo-authz","vito","varapi","tuso","zibo","msika","ubomi","experience-bff")) {
        try {
            $cid = (Invoke-Compose ps -q $service).Trim()
            if ($cid) { docker inspect $cid | Out-File -FilePath (Join-Path $artifactDir "inspect-$service.json") -Encoding utf8 }
        } catch {}
    }
}

try {
    Assert-Command "docker"
    if (-not (docker compose version 2>$null)) { throw "[registry-e2e] docker compose v2 is required but unavailable" }
    if (-not (docker info 2>$null)) { throw "[registry-e2e] Docker daemon is unavailable. Start Docker and retry." }
    Invoke-Compose config | Out-Null

    Write-Host "[registry-e2e] Starting registry runtime stack (project=$projectName)..."
    Invoke-Compose up -d --build | Out-Null

    foreach ($svc in @("postgres","redis","kafka","keycloak","tshepo-authz","vito","varapi","tuso","zibo","msika","ubomi","experience-bff")) {
        Wait-ServiceHealthy -ServiceName $svc -TimeoutSeconds $startupTimeoutSeconds
    }

    Invoke-HealthWait -Name "tshepo-authz" -Url "http://localhost:48081/actuator/health"
    Invoke-HealthWait -Name "vito" -Url "http://localhost:48082/actuator/health"
    Invoke-HealthWait -Name "varapi" -Url "http://localhost:48083/actuator/health"
    Invoke-HealthWait -Name "tuso" -Url "http://localhost:48084/actuator/health"
    Invoke-HealthWait -Name "zibo" -Url "http://localhost:48085/actuator/health"
    Invoke-HealthWait -Name "msika" -Url "http://localhost:48086/actuator/health"
    Invoke-HealthWait -Name "ubomi" -Url "http://localhost:48087/actuator/health"
    Invoke-HealthWait -Name "experience-bff" -Url "http://localhost:48160/actuator/health"

    $reqId = "registry-e2e-req-1"
    $corrId = [guid]::NewGuid().ToString()

    $facility = Invoke-RestMethod -Method Get -Uri "http://localhost:48160/internal/v1/facilities?page=0&size=5" -Headers @{
        "X-Tenant-Id" = "tenant-registry-e2e"
        "X-Request-Id" = $reqId
        "X-Correlation-Id" = $corrId
    }
    if ($null -eq $facility.data -or $null -eq $facility.meta) {
        throw "[registry-e2e] Facility BFF response missing data/meta envelope"
    }

    $geo = Invoke-RestMethod -Method Get -Uri "http://localhost:48160/internal/v1/registry/geo/zw/districts?provinceCode=HA" -Headers @{
        "X-Request-Id" = $reqId
        "X-Correlation-Id" = $corrId
    }
    if ($null -eq $geo.data -or $null -eq $geo.meta) {
        throw "[registry-e2e] Registry geo BFF response missing data/meta envelope"
    }

    Invoke-Compose stop tuso | Out-Null
    $failResp = Invoke-WebRequest -Method Get -Uri "http://localhost:48160/internal/v1/facilities?page=0&size=2" -Headers @{
        "X-Tenant-Id" = "tenant-registry-e2e"
        "X-Request-Id" = $reqId
        "X-Correlation-Id" = $corrId
    } -SkipHttpErrorCheck
    if ($failResp.StatusCode -ne 502) { throw "[registry-e2e] Expected BFF fail-close 502, got $($failResp.StatusCode)" }
    Invoke-Compose start tuso | Out-Null
    Wait-ServiceHealthy -ServiceName "tuso" -TimeoutSeconds 240
    Invoke-HealthWait -Name "tuso" -Url "http://localhost:48084/actuator/health" -TimeoutSeconds 240

    $secResp = Invoke-WebRequest -Method Get -Uri "http://localhost:48082/v1/client-registry/clients" -SkipHttpErrorCheck
    if ($secResp.StatusCode -ne 401 -and $secResp.StatusCode -ne 403) {
        throw "[registry-e2e] Expected secured vito endpoint (401/403), got $($secResp.StatusCode)"
    }

    $authzResp = Invoke-WebRequest -Method Post -Uri "http://localhost:48081/v1/authorize" -Headers @{
        "X-Tenant-Id" = "tenant-registry-e2e"
        "X-Actor-Id" = "actor-registry-e2e"
        "X-Actor-Type" = "PROVIDER"
    } -SkipHttpErrorCheck
    if ($authzResp.StatusCode -ne 403) {
        throw "[registry-e2e] Expected authz deny 403, got $($authzResp.StatusCode)"
    }

    Write-Host "[registry-e2e] PASS - registry runtime harness assertions succeeded"
} catch {
    $failed = $true
    Write-Error $_
    throw
} finally {
    if ($failed) { Dump-Diagnostics }
    if ($keepStack -eq "1") {
        Write-Host "[registry-e2e] REGISTRY_E2E_KEEP_STACK=1 set; skipping compose down"
    } else {
        try { Invoke-Compose down -v --remove-orphans | Out-Null } catch { Write-Warning "[registry-e2e] compose down failed: $($_.Exception.Message)" }
    }
}

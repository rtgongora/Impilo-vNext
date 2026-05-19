$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$composeFile = Join-Path $root "compose/trust/docker-compose.trust-e2e.yml"
$projectName = if ($env:TRUST_E2E_COMPOSE_PROJECT) { $env:TRUST_E2E_COMPOSE_PROJECT } else { "trust-e2e-ci" }
$artifactDir = if ($env:TRUST_E2E_ARTIFACT_DIR) { $env:TRUST_E2E_ARTIFACT_DIR } else { Join-Path $root ".tmp/trust-e2e-artifacts" }
$workDir = if ($env:TRUST_E2E_WORK_DIR) { $env:TRUST_E2E_WORK_DIR } else { Join-Path $root ".tmp/trust-e2e-work" }
$keepStack = if ($env:TRUST_E2E_KEEP_STACK) { $env:TRUST_E2E_KEEP_STACK } else { "0" }
$startupTimeoutSeconds = if ($env:TRUST_E2E_STARTUP_TIMEOUT_SECONDS) { [int]$env:TRUST_E2E_STARTUP_TIMEOUT_SECONDS } else { 420 }

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
        throw "[trust-e2e] Missing required command: $Name"
    }
}

function Invoke-HealthWait {
    param(
        [string]$Name,
        [string]$Url,
        [int]$TimeoutSeconds = 180
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            $resp = Invoke-RestMethod -Uri $Url -Method Get
            if ($resp.status -eq "UP") {
                Write-Host "[trust-e2e] Health endpoint UP: $Name ($Url)"
                return
            }
        } catch {
            # retry
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "[trust-e2e] Timed out waiting for health endpoint: $Name ($Url)"
}

function Wait-ServiceHealthy {
    param(
        [string]$ServiceName,
        [int]$TimeoutSeconds = 420
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $cid = (Invoke-Compose ps -q $ServiceName).Trim()
        if ($cid) {
            $health = (docker inspect --format "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}" $cid 2>$null).Trim()
            if ($health -eq "healthy" -or $health -eq "running") {
                Write-Host "[trust-e2e] Service $ServiceName is $health"
                return
            }
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "[trust-e2e] Timed out waiting for service: $ServiceName"
}

function Dump-Diagnostics {
    try {
        Invoke-Compose ps | Out-File -FilePath (Join-Path $artifactDir "compose-ps.txt") -Encoding utf8
    } catch {}
    try {
        Invoke-Compose logs --no-color | Out-File -FilePath (Join-Path $artifactDir "compose-logs.txt") -Encoding utf8
    } catch {}

    foreach ($service in @("postgres","redis","kafka","keycloak","tshepo-consent","tshepo-authz","tshepo-audit","mvumo","tshepo")) {
        try {
            $cid = (Invoke-Compose ps -q $service).Trim()
            if ($cid) {
                docker inspect $cid | Out-File -FilePath (Join-Path $artifactDir "inspect-$service.json") -Encoding utf8
            }
        } catch {}
    }
}

try {
    Assert-Command "docker"
    if (-not (docker compose version 2>$null)) {
        throw "[trust-e2e] docker compose v2 is required but unavailable"
    }
    if (-not (docker info 2>$null)) {
        throw "[trust-e2e] Docker daemon is unavailable. Start Docker and retry."
    }
    Invoke-Compose config | Out-Null

    Write-Host "[trust-e2e] Starting trust runtime stack (project=$projectName)..."
    Invoke-Compose up -d --build | Out-Null

    foreach ($svc in @("postgres","redis","kafka","keycloak","tshepo-consent","tshepo-authz","tshepo-audit","mvumo","tshepo")) {
        Wait-ServiceHealthy -ServiceName $svc -TimeoutSeconds $startupTimeoutSeconds
    }

    Invoke-HealthWait -Name "mvumo" -Url "http://localhost:38195/actuator/health" -TimeoutSeconds 180
    Invoke-HealthWait -Name "tshepo-authz" -Url "http://localhost:38081/actuator/health" -TimeoutSeconds 180
    Invoke-HealthWait -Name "tshepo-consent" -Url "http://localhost:38182/actuator/health" -TimeoutSeconds 180
    Invoke-HealthWait -Name "tshepo-audit" -Url "http://localhost:38183/actuator/health" -TimeoutSeconds 180
    Invoke-HealthWait -Name "tshepo-legacy" -Url "http://localhost:38079/actuator/health" -TimeoutSeconds 180

    $tenant = "00000000-0000-0000-0000-000000000001"
    $corr = [guid]::NewGuid().ToString()

    $create = Invoke-RestMethod -Method Post -Uri "http://localhost:38195/internal/v1/mvumo/consent-requests" -Headers @{
        "X-Tenant-ID" = $tenant
    } -ContentType "application/json" -Body '{"subjectPatientRef":"Patient/runtime-e2e-1","consentType":"DIGITAL_TELEMEDICINE","requiredAssurance":"L1_SIMPLE_DIGITAL"}'
    $consentId = $create.data.id

    $session = Invoke-RestMethod -Method Post -Uri "http://localhost:38195/internal/v1/mvumo/remote-sessions" -Headers @{
        "X-Tenant-ID" = $tenant
    } -ContentType "application/json" -Body "{`"consentRequestId`":`"$consentId`",`"channel`":`"TOKEN_LINK`",`"ttlMinutes`":30}"

    $sessionId = $session.data.sessionId
    $token = $session.data.token

    $verify = Invoke-RestMethod -Method Post -Uri "http://localhost:38195/internal/v1/mvumo/remote-sessions/$sessionId/verify" -Headers @{
        "X-Tenant-ID" = $tenant
        "X-Actor-Id" = "provider-runtime-1"
        "X-Purpose-Of-Use" = "TREATMENT"
        "X-Correlation-Id" = $corr
    } -ContentType "application/json" -Body "{`"token`":`"$token`"}"
    if ($verify.data.sessionState -ne "VERIFIED") { throw "[trust-e2e] verify flow did not reach VERIFIED" }

    $grant = Invoke-RestMethod -Method Post -Uri "http://localhost:38195/internal/v1/mvumo/remote-sessions/$sessionId/grant" -Headers @{
        "X-Tenant-ID" = $tenant
        "X-Actor-Id" = "provider-runtime-1"
        "X-Purpose-Of-Use" = "TREATMENT"
        "X-Correlation-Id" = $corr
    } -ContentType "application/json" -Body '{"grantedByRef":"provider-runtime-1","reason":"runtime-e2e-grant"}'
    if ($grant.data.sessionState -ne "GRANTED") { throw "[trust-e2e] grant flow did not reach GRANTED" }

    $denyResp = Invoke-WebRequest -Method Post -Uri "http://localhost:38081/v1/authorize" -Headers @{
        "X-Tenant-Id" = $tenant
        "X-Actor-Id" = "provider-runtime-1"
        "X-Actor-Type" = "PROVIDER"
    } -SkipHttpErrorCheck
    if ($denyResp.StatusCode -ne 403) { throw "[trust-e2e] Expected authz deny 403, got $($denyResp.StatusCode)" }

    $consentDeny = Invoke-RestMethod -Method Get -Uri "http://localhost:38182/v1/consent/evaluate?tenantId=$tenant&actorId=provider-runtime-2&subjectRef=Patient/no-consent&purpose=TREATMENT&scope=clinical-data"
    if ($consentDeny.data.permitted -ne $false) { throw "[trust-e2e] Expected consent deny" }

    Invoke-Compose stop tshepo-consent | Out-Null
    $depFail = Invoke-WebRequest -Method Post -Uri "http://localhost:38195/internal/v1/mvumo/evaluate" -Headers @{
        "X-Tenant-ID" = $tenant
    } -ContentType "application/json" -Body '{"actorId":"provider-runtime-1","subjectRef":"Patient/runtime-e2e-1","purpose":"TREATMENT","scope":"clinical-data"}' -SkipHttpErrorCheck
    if ($depFail.StatusCode -ne 503) { throw "[trust-e2e] Expected dependency failure 503, got $($depFail.StatusCode)" }
    Invoke-Compose start tshepo-consent | Out-Null
    Wait-ServiceHealthy -ServiceName "tshepo-consent" -TimeoutSeconds 180
    Invoke-HealthWait -Name "tshepo-consent" -Url "http://localhost:38182/actuator/health" -TimeoutSeconds 180

    $ctxFail = Invoke-WebRequest -Method Post -Uri "http://localhost:38195/internal/v1/mvumo/remote-sessions/$sessionId/verify" -ContentType "application/json" -Body "{`"token`":`"$token`"}" -SkipHttpErrorCheck
    if ($ctxFail.StatusCode -ne 400) { throw "[trust-e2e] Expected missing-context 400, got $($ctxFail.StatusCode)" }

    Write-Host "[trust-e2e] Legacy telemetry endpoint reachability check..."
    $legacy = Invoke-RestMethod -Method Get -Uri "http://localhost:38079/internal/v1/legacy/route-usage"
    if ($null -eq $legacy.totalLegacyHits) { throw "[trust-e2e] Expected totalLegacyHits field in legacy telemetry payload" }
    if ($null -eq $legacy.routes) { throw "[trust-e2e] Expected routes field in legacy telemetry payload" }
    if ($null -eq $legacy.recentEvents) { throw "[trust-e2e] Expected recentEvents field in legacy telemetry payload" }

    $postgresContainer = (Invoke-Compose ps -q postgres).Trim()
    $outboxCount = (docker exec $postgresContainer psql -U impilo -d mvumo -t -c "select count(*) from mvumo.event_outbox;").Trim()
    if ([int]$outboxCount -le 0) { throw "[trust-e2e] Expected mvumo outbox evidence" }

    Write-Host "[trust-e2e] PASS - full runtime harness assertions succeeded"
} catch {
    $failed = $true
    Write-Error $_
    throw
} finally {
    if ($failed) {
        Dump-Diagnostics
    }

    if ($keepStack -eq "1") {
        Write-Host "[trust-e2e] TRUST_E2E_KEEP_STACK=1 set; skipping compose down"
    } else {
        try {
            Invoke-Compose down -v --remove-orphans | Out-Null
        } catch {
            Write-Warning "[trust-e2e] compose down failed: $($_.Exception.Message)"
        }
    }
}

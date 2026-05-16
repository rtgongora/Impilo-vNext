Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RootDir = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$ComposeFile = Join-Path $RootDir "compose\core-transaction\docker-compose.core-transaction-e2e.yml"
$ComposeProjectName = if ($env:CORE_TX_E2E_COMPOSE_PROJECT) { $env:CORE_TX_E2E_COMPOSE_PROJECT } else { "core-tx-e2e-ci" }

function Compose {
    param(
        [Parameter(ValueFromRemainingArguments = $true)]
        [string[]]$Args
    )
    docker compose -p $ComposeProjectName -f $ComposeFile @Args
}

function Wait-HttpUp {
    param(
        [string]$Name,
        [string]$Url,
        [int]$TimeoutSeconds = 360
    )
    $start = Get-Date
    while (((Get-Date) - $start).TotalSeconds -lt $TimeoutSeconds) {
        try {
            $resp = Invoke-RestMethod -Uri $Url -Method Get
            if ($resp.status -eq "UP") {
                Write-Output "[core-tx-e2e] Health endpoint UP: $Name ($Url)"
                return
            }
        } catch {
            Start-Sleep -Seconds 2
        }
        Start-Sleep -Seconds 2
    }
    throw "[core-tx-e2e] Timed out waiting for health endpoint: $Name ($Url)"
}

function Assert-True {
    param(
        [bool]$Condition,
        [string]$Message
    )
    if (-not $Condition) {
        throw $Message
    }
}

function Invoke-JsonGet {
    param(
        [string]$Url,
        [hashtable]$Headers
    )
    return Invoke-RestMethod -Uri $Url -Method Get -Headers $Headers
}

function Invoke-JsonPost {
    param(
        [string]$Url,
        [hashtable]$Headers,
        [hashtable]$Body
    )
    $json = $Body | ConvertTo-Json -Depth 10
    return Invoke-RestMethod -Uri $Url -Method Post -Headers $Headers -ContentType "application/json" -Body $json
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "[core-tx-e2e] Missing required command: docker"
}
if (-not (Get-Command curl -ErrorAction SilentlyContinue)) {
    throw "[core-tx-e2e] Missing required command: curl"
}
if (-not (Get-Command python -ErrorAction SilentlyContinue)) {
    throw "[core-tx-e2e] Missing required command: python"
}

docker info | Out-Null
Compose config | Out-Null

Write-Output "[core-tx-e2e] Starting core transaction runtime stack (project=$ComposeProjectName)..."
Compose up -d --build | Out-Null

try {
    Wait-HttpUp -Name "experience-bff" -Url "http://localhost:48161/actuator/health"

    $reqId = "core-tx-e2e-req-1"
    $corrId = [Guid]::NewGuid().ToString()
    $headers = @{
        "X-Tenant-Id" = "tenant-core-tx-e2e"
        "X-Pod-Id" = "national-spine"
        "X-Request-Id" = $reqId
        "X-Correlation-Id" = $corrId
    }

    Write-Output "[core-tx-e2e] Verifying list endpoint..."
    $listResp = Invoke-JsonGet -Url "http://localhost:48161/internal/v1/core-transactions?state=TRUST_CONTEXT_ESTABLISHED" -Headers $headers
    Assert-True ($null -ne $listResp.data.items -and $listResp.data.items.Count -ge 1) "List endpoint returned no items"

    Write-Output "[core-tx-e2e] Verifying get endpoint..."
    $getResp = Invoke-JsonGet -Url "http://localhost:48161/internal/v1/core-transactions/tx-1" -Headers $headers
    Assert-True ($getResp.data.transaction.id -eq "tx-1") "GET endpoint returned unexpected transaction id"
    $blockerReasons = @($getResp.data.blockerReasons)
    Assert-True (($blockerReasons | Where-Object { $_ -match "(?i)triage note" }).Count -ge 1) "GET endpoint missing expected blocker reason"

    Write-Output "[core-tx-e2e] Verifying alias endpoint..."
    $aliasResp = Invoke-JsonGet -Url "http://localhost:48161/experience/core-transactions/tx-1/next-actions" -Headers $headers
    Assert-True ($null -ne $aliasResp.data.nextActions) "Alias next-actions endpoint missing nextActions"

    Write-Output "[core-tx-e2e] Verifying journeys endpoint..."
    $journeysResp = Invoke-JsonGet -Url "http://localhost:48161/internal/v1/core-transactions/tx-1/journeys" -Headers $headers
    $journeys = $journeysResp.data.journeys
    Assert-True ($null -ne $journeys.person -and $null -ne $journeys.provider -and $null -ne $journeys.platform) "Journeys payload missing one or more journey views"
    Assert-True ($null -ne $journeys.person.blockers) "Person journey blockers missing"
    $personReasons = @($journeys.person.blockerReasons)
    Assert-True (($personReasons | Where-Object { $_ -match "(?i)triage note" }).Count -ge 1) "Journeys endpoint missing blockerReasons enrichment"

    Write-Output "[core-tx-e2e] Verifying nompilo endpoint..."
    $nompiloResp = Invoke-JsonGet -Url "http://localhost:48161/internal/v1/core-transactions/tx-1/nompilo" -Headers $headers
    $nompilo = $nompiloResp.data.nompilo
    Assert-True ($nompilo.available -eq $true) "Nompilo should be available"
    Assert-True ($null -ne $nompilo.mode) "Nompilo mode missing"
    Assert-True ($null -ne $nompilo.currentGuidance -and $nompilo.currentGuidance.Count -ge 1) "Nompilo guidance missing"

    Write-Output "[core-tx-e2e] Verifying feedback endpoint..."
    $feedbackResp = Invoke-JsonPost -Url "http://localhost:48161/internal/v1/core-transactions/tx-1/feedback" -Headers $headers -Body @{
        channel = "IN_APP"
        promptId = "feedback-in-app"
        response = "helpful"
    }
    Assert-True ($feedbackResp.data.status -eq "ACCEPTED") "Feedback endpoint should return ACCEPTED"

    Write-Output "[core-tx-e2e] Verifying nompilo handoff endpoint..."
    $handoffResp = Invoke-JsonPost -Url "http://localhost:48161/internal/v1/core-transactions/tx-1/nompilo/handoff" -Headers $headers -Body @{
        handoffOptionId = "handoff-helpdesk"
    }
    Assert-True ($handoffResp.data.status -eq "ACCEPTED") "Handoff endpoint should return ACCEPTED"

    Write-Output "[core-tx-e2e] Verifying nompilo command endpoint..."
    $commandResp = Invoke-JsonPost -Url "http://localhost:48161/internal/v1/core-transactions/tx-1/nompilo/command" -Headers $headers -Body @{
        query = "find anc service near me"
        surface = "GLOBAL_SEARCH_BAR"
    }
    Assert-True ($commandResp.data.status -eq "ACCEPTED") "Nompilo command endpoint should return ACCEPTED"
    Assert-True ($null -ne $commandResp.data.intent) "Nompilo command endpoint should return intent"

    Write-Output "[core-tx-e2e] Verifying alias journeys endpoint..."
    $aliasJourneysResp = Invoke-JsonGet -Url "http://localhost:48161/experience/core-transactions/tx-1/journeys" -Headers $headers
    Assert-True ($null -ne $aliasJourneysResp.data.journeys) "Alias journeys endpoint missing journeys payload"

    Write-Output "[core-tx-e2e] Verifying action endpoint..."
    $actionResp = Invoke-JsonPost -Url "http://localhost:48161/internal/v1/core-transactions/tx-1/actions/START_TRIAGE" -Headers $headers -Body @{
        note = "start triage"
    }
    Assert-True ($null -ne $actionResp.data) "Action endpoint should return data"

    Write-Output "[core-tx-e2e] PASS - core transaction runtime harness assertions succeeded"
}
finally {
    if ($env:CORE_TX_E2E_KEEP_STACK -eq "1") {
        Write-Output "[core-tx-e2e] CORE_TX_E2E_KEEP_STACK=1 set; skipping compose down"
    } else {
        Compose down -v --remove-orphans | Out-Null
    }
}

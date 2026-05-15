$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$artifactDir = if ($env:ENTERPRISE_E2E_ARTIFACT_DIR) { $env:ENTERPRISE_E2E_ARTIFACT_DIR } else { Join-Path $root ".tmp/enterprise-e2e-artifacts" }
$workDir = if ($env:ENTERPRISE_E2E_WORK_DIR) { $env:ENTERPRISE_E2E_WORK_DIR } else { Join-Path $root ".tmp/enterprise-e2e-work" }
$startupTimeoutSeconds = if ($env:ENTERPRISE_E2E_STARTUP_TIMEOUT_SECONDS) { [int]$env:ENTERPRISE_E2E_STARTUP_TIMEOUT_SECONDS } else { 180 }

$experienceBffBaseUrl = if ($env:EXPERIENCE_BFF_BASE_URL) { $env:EXPERIENCE_BFF_BASE_URL } else { "http://localhost:8160" }
$mushexBaseUrl = if ($env:MUSHEX_BASE_URL) { $env:MUSHEX_BASE_URL } else { "http://localhost:8086" }
$costaBaseUrl = if ($env:COSTA_BASE_URL) { $env:COSTA_BASE_URL } else { "http://localhost:8087" }
$coverageBaseUrl = if ($env:COVERAGE_BASE_URL) { $env:COVERAGE_BASE_URL } else { "http://localhost:8088" }
$glBaseUrl = if ($env:GL_BASE_URL) { $env:GL_BASE_URL } else { "http://localhost:8089" }
$procurementBaseUrl = if ($env:PROCUREMENT_BASE_URL) { $env:PROCUREMENT_BASE_URL } else { "http://localhost:8090" }
$hrPayrollBaseUrl = if ($env:HR_PAYROLL_BASE_URL) { $env:HR_PAYROLL_BASE_URL } else { "http://localhost:8091" }
$msikaFlowBaseUrl = if ($env:MSIKA_FLOW_BASE_URL) { $env:MSIKA_FLOW_BASE_URL } else { "http://localhost:8092" }

New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null
New-Item -ItemType Directory -Force -Path $workDir | Out-Null

function Wait-HealthUp {
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
                Write-Host "[enterprise-e2e] Health endpoint UP: $Name ($Url)"
                return
            }
        } catch {
            # retry
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)

    throw "[enterprise-e2e] Timed out waiting for health endpoint: $Name ($Url)"
}

function Assert-Typed501 {
    param(
        [string]$Method,
        [string]$Url,
        [string]$ExpectedCode,
        [string]$OutputFile
    )

    if ($Method -eq "POST") {
        $resp = Invoke-WebRequest -Method Post -Uri $Url -Headers @{
            "X-Request-Id" = "req-enterprise-e2e-501"
            "X-Correlation-Id" = "corr-enterprise-e2e-501"
        } -ContentType "application/json" -Body "{}" -SkipHttpErrorCheck
    } else {
        $resp = Invoke-WebRequest -Method Get -Uri $Url -Headers @{
            "X-Request-Id" = "req-enterprise-e2e-501"
            "X-Correlation-Id" = "corr-enterprise-e2e-501"
        } -SkipHttpErrorCheck
    }

    if ($resp.StatusCode -ne 501) {
        throw "[enterprise-e2e] Expected HTTP 501 from $Url, got $($resp.StatusCode)"
    }

    $resp.Content | Out-File -FilePath $OutputFile -Encoding utf8
    $payload = $resp.Content | ConvertFrom-Json
    if ($null -eq $payload.error) { throw "[enterprise-e2e] Missing error envelope for $Url" }
    if ($payload.error.code -ne $ExpectedCode) { throw "[enterprise-e2e] Expected code $ExpectedCode, got $($payload.error.code)" }
    if ($null -eq $payload.meta) { throw "[enterprise-e2e] Missing meta envelope for $Url" }
}

function Assert-Non5xx {
    param(
        [string]$Url,
        [string]$OutputFile
    )

    $resp = Invoke-WebRequest -Method Get -Uri $Url -Headers @{
        "X-Request-Id" = "req-enterprise-e2e-live"
        "X-Correlation-Id" = "corr-enterprise-e2e-live"
    } -SkipHttpErrorCheck

    if ($resp.StatusCode -ge 500) {
        throw "[enterprise-e2e] Expected non-5xx from $Url, got $($resp.StatusCode)"
    }

    $resp.Content | Out-File -FilePath $OutputFile -Encoding utf8
}

Wait-HealthUp -Name "experience-bff" -Url "$experienceBffBaseUrl/actuator/health" -TimeoutSeconds $startupTimeoutSeconds
Wait-HealthUp -Name "mushex-service" -Url "$mushexBaseUrl/actuator/health" -TimeoutSeconds $startupTimeoutSeconds
Wait-HealthUp -Name "costing-engine-service" -Url "$costaBaseUrl/actuator/health" -TimeoutSeconds $startupTimeoutSeconds
Wait-HealthUp -Name "coverage-service" -Url "$coverageBaseUrl/actuator/health" -TimeoutSeconds $startupTimeoutSeconds
Wait-HealthUp -Name "general-ledger-service" -Url "$glBaseUrl/actuator/health" -TimeoutSeconds $startupTimeoutSeconds
Wait-HealthUp -Name "procurement-service" -Url "$procurementBaseUrl/actuator/health" -TimeoutSeconds $startupTimeoutSeconds
Wait-HealthUp -Name "hr-payroll-service" -Url "$hrPayrollBaseUrl/actuator/health" -TimeoutSeconds $startupTimeoutSeconds
Wait-HealthUp -Name "msika-flow-service" -Url "$msikaFlowBaseUrl/actuator/health" -TimeoutSeconds $startupTimeoutSeconds

Assert-Typed501 -Method "GET" -Url "$experienceBffBaseUrl/internal/v1/mobile/provider/billing/charges" `
    -ExpectedCode "BILLING_ROUTE_UNAVAILABLE" -OutputFile (Join-Path $workDir "mobile-billing-charges-501.json")
Assert-Typed501 -Method "POST" -Url "$experienceBffBaseUrl/internal/v1/mobile/provider/billing/charge" `
    -ExpectedCode "BILLING_ROUTE_UNAVAILABLE" -OutputFile (Join-Path $workDir "mobile-billing-charge-501.json")

Assert-Non5xx -Url "$experienceBffBaseUrl/internal/v1/erp/procurement/suppliers" `
    -OutputFile (Join-Path $workDir "procurement-suppliers.json")
Assert-Non5xx -Url "$experienceBffBaseUrl/internal/v1/erp/hr/employees" `
    -OutputFile (Join-Path $workDir "hr-employees.json")
Assert-Non5xx -Url "$experienceBffBaseUrl/internal/v1/finance/patient-accounts/runtime-e2e-patient" `
    -OutputFile (Join-Path $workDir "patient-account.json")
Assert-Non5xx -Url "$experienceBffBaseUrl/internal/v1/finance/payment-plans?patient_cpid=runtime-e2e-patient" `
    -OutputFile (Join-Path $workDir "payment-plans.json")

Copy-Item -Path (Join-Path $workDir "*.json") -Destination $artifactDir -Force -ErrorAction SilentlyContinue
Write-Host "[enterprise-e2e] PASS - enterprise runtime proof harness assertions succeeded"

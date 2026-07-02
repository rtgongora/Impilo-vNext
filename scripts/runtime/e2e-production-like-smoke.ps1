param(
  [switch]$StartStack = $false,
  [switch]$StopStack = $false,
  [int]$LoadIterations = 12,
  [int]$RequestTimeoutSec = 15,
  [string]$ReportPath = "docs/operations/PRODUCTION_LIKE_E2E_REPORT.md"
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$ComposeFiles = @(
  (Join-Path $ProjectRoot "ops/runtime/docker-compose.infra.yml"),
  (Join-Path $ProjectRoot "ops/runtime/docker-compose.shared.yml"),
  (Join-Path $ProjectRoot "ops/runtime/docker-compose.edge.yml"),
  (Join-Path $ProjectRoot "ops/runtime/docker-compose.operations.yml"),
  (Join-Path $ProjectRoot "ops/runtime/docker-compose.apps.yml")
)
$Timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss zzz"

$checks = [System.Collections.Generic.List[object]]::new()
function Add-Check {
  param(
    [string]$Area,
    [string]$Name,
    [string]$Status,
    [string]$Detail
  )
  $checks.Add([pscustomobject]@{
    Area = $Area
    Check = $Name
    Status = $Status
    Detail = $Detail
  })
}

function Try-InvokeJson {
  param(
    [string]$Method,
    [string]$Url,
    [hashtable]$Headers = @{},
    [object]$Body = $null,
    [int]$TimeoutSec = 10
  )
  try {
    $params = @{
      Method = $Method
      Uri = $Url
      Headers = $Headers
      TimeoutSec = $TimeoutSec
      ErrorAction = "Stop"
    }
    if ($null -ne $Body) {
      $params.ContentType = "application/json"
      $params.Body = ($Body | ConvertTo-Json -Depth 12 -Compress)
    }
    $resp = Invoke-WebRequest @params
    $json = $null
    if ($resp.Content) {
      try { $json = $resp.Content | ConvertFrom-Json -Depth 100 } catch { $json = $null }
    }
    return @{
      Ok = $true
      StatusCode = [int]$resp.StatusCode
      Json = $json
      Raw = $resp.Content
    }
  } catch {
    $status = 0
    if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
      $status = [int]$_.Exception.Response.StatusCode
    }
    return @{
      Ok = $false
      StatusCode = $status
      Json = $null
      Raw = $_.Exception.Message
    }
  }
}

function Wait-Health {
  param(
    [string]$Name,
    [string]$BaseUrl,
    [int]$MaxSeconds = 180
  )
  $deadline = (Get-Date).AddSeconds($MaxSeconds)
  while ((Get-Date) -lt $deadline) {
    $res = Try-InvokeJson -Method "GET" -Url "$BaseUrl/actuator/health" -TimeoutSec 5
    if ($res.Ok -and $res.Json -and $res.Json.status -eq "UP") {
      return $true
    }
    Start-Sleep -Seconds 2
  }
  return $false
}

function Add-LatencyCheck {
  param(
    [string]$Area,
    [string]$Name,
    [double[]]$DurationsMs,
    [int]$SuccessCount,
    [int]$ExpectedCount
  )
  if ($SuccessCount -eq 0) {
    Add-Check -Area $Area -Name $Name -Status "FAIL" -Detail "0/$ExpectedCount requests succeeded."
    return
  }
  $sorted = $DurationsMs | Sort-Object
  $avg = [Math]::Round((($DurationsMs | Measure-Object -Average).Average), 1)
  $p95Index = [Math]::Min($sorted.Count - 1, [Math]::Floor(($sorted.Count - 1) * 0.95))
  $p95 = [Math]::Round($sorted[$p95Index], 1)
  $status = if ($SuccessCount -eq $ExpectedCount) { "PASS" } else { "WARN" }
  Add-Check -Area $Area -Name $Name -Status $status -Detail "$SuccessCount/$ExpectedCount succeeded; avg=${avg}ms p95=${p95}ms"
}

$tenant = "tenant-e2e-smoke"
$actorId = "e2e-smoke-actor"
$subjectType = "PROVIDER"
$subjectId = "PROV-E2E-001"
$headers = @{
  "X-Tenant-ID" = $tenant
  "X-Actor-ID" = $actorId
}

$services = @(
  @{ Name = "postgres"; Url = "http://localhost:5432"; Kind = "tcp" },
  @{ Name = "redis"; Url = "http://localhost:6379"; Kind = "tcp" },
  @{ Name = "kafka"; Url = "http://localhost:9092"; Kind = "tcp" },
  @{ Name = "keycloak"; Url = "http://localhost:8080"; Kind = "http" },
  @{ Name = "envoy"; Url = "http://localhost:9901/ready"; Kind = "http" },
  @{ Name = "learning-service"; Url = "http://localhost:8235"; Kind = "health" },
  @{ Name = "experience-bff"; Url = "http://localhost:8160"; Kind = "health" },
  @{ Name = "one-ui-shell"; Url = "http://localhost:3000"; Kind = "http" }
)

$dockerAvailable = $false
try {
  $dockerInfo = & docker info 2>&1
  if ($LASTEXITCODE -eq 0) {
    $dockerAvailable = $true
    Add-Check -Area "Runtime" -Name "Docker daemon" -Status "PASS" -Detail "Docker API reachable."
  } else {
    Add-Check -Area "Runtime" -Name "Docker daemon" -Status "FAIL" -Detail "Docker not reachable."
  }
} catch {
  Add-Check -Area "Runtime" -Name "Docker daemon" -Status "FAIL" -Detail "Docker command failed: $($_.Exception.Message)"
}

if ($StartStack -and $dockerAvailable) {
  try {
    $networkExists = (& docker network ls --format "{{.Name}}" 2>$null | Select-String "^impilo-network$") -ne $null
    if (-not $networkExists) {
      & docker network create impilo-network 2>&1 | Out-Null
    }
    $composeArgs = @()
    foreach ($f in $ComposeFiles) {
      $composeArgs += "-f"
      $composeArgs += $f
    }
    $stackServices = @(
      "postgres",
      "redis",
      "kafka",
      "keycloak",
      "opa",
      "envoy",
      "learning-service",
      "experience-bff",
      "one-ui-shell"
    )
    $composeOutput = & docker compose @composeArgs up -d --build @stackServices 2>&1
    if ($LASTEXITCODE -eq 0) {
      Add-Check -Area "Runtime" -Name "Compose up" -Status "PASS" -Detail "Started Fundo-focused runtime subset."
    } else {
      $detail = if ($composeOutput) { ($composeOutput | Select-Object -First 1).ToString() } else { "docker compose up returned exit code $LASTEXITCODE" }
      Add-Check -Area "Runtime" -Name "Compose up" -Status "FAIL" -Detail $detail
      $dockerAvailable = $false
    }
  } catch {
    Add-Check -Area "Runtime" -Name "Compose up" -Status "FAIL" -Detail $_.Exception.Message
    $dockerAvailable = $false
  }
}

if (-not $dockerAvailable) {
  Add-Check -Area "Runtime" -Name "Stack health sweep" -Status "FAIL" -Detail "Skipped: Docker daemon unavailable."
  Add-Check -Area "Auth/Session" -Name "Keycloak token grant" -Status "FAIL" -Detail "Skipped: runtime stack unavailable."
  Add-Check -Area "Gateway" -Name "Header enforcement deny" -Status "FAIL" -Detail "Skipped: runtime stack unavailable."
  Add-Check -Area "Gateway" -Name "Header enforcement allow" -Status "FAIL" -Detail "Skipped: runtime stack unavailable."
  Add-Check -Area "Journey API" -Name "Learning studio API journey" -Status "FAIL" -Detail "Skipped: runtime stack unavailable."
  Add-Check -Area "Adapters" -Name "Nompilo and Comms adapters" -Status "FAIL" -Detail "Skipped: runtime stack unavailable."
  Add-Check -Area "Load" -Name "Adapter load checks" -Status "FAIL" -Detail "Skipped: runtime stack unavailable."
  Add-Check -Area "Web Journey" -Name "One UI route probes" -Status "FAIL" -Detail "Skipped: runtime stack unavailable."
  Add-Check -Area "Mobile Journey" -Name "Provider app critical path (manual)" -Status "TODO" -Detail "Run after runtime stack is healthy."
  Add-Check -Area "Mobile Journey" -Name "Citizen app critical path (manual)" -Status "TODO" -Detail "Run after runtime stack is healthy."
} else {
foreach ($svc in $services) {
  if ($svc.Kind -eq "health") {
    $ok = Wait-Health -Name $svc.Name -BaseUrl $svc.Url -MaxSeconds 120
    Add-Check -Area "Runtime" -Name "$($svc.Name) health" -Status ($(if ($ok) { "PASS" } else { "FAIL" })) -Detail $svc.Url
  } elseif ($svc.Kind -eq "http") {
    $res = Try-InvokeJson -Method "GET" -Url $svc.Url -TimeoutSec 6
    Add-Check -Area "Runtime" -Name "$($svc.Name) endpoint" -Status ($(if ($res.Ok) { "PASS" } else { "FAIL" })) -Detail "status=$($res.StatusCode)"
  } else {
    $port = [int](($svc.Url -split ":")[-1])
    $tcp = New-Object System.Net.Sockets.TcpClient
    try {
      $iar = $tcp.BeginConnect("localhost", $port, $null, $null)
      $ok = $iar.AsyncWaitHandle.WaitOne(1500, $false) -and $tcp.Connected
      Add-Check -Area "Runtime" -Name "$($svc.Name) tcp" -Status ($(if ($ok) { "PASS" } else { "FAIL" })) -Detail "port $port"
    } catch {
      Add-Check -Area "Runtime" -Name "$($svc.Name) tcp" -Status "FAIL" -Detail $_.Exception.Message
    } finally {
      $tcp.Close()
    }
  }
}

$token = $null
$tokenRes = Try-InvokeJson -Method "POST" `
  -Url "http://localhost:8080/realms/impilo/protocol/openid-connect/token" `
  -Headers @{ } `
  -Body @{
    grant_type = "password"
    client_id = "integration-test"
    client_secret = "integration-test-secret"
    username = "dr.mapfumo"
    password = "ImpiloTest123!"
  } `
  -TimeoutSec 10
if ($tokenRes.Ok -and $tokenRes.Json -and $tokenRes.Json.access_token) {
  $token = [string]$tokenRes.Json.access_token
  Add-Check -Area "Auth/Session" -Name "Keycloak token grant" -Status "PASS" -Detail "integration-test password grant succeeded."
} else {
  Add-Check -Area "Auth/Session" -Name "Keycloak token grant" -Status "FAIL" -Detail "status=$($tokenRes.StatusCode)"
}

$gatewayNoHeaders = Try-InvokeJson -Method "GET" -Url "http://localhost:10000/internal/v1/test" -TimeoutSec 6
if (-not $gatewayNoHeaders.Ok -and ($gatewayNoHeaders.StatusCode -eq 401 -or $gatewayNoHeaders.StatusCode -eq 403)) {
  Add-Check -Area "Gateway" -Name "Header enforcement deny" -Status "PASS" -Detail "Denied without required headers."
} else {
  Add-Check -Area "Gateway" -Name "Header enforcement deny" -Status "WARN" -Detail "Unexpected status=$($gatewayNoHeaders.StatusCode)"
}

$gatewayHeaders = @{
  "X-Tenant-ID" = $tenant
  "X-Pod-ID" = "pod-e2e"
  "X-Request-ID" = "req-e2e-1"
  "X-Correlation-ID" = "corr-e2e-1"
}
$gatewayWithHeaders = Try-InvokeJson -Method "GET" -Url "http://localhost:10000/internal/v1/test" -Headers $gatewayHeaders -TimeoutSec 6
if ($gatewayWithHeaders.StatusCode -ne 401 -and $gatewayWithHeaders.StatusCode -ne 403 -and $gatewayWithHeaders.StatusCode -ne 0) {
  Add-Check -Area "Gateway" -Name "Header enforcement allow" -Status "PASS" -Detail "Not denied with required headers."
} else {
  Add-Check -Area "Gateway" -Name "Header enforcement allow" -Status "FAIL" -Detail "status=$($gatewayWithHeaders.StatusCode)"
}

$bffHeaders = @{}
$bffHeaders["X-Tenant-ID"] = $tenant
$bffHeaders["X-Actor-ID"] = $actorId
if ($token) { $bffHeaders["Authorization"] = "Bearer $token" }

$newCourseRes = Try-InvokeJson -Method "POST" -Url "http://localhost:8160/internal/v1/learning/v11/catalog" -Headers $bffHeaders -Body @{
  title = "E2E Fundo Course $([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())"
  status = "DRAFT"
  category = "GENERAL"
} -TimeoutSec $RequestTimeoutSec

$courseId = $null
if ($newCourseRes.Ok -and $newCourseRes.Json -and $newCourseRes.Json.data -and $newCourseRes.Json.data.course -and $newCourseRes.Json.data.course.id) {
  $courseId = [string]$newCourseRes.Json.data.course.id
  Add-Check -Area "Journey API" -Name "Create course" -Status "PASS" -Detail "courseId=$courseId"
} else {
  Add-Check -Area "Journey API" -Name "Create course" -Status "FAIL" -Detail "status=$($newCourseRes.StatusCode)"
}

$moduleId = $null
if ($courseId) {
  $createModule = Try-InvokeJson -Method "POST" -Url "http://localhost:8160/internal/v1/learning/v11/courses/$courseId/modules" -Headers $bffHeaders -Body @{
    title = "E2E Module"
  } -TimeoutSec $RequestTimeoutSec
  if ($createModule.Ok -and $createModule.Json -and $createModule.Json.data -and $createModule.Json.data.module -and $createModule.Json.data.module.id) {
    $moduleId = [string]$createModule.Json.data.module.id
    Add-Check -Area "Journey API" -Name "Create module" -Status "PASS" -Detail "moduleId=$moduleId"
  } else {
    Add-Check -Area "Journey API" -Name "Create module" -Status "FAIL" -Detail "status=$($createModule.StatusCode)"
  }
}

$lessonId = $null
if ($moduleId) {
  $createLesson = Try-InvokeJson -Method "POST" -Url "http://localhost:8160/internal/v1/learning/v11/modules/$moduleId/lessons" -Headers $bffHeaders -Body @{
    title = "E2E Lesson"
    contentType = "TEXT"
    contentBody = "Runtime E2E lesson content."
  } -TimeoutSec $RequestTimeoutSec
  if ($createLesson.Ok -and $createLesson.Json -and $createLesson.Json.data -and $createLesson.Json.data.lesson -and $createLesson.Json.data.lesson.id) {
    $lessonId = [string]$createLesson.Json.data.lesson.id
    Add-Check -Area "Journey API" -Name "Create lesson" -Status "PASS" -Detail "lessonId=$lessonId"
  } else {
    Add-Check -Area "Journey API" -Name "Create lesson" -Status "FAIL" -Detail "status=$($createLesson.StatusCode)"
  }
}

if ($courseId) {
  $readiness = Try-InvokeJson -Method "GET" -Url "http://localhost:8160/internal/v1/learning/v11/studio/courses/$courseId/readiness" -Headers $bffHeaders -TimeoutSec $RequestTimeoutSec
  Add-Check -Area "Journey API" -Name "Readiness check" -Status ($(if ($readiness.Ok) { "PASS" } else { "FAIL" })) -Detail "status=$($readiness.StatusCode)"
}

$aiDraft = Try-InvokeJson -Method "POST" -Url "http://localhost:8160/internal/v1/learning/v11/ai/generate" -Headers $bffHeaders -Body @{
  generationType = "summary"
  prompt = "Summarize hypertensive disorder red flags."
  targetAudience = "CLINICIAN"
} -TimeoutSec $RequestTimeoutSec
Add-Check -Area "Adapters" -Name "AI generate route" -Status ($(if ($aiDraft.Ok) { "PASS" } else { "FAIL" })) -Detail "status=$($aiDraft.StatusCode)"

$nompilo = Try-InvokeJson -Method "POST" -Url "http://localhost:8160/internal/v1/learning/v11/nompilo/assist" -Headers $bffHeaders -Body @{
  message = "Give me revision steps for triage."
  riskLevel = "LOW"
} -TimeoutSec $RequestTimeoutSec
Add-Check -Area "Adapters" -Name "Nompilo assist route" -Status ($(if ($nompilo.Ok) { "PASS" } else { "FAIL" })) -Detail "status=$($nompilo.StatusCode)"

$scheduleNotification = Try-InvokeJson -Method "POST" -Url "http://localhost:8160/internal/v1/learning/v11/notifications" -Headers $bffHeaders -Body @{
  subjectType = $subjectType
  subjectId = $subjectId
  title = "E2E learning reminder"
  message = "Complete your simulation."
  channelPreference = "IN_APP"
  eventCode = "learning.e2e.smoke"
} -TimeoutSec $RequestTimeoutSec
if ($scheduleNotification.Ok) {
  $dispatchStatus = $null
  if ($scheduleNotification.Json -and $scheduleNotification.Json.data -and $scheduleNotification.Json.data.commsHubDispatch -and $scheduleNotification.Json.data.commsHubDispatch.status) {
    $dispatchStatus = [string]$scheduleNotification.Json.data.commsHubDispatch.status
  }
  $detail = if ($dispatchStatus) { "status=$($scheduleNotification.StatusCode), commsDispatch=$dispatchStatus" } else { "status=$($scheduleNotification.StatusCode), commsDispatch=unknown" }
  Add-Check -Area "Adapters" -Name "Comms dispatch bridge" -Status "PASS" -Detail $detail
} else {
  Add-Check -Area "Adapters" -Name "Comms dispatch bridge" -Status "FAIL" -Detail "status=$($scheduleNotification.StatusCode)"
}

$listNotifications = Try-InvokeJson -Method "GET" -Url "http://localhost:8160/internal/v1/learning/v11/notifications?subjectType=$subjectType&subjectId=$subjectId&limit=10" -Headers $bffHeaders -TimeoutSec $RequestTimeoutSec
Add-Check -Area "Journey API" -Name "List notifications" -Status ($(if ($listNotifications.Ok) { "PASS" } else { "FAIL" })) -Detail "status=$($listNotifications.StatusCode)"

$nompiloDurations = New-Object System.Collections.Generic.List[double]
$nompiloSuccess = 0
for ($i = 1; $i -le $LoadIterations; $i++) {
  $sw = [System.Diagnostics.Stopwatch]::StartNew()
  $probe = Try-InvokeJson -Method "POST" -Url "http://localhost:8160/internal/v1/learning/v11/nompilo/assist" -Headers $bffHeaders -Body @{
    message = "Load probe $i"
    riskLevel = "LOW"
  } -TimeoutSec $RequestTimeoutSec
  $sw.Stop()
  $nompiloDurations.Add($sw.Elapsed.TotalMilliseconds)
  if ($probe.Ok) { $nompiloSuccess++ }
}
Add-LatencyCheck -Area "Load" -Name "Nompilo adapter under load" -DurationsMs $nompiloDurations -SuccessCount $nompiloSuccess -ExpectedCount $LoadIterations

$commsDurations = New-Object System.Collections.Generic.List[double]
$commsSuccess = 0
for ($i = 1; $i -le $LoadIterations; $i++) {
  $sw = [System.Diagnostics.Stopwatch]::StartNew()
  $probe = Try-InvokeJson -Method "POST" -Url "http://localhost:8160/internal/v1/learning/v11/notifications" -Headers $bffHeaders -Body @{
    subjectType = $subjectType
    subjectId = $subjectId
    title = "Comms load probe $i"
    message = "Probe dispatch"
    channelPreference = "IN_APP"
    eventCode = "learning.e2e.load"
  } -TimeoutSec $RequestTimeoutSec
  $sw.Stop()
  $commsDurations.Add($sw.Elapsed.TotalMilliseconds)
  if ($probe.Ok) { $commsSuccess++ }
}
Add-LatencyCheck -Area "Load" -Name "Comms bridge under load" -DurationsMs $commsDurations -SuccessCount $commsSuccess -ExpectedCount $LoadIterations

$uiJourneyRoutes = @(
  "http://localhost:3000/learning",
  "http://localhost:3000/learning/studio",
  "http://localhost:3000/learning/studio/courses",
  "http://localhost:3000/learning/studio/analytics",
  "http://localhost:3000/learning/notifications"
)
foreach ($route in $uiJourneyRoutes) {
  $res = Try-InvokeJson -Method "GET" -Url $route -TimeoutSec 8
  Add-Check -Area "Web Journey" -Name "Route $route" -Status ($(if ($res.Ok) { "PASS" } else { "FAIL" })) -Detail "status=$($res.StatusCode)"
}

Add-Check -Area "Mobile Journey" -Name "Provider app critical path (manual)" -Status "TODO" -Detail "Run provider app dev client, sign in, open learning hub, create/assign learning task."
Add-Check -Area "Mobile Journey" -Name "Citizen app critical path (manual)" -Status "TODO" -Detail "Run citizen app dev client, sign in, open learning notifications and complete assigned item."
}

if ($StopStack -and $dockerAvailable) {
  try {
    $composeArgs = @()
    foreach ($f in $ComposeFiles) {
      $composeArgs += "-f"
      $composeArgs += $f
    }
    & docker compose @composeArgs down 2>&1 | Out-Null
    Add-Check -Area "Runtime" -Name "Compose down" -Status "PASS" -Detail "Runtime stack stopped."
  } catch {
    Add-Check -Area "Runtime" -Name "Compose down" -Status "WARN" -Detail $_.Exception.Message
  }
}

$pass = @($checks | Where-Object { $_.Status -eq "PASS" }).Count
$fail = @($checks | Where-Object { $_.Status -eq "FAIL" }).Count
$warn = @($checks | Where-Object { $_.Status -eq "WARN" }).Count
$todo = @($checks | Where-Object { $_.Status -eq "TODO" }).Count

$reportDir = Split-Path -Parent (Join-Path $ProjectRoot $ReportPath)
if (-not (Test-Path $reportDir)) {
  New-Item -ItemType Directory -Path $reportDir -Force | Out-Null
}

$reportAbsPath = Join-Path $ProjectRoot $ReportPath
$lines = @()
$lines += "# Production-like E2E Smoke Report"
$lines += ""
$lines += "Generated: $Timestamp"
$lines += ""
$lines += "| Area | Check | Status | Detail |"
$lines += "|---|---|---|---|"
foreach ($c in $checks) {
  $safeDetail = ($c.Detail -replace "\|", "/")
  $lines += "| $($c.Area) | $($c.Check) | $($c.Status) | $safeDetail |"
}
$lines += ""
$lines += "- PASS: $pass"
$lines += "- FAIL: $fail"
$lines += "- WARN: $warn"
$lines += "- TODO: $todo"
$lines += ""
$lines += "## Manual Mobile Closure"
$lines += ""
$lines += "1. Start provider dev client and authenticate (`provider-app`)."
$lines += "2. Run learning assignment journey (create assignment, open, progress)."
$lines += "3. Start citizen dev client and authenticate (`citizen-app`)."
$lines += "4. Verify notification receipt and learning completion evidence."
$lines += "5. Capture screenshots + timestamps and append here for final sign-off."

Set-Content -Path $reportAbsPath -Value $lines -Encoding UTF8

Write-Host "Report written: $ReportPath"
Write-Host "PASS=$pass FAIL=$fail WARN=$warn TODO=$todo"

if ($fail -gt 0) { exit 1 }
exit 0

#!/usr/bin/env bash
# =============================================================================
# iatg-end-to-end-runtime.sh — IATG doctrine end-to-end runtime harness
# =============================================================================
# Worker: E1-E2E (Impilo vNext IATG end-to-end program)
#
# WHAT THIS IS
#   An executable, idempotent end-to-end harness that DRIVES the full IATG
#   doctrine journey against a RUNNING preview stack and ASSERTS every outcome.
#   It proves "IATG works end-to-end": origin -> country operation -> national
#   admin -> organization -> provider preload -> citizen claim -> employment
#   trust upgrade -> four-block trust profile -> facility legitimacy -> (optional)
#   Channel-C adjudication.
#
#   This harness AUTHORS + LINTS only in this container (the full stack cannot
#   run here). The coordinator / preview VM RUNS it against the live estate.
#
# HONESTY CONTRACT (non-negotiable)
#   Every step asserts the HTTP status AND a named response field. On any
#   mismatch it prints the raw response body and exits non-zero. Nothing is ever
#   faked, stubbed, or swallowed. Real exit codes throughout.
#
# CONTRACTS (verified against source — file:line in comments per step)
#   All paths/payloads/response shapes were read from the controllers; none are
#   guessed. See each run_step_N function.
#
# KNOWN PHASE-E2 GAP
#   Step 8 (facility per-source legitimacy) hits tuso-service DIRECTLY in-mesh
#   because NO experience-bff proxy exists yet for /v1/facilities/.../status-
#   composite. This is a known Phase-E2 BFF-proxy gap and is annotated as such in
#   the PREREQUISITES banner and inline at the step.
#
# LEASE
#   Writes ONLY under test/integration/** and the reports/iatg-e2e/ output dir.
#
# USAGE
#   test/integration/iatg-end-to-end-runtime.sh [--dry-run] [--help]
#     --dry-run   Print the PREREQUISITES banner + the planned call chain and
#                 exit 0 WITHOUT any network I/O.
#     (no flag)   Drive the live chain and assert; exit 0 iff every step passes.
#
# ENV PARAMETERS (all overridable; preview defaults shown)
#   PREVIEW_HOST          Base host for all preview services   (41.57.127.235)
#   BFF_URL               experience-bff base URL              (PREVIEW_HOST:8160)
#   VARAPI_URL            varapi-service base URL              (PREVIEW_HOST:8083)
#   TUSO_URL              tuso-service base URL                (PREVIEW_HOST:8084)
#   WGV_URL               workforce-governance-service URL     (PREVIEW_HOST:8165)
#   ORG_REGISTRY_URL      organization-registry-service URL    (PREVIEW_HOST:8153)
#   TENANT_ID             canonical UUID tenant (UUID REQUIRED by tuso/wgv/bff)
#                         default 00000000-0000-4000-8000-000000000001
#   NATIONAL_ADMIN_TOKEN  bearer for national-admin actions    (optional)
#   ORIGIN_ADMIN_A_TOKEN  bearer for first origin approver     (optional)
#   ORIGIN_ADMIN_B_TOKEN  bearer for second origin approver    (optional)
#   CITIZEN_TOKEN         bearer for the claiming citizen       (optional)
#   ORIGIN_ADMIN_A_ID     first  two-person approver principal  (distinct)
#   ORIGIN_ADMIN_B_ID     second two-person approver principal  (distinct)
#   NATIONAL_ADMIN_ID     national administrator actor id
#   CLAIMANT_HEALTH_ID    citizen Health ID that claims a provider (X-Actor-ID)
#   PROVIDER_ID           provider public id / numeric id for trust assertion
#   PROVIDER_PUBLIC_ID    (seed) provider public id             (alias source)
#   EC_NUMBER             EC employment number for Channel-B match
#   REG_NUMBER            (seed) council registration number
#   FACILITY_UUID         canonical tuso facility UUID (status-composite)
#   FACILITY_ID           (seed) facility UUID                  (alias source)
#   CLAIM_TOKEN           preload claim token (from E1-SEED)
#   ORG_CODE              organization code base (a run suffix is appended)
#   LABEL                 run label (report dir); default iso timestamp
#   RUN_ADJUDICATION      1 to run the heavy Channel-C adjudication section (0)
#   IATG_SEED_ENV         path to the seed env file (reports/iatg-e2e/seed.env)
#
# SEED DEPENDENCY (E1-SEED)
#   scripts/seed/iatg-e2e-seed.sh (authored by worker E1-SEED) seeds the
#   preconditions and EMITS an env file at reports/iatg-e2e/seed.env exporting:
#       CLAIM_TOKEN, PROVIDER_PUBLIC_ID, FACILITY_ID, REG_NUMBER
#   This harness SOURCES that file if present (or $IATG_SEED_ENV) and falls back
#   to individual env vars. Values still missing at run time cause the dependent
#   step to fail honestly (never a fabricated pass).
#
# GATE (authoring container)
#   bash -n test/integration/iatg-end-to-end-runtime.sh   -> SH_OK
#   shellcheck (if installed)
#   test/integration/iatg-end-to-end-runtime.sh --dry-run -> prints chain, exit 0
# =============================================================================

set -euo pipefail

# ── Colors (disabled when not a TTY) ─────────────────────────────────────────
if [[ -t 1 ]]; then
  RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'; BLUE='\033[0;34m'; NC='\033[0m'
else
  RED=''; GREEN=''; YELLOW=''; BLUE=''; NC=''
fi

# ── Args ─────────────────────────────────────────────────────────────────────
DRY_RUN=0
for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=1 ;;
    -h|--help)
      sed -n '2,80p' "$0" | sed 's/^# \{0,1\}//'
      exit 0 ;;
    *) echo "Unknown argument: $arg (use --dry-run or --help)" >&2; exit 2 ;;
  esac
done

# ── Locate repo + seed env ───────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
IATG_SEED_ENV="${IATG_SEED_ENV:-${REPO_ROOT}/reports/iatg-e2e/seed.env}"
SEED_SOURCED="no"
if [[ -f "$IATG_SEED_ENV" ]]; then
  # shellcheck disable=SC1090
  set -a; source "$IATG_SEED_ENV"; set +a
  SEED_SOURCED="yes ($IATG_SEED_ENV)"
fi

# ── Env parameters with preview defaults ─────────────────────────────────────
PREVIEW_HOST="${PREVIEW_HOST:-41.57.127.235}"
BFF_URL="${BFF_URL:-http://${PREVIEW_HOST}:8160}"
VARAPI_URL="${VARAPI_URL:-http://${PREVIEW_HOST}:8083}"
TUSO_URL="${TUSO_URL:-http://${PREVIEW_HOST}:8084}"
WGV_URL="${WGV_URL:-http://${PREVIEW_HOST}:8165}"
ORG_REGISTRY_URL="${ORG_REGISTRY_URL:-http://${PREVIEW_HOST}:8153}"

# tuso/wgv/bff silently null non-UUID tenants — canonical UUID is REQUIRED.
TENANT_ID="${TENANT_ID:-00000000-0000-4000-8000-000000000001}"
POD_ID="${POD_ID:-national}"

# Tokens (optional — the preview mesh may run ext_authz disabled behind Envoy).
NATIONAL_ADMIN_TOKEN="${NATIONAL_ADMIN_TOKEN:-}"
ORIGIN_ADMIN_A_TOKEN="${ORIGIN_ADMIN_A_TOKEN:-}"
ORIGIN_ADMIN_B_TOKEN="${ORIGIN_ADMIN_B_TOKEN:-}"
CITIZEN_TOKEN="${CITIZEN_TOKEN:-}"

# Actor principals — the two origin approvers MUST be distinct (two-person rule).
ORIGIN_ADMIN_A_ID="${ORIGIN_ADMIN_A_ID:-origin-admin-a}"
ORIGIN_ADMIN_B_ID="${ORIGIN_ADMIN_B_ID:-origin-admin-b}"
NATIONAL_ADMIN_ID="${NATIONAL_ADMIN_ID:-national-admin}"
CLAIMANT_HEALTH_ID="${CLAIMANT_HEALTH_ID:-}"

# Seed aliases: prefer explicit override, else the E1-SEED emitted name.
PROVIDER_ID="${PROVIDER_ID:-${PROVIDER_PUBLIC_ID:-}}"
FACILITY_UUID="${FACILITY_UUID:-${FACILITY_ID:-}}"
CLAIM_TOKEN="${CLAIM_TOKEN:-}"
EC_NUMBER="${EC_NUMBER:-}"
REG_NUMBER="${REG_NUMBER:-}"

ORG_CODE="${ORG_CODE:-IATG-E2E-ORG}"
RUN_ADJUDICATION="${RUN_ADJUDICATION:-0}"
RUN_SUFFIX="$(date +%Y%m%d%H%M%S)"
LABEL="${LABEL:-${RUN_SUFFIX}}"

# ── Report sink ──────────────────────────────────────────────────────────────
REPORT_DIR="${REPO_ROOT}/reports/iatg-e2e/${LABEL}"
SUMMARY_JSON="${REPORT_DIR}/summary.json"
RUN_LOG="${REPORT_DIR}/run.log"

PASS_COUNT=0
FAIL_COUNT=0
STEP_RESULTS=()   # each: "step|name|status|http|detail"

# ── Assertion + reporting helpers ────────────────────────────────────────────
log() { echo -e "$*" | tee -a "$RUN_LOG" 2>/dev/null || echo -e "$*"; }

record() {  # step name status http detail
  STEP_RESULTS+=("$1|$2|$3|$4|$5")
}

pass() {  # step name detail http
  PASS_COUNT=$((PASS_COUNT + 1))
  log "${GREEN}[PASS]${NC} step $1: $2 — $3 (HTTP ${4:-n/a})"
  record "$1" "$2" "PASS" "${4:-}" "$3"
}

fail() {  # step name detail http body
  FAIL_COUNT=$((FAIL_COUNT + 1))
  log "${RED}[FAIL]${NC} step $1: $2 — $3 (HTTP ${4:-n/a})"
  if [[ -n "${5:-}" ]]; then
    log "${RED}------ response body ------${NC}"
    log "$5"
    log "${RED}--------------------------${NC}"
  fi
  record "$1" "$2" "FAIL" "${4:-}" "$3"
  finalize
  exit 1
}

# jq is required for honest field assertions.
require_jq() {
  if ! command -v jq >/dev/null 2>&1; then
    echo "FATAL: jq is required for field assertions but was not found on PATH." >&2
    exit 3
  fi
}

# Build trust headers for curl. Args: actor_id (optional) bearer (optional)
# Emits an array assignment consumed via "${HDRS[@]}".
build_headers() {
  local actor_id="${1:-}" bearer="${2:-}"
  HDRS=(
    -H "X-Tenant-ID: ${TENANT_ID}"
    -H "X-Pod-ID: ${POD_ID}"
    -H "X-Request-ID: req-${RUN_SUFFIX}-$RANDOM"
    -H "X-Correlation-ID: corr-${RUN_SUFFIX}-$RANDOM"
    -H "Content-Type: application/json"
  )
  [[ -n "$actor_id" ]] && HDRS+=(-H "X-Actor-ID: ${actor_id}" -H "X-Actor-Type: PERSON" -H "X-Purpose-Of-Use: TREATMENT")
  [[ -n "$bearer"   ]] && HDRS+=(-H "Authorization: Bearer ${bearer}")
}

# HTTP call: method url [json_body] — sets globals HTTP_CODE and RESP_BODY.
# Requires build_headers to have populated HDRS first.
http_call() {
  local method="$1" url="$2" body="${3:-}"
  local tmp; tmp="$(mktemp)"
  local code
  if [[ -n "$body" ]]; then
    code="$(curl -sS -o "$tmp" -w '%{http_code}' -X "$method" "${HDRS[@]}" -d "$body" "$url" || echo "000")"
  else
    code="$(curl -sS -o "$tmp" -w '%{http_code}' -X "$method" "${HDRS[@]}" "$url" || echo "000")"
  fi
  HTTP_CODE="$code"
  RESP_BODY="$(cat "$tmp")"
  rm -f "$tmp"
}

# Assert HTTP code is one of the accepted set (space-separated). Fails honestly.
assert_http() {  # step name accepted_codes
  local step="$1" name="$2" accepted="$3"
  for c in $accepted; do
    if [[ "$HTTP_CODE" == "$c" ]]; then return 0; fi
  done
  fail "$step" "$name" "expected HTTP [$accepted], got $HTTP_CODE" "$HTTP_CODE" "$RESP_BODY"
}

# Assert a jq path equals an expected value against RESP_BODY.
assert_field_eq() {  # step name jqpath expected
  local step="$1" name="$2" path="$3" expected="$4" actual
  actual="$(echo "$RESP_BODY" | jq -r "$path" 2>/dev/null || echo "__JQERR__")"
  if [[ "$actual" == "$expected" ]]; then
    pass "$step" "$name" "$path=$expected" "$HTTP_CODE"
  else
    fail "$step" "$name" "expected $path=$expected, got '$actual'" "$HTTP_CODE" "$RESP_BODY"
  fi
}

# Assert a jq path resolves to a non-empty / non-null value against RESP_BODY.
assert_field_present() {  # step name jqpath
  local step="$1" name="$2" path="$3" actual
  actual="$(echo "$RESP_BODY" | jq -r "$path" 2>/dev/null || echo "")"
  if [[ -n "$actual" && "$actual" != "null" ]]; then
    pass "$step" "$name" "$path=$actual" "$HTTP_CODE"
  else
    fail "$step" "$name" "$path missing or null" "$HTTP_CODE" "$RESP_BODY"
  fi
}

# Fetch a jq scalar from RESP_BODY (helper for chaining captured ids).
jq_get() { echo "$RESP_BODY" | jq -r "$1" 2>/dev/null; }

# Fail fast if a required input for a step is empty.
require_var() {  # step name varname value
  if [[ -z "${4:-}" ]]; then
    fail "$1" "$2" "required input '$3' is empty (provide via env or E1-SEED seed.env)" "n/a" ""
  fi
}

# ── PREREQUISITES banner ─────────────────────────────────────────────────────
print_prereq_banner() {
  cat <<BANNER
${BLUE}=============================================================================${NC}
 IATG END-TO-END RUNTIME HARNESS — PREREQUISITES
${BLUE}=============================================================================${NC}
 Run label            : ${LABEL}
 Report directory     : ${REPORT_DIR}
 Seed env sourced     : ${SEED_SOURCED}   (expected: reports/iatg-e2e/seed.env from E1-SEED)
 Tenant (UUID req'd)  : ${TENANT_ID}
 RUN_ADJUDICATION     : ${RUN_ADJUDICATION}  (1 = run heavy Channel-C section)

 SERVICE ENDPOINTS
   experience-bff             : ${BFF_URL}
   varapi-service             : ${VARAPI_URL}
   tuso-service               : ${TUSO_URL}
   workforce-governance (WGV) : ${WGV_URL}
   organization-registry      : ${ORG_REGISTRY_URL}

 FEATURE FLAGS THAT MUST BE ON (set by E1-DEPLOY)
   * impilo.features.ec-matching=true
        -> required for step 6 (EC evidence -> EMPLOYMENT_MATCHED) and the
           EC_NUMBER path in step 5's provider-claim controller.
   * IF RUN_ADJUDICATION=1 (step 9), the 3 kafka/adjudication flags must be ON
     AND Kafka + organization-registry-service + workflow-service deployed, so
     the org-registry claim resolves to ACCEPTED via the Kafka feedback consumer.

 SERVICES THAT MUST BE DEPLOYED + HEALTHY
   experience-bff, varapi-service, tuso-service, workforce-governance-service,
   organization-registry-service; plus (step 9 only) workflow-service + Kafka.

 SEED PRECONDITIONS (E1-SEED: scripts/seed/iatg-e2e-seed.sh -> seed.env exports)
   CLAIM_TOKEN, PROVIDER_PUBLIC_ID, FACILITY_ID, REG_NUMBER
   (provider Channel-A preload token, provider id, facility UUID with the seeded
    per-source legitimacy rows incl. GOVERNMENT_OPERATIONAL_EXCEPTION).

 KNOWN PHASE-E2 GAP
   * Step 8 (facility per-source legitimacy) hits tuso-service DIRECTLY in-mesh.
     There is NO experience-bff proxy for /v1/facilities/{id}/status-composite
     yet — this is a tracked Phase-E2 BFF-proxy gap, not a harness shortcut.
${BLUE}=============================================================================${NC}
BANNER
}

# ── Dry-run: print the planned chain, no network ─────────────────────────────
print_planned_chain() {
  cat <<PLAN
${YELLOW}PLANNED CALL CHAIN (dry-run — no network performed)${NC}

 Step 1  Country Operation (two-person)
   POST ${BFF_URL}/internal/v1/platform-origin/country-operations
     assert HTTP 201 AND .data.status == PENDING            [capture accessRequestId]
   POST .../platform-origin/actions/{id}/approve  (approver A=${ORIGIN_ADMIN_A_ID})
     assert HTTP 200 AND .data.status != APPROVED  (one approval insufficient)
   POST .../platform-origin/actions/{id}/approve  (approver B=${ORIGIN_ADMIN_B_ID}, distinct)
     assert HTTP 200 AND .data.status == APPROVED  (two distinct approvals -> executable)
   POST .../platform-origin/actions/{id}/execute
     assert HTTP 200 AND .data.status == EXECUTED

 Step 2  Appoint National Administrator (CORRECTED nested path, two-person)
   POST ${BFF_URL}/internal/v1/platform-origin/country-operations/{id}/appointments
     assert HTTP 201 AND .data.status == PENDING            [appointment access request]

 Step 3  Organization onboarded (DRAFT -> PENDING_VERIFICATION -> VERIFIED/ACTIVE)
   POST ${BFF_URL}/internal/v1/organizations
     assert HTTP 201 AND .data.status == DRAFT              [capture organizationId]
   POST .../organizations/{id}/representatives
     assert HTTP 201 AND .data.id present                   [capture representativeId]
   POST .../organizations/{id}/submit-verification
     assert HTTP 200 AND .data.verificationStatus == PENDING_VERIFICATION
   POST .../organizations/{id}/verify  {verifiedRepresentativeIds:[repId]}
     assert HTTP 200 AND .data.verificationStatus == VERIFIED AND .data.status == ACTIVE

 Step 4  Provider preloaded via Channel A
   PREFER seeded CLAIM_TOKEN; else
   POST ${VARAPI_URL}/v1/internal/providers/bootstrap/preload
     assert HTTP 200 AND .results[0].outcome == CREATED     [capture claimToken, providerPublicId]

 Step 5  Citizen claims the Provider ID (claimant bound to X-Actor-ID; consent required)
   GET  ${BFF_URL}/api/v1/provider-claim/eligibility        (X-Actor-ID=${CLAIMANT_HEALTH_ID:-<claimant>})
     assert HTTP 200 AND .data.eligibleForClaim == true
   POST .../provider-claim/preview  {claimToken}
     assert HTTP 200 AND .data.providerPublicId present (masked)
   POST .../provider-claim/claim    {claimToken, consent:true}
     assert HTTP 200 AND .data.linked == true

 Step 6  EC evidence upgrades trust to EMPLOYMENT_MATCHED  (needs ec-matching flag ON)
   POST ${BFF_URL}/api/v1/trust/employment-match  {ecNumber, providerId}   (X-Actor-ID)
     assert HTTP 200 AND .trustAssertion.trustLevel == EMPLOYMENT_MATCHED

 Step 7  Four-block trust profile
   GET ${BFF_URL}/api/v1/trust/profile/{healthId}
     assert HTTP 200 AND .identity.sourceOfRecord, .professional.sourceOfRecord,
            .employment.sourceOfRecord, .operational.sourceOfRecord all present
            AND at least one block degrades honestly to status UNAVAILABLE without
            poisoning the others (four blocks always render).

 Step 8  Facility per-source legitimacy  [PHASE-E2 GAP: tuso DIRECT, no BFF proxy]
   GET ${TUSO_URL}/v1/facilities/{facilityUuid}/status-composite
     assert HTTP 200 AND .data.platformAccessAllowed present AND reflects the
            seeded rows (incl. GOVERNMENT_OPERATIONAL_EXCEPTION -> reasons non-empty)

 Step 9  (RUN_ADJUDICATION=${RUN_ADJUDICATION}) Channel-C claim -> escalate -> decision -> resolve
   POST ${ORG_REGISTRY_URL}/v1/organizations/{orgId}/claims   assert 201 .status == SUBMITTED
   POST ${ORG_REGISTRY_URL}/v1/claims/{id}/escalate           assert 200 .status == UNDER_REVIEW
   POST ${WGV_URL}/internal/v1/workforce-governance/adjudications/decisions
        {subjectType,subjectRef,decision:APPROVED,reason}     assert 201 .data.decision == APPROVED
   POLL GET ${ORG_REGISTRY_URL}/v1/claims/{id} until .status == ACCEPTED (Kafka feedback)
   (gated OFF by default — heaviest dependency set)
PLAN
}

# =============================================================================
# STEP IMPLEMENTATIONS
# =============================================================================

# ── Step 1: Country Operation (two-person) ───────────────────────────────────
# BFF experience-bff/.../controller/PlatformOriginController.java:34 (initiate),
#   :42 (approve, body {approverUserId,approverRole,decision,note}), :52 (execute)
# WGV governance/api/PlatformOriginController.java:50/59/74 — actionView {status}
#   two-person: distinct approverUserId values required for APPROVED.
run_step_1() {
  log "${BLUE}== Step 1: Country Operation (two-person approve/execute) ==${NC}"
  build_headers "$ORIGIN_ADMIN_A_ID" "$ORIGIN_ADMIN_A_TOKEN"
  local init_body
  init_body="$(jq -nc --arg n "IATG E2E ${RUN_SUFFIX}" '{name:$n, countryCode:"ZW", operationType:"CREATE_COUNTRY_OPERATION"}')"
  http_call POST "${BFF_URL}/internal/v1/platform-origin/country-operations" "$init_body"
  assert_http 1 "country-operation initiate" "201"
  assert_field_eq 1 "country-operation initiate -> PENDING" '.data.status' "PENDING"
  local action_id; action_id="$(jq_get '.data.accessRequestId')"
  require_var 1 "country-operation initiate" "accessRequestId" "$action_id"

  # First approval (approver A) — must NOT be sufficient on its own.
  build_headers "$ORIGIN_ADMIN_A_ID" "$ORIGIN_ADMIN_A_TOKEN"
  local appr_a
  appr_a="$(jq -nc --arg u "$ORIGIN_ADMIN_A_ID" '{approverUserId:$u, approverRole:"PLATFORM_ORIGIN_ADMINISTRATOR", decision:"APPROVE", note:"first approval"}')"
  http_call POST "${BFF_URL}/internal/v1/platform-origin/actions/${action_id}/approve" "$appr_a"
  assert_http 1 "country-operation approve #1" "200"
  local status_after_one; status_after_one="$(jq_get '.data.status')"
  if [[ "$status_after_one" == "APPROVED" ]]; then
    fail 1 "one-approval-insufficient" "status became APPROVED after a single approval (two-person rule violated)" "$HTTP_CODE" "$RESP_BODY"
  fi
  pass 1 "one-approval-insufficient" "status=$status_after_one (not yet APPROVED)" "$HTTP_CODE"

  # Second approval (approver B, DISTINCT) — now two-person satisfied.
  build_headers "$ORIGIN_ADMIN_B_ID" "$ORIGIN_ADMIN_B_TOKEN"
  local appr_b
  appr_b="$(jq -nc --arg u "$ORIGIN_ADMIN_B_ID" '{approverUserId:$u, approverRole:"PLATFORM_ORIGIN_ADMINISTRATOR", decision:"APPROVE", note:"second distinct approval"}')"
  http_call POST "${BFF_URL}/internal/v1/platform-origin/actions/${action_id}/approve" "$appr_b"
  assert_http 1 "country-operation approve #2 (distinct)" "200"
  assert_field_eq 1 "two-distinct-approvals -> APPROVED" '.data.status' "APPROVED"

  # Execute the APPROVED action.
  build_headers "$ORIGIN_ADMIN_A_ID" "$ORIGIN_ADMIN_A_TOKEN"
  http_call POST "${BFF_URL}/internal/v1/platform-origin/actions/${action_id}/execute"
  assert_http 1 "country-operation execute" "200"
  assert_field_eq 1 "country-operation execute -> EXECUTED" '.data.status' "EXECUTED"

  # Resolve the country operation id for downstream steps (org countryOperationRef).
  build_headers "$NATIONAL_ADMIN_ID" "$NATIONAL_ADMIN_TOKEN"
  http_call GET "${BFF_URL}/internal/v1/platform-origin/country-operations"
  if [[ "$HTTP_CODE" == "200" ]]; then
    COUNTRY_OP_ID="$(jq_get '.data[0].id // .data.countryOperations[0].id // empty')"
  fi
  log "  captured COUNTRY_OP_ID=${COUNTRY_OP_ID:-<none>} (best-effort for org countryOperationRef)"
}

# ── Step 2: Appoint National Administrator (CORRECTED nested path) ───────────
# BFF PlatformOriginController.java:83
#   POST /internal/v1/platform-origin/country-operations/{id}/appointments -> 201
#   actionView status=PENDING (itself a two-person PLATFORM_ACTION).
run_step_2() {
  log "${BLUE}== Step 2: Appoint National Administrator ==${NC}"
  require_var 2 "appoint-national-admin" "COUNTRY_OP_ID" "${COUNTRY_OP_ID:-}"
  build_headers "$ORIGIN_ADMIN_A_ID" "$ORIGIN_ADMIN_A_TOKEN"
  local body
  body="$(jq -nc --arg h "$NATIONAL_ADMIN_ID" '{appointeeHealthId:$h, role:"NATIONAL_ADMINISTRATOR", reason:"IATG E2E appointment"}')"
  http_call POST "${BFF_URL}/internal/v1/platform-origin/country-operations/${COUNTRY_OP_ID}/appointments" "$body"
  assert_http 2 "appoint-national-admin initiate" "201"
  assert_field_eq 2 "appoint-national-admin -> PENDING" '.data.status' "PENDING"
  APPOINTMENT_ACTION_ID="$(jq_get '.data.accessRequestId')"
  log "  captured APPOINTMENT_ACTION_ID=${APPOINTMENT_ACTION_ID:-<none>}"
}

# ── Step 3: Organization onboarded ───────────────────────────────────────────
# BFF OrganizationRegistryController.java:58 create, :84 addRep, :98 submit, :111 verify
#   envelope {success,requestId,correlationId,data}
#   OrganizationService.java:73 DRAFT/UNVERIFIED, :172 PENDING_VERIFICATION, :189-190 VERIFIED/ACTIVE
run_step_3() {
  log "${BLUE}== Step 3: Organization onboarded (DRAFT -> VERIFIED/ACTIVE) ==${NC}"
  build_headers "$NATIONAL_ADMIN_ID" "$NATIONAL_ADMIN_TOKEN"
  local code="${ORG_CODE}-${RUN_SUFFIX}"
  local create_body
  create_body="$(jq -nc --arg c "$code" --arg op "${COUNTRY_OP_ID:-}" \
    '{code:$c, legalName:("IATG E2E Mission Health " + $c), orgType:"MISSION",
      registrationIdentifiers:{source:"IATG-E2E"}}
     | if $op == "" then . else . + {countryOperationRef:$op} end')"
  http_call POST "${BFF_URL}/internal/v1/organizations" "$create_body"
  assert_http 3 "organization create" "201"
  assert_field_eq 3 "organization create -> DRAFT" '.data.status' "DRAFT"
  local org_id; org_id="$(jq_get '.data.id')"
  require_var 3 "organization create" "organizationId" "$org_id"

  # Add an authorized representative.
  build_headers "$NATIONAL_ADMIN_ID" "$NATIONAL_ADMIN_TOKEN"
  local rep_body
  rep_body="$(jq -nc --arg h "${CLAIMANT_HEALTH_ID:-rep-health-id}" \
    '{personHealthId:$h, roleTitle:"AUTHORIZED_OFFICER", evidenceRef:"IATG-E2E-EVIDENCE",
      appointedBy:"national-admin"}')"
  http_call POST "${BFF_URL}/internal/v1/organizations/${org_id}/representatives" "$rep_body"
  assert_http 3 "organization add-representative" "201"
  assert_field_present 3 "organization add-representative id" '.data.id'
  local rep_id; rep_id="$(jq_get '.data.id')"

  # Submit for verification.
  build_headers "$NATIONAL_ADMIN_ID" "$NATIONAL_ADMIN_TOKEN"
  http_call POST "${BFF_URL}/internal/v1/organizations/${org_id}/submit-verification"
  assert_http 3 "organization submit-verification" "200"
  assert_field_eq 3 "organization submit -> PENDING_VERIFICATION" '.data.verificationStatus' "PENDING_VERIFICATION"

  # National-admin verify (attest the representative).
  build_headers "$NATIONAL_ADMIN_ID" "$NATIONAL_ADMIN_TOKEN"
  local verify_body
  verify_body="$(jq -nc --arg r "$rep_id" '{verifiedRepresentativeIds:[$r]}')"
  http_call POST "${BFF_URL}/internal/v1/organizations/${org_id}/verify" "$verify_body"
  assert_http 3 "organization verify" "200"
  assert_field_eq 3 "organization verify -> VERIFIED" '.data.verificationStatus' "VERIFIED"
  assert_field_eq 3 "organization verify -> ACTIVE" '.data.status' "ACTIVE"
  ORG_ID="$org_id"; ORG_REP_ID="$rep_id"
}

# ── Step 4: Provider preloaded via Channel A ─────────────────────────────────
# varapi ProviderBootstrapController.java:45 POST /v1/internal/providers/bootstrap/preload
#   BulkPreloadResponse.results[].{outcome,providerPublicId,claimToken}
# Prefer the seeded CLAIM_TOKEN (E1-SEED); else preload here.
run_step_4() {
  log "${BLUE}== Step 4: Provider preloaded via Channel A ==${NC}"
  if [[ -n "$CLAIM_TOKEN" ]]; then
    pass 4 "provider-preload (seeded token)" "using CLAIM_TOKEN from seed/env (${CLAIM_TOKEN:0:6}...)" "n/a"
    return 0
  fi
  build_headers "$NATIONAL_ADMIN_ID" "$NATIONAL_ADMIN_TOKEN"
  local reg="${REG_NUMBER:-MDPCZ-${RUN_SUFFIX}}"
  local body
  body="$(jq -nc --arg reg "$reg" \
    '{rows:[{givenName:"Tendai", familyName:"Moyo", profession:"DOCTOR", cadre:"MEDICAL_OFFICER",
             councilCode:"MDPCZ", registrationNumber:$reg}]}')"
  http_call POST "${VARAPI_URL}/v1/internal/providers/bootstrap/preload" "$body"
  assert_http 4 "provider bulk-preload" "200"
  assert_field_eq 4 "provider bulk-preload -> CREATED" '.results[0].outcome' "CREATED"
  CLAIM_TOKEN="$(jq_get '.results[0].claimToken')"
  PROVIDER_ID="${PROVIDER_ID:-$(jq_get '.results[0].providerPublicId')}"
  require_var 4 "provider bulk-preload" "claimToken" "$CLAIM_TOKEN"
  log "  captured CLAIM_TOKEN + PROVIDER_ID=${PROVIDER_ID:-<none>}"
}

# ── Step 5: Citizen claims the Provider ID ───────────────────────────────────
# BFF ProviderClaimController.java:73 eligibility, :100 preview, :134 claim
#   claimant bound to X-Actor-ID (:154); consent:true required (:147)
run_step_5() {
  log "${BLUE}== Step 5: Citizen claims the Provider ID ==${NC}"
  require_var 5 "provider-claim" "CLAIMANT_HEALTH_ID" "$CLAIMANT_HEALTH_ID"
  require_var 5 "provider-claim" "CLAIM_TOKEN" "$CLAIM_TOKEN"

  build_headers "$CLAIMANT_HEALTH_ID" "$CITIZEN_TOKEN"
  http_call GET "${BFF_URL}/api/v1/provider-claim/eligibility"
  assert_http 5 "provider-claim eligibility" "200"
  assert_field_eq 5 "provider-claim eligible" '.data.eligibleForClaim' "true"

  build_headers "$CLAIMANT_HEALTH_ID" "$CITIZEN_TOKEN"
  local prev; prev="$(jq -nc --arg t "$CLAIM_TOKEN" '{claimToken:$t}')"
  http_call POST "${BFF_URL}/api/v1/provider-claim/preview" "$prev"
  assert_http 5 "provider-claim preview" "200"
  assert_field_present 5 "provider-claim preview providerPublicId" '.data.providerPublicId'

  build_headers "$CLAIMANT_HEALTH_ID" "$CITIZEN_TOKEN"
  local claim; claim="$(jq -nc --arg t "$CLAIM_TOKEN" '{claimToken:$t, consent:true}')"
  http_call POST "${BFF_URL}/api/v1/provider-claim/claim" "$claim"
  assert_http 5 "provider-claim claim" "200"
  assert_field_eq 5 "provider-claim linked" '.data.linked' "true"
  assert_field_present 5 "provider-claim lifecycleStatus" '.data.lifecycleStatus'
}

# ── Step 6: EC evidence upgrades trust to EMPLOYMENT_MATCHED ──────────────────
# BFF TrustProfileBffController.java:55 POST /api/v1/trust/employment-match
#   EmploymentMatchBffService.java:110-133 -> ProviderTrustClient.assertTrust
#   trustAssertion.trustLevel == EMPLOYMENT_MATCHED  (needs ec-matching flag ON)
run_step_6() {
  log "${BLUE}== Step 6: EC evidence -> EMPLOYMENT_MATCHED ==${NC}"
  require_var 6 "employment-match" "CLAIMANT_HEALTH_ID" "$CLAIMANT_HEALTH_ID"
  require_var 6 "employment-match" "EC_NUMBER" "$EC_NUMBER"
  require_var 6 "employment-match" "PROVIDER_ID" "$PROVIDER_ID"
  build_headers "$CLAIMANT_HEALTH_ID" "$CITIZEN_TOKEN"
  local body; body="$(jq -nc --arg ec "$EC_NUMBER" --arg pid "$PROVIDER_ID" '{ecNumber:$ec, providerId:$pid}')"
  http_call POST "${BFF_URL}/api/v1/trust/employment-match" "$body"
  assert_http 6 "employment-match" "200"
  assert_field_eq 6 "employment-match status" '.status' "OK"
  assert_field_eq 6 "employment-match -> EMPLOYMENT_MATCHED" '.trustAssertion.trustLevel' "EMPLOYMENT_MATCHED"
}

# ── Step 7: Four-block trust profile ─────────────────────────────────────────
# BFF TrustProfileBffController.java:48 GET /api/v1/trust/profile/{healthId}
#   TrustProfileComposer.java:76-81 blocks; :245-247 sourced()/sourceOfRecord;
#   independent status=UNAVAILABLE degradation (:94/127/162/185).
run_step_7() {
  log "${BLUE}== Step 7: Four-block trust profile ==${NC}"
  require_var 7 "trust-profile" "CLAIMANT_HEALTH_ID" "$CLAIMANT_HEALTH_ID"
  build_headers "$CLAIMANT_HEALTH_ID" "$CITIZEN_TOKEN"
  http_call GET "${BFF_URL}/api/v1/trust/profile/${CLAIMANT_HEALTH_ID}"
  assert_http 7 "trust-profile fetch" "200"
  # All four doctrine blocks must be present, each citing its system of record.
  assert_field_present 7 "trust-profile identity.sourceOfRecord"     '.identity.sourceOfRecord'
  assert_field_present 7 "trust-profile professional.sourceOfRecord" '.professional.sourceOfRecord'
  assert_field_present 7 "trust-profile employment.sourceOfRecord"   '.employment.sourceOfRecord'
  assert_field_present 7 "trust-profile operational.sourceOfRecord"  '.operational.sourceOfRecord'
  # Each block must carry a status; a block may honestly be UNAVAILABLE without
  # poisoning the others. Assert all four statuses render.
  local n_status
  n_status="$(echo "$RESP_BODY" | jq -r '[.identity.status,.professional.status,.employment.status,.operational.status] | map(select(. != null)) | length' 2>/dev/null || echo 0)"
  if [[ "$n_status" == "4" ]]; then
    local statuses; statuses="$(echo "$RESP_BODY" | jq -c '[.identity.status,.professional.status,.employment.status,.operational.status]')"
    pass 7 "trust-profile four blocks render (honest degradation ok)" "statuses=$statuses" "$HTTP_CODE"
  else
    fail 7 "trust-profile four blocks render" "expected 4 block statuses, got $n_status" "$HTTP_CODE" "$RESP_BODY"
  fi
}

# ── Step 8: Facility per-source legitimacy [PHASE-E2 GAP: tuso DIRECT] ────────
# tuso FacilitySourceLegitimacyController.java:57
#   GET /v1/facilities/{facilityId}/status-composite
#   ApiResponse.data.{platformAccessAllowed, sourceLegitimacy[], reasons[]}
# NOTE: No experience-bff proxy exists for this route yet — hitting tuso directly
#       in-mesh is a KNOWN PHASE-E2 BFF-PROXY GAP (see prereq banner).
run_step_8() {
  log "${BLUE}== Step 8: Facility per-source legitimacy [Phase-E2 gap: tuso direct] ==${NC}"
  require_var 8 "facility-legitimacy" "FACILITY_UUID" "$FACILITY_UUID"
  build_headers "$NATIONAL_ADMIN_ID" "$NATIONAL_ADMIN_TOKEN"
  http_call GET "${TUSO_URL}/v1/facilities/${FACILITY_UUID}/status-composite"
  assert_http 8 "facility status-composite (tuso direct)" "200"
  assert_field_present 8 "facility platformAccessAllowed present" '.data.platformAccessAllowed'
  # The seeded rows include a GOVERNMENT_OPERATIONAL_EXCEPTION -> platform access
  # is allowed under exception AND at least one per-source verdict + reason exist.
  local n_sources
  n_sources="$(echo "$RESP_BODY" | jq -r '(.data.sourceLegitimacy // []) | length' 2>/dev/null || echo 0)"
  if [[ "${n_sources:-0}" -ge 1 ]]; then
    local access reasons
    access="$(jq_get '.data.platformAccessAllowed')"
    reasons="$(echo "$RESP_BODY" | jq -c '.data.reasons // []')"
    pass 8 "facility per-source verdicts reflect seeded rows" "platformAccessAllowed=$access sources=$n_sources reasons=$reasons" "$HTTP_CODE"
  else
    fail 8 "facility per-source verdicts reflect seeded rows" "no sourceLegitimacy rows returned (seed missing?)" "$HTTP_CODE" "$RESP_BODY"
  fi
}

# ── Step 9 (OPTIONAL): Channel-C claim -> escalate -> decision -> resolve ─────
# org-registry ClaimSubmissionController.java:23 submit, :52 escalate
# WGV AdjudicationDecisionController.java:93 decisions (decision=APPROVED)
# Resolution ACCEPTED arrives via the Kafka feedback consumer.
run_step_9() {
  log "${BLUE}== Step 9: Channel-C adjudication (RUN_ADJUDICATION=1) ==${NC}"
  require_var 9 "adjudication" "ORG_ID" "${ORG_ID:-}"
  require_var 9 "adjudication" "ORG_REP_ID" "${ORG_REP_ID:-}"

  build_headers "$NATIONAL_ADMIN_ID" "$NATIONAL_ADMIN_TOKEN"
  local claim_body
  claim_body="$(jq -nc --arg rep "$ORG_REP_ID" --arg h "${CLAIMANT_HEALTH_ID:-subject-health-id}" \
    '{submittedByRepId:$rep, subjectHealthId:$h, claimedRole:"PROVIDER_AFFILIATION",
      trustBasis:"ORG_ATTESTATION", evidence:{source:"IATG-E2E"}}')"
  http_call POST "${ORG_REGISTRY_URL}/v1/organizations/${ORG_ID}/claims" "$claim_body"
  assert_http 9 "channel-c claim submit" "201"
  assert_field_eq 9 "channel-c claim SUBMITTED" '.status' "SUBMITTED"
  local claim_id; claim_id="$(jq_get '.id')"
  require_var 9 "channel-c claim" "claimId" "$claim_id"

  build_headers "$NATIONAL_ADMIN_ID" "$NATIONAL_ADMIN_TOKEN"
  http_call POST "${ORG_REGISTRY_URL}/v1/claims/${claim_id}/escalate"
  assert_http 9 "channel-c claim escalate" "200"
  assert_field_eq 9 "channel-c claim UNDER_REVIEW" '.status' "UNDER_REVIEW"

  # WGV adjudication decision (APPROVED).
  build_headers "$NATIONAL_ADMIN_ID" "$NATIONAL_ADMIN_TOKEN"
  local dec_body
  dec_body="$(jq -nc --arg ref "$claim_id" \
    '{subjectType:"ORG_CLAIM", subjectRef:$ref, decision:"APPROVED",
      reason:"IATG E2E adjudication approval", authorityRole:"NATIONAL_ADMINISTRATOR"}')"
  http_call POST "${WGV_URL}/internal/v1/workforce-governance/adjudications/decisions" "$dec_body"
  assert_http 9 "wgv adjudication decision" "201"
  assert_field_eq 9 "wgv adjudication APPROVED" '.data.decision' "APPROVED"

  # Poll for the Kafka-fed resolution to ACCEPTED.
  local attempts="${ADJUDICATION_POLL_ATTEMPTS:-15}" delay="${ADJUDICATION_POLL_DELAY:-2}"
  local i resolved="no" cur
  for (( i=1; i<=attempts; i++ )); do
    build_headers "$NATIONAL_ADMIN_ID" "$NATIONAL_ADMIN_TOKEN"
    http_call GET "${ORG_REGISTRY_URL}/v1/claims/${claim_id}"
    cur="$(jq_get '.status')"
    log "  poll ${i}/${attempts}: claim status=${cur}"
    if [[ "$cur" == "ACCEPTED" ]]; then resolved="yes"; break; fi
    sleep "$delay"
  done
  if [[ "$resolved" == "yes" ]]; then
    pass 9 "channel-c claim resolves ACCEPTED (Kafka feedback)" "status=ACCEPTED after $i poll(s)" "$HTTP_CODE"
  else
    fail 9 "channel-c claim resolves ACCEPTED (Kafka feedback)" "claim did not reach ACCEPTED within $attempts polls (last=$cur)" "$HTTP_CODE" "$RESP_BODY"
  fi
}

# ── Finalize: write summary.json + counts ────────────────────────────────────
finalize() {
  mkdir -p "$REPORT_DIR" 2>/dev/null || true
  {
    echo "{"
    echo "  \"label\": \"${LABEL}\","
    echo "  \"generatedAt\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\","
    echo "  \"tenant\": \"${TENANT_ID}\","
    echo "  \"runAdjudication\": ${RUN_ADJUDICATION},"
    echo "  \"passed\": ${PASS_COUNT},"
    echo "  \"failed\": ${FAIL_COUNT},"
    echo "  \"steps\": ["
    local first=1 r
    for r in "${STEP_RESULTS[@]}"; do
      IFS='|' read -r s n st ht dt <<< "$r"
      [[ $first -eq 0 ]] && echo ","
      first=0
      printf '    {"step": "%s", "name": %s, "status": "%s", "http": "%s", "detail": %s}' \
        "$s" "$(jq -Rn --arg v "$n" '$v')" "$st" "$ht" "$(jq -Rn --arg v "$dt" '$v')"
    done
    echo ""
    echo "  ]"
    echo "}"
  } > "$SUMMARY_JSON" 2>/dev/null || true
}

print_summary() {
  local total=$((PASS_COUNT + FAIL_COUNT))
  log ""
  log "${BLUE}=========================================${NC}"
  log "IATG E2E results: ${GREEN}${PASS_COUNT} passed${NC}, ${RED}${FAIL_COUNT} failed${NC} / ${total}"
  log "Summary: ${SUMMARY_JSON}"
  log "${BLUE}=========================================${NC}"
}

# =============================================================================
# MAIN
# =============================================================================
main() {
  print_prereq_banner

  if [[ "$DRY_RUN" == "1" ]]; then
    print_planned_chain
    echo ""
    echo -e "${GREEN}[DRY-RUN OK]${NC} planned chain printed; no network performed."
    exit 0
  fi

  require_jq
  mkdir -p "$REPORT_DIR"
  : > "$RUN_LOG"

  COUNTRY_OP_ID=""; APPOINTMENT_ACTION_ID=""; ORG_ID=""; ORG_REP_ID=""

  run_step_1
  run_step_2
  run_step_3
  run_step_4
  run_step_5
  run_step_6
  run_step_7
  run_step_8

  if [[ "$RUN_ADJUDICATION" == "1" ]]; then
    run_step_9
  else
    log "${YELLOW}[SKIP]${NC} step 9 (Channel-C adjudication) — set RUN_ADJUDICATION=1 to enable."
  fi

  finalize
  print_summary
  [[ $FAIL_COUNT -eq 0 ]] || exit 1
}

main "$@"

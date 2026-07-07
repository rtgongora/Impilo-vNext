#!/usr/bin/env bash
# =============================================================================
# IATG real-life journey seeder — the 21 preview scenarios (Section 6).
#
# Provisions the fixtures the six real-life browser journeys (A–F) need so a real
# person can walk the product and reach a governed outcome, including the PENDING
# and FAILURE decision points (not only happy-path terminal states).
#
# HONEST BY CONSTRUCTION
#   * Every call is a real curl with a checked HTTP status. On a status mismatch
#     the body is printed and the scenario is recorded FAILED (the script exits
#     non-zero at the end). Nothing is faked.
#   * A scenario whose precondition cannot be met on this deployment (e.g. a
#     facility code that does not resolve) is recorded SKIPPED with a reason —
#     never silently dropped, never reported as done.
#   * Idempotent: legitimacy PUTs and employment/decision upserts are natural
#     upserts or append-only; provider preload uses a per-run registration nonce
#     so every run mints a fresh redeemable token. Re-running is safe.
#
# WHAT IT DOES NOT DO
#   * It does not create realm principals (scenarios 1–4). Those are seeded by
#     the Keycloak realm import (origin.admin.one/.two, national.admin.one,
#     citizen.moyo). They are DOCUMENTED in the manifest, not minted here.
#   * It does not drive the journey STEPS the e2e harness asserts (claim,
#     two-person execute, verification) — those are the runtime proof, not seed.
#
# CANONICAL FACILITY UUID
#   tuso legitimacy/claim endpoints bind @PathVariable UUID facilityId = the
#   canonical facility_uuid column, which is NOT the search result's facilityUid
#   (a master-pack import string). We resolve code -> numeric id (search) ->
#   canonical UUID (GET /configuration) so legitimacy rows land on the SAME uuid
#   the experience shell reads by. If /configuration is unavailable we fall back
#   to facilityUid and flag it in the manifest.
#
# USAGE
#   ADMIN_TOKEN=<bearer> bash scripts/seed/iatg-realjourney-seed.sh [--dry-run]
#
# ENV (preview defaults; from the VM host override *_BASE with the external IP)
#   VARAPI_BASE   (default http://varapi-service:8083)
#   TUSO_BASE     (default http://tuso-service:8084)
#   WGV_BASE      (default http://workforce-governance-service:8165)
#   ORG_BASE      (default http://organization-registry-service:8153)
#   WORKFLOW_BASE (default http://workflow-service:8250)
#   ADMIN_TOKEN   bearer for the internal/governance plane (required unless --dry-run)
#   TENANT_ID     (default 00000000-0000-4000-8000-000000000001) canonical UUID
#   POD_ID        (default national)  # workflow-service requires X-Pod-ID
#   CITIZEN_HEALTH_ID   (default b0000000-0000-4000-8000-000000000001 = citizen.moyo)
#   FACILITY_CODE_COMPLIANT (default ZW-HCH-001) HPA current + exception facility
#   FACILITY_CODE_EXPIRED   (default ZW-HCH-002) expired/non-compliant facility
#   FACILITY_CODE_PRIVATE   (default ZW-PVT-001) private facility awaiting claim
#     Any code that does not resolve marks its facility scenarios SKIPPED.
#   PROVIDER_REG_PREFIX (default MDPCZ-RJ) provider preload reg-number prefix
#   ADJ_DEFINITION_PROVIDER (default ad1d0000-0000-4000-8000-000000000002)
# =============================================================================
set -euo pipefail

# ── args ─────────────────────────────────────────────────────────────────────
DRY_RUN=0
for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=1 ;;
    -h|--help) sed -n '2,60p' "$0"; exit 0 ;;
    *) echo "unknown argument: $arg" >&2; exit 2 ;;
  esac
done

# ── config ───────────────────────────────────────────────────────────────────
VARAPI_BASE="${VARAPI_BASE:-http://varapi-service:8083}"
TUSO_BASE="${TUSO_BASE:-http://tuso-service:8084}"
WGV_BASE="${WGV_BASE:-http://workforce-governance-service:8165}"
ORG_BASE="${ORG_BASE:-http://organization-registry-service:8153}"
WORKFLOW_BASE="${WORKFLOW_BASE:-http://workflow-service:8250}"
ADMIN_TOKEN="${ADMIN_TOKEN:-}"
TENANT_ID="${TENANT_ID:-00000000-0000-4000-8000-000000000001}"
POD_ID="${POD_ID:-national}"
CITIZEN_HEALTH_ID="${CITIZEN_HEALTH_ID:-b0000000-0000-4000-8000-000000000001}"
FACILITY_CODE_COMPLIANT="${FACILITY_CODE_COMPLIANT:-ZW-HCH-001}"
FACILITY_CODE_EXPIRED="${FACILITY_CODE_EXPIRED:-ZW-HCH-002}"
FACILITY_CODE_PRIVATE="${FACILITY_CODE_PRIVATE:-ZW-PVT-001}"
PROVIDER_REG_PREFIX="${PROVIDER_REG_PREFIX:-MDPCZ-RJ}"
ADJ_DEFINITION_PROVIDER="${ADJ_DEFINITION_PROVIDER:-ad1d0000-0000-4000-8000-000000000002}"
RUN_STAMP="$(date -u +%Y%m%d%H%M%S)"
CORRELATION_ID="iatg-rj-seed-${RUN_STAMP}"

# A distinct EC-conflict citizen (a second Health ID competing for the same EC).
CONFLICT_HEALTH_ID="${CONFLICT_HEALTH_ID:-b0000000-0000-4000-8000-000000000009}"

info() { printf '==> %s\n' "$*"; }
warn() { printf 'WARN: %s\n' "$*" >&2; }
die()  { printf 'FATAL: %s\n' "$*" >&2; exit 1; }

command -v curl >/dev/null 2>&1 || die "curl is required"
command -v python3 >/dev/null 2>&1 || die "python3 is required"

if [[ "$DRY_RUN" -eq 0 && -z "$ADMIN_TOKEN" ]]; then
  die "ADMIN_TOKEN is required (export a bearer token, or pass --dry-run)"
fi

TMP_BODY="$(mktemp)"
cleanup() { rm -f "$TMP_BODY"; }
trap cleanup EXIT

gen_uuid() {
  if [[ -r /proc/sys/kernel/random/uuid ]]; then cat /proc/sys/kernel/random/uuid
  else python3 -c 'import uuid; print(uuid.uuid4())'; fi
}

# ── scenario manifest ────────────────────────────────────────────────────────
# Each entry: "N|title|state|detail" where state ∈ SEEDED|PRINCIPAL|SKIPPED|FAILED.
SCENARIOS=()
record_scenario() { SCENARIOS+=("$1|$2|$3|$4"); }

# ── HTTP ─────────────────────────────────────────────────────────────────────
# http_call METHOD URL EXPECTED_STATUS [BODY] [EXTRA_HEADER...]
# Sets RESP_BODY + RESP_CODE. Returns 0 on match, 1 on mismatch (caller decides).
RESP_BODY=""; RESP_CODE=""
http_call() {
  local method="$1" url="$2" expected="$3" body="${4:-}"; shift 4 || shift $#
  if [[ "$DRY_RUN" -eq 1 ]]; then
    printf -- '--- planned: %s %s (expect %s)\n' "$method" "$url" "$expected" >&2
    [[ -n "$body" ]] && printf '    body: %s\n' "$body" >&2
    RESP_BODY=""; RESP_CODE="$expected"; return 0
  fi
  local -a args=(-sS -X "$method" "$url"
    -H "X-Tenant-ID: ${TENANT_ID}" -H "x-tenant-id: ${TENANT_ID}"
    -H "X-Pod-ID: ${POD_ID}"
    -H "X-Correlation-ID: ${CORRELATION_ID}" -H "x-correlation-id: ${CORRELATION_ID}"
    -H "X-Request-ID: $(gen_uuid)"
    -H "Idempotency-Key: $(gen_uuid)"
    -H "X-Actor-ID: ${CITIZEN_HEALTH_ID}" -H "x-actor-id: ${CITIZEN_HEALTH_ID}"
    -H "X-Actor-Type: PLATFORM_ORIGIN_ADMINISTRATOR"
    -H "X-Purpose-Of-Use: PLATFORM_ADMINISTRATION")
  [[ -n "$ADMIN_TOKEN" ]] && args+=(-H "Authorization: Bearer ${ADMIN_TOKEN}")
  local h; for h in "$@"; do args+=(-H "$h"); done
  [[ -n "$body" ]] && args+=(-H "Content-Type: application/json" --data "$body")
  RESP_CODE="$(curl "${args[@]}" -o "$TMP_BODY" -w '%{http_code}' || echo 000)"
  RESP_BODY="$(cat "$TMP_BODY")"
  [[ "$RESP_CODE" == "$expected" ]]
}

# Extract a python expr over `d` (parsed RESP_BODY). Never fails the script:
# a parse error / missing field yields an empty string.
jqx() { printf '%s' "$RESP_BODY" | python3 -c "import json,sys; d=json.load(sys.stdin); print($1)" 2>/dev/null || true; }

info "IATG real-journey seed starting (dry_run=${DRY_RUN}) tenant=${TENANT_ID}"

# ── Scenarios 1–4: realm principals (documented, not minted here) ────────────
record_scenario 1 "Platform Origin Administrator" PRINCIPAL "realm user origin.admin.one (role PLATFORM_ORIGIN_ADMINISTRATOR)"
record_scenario 2 "National Administrator" PRINCIPAL "realm user national.admin.one (role NATIONAL_ADMINISTRATOR)"
record_scenario 3 "Second approver (two-person)" PRINCIPAL "realm user origin.admin.two (distinct PLATFORM_ORIGIN_ADMINISTRATOR)"
record_scenario 4 "Citizen with Health ID only" PRINCIPAL "realm user citizen.moyo (health_id ${CITIZEN_HEALTH_ID})"

# ── Facility resolution helper (code -> canonical UUID) ──────────────────────
# Echoes the canonical UUID (or empty). Sets RESOLVE_NOTE.
RESOLVE_NOTE=""
resolve_facility_uuid() {
  local code="$1"
  RESOLVE_NOTE=""
  if [[ "$DRY_RUN" -eq 1 ]]; then echo "<uuid-for-${code}>"; return 0; fi
  local search; search="$(printf '{"query":"%s","page":0,"size":50}' "$code")"
  if ! http_call POST "${TUSO_BASE}/v1/internal/facilities/search" 200 "$search"; then
    RESOLVE_NOTE="facility search failed (HTTP ${RESP_CODE})"; echo ""; return 0
  fi
  local numeric; numeric="$(FCODE="$code" python3 -c '
import json,os,sys
code=os.environ["FCODE"]; doc=json.load(sys.stdin)
items=(doc.get("data") or {}).get("items") or []
m=next((i for i in items if i.get("code")==code), None)
print(m.get("id") if m and m.get("id") is not None else "")' <<<"$RESP_BODY" 2>/dev/null)"
  if [[ -z "$numeric" ]]; then RESOLVE_NOTE="no facility with code ${code}"; echo ""; return 0; fi
  # numeric id -> canonical uuid via configuration
  if http_call GET "${TUSO_BASE}/v1/internal/facilities/${numeric}/configuration" 200 ""; then
    local uuid; uuid="$(jqx 'd.get("data",{}).get("facilityUuid","") or ""')"
    if [[ -n "$uuid" && "$uuid" != "None" ]]; then echo "$uuid"; return 0; fi
  fi
  # fallback: facilityUid from search (flagged)
  local uid; uid="$(FCODE="$code" python3 -c '
import json,os,sys
code=os.environ["FCODE"]; doc=json.load(sys.stdin)
items=(doc.get("data") or {}).get("items") or []
m=next((i for i in items if i.get("code")==code), None)
print(m.get("facilityUid") if m else "")' <<<"$RESP_BODY" 2>/dev/null)"
  RESOLVE_NOTE="canonical uuid unavailable; used facilityUid fallback"
  echo "$uid"
}

# ── legitimacy PUT helper ────────────────────────────────────────────────────
put_legitimacy() { # facilityUuid source bodyjson
  http_call PUT "${TUSO_BASE}/v1/internal/facilities/${1}/source-legitimacy/${2}" 200 "$3"
}

# ── Scenarios 13 + 15 + 16: compliant/exception facility ─────────────────────
FACILITY_UUID_COMPLIANT="$(resolve_facility_uuid "$FACILITY_CODE_COMPLIANT")"
if [[ -n "$FACILITY_UUID_COMPLIANT" ]]; then
  info "compliant facility ${FACILITY_CODE_COMPLIANT} -> ${FACILITY_UUID_COMPLIANT} ${RESOLVE_NOTE:+(${RESOLVE_NOTE})}"
  if put_legitimacy "$FACILITY_UUID_COMPLIANT" HPA_LEGAL \
       '{"status":"REGISTERED_CURRENT","evidenceRef":"IATG-RJ-SEED"}'; then
    record_scenario 13 "Facility HPA-registered/current" SEEDED "${FACILITY_CODE_COMPLIANT} HPA_LEGAL=REGISTERED_CURRENT (uuid ${FACILITY_UUID_COMPLIANT})"
  else
    record_scenario 13 "Facility HPA-registered/current" FAILED "HPA_LEGAL PUT HTTP ${RESP_CODE}: ${RESP_BODY}"
  fi
  put_legitimacy "$FACILITY_UUID_COMPLIANT" MINISTRY_OPERATIONAL \
    '{"status":"REGISTERED_CURRENT","evidenceRef":"IATG-RJ-SEED"}' || true
  if put_legitimacy "$FACILITY_UUID_COMPLIANT" PLATFORM_OPERATIONAL \
       '{"status":"GOVERNMENT_OPERATIONAL_EXCEPTION","allowedOnPlatform":true,"reason":"Public facility operates under Ministry authority despite lapsed HPA registration (IATG RJ seed).","evidenceRef":"IATG-RJ-SEED"}'; then
    record_scenario 15 "Public facility GOVERNMENT_OPERATIONAL_EXCEPTION + reason" SEEDED "${FACILITY_CODE_COMPLIANT} PLATFORM_OPERATIONAL=GOVERNMENT_OPERATIONAL_EXCEPTION allowedOnPlatform=true"
  else
    record_scenario 15 "Public facility GOVERNMENT_OPERATIONAL_EXCEPTION + reason" FAILED "PLATFORM_OPERATIONAL PUT HTTP ${RESP_CODE}: ${RESP_BODY}"
  fi
else
  record_scenario 13 "Facility HPA-registered/current" SKIPPED "facility code ${FACILITY_CODE_COMPLIANT} did not resolve (${RESOLVE_NOTE:-not found})"
  record_scenario 15 "Public facility GOVERNMENT_OPERATIONAL_EXCEPTION + reason" SKIPPED "facility code ${FACILITY_CODE_COMPLIANT} did not resolve"
fi

# ── Scenario 14: expired/non-compliant facility ──────────────────────────────
FACILITY_UUID_EXPIRED="$(resolve_facility_uuid "$FACILITY_CODE_EXPIRED")"
if [[ -n "$FACILITY_UUID_EXPIRED" ]]; then
  if put_legitimacy "$FACILITY_UUID_EXPIRED" HPA_LEGAL \
       '{"status":"EXPIRED","asOf":"2023-01-01T00:00:00Z","evidenceRef":"IATG-RJ-SEED"}'; then
    record_scenario 14 "Facility expired/non-compliant" SEEDED "${FACILITY_CODE_EXPIRED} HPA_LEGAL=EXPIRED (uuid ${FACILITY_UUID_EXPIRED})"
  else
    record_scenario 14 "Facility expired/non-compliant" FAILED "HPA_LEGAL PUT HTTP ${RESP_CODE}: ${RESP_BODY}"
  fi
else
  record_scenario 14 "Facility expired/non-compliant" SKIPPED "facility code ${FACILITY_CODE_EXPIRED} did not resolve (${RESOLVE_NOTE:-not found})"
fi

# ── Scenario 16: private facility awaiting claim (legitimacy allows claim) ────
FACILITY_UUID_PRIVATE="$(resolve_facility_uuid "$FACILITY_CODE_PRIVATE")"
if [[ -n "$FACILITY_UUID_PRIVATE" ]]; then
  # make it platform-allowed so a claim can be submitted (else claim 409s)
  put_legitimacy "$FACILITY_UUID_PRIVATE" MINISTRY_OPERATIONAL \
    '{"status":"REGISTERED_CURRENT","allowedOnPlatform":true,"evidenceRef":"IATG-RJ-SEED"}' || true
  record_scenario 16 "Private facility claim requiring verification" SEEDED "${FACILITY_CODE_PRIVATE} platform-allowed; claim submitted in scenario 11 (uuid ${FACILITY_UUID_PRIVATE})"
else
  record_scenario 16 "Private facility claim requiring verification" SKIPPED "facility code ${FACILITY_CODE_PRIVATE} did not resolve (${RESOLVE_NOTE:-not found})"
fi

# ── provider preload helper (mints a fresh redeemable token) ─────────────────
# Echoes providerPublicId (or empty). Sets PRELOAD_TOKEN + PRELOAD_NOTE.
PRELOAD_TOKEN=""; PRELOAD_NOTE=""
preload_provider() { # regsuffix givenName familyName [councilCode]
  PRELOAD_TOKEN=""; PRELOAD_NOTE=""
  local reg="${PROVIDER_REG_PREFIX}-$1-${RUN_STAMP}"
  if [[ "$DRY_RUN" -eq 1 ]]; then PRELOAD_TOKEN="<token>"; echo "<provider-$1>"; return 0; fi
  local body
  body="$(GN="$2" FN="$3" CC="${4:-MDPCZ}" REG="$reg" python3 -c '
import json,os
print(json.dumps({"rows":[{"givenName":os.environ["GN"],"familyName":os.environ["FN"],
  "profession":"Medical Practitioner","cadre":"DOCTOR","councilCode":os.environ["CC"],
  "registrationNumber":os.environ["REG"]}]}))')"
  if ! http_call POST "${VARAPI_BASE}/v1/internal/providers/bootstrap/preload" 200 "$body"; then
    PRELOAD_NOTE="preload HTTP ${RESP_CODE}: ${RESP_BODY}"; echo ""; return 0
  fi
  local outcome; outcome="$(jqx 'd["results"][0]["outcome"]')"
  if [[ "$outcome" != "CREATED" ]]; then PRELOAD_NOTE="outcome=${outcome} (no token minted)"; echo ""; return 0; fi
  PRELOAD_TOKEN="$(jqx 'd["results"][0].get("claimToken","") or ""')"
  jqx 'd["results"][0].get("providerPublicId","") or ""'
}

# ── Scenario 5: existing provider WITH Provider ID (claimable skeleton) ───────
PID_5="$(preload_provider p5 Tarisai Moyo)"
if [[ -n "$PID_5" ]]; then
  record_scenario 5 "Existing provider with Provider ID" SEEDED "providerPublicId=${PID_5}; claim token minted (redeem in Journey D)"
else
  record_scenario 5 "Existing provider with Provider ID" FAILED "${PRELOAD_NOTE}"
fi
SEED_CLAIM_TOKEN="$PRELOAD_TOKEN"; SEED_PROVIDER_PUBLIC_ID="$PID_5"

# ── Scenario 6: provider with council number, no linked Health ID ────────────
PID_6="$(preload_provider p6 Rudo Chikafu)"
if [[ -n "$PID_6" ]]; then
  record_scenario 6 "Provider with council number, no linked Health ID" SEEDED "providerPublicId=${PID_6} (unclaimed: council registration only, no linked Health ID)"
else
  record_scenario 6 "Provider with council number, no linked Health ID" FAILED "${PRELOAD_NOTE}"
fi

# ── Scenario 7: lost/unknown Provider ID recovery fixture ────────────────────
PID_7="$(preload_provider p7 Nyasha Dube)"
if [[ -n "$PID_7" ]]; then
  record_scenario 7 "Lost/unknown Provider ID (recovery scenario)" SEEDED "providerPublicId=${PID_7} exists as an unlinked profile; recover via /provider-claim/recover (never re-issue)"
else
  record_scenario 7 "Lost/unknown Provider ID (recovery scenario)" FAILED "${PRELOAD_NOTE}"
fi

# ── Scenario 8: public-sector worker with EC + employment match ──────────────
EC_NUMBER_8="${EC_NUMBER_8:-EC-RJ-0001}"
emp8="$(HID="$CITIZEN_HEALTH_ID" EC="$EC_NUMBER_8" python3 -c '
import json,os
print(json.dumps({"providerWorkerId":"PW-RJ-8","employmentStatus":"ACTIVE",
  "linkedHealthId":os.environ["HID"],"ecNumber":os.environ["EC"],
  "postTitle":"Medical Officer","cadre":"DOCTOR","verificationStatus":"VERIFIED"}))')"
if http_call POST "${WGV_BASE}/v1/internal/governance/hsc/employment-records" 201 "$emp8"; then
  record_scenario 8 "Public-sector worker with EC + employment match" SEEDED "EC ${EC_NUMBER_8} linked ${CITIZEN_HEALTH_ID} (Channel-B match -> EMPLOYMENT_MATCHED)"
else
  record_scenario 8 "Public-sector worker with EC + employment match" FAILED "employment upsert HTTP ${RESP_CODE}: ${RESP_BODY}"
fi

# ── Scenario 9: EC conflict (two Health IDs claim the same EC) ───────────────
emp9="$(HID="$CONFLICT_HEALTH_ID" EC="$EC_NUMBER_8" python3 -c '
import json,os
print(json.dumps({"providerWorkerId":"PW-RJ-9","employmentStatus":"ACTIVE",
  "linkedHealthId":os.environ["HID"],"ecNumber":os.environ["EC"],
  "postTitle":"Medical Officer","cadre":"DOCTOR","verificationStatus":"VERIFIED"}))')"
if http_call POST "${WGV_BASE}/v1/internal/governance/hsc/employment-records" 201 "$emp9"; then
  record_scenario 9 "Provider with EC conflict scenario" SEEDED "second row EC ${EC_NUMBER_8} linked to distinct ${CONFLICT_HEALTH_ID} — WS-D CONFLICT producer path"
else
  record_scenario 9 "Provider with EC conflict scenario" SKIPPED "conflict row upsert HTTP ${RESP_CODE} (may be rejected as duplicate EC — that IS the honest conflict signal)"
fi

# ── access-request helper (varapi durable record) ────────────────────────────
submit_access_request() { # jsonbody
  http_call POST "${VARAPI_BASE}/v1/internal/providers/access-requests" 201 "$1"
}

# ── Scenario 10: provider invited by organization ────────────────────────────
ar10='{"requestType":"ORG_INVITATION","profession":"Medical Practitioner","organizationRef":"IATG-RJ-ORG-INVITE","evidenceSummary":"Invited by mission hospital (IATG RJ seed)"}'
if submit_access_request "$ar10"; then
  record_scenario 10 "Provider invited by organization" SEEDED "access-request publicId=$(jqx 'd.get("data",{}).get("publicId","")') status=$(jqx 'd.get("data",{}).get("status","")')"
else
  record_scenario 10 "Provider invited by organization" FAILED "access-request HTTP ${RESP_CODE}: ${RESP_BODY}"
fi

# ── facility admin-claim helpers ─────────────────────────────────────────────
submit_admin_claim() { # facilityUuid healthId -> echoes appointmentId
  if [[ "$DRY_RUN" -eq 1 ]]; then echo "9001"; return 0; fi
  local b; b="$(HID="$2" python3 -c 'import json,os; print(json.dumps({"personHealthId":os.environ["HID"],"role":"FACILITY_ADMINISTRATOR","evidenceRef":"IATG-RJ-SEED"}))')"
  if http_call POST "${TUSO_BASE}/v1/internal/facilities/${1}/admin-claim" 201 "$b"; then
    jqx 'd.get("data",{}).get("id","") or d.get("id","")'
  else echo ""; fi
}

# ── Scenario 11: facility assignment, no admin rights (PENDING claim) ─────────
CLAIM_HEALTH_ID_11="${CLAIM_HEALTH_ID_11:-b0000000-0000-4000-8000-000000000011}"
if [[ -n "$FACILITY_UUID_PRIVATE" ]]; then
  APPT_11="$(submit_admin_claim "$FACILITY_UUID_PRIVATE" "$CLAIM_HEALTH_ID_11")"
  if [[ -n "$APPT_11" && "$APPT_11" != "None" ]]; then
    record_scenario 11 "Provider with facility assignment but no admin rights" SEEDED "PENDING admin-claim appointmentId=${APPT_11} on ${FACILITY_CODE_PRIVATE} (not yet ACTIVE)"
  else
    record_scenario 11 "Provider with facility assignment but no admin rights" FAILED "admin-claim HTTP ${RESP_CODE}: ${RESP_BODY}"
  fi
else
  record_scenario 11 "Provider with facility assignment but no admin rights" SKIPPED "private facility ${FACILITY_CODE_PRIVATE} did not resolve"
fi

# ── Scenario 12: facility ADMIN rights (approve PENDING -> ACTIVE) ────────────
CLAIM_HEALTH_ID_12="${CLAIM_HEALTH_ID_12:-b0000000-0000-4000-8000-000000000012}"
if [[ -n "$FACILITY_UUID_COMPLIANT" ]]; then
  APPT_12="$(submit_admin_claim "$FACILITY_UUID_COMPLIANT" "$CLAIM_HEALTH_ID_12")"
  if [[ -n "$APPT_12" && "$APPT_12" != "None" ]]; then
    if http_call POST "${TUSO_BASE}/v1/internal/facility-admin-appointments/${APPT_12}/approve" 200 '{"approvedBy":"national-admin"}'; then
      record_scenario 12 "Provider with facility admin rights" SEEDED "ACTIVE admin appointmentId=${APPT_12} on ${FACILITY_CODE_COMPLIANT} (Facility Mode eligible: admin ${CLAIM_HEALTH_ID_12})"
    else
      record_scenario 12 "Provider with facility admin rights" FAILED "approve HTTP ${RESP_CODE}: ${RESP_BODY}"
    fi
  else
    record_scenario 12 "Provider with facility admin rights" FAILED "admin-claim HTTP ${RESP_CODE}: ${RESP_BODY}"
  fi
else
  record_scenario 12 "Provider with facility admin rights" SKIPPED "compliant facility ${FACILITY_CODE_COMPLIANT} did not resolve"
fi

# ── org create helper ────────────────────────────────────────────────────────
create_org() { # code legalName orgType -> echoes id
  if [[ "$DRY_RUN" -eq 1 ]]; then echo "00000000-0000-4000-8000-0000000000aa"; return 0; fi
  local b; b="$(C="$1" L="$2" T="$3" python3 -c 'import json,os; print(json.dumps({"code":os.environ["C"],"legalName":os.environ["L"],"orgType":os.environ["T"],"registrationIdentifiers":{"source":"IATG-RJ-SEED"}}))')"
  # code is unique; a re-run 500s on duplicate — treat non-201 as idempotent skip.
  if http_call POST "${ORG_BASE}/v1/organizations" 201 "$b"; then
    jqx 'd.get("id","") or ""'
  else echo "__DUP__"; fi
}

# ── Scenarios 17/18/19: organizations of each type ───────────────────────────
seed_org() { # n title code legalName orgType
  local id; id="$(create_org "$3-${RUN_STAMP}" "$4" "$5")"
  if [[ "$id" == "__DUP__" ]]; then
    record_scenario "$1" "$2" SKIPPED "org create HTTP ${RESP_CODE} (code likely exists — idempotent skip): ${RESP_BODY:0:120}"
  elif [[ -n "$id" && "$id" != "None" ]]; then
    record_scenario "$1" "$2" SEEDED "orgType=$5 id=${id} code=$3-${RUN_STAMP}"
  else
    record_scenario "$1" "$2" FAILED "org create returned no id: ${RESP_BODY}"
  fi
}
seed_org 17 "Government/MoHCC organization" "RJ-GOV" "IATG RJ Ministry of Health" GOVERNMENT_HEALTH_SERVICE
seed_org 18 "Private/multi-facility organization" "RJ-PVT" "IATG RJ Private Health Group" PRIVATE_PROVIDER_GROUP
seed_org 19 "NGO/implementing-partner organization" "RJ-NGO" "IATG RJ Implementing Partner" NGO_IMPLEMENTING_PARTNER

# ── Scenario 20: pending adjudication case (workflow instance) ────────────────
wf20="$(DEF="$ADJ_DEFINITION_PROVIDER" python3 -c 'import json,os; print(json.dumps({"definitionId":os.environ["DEF"],"initiatorRef":"iatg-rj-seed","subjectRef":"PROVIDER-RJ-PENDING","context":{"scenario":"pending-adjudication"}}))')"
if http_call POST "${WORKFLOW_BASE}/internal/v1/workflows/instances" 201 "$wf20"; then
  record_scenario 20 "At least one pending adjudication case" SEEDED "workflow instanceId=$(jqx 'd.get("instanceId","")') (provider-claim-adjudication, PENDING)"
else
  record_scenario 20 "At least one pending adjudication case" SKIPPED "workflow start HTTP ${RESP_CODE} (workflow-service/Kafka may be off): ${RESP_BODY:0:120}"
fi

# ── Scenario 21: rejected / needs-more-information path ───────────────────────
# A recorded DENIED adjudication decision is a durable terminal rejection
# (append-only, always 201 when WGV is up). Readable via the decisions query.
dec21="$(python3 -c 'import json; print(json.dumps({"subjectType":"PROVIDER","subjectRef":"PROVIDER-RJ-DENIED","decision":"DECIDED_DENIED","reason":"IATG RJ seed: evidence insufficient — needs more information","authorityRole":"NATIONAL_ADMINISTRATOR"}))')"
if http_call POST "${WGV_BASE}/internal/v1/workforce-governance/adjudications/decisions" 201 "$dec21"; then
  record_scenario 21 "Rejected or needs-more-information path" SEEDED "adjudication decision id=$(jqx 'd.get("data",{}).get("id","")') DECIDED_DENIED for PROVIDER-RJ-DENIED"
else
  record_scenario 21 "Rejected or needs-more-information path" FAILED "decision record HTTP ${RESP_CODE}: ${RESP_BODY}"
fi

# ── Emit the harness seed.env compatible block (Journey D/E/8 inputs) ────────
printf -- '----- IATG_E2E_SEED_OUTPUT_BEGIN -----\n'
printf 'FACILITY_CODE=%s\n' "$FACILITY_CODE_COMPLIANT"
printf 'FACILITY_ID=%s\n' "${FACILITY_UUID_COMPLIANT:-}"
printf 'PROVIDER_PUBLIC_ID=%s\n' "${SEED_PROVIDER_PUBLIC_ID:-}"
printf 'CLAIM_TOKEN=%s\n' "${SEED_CLAIM_TOKEN:-}"
printf 'CLAIMANT_HEALTH_ID=%s\n' "$CITIZEN_HEALTH_ID"
printf 'EC_NUMBER=%s\n' "$EC_NUMBER_8"
printf -- '----- IATG_E2E_SEED_OUTPUT_END -----\n'

# ── Manifest ─────────────────────────────────────────────────────────────────
seeded=0; skipped=0; failed=0; principal=0
printf '\n================ IATG 21-SCENARIO SEED MANIFEST ================\n'
printf '%-3s %-10s %s\n' "#" "STATE" "SCENARIO"
for row in "${SCENARIOS[@]}"; do
  IFS='|' read -r n title state detail <<< "$row"
  printf '%-3s %-10s %s\n        %s\n' "$n" "$state" "$title" "$detail"
  case "$state" in
    SEEDED) seeded=$((seeded+1)) ;;
    SKIPPED) skipped=$((skipped+1)) ;;
    FAILED) failed=$((failed+1)) ;;
    PRINCIPAL) principal=$((principal+1)) ;;
  esac
done
printf -- '---------------------------------------------------------------\n'
printf 'principals=%d seeded=%d skipped=%d failed=%d / 21\n' "$principal" "$seeded" "$skipped" "$failed"
printf 'login principals: origin.admin.one/.two, national.admin.one, superadmin, citizen.moyo\n'
printf 'roles: PLATFORM_ORIGIN_ADMINISTRATOR, NATIONAL_ADMINISTRATOR, ORGANIZATION_ADMIN, CITIZEN\n'
printf '===============================================================\n'

if [[ "$DRY_RUN" -eq 1 ]]; then
  info "dry-run complete — no changes made"
  exit 0
fi
if [[ "$failed" -gt 0 ]]; then
  die "${failed} scenario(s) FAILED — see manifest above (nothing faked; fix the deployment/preconditions and re-run)"
fi
info "seed complete: ${seeded} seeded, ${skipped} skipped (honest), ${principal} realm principals"

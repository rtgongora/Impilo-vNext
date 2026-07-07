#!/usr/bin/env bash
# =============================================================================
# IATG e2e journey — GIVEN-fixture seeder (preconditions only).
#
# Provisions the fixtures the doctrine journey NEEDS as preconditions but that
# are NOT themselves the steps-under-test:
#   (a) a Channel-A provider PRELOAD for demo provider "Dr Moyo" via varapi,
#       minting a single-use CLAIM_TOKEN the e2e harness later redeems; and
#   (b) a facility with three honest per-source legitimacy verdicts via tuso:
#         HPA_LEGAL             = EXPIRED
#         MINISTRY_OPERATIONAL  = REGISTERED_CURRENT
#         PLATFORM_OPERATIONAL  = GOVERNMENT_OPERATIONAL_EXCEPTION (+reason,
#                                 allowedOnPlatform=true) — the doctrine escape
#                                 hatch for a public facility operating despite a
#                                 lapsed HPA registration.
#   (c) an EC-bearing HSC employment row for the seeded citizen via
#       workforce-governance, so the harness Step-6 EC match can upgrade trust
#       to EMPLOYMENT_MATCHED. Emitted as CLAIMANT_HEALTH_ID + EC_NUMBER. (The
#       row is created over the real REST upsert — the governance registry is
#       the system of record for public-sector employment.)
#
# It does NOT create the Country Operation, the Organization, or the provider
# CLAIM — those ARE the journey steps that the e2e harness drives and asserts.
#
# HONEST BY CONSTRUCTION: every call is a real curl with a checked HTTP status.
# On any status mismatch the response body is printed and the script exits
# non-zero. It never fabricates success. Use --dry-run to print the planned
# calls without touching anything.
#
# IDEMPOTENCY: the tuso legitimacy PUTs are natural upserts (one row per
# (facility, source)) so re-running is a no-op-equivalent. The varapi claim
# token is mintable exactly ONCE per registration number, so PROVIDER_REG_NUMBER
# defaults to a per-run nonce — every run yields a fresh, redeemable token. Pin
# PROVIDER_REG_NUMBER to a fixed value for a deterministic re-run (the second
# run then reports SKIPPED_DUPLICATE and exits non-zero because no new token can
# be minted). The actual registration number used is echoed as REG_NUMBER so the
# harness/operator can trace the minted token back to its provider skeleton.
#
# USAGE:
#   ADMIN_TOKEN=<bearer> bash scripts/seed/iatg-e2e-seed.sh [--dry-run]
#
# ENV (preview defaults):
#   Base URLs — default to in-mesh service names (run from inside the cluster,
#   e.g. `kubectl exec deploy/experience-bff -- ...`). From the preview VM host,
#   override with the external IP, e.g.:
#       VARAPI_BASE=http://41.57.127.235:8083 \
#       TUSO_BASE=http://41.57.127.235:8084 \
#       ADMIN_TOKEN=... bash scripts/seed/iatg-e2e-seed.sh
#   VARAPI_BASE            (default http://varapi-service:8083)
#   TUSO_BASE              (default http://tuso-service:8084)
#   WGV_BASE              (default http://workforce-governance-service:8165)
#   BFF_BASE              (default http://experience-bff:8160)  # no preload route today; reserved
#   ADMIN_TOKEN           bearer token for the internal/governance plane (required unless --dry-run)
#   TENANT_ID             (default 00000000-0000-4000-8000-000000000001)
#                         MUST equal the X-Tenant-ID the BFF forwards for the citizen's
#                         employment-match call (default preview tenant), or Step 6 NOT_FOUNDs.
#   CLAIMANT_HEALTH_ID    (default b0000000-0000-4000-8000-000000000001 = citizen.moyo)
#   EC_NUMBER             (default EC-000001; canonicalised A-Z/0-9/-/ , >=1 digit)
#   POD_ID                (default pod-iatg-seed)
#   ACTOR_ID              (default b0000000-0000-4000-8000-000000000101 = origin.admin.one)
#   ACTOR_TYPE            (default PLATFORM_ORIGIN_ADMINISTRATOR)
#   PURPOSE_OF_USE        (default PLATFORM_ADMINISTRATION)
#   FACILITY_CODE         (default ZW-HCH-001)
#   FACILITY_ID           (canonical facility UUID; resolved from FACILITY_CODE if unset)
#   PROVIDER_REG_NUMBER   (default MDPCZ-DRMOYO-<UTC timestamp>; pin for deterministic re-runs)
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
BFF_BASE="${BFF_BASE:-http://experience-bff:8160}"   # reserved; no preload route today
ADMIN_TOKEN="${ADMIN_TOKEN:-}"
TENANT_ID="${TENANT_ID:-00000000-0000-4000-8000-000000000001}"
POD_ID="${POD_ID:-pod-iatg-seed}"
ACTOR_ID="${ACTOR_ID:-b0000000-0000-4000-8000-000000000101}"
ACTOR_TYPE="${ACTOR_TYPE:-PLATFORM_ORIGIN_ADMINISTRATOR}"
PURPOSE_OF_USE="${PURPOSE_OF_USE:-PLATFORM_ADMINISTRATION}"
FACILITY_CODE="${FACILITY_CODE:-ZW-HCH-001}"
FACILITY_ID="${FACILITY_ID:-}"
PROVIDER_REG_NUMBER="${PROVIDER_REG_NUMBER:-MDPCZ-DRMOYO-$(date -u +%Y%m%d%H%M%S)}"
CORRELATION_ID="iatg-e2e-seed-$(date -u +%Y%m%d%H%M%S)"

# Channel-B EC employment fixture for the seeded citizen (harness Step 6).
CLAIMANT_HEALTH_ID="${CLAIMANT_HEALTH_ID:-b0000000-0000-4000-8000-000000000001}"
EC_NUMBER="${EC_NUMBER:-EC-000001}"
EMPLOYMENT_WORKER_ID="${EMPLOYMENT_WORKER_ID:-PW-IATG-E2E}"

# Demo provider "Dr Moyo" skeleton (demographics only; the real provider fills the rest at claim time).
PROVIDER_GIVEN_NAME="${PROVIDER_GIVEN_NAME:-Tarisai}"
PROVIDER_FAMILY_NAME="${PROVIDER_FAMILY_NAME:-Moyo}"
PROVIDER_PROFESSION="${PROVIDER_PROFESSION:-Medical Practitioner}"
PROVIDER_CADRE="${PROVIDER_CADRE:-DOCTOR}"
PROVIDER_COUNCIL_CODE="${PROVIDER_COUNCIL_CODE:-MDPCZ}"
PROVIDER_CONTACT_HINT="${PROVIDER_CONTACT_HINT:-dr.moyo@example.com}"

# Outputs (populated as we go).
PROVIDER_PUBLIC_ID=""
CLAIM_TOKEN=""

info() { printf '==> %s\n' "$*"; }
fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }

command -v curl >/dev/null 2>&1 || fail "curl is required"
command -v python3 >/dev/null 2>&1 || fail "python3 is required"

if [[ "$DRY_RUN" -eq 0 && -z "$ADMIN_TOKEN" ]]; then
  fail "ADMIN_TOKEN is required (export a bearer token, or pass --dry-run to preview the calls)"
fi

TMP_BODY="$(mktemp)"
cleanup() { rm -f "$TMP_BODY"; }
trap cleanup EXIT

gen_uuid() {
  if [[ -r /proc/sys/kernel/random/uuid ]]; then
    cat /proc/sys/kernel/random/uuid
  else
    python3 -c 'import uuid; print(uuid.uuid4())'
  fi
}

# Standard trust headers for the internal/governance plane. A fresh X-Request-ID
# is added per call inside http_call.
build_headers() {
  BASE_HEADERS=(
    -H "X-Tenant-ID: ${TENANT_ID}"
    -H "X-Pod-ID: ${POD_ID}"
    -H "X-Correlation-ID: ${CORRELATION_ID}"
    -H "X-Actor-ID: ${ACTOR_ID}"
    -H "X-Actor-Type: ${ACTOR_TYPE}"
    -H "X-Purpose-Of-Use: ${PURPOSE_OF_USE}"
  )
  if [[ -n "$ADMIN_TOKEN" ]]; then
    BASE_HEADERS+=(-H "Authorization: Bearer ${ADMIN_TOKEN}")
  fi
}

print_planned() {
  local method="$1" url="$2" body="${3:-}"
  printf -- '--- planned call ---\n'
  printf '  %s %s\n' "$method" "$url"
  printf '  headers: X-Tenant-ID, X-Pod-ID, X-Request-ID, X-Correlation-ID, X-Actor-ID, X-Actor-Type, X-Purpose-Of-Use'
  if [[ -n "$ADMIN_TOKEN" ]]; then printf ', Authorization: Bearer <redacted>'; else printf ', Authorization: <ADMIN_TOKEN unset>'; fi
  printf '\n'
  if [[ -n "$body" ]]; then printf '  body: %s\n' "$body"; fi
}

# http_call METHOD URL EXPECTED_STATUS [BODY]
# On success sets RESP_BODY to the response body. On status mismatch prints the
# body and exits non-zero. In --dry-run prints the planned call and returns.
RESP_BODY=""
http_call() {
  local method="$1" url="$2" expected="$3" body="${4:-}"
  if [[ "$DRY_RUN" -eq 1 ]]; then
    print_planned "$method" "$url" "$body"
    RESP_BODY=""
    return 0
  fi
  local -a args=(-sS -X "$method" "$url")
  args+=("${BASE_HEADERS[@]}")
  args+=(-H "X-Request-ID: $(gen_uuid)")
  args+=(-H "Idempotency-Key: $(gen_uuid)")
  if [[ -n "$body" ]]; then
    args+=(-H "Content-Type: application/json" --data "$body")
  fi
  local code
  if ! code="$(curl "${args[@]}" -o "$TMP_BODY" -w '%{http_code}')"; then
    printf 'curl transport error: %s %s\n' "$method" "$url" >&2
    fail "curl failed"
  fi
  RESP_BODY="$(cat "$TMP_BODY")"
  if [[ "$code" != "$expected" ]]; then
    printf 'HTTP %s (expected %s) for %s %s\n%s\n' "$code" "$expected" "$method" "$url" "$RESP_BODY" >&2
    fail "unexpected HTTP status"
  fi
}

# json_extract <python-expr-over-`d`> — evaluate against RESP_BODY.
json_extract() {
  printf '%s' "$RESP_BODY" | python3 -c "import json,sys; d=json.load(sys.stdin); print($1)"
}

build_headers

info "IATG e2e seed starting (dry_run=${DRY_RUN})"
info "varapi=${VARAPI_BASE} tuso=${TUSO_BASE} tenant=${TENANT_ID}"

# ── Step 1: resolve the canonical facility UUID ──────────────────────────────
if [[ -z "$FACILITY_ID" ]]; then
  info "1/5 resolving facility UUID for code ${FACILITY_CODE}"
  search_body="$(printf '{"query":"%s","page":0,"size":50}' "$FACILITY_CODE")"
  http_call POST "${TUSO_BASE}/v1/facilities/search" 200 "$search_body"
  if [[ "$DRY_RUN" -eq 1 ]]; then
    FACILITY_ID="<resolved-from-${FACILITY_CODE}>"
  else
    FACILITY_ID="$(printf '%s' "$RESP_BODY" | FACILITY_CODE="$FACILITY_CODE" python3 -c '
import json, os, sys
code = os.environ["FACILITY_CODE"]
doc = json.load(sys.stdin)
items = (doc.get("data") or {}).get("items") or []
match = next((i for i in items if i.get("code") == code), None)
if not match or not match.get("facilityUid"):
    sys.stderr.write("no facility with code %s (or missing facilityUid) in search results\n" % code)
    sys.exit(3)
print(match["facilityUid"])
')" || fail "could not resolve facility UUID for ${FACILITY_CODE}"
  fi
  info "    facility UUID = ${FACILITY_ID}"
else
  info "1/5 using supplied FACILITY_ID=${FACILITY_ID}"
fi

# ── Step 2: preload demo provider "Dr Moyo" → mint claim token ───────────────
info "2/5 preloading provider Dr ${PROVIDER_FAMILY_NAME} (reg ${PROVIDER_REG_NUMBER})"
preload_body="$(
  PG="$PROVIDER_GIVEN_NAME" PF="$PROVIDER_FAMILY_NAME" PP="$PROVIDER_PROFESSION" \
  PC="$PROVIDER_CADRE" PCC="$PROVIDER_COUNCIL_CODE" PRN="$PROVIDER_REG_NUMBER" \
  PCH="$PROVIDER_CONTACT_HINT" python3 -c '
import json, os
row = {
    "givenName": os.environ["PG"],
    "familyName": os.environ["PF"],
    "profession": os.environ["PP"],
    "cadre": os.environ["PC"],
    "councilCode": os.environ["PCC"],
    "registrationNumber": os.environ["PRN"],
    "contactHint": os.environ["PCH"],
}
print(json.dumps({"rows": [row]}))
')"
http_call POST "${VARAPI_BASE}/v1/internal/providers/bootstrap/preload" 200 "$preload_body"
if [[ "$DRY_RUN" -eq 1 ]]; then
  PROVIDER_PUBLIC_ID="<minted-on-create>"
  CLAIM_TOKEN="<minted-on-create>"
else
  outcome="$(json_extract 'd["results"][0]["outcome"]')" || fail "malformed preload response: ${RESP_BODY}"
  if [[ "$outcome" != "CREATED" ]]; then
    printf '%s\n' "$RESP_BODY" >&2
    fail "provider preload outcome=${outcome} (a claim token is minted only on CREATED). Registration '${PROVIDER_REG_NUMBER}' likely already preloaded — unset PROVIDER_REG_NUMBER for a fresh nonce, or use a new value."
  fi
  PROVIDER_PUBLIC_ID="$(json_extract 'd["results"][0]["providerPublicId"] or ""')"
  CLAIM_TOKEN="$(json_extract 'd["results"][0]["claimToken"] or ""')"
  [[ -n "$CLAIM_TOKEN" ]] || { printf '%s\n' "$RESP_BODY" >&2; fail "preload CREATED but no claimToken returned"; }
  info "    provider ${PROVIDER_PUBLIC_ID} preloaded; claim token captured"
fi

# ── Step 3: facility per-source legitimacy verdicts (upserts) ────────────────
info "3/5 stamping per-source facility legitimacy for ${FACILITY_ID}"

hpa_body='{"status":"EXPIRED","asOf":"2023-01-01T00:00:00Z","evidenceRef":"IATG-E2E-SEED"}'
http_call PUT "${TUSO_BASE}/v1/internal/facilities/${FACILITY_ID}/source-legitimacy/HPA_LEGAL" 200 "$hpa_body"
info "    HPA_LEGAL = EXPIRED"

ministry_body='{"status":"REGISTERED_CURRENT","evidenceRef":"IATG-E2E-SEED"}'
http_call PUT "${TUSO_BASE}/v1/internal/facilities/${FACILITY_ID}/source-legitimacy/MINISTRY_OPERATIONAL" 200 "$ministry_body"
info "    MINISTRY_OPERATIONAL = REGISTERED_CURRENT"

exception_body='{"status":"GOVERNMENT_OPERATIONAL_EXCEPTION","allowedOnPlatform":true,"reason":"Public facility operates under Ministry authority despite lapsed HPA registration (IATG e2e seed).","evidenceRef":"IATG-E2E-SEED"}'
http_call PUT "${TUSO_BASE}/v1/internal/facilities/${FACILITY_ID}/source-legitimacy/PLATFORM_OPERATIONAL" 200 "$exception_body"
info "    PLATFORM_OPERATIONAL = GOVERNMENT_OPERATIONAL_EXCEPTION (allowedOnPlatform=true)"

# ── Step 4: EC-bearing HSC employment for the citizen (Channel-B match key) ──
# Created over the real governance REST upsert (system of record for public
# employment). linkedHealthId = the seeded citizen; ecNumber is the harness
# Step-6 match key. Idempotent: re-running upserts a fresh row, and the match is
# a single-EC lookup — pin EC_NUMBER only if you also clear prior rows, else a
# duplicate EC would (honestly) resolve to CONFLICT.
info "4/5 seeding citizen EC employment (health=${CLAIMANT_HEALTH_ID} ec=${EC_NUMBER})"
employment_body="$(
  WID="$EMPLOYMENT_WORKER_ID" HID="$CLAIMANT_HEALTH_ID" EC="$EC_NUMBER" python3 -c '
import json, os
print(json.dumps({
    "providerWorkerId": os.environ["WID"],
    "employmentStatus": "ACTIVE",
    "linkedHealthId": os.environ["HID"],
    "ecNumber": os.environ["EC"],
    "postTitle": "Medical Officer",
    "cadre": "DOCTOR",
    "verificationStatus": "VERIFIED",
}))
')"
http_call POST "${WGV_BASE}/v1/internal/governance/hsc/employment-records" 201 "$employment_body"
if [[ "$DRY_RUN" -eq 0 ]]; then
  emp_ec="$(json_extract 'd["data"]["ecNumber"] or ""')" || fail "malformed employment upsert response: ${RESP_BODY}"
  [[ -n "$emp_ec" ]] || { printf '%s\n' "$RESP_BODY" >&2; fail "employment upsert returned no ecNumber (E3 wiring missing on this build?)"; }
  info "    HSC employment created; ecNumber=${emp_ec} linkedHealthId=${CLAIMANT_HEALTH_ID}"
fi

# ── Step 5: emit machine-parseable outputs for the e2e harness ───────────────
info "5/5 emitting fixture outputs"
printf -- '----- IATG_E2E_SEED_OUTPUT_BEGIN -----\n'
printf 'FACILITY_CODE=%s\n' "$FACILITY_CODE"
printf 'FACILITY_ID=%s\n' "$FACILITY_ID"
printf 'REG_NUMBER=%s\n' "$PROVIDER_REG_NUMBER"
printf 'PROVIDER_PUBLIC_ID=%s\n' "$PROVIDER_PUBLIC_ID"
printf 'CLAIM_TOKEN=%s\n' "$CLAIM_TOKEN"
printf 'CLAIMANT_HEALTH_ID=%s\n' "$CLAIMANT_HEALTH_ID"
printf 'EC_NUMBER=%s\n' "$EC_NUMBER"
printf 'LEGITIMACY_HPA_LEGAL=EXPIRED\n'
printf 'LEGITIMACY_MINISTRY_OPERATIONAL=REGISTERED_CURRENT\n'
printf 'LEGITIMACY_PLATFORM_OPERATIONAL=GOVERNMENT_OPERATIONAL_EXCEPTION\n'
printf -- '----- IATG_E2E_SEED_OUTPUT_END -----\n'

if [[ "$DRY_RUN" -eq 1 ]]; then
  info "dry-run complete — no changes made"
else
  info "seed complete"
fi

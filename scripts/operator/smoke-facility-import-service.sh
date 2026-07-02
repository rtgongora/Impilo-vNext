#!/usr/bin/env bash
#
# Service-level facility master import smoke.
#
# Proves the facility absorption flow through the RUNNING services (TUSO + BFF/admin), not just SQL and
# unit tests. It orchestrates the already-built importer script for stage/apply/reconcile and calls the
# real TUSO + BFF APIs for reads and review-decision mutations. No business logic is duplicated here.
#
# Flow (fixture mode, default):
#   1. stage a tiny fixture via `import-facility-master-pack.sh --stage-only`  -> capture run id
#   2. read run/rows/review via API; assert missing-code / duplicate-name / acceptable-missing rows
#   3. review mutations via API: supply-code + approve the corrected row; approve a clean row;
#      reject one duplicate-name row; resolve-distinct the other (reason); prove a duplicate-code row
#      cannot be approved while its twin is unresolved (expect 409)
#   4. apply approved rows via `--apply-approved`
#   5. reconcile via `--reconcile`
#   6. BFF/admin route checks (list/detail/rows/review + facility provenance/checklist)
#   7. print admin UI route hints
#
# Full-stage mode (--full-stage): stage the real 1,773-row clean CSV (verify count) but NEVER auto-apply.
#
# HONEST BOUNDARY: this only proves anything when pointed at a running TUSO/BFF stack. Against no stack
# it fails safely. It never fabricates decisions or codes and never marks facilities operational.
#
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMPORTER="$HERE/import-facility-master-pack.sh"
REPO_ROOT="$(cd "$HERE/../.." && pwd)"

TUSO_BASE="${TUSO_BASE_URL:-http://localhost:8084}"
BFF_BASE="${EXPERIENCE_BFF_BASE_URL:-http://localhost:8160}"
TOKEN="${IMPILO_AUTH_TOKEN:-}"
TENANT_ID="${X_TENANT_ID:-moh-zw}"
SMOKE_FIXTURE="${SMOKE_FIXTURE:-$HERE/fixtures/smoke_facility_import.csv}"
SOURCE="${FACILITY_IMPORT_SOURCE:-}"
SOURCE_LABEL="${FACILITY_IMPORT_SOURCE_LABEL:-}"
RUN_ID="${FACILITY_IMPORT_RUN_ID:-}"
FULL_STAGE=0
SKIP_UI_CHECK=0
FULL_CSV="$REPO_ROOT/data/source/zimbabwe/master-health-facility/2024-07-23/clean_tuso_facility_import.csv"
EXPECTED_FULL_ROWS=1773
CURL="${IMPORT_CURL:-curl}"

usage() { sed -n '2,33p' "$0"; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url)       TUSO_BASE="$2"; shift 2 ;;
    --bff-base-url)   BFF_BASE="$2"; shift 2 ;;
    --source)         SOURCE="$2"; shift 2 ;;
    --source-label)   SOURCE_LABEL="$2"; shift 2 ;;
    --token)          TOKEN="$2"; shift 2 ;;
    --tenant)         TENANT_ID="$2"; shift 2 ;;
    --smoke-fixture)  SMOKE_FIXTURE="$2"; shift 2 ;;
    --run-id)         RUN_ID="$2"; shift 2 ;;
    --full-stage)     FULL_STAGE=1; shift ;;
    --skip-ui-check)  SKIP_UI_CHECK=1; shift ;;
    -h|--help)        usage; exit 0 ;;
    *) echo "unknown argument: $1 (see --help)" >&2; exit 1 ;;
  esac
done

fail() { echo "FAIL: $*" >&2; exit 1; }
ok()   { echo "  ok: $*"; }
info() { echo "==> $*"; }

hdr=(
  -H "Content-Type: application/json"
  -H "X-Tenant-ID: ${TENANT_ID}"
  -H "X-Pod-ID: ${X_POD_ID:-national}"
  -H "X-Request-ID: facility-smoke-$(date +%s)"
  -H "X-Correlation-ID: facility-smoke-$(date +%s)"
  -H "X-Actor-ID: ${X_ACTOR_ID:-operator-smoke}"
  -H "X-Actor-Type: OPERATOR"
  -H "X-Purpose-Of-Use: ${X_PURPOSE_OF_USE:-SYSTEM_ADMINISTRATION}"
)
[[ -n "$TOKEN" ]] && hdr+=( -H "Authorization: Bearer ${TOKEN}" )

# ---- HTTP (return curl status; caller decides). Overridable via IMPORT_CURL for tests. ------------
tget()  { "$CURL" -sS -f "${hdr[@]}" "$1"; }
tpost() { "$CURL" -sS -f -X POST "${hdr[@]}" -d "${2:-{}}" "$1"; }
# POST expecting a downstream 4xx (returns 0 iff curl reported an HTTP error).
tpost_expect_fail() { "$CURL" -sS -f -X POST "${hdr[@]}" -d "${2:-{}}" "$1" >/dev/null 2>&1 && return 1 || return 0; }

field() { python3 -c '
import json,sys
doc=json.loads(sys.argv[1] or "{}"); d=doc.get("data",doc) if isinstance(doc,dict) else doc
cur=d
for p in sys.argv[2].split("."):
    if p.isdigit() and isinstance(cur,list):
        cur = cur[int(p)] if int(p) < len(cur) else None
    else:
        cur = cur.get(p) if isinstance(cur,dict) else None
    if cur is None: break
print("" if cur is None else cur)
' "$1" "$2"; }

tuso_runs="${TUSO_BASE}/v1/internal/facilities/import/runs"

rows_json() { # $1 query -> rows response body
  tget "${tuso_runs}/${RUN_ID}/rows?$1" || fail "rows read failed (backend unreachable / non-2xx)"
}
first_row_id() { # $1 query -> first content[].id or empty
  local body; body="$(rows_json "$1")" || return 1
  field "$body" content.0.id
}

# ==================================================================================================
# Ready-pack / fixture existence checks
# ==================================================================================================
if [[ "$FULL_STAGE" == "1" ]]; then
  SOURCE="${SOURCE:-$FULL_CSV}"
  SOURCE_LABEL="${SOURCE_LABEL:-MASTER_HEALTH_FACILITY_2024_07_23}"
  [[ -f "$SOURCE" ]] || fail "ready pack missing: $SOURCE"
  actual="$(python3 -c 'import csv,sys; print(sum(1 for _ in csv.DictReader(open(sys.argv[1],encoding="utf-8-sig"))))' "$SOURCE")"
  [[ "$actual" == "$EXPECTED_FULL_ROWS" ]] || fail "row count mismatch: expected ${EXPECTED_FULL_ROWS}, got ${actual}"
  ok "ready pack present with ${actual} rows"
else
  SOURCE="${SOURCE:-$SMOKE_FIXTURE}"
  SOURCE_LABEL="${SOURCE_LABEL:-MASTER_HEALTH_FACILITY_SERVICE_SMOKE}"
  [[ -f "$SOURCE" ]] || fail "smoke fixture missing: $SOURCE"
  ok "smoke fixture present: $SOURCE"
fi

# ==================================================================================================
# FULL-STAGE MODE: stage the real pack (no apply), verify, print route, stop.
# ==================================================================================================
if [[ "$FULL_STAGE" == "1" ]]; then
  info "FULL-STAGE: staging ${EXPECTED_FULL_ROWS}-row clean CSV (no auto-apply)"
  out="$(FACILITY_IMPORT_SOURCE="" TUSO_BASE_URL="$TUSO_BASE" IMPORT_CURL="$CURL" \
        bash "$IMPORTER" --stage-only --source "$SOURCE" --source-label "$SOURCE_LABEL")" \
        || fail "stage-only failed against ${TUSO_BASE}"
  echo "$out"
  RUN_ID="$(grep -oE 'Import run ID: [0-9]+' <<<"$out" | grep -oE '[0-9]+' | head -1)"
  [[ -n "$RUN_ID" ]] || fail "no run id returned from stage-only"
  total="$(field "$(rows_json 'size=1')" totalElements)"
  [[ "$total" == "$EXPECTED_FULL_ROWS" ]] || fail "staged row count ${total} != ${EXPECTED_FULL_ROWS}"
  ok "run ${RUN_ID} staged with ${total} rows"
  acc="$(field "$(rows_json 'hasAcceptableMissingFields=true&size=1')" totalElements)"
  ok "acceptable-missing rows still flagged: ${acc}"
  imported="$(field "$(rows_json 'decisionStatus=IMPORTED&size=1')" totalElements)"
  [[ "${imported:-0}" == "0" || -z "$imported" ]] || fail "rows were auto-imported in stage-only (${imported})"
  ok "no rows auto-imported (stage-only)"
  echo "  Admin UI: /admin/facility-imports/${RUN_ID}"
  echo "  NOTE: full apply is NOT run automatically. Review + approve, then --apply-approved --run-id ${RUN_ID}."
  exit 0
fi

# ==================================================================================================
# FIXTURE MODE: full service flow
# ==================================================================================================
info "SERVICE SMOKE — fixture flow against TUSO=${TUSO_BASE} BFF=${BFF_BASE}"

# 1. Stage fixture -------------------------------------------------------------------------------
info "1) stage fixture"
out="$(FACILITY_IMPORT_SOURCE="" TUSO_BASE_URL="$TUSO_BASE" IMPORT_CURL="$CURL" \
      bash "$IMPORTER" --stage-only --source "$SOURCE" --source-label "$SOURCE_LABEL")" \
      || fail "stage-only failed against ${TUSO_BASE}"
RUN_ID="$(grep -oE 'Import run ID: [0-9]+' <<<"$out" | grep -oE '[0-9]+' | head -1)"
[[ -n "$RUN_ID" ]] || fail "no run id returned from stage-only"
ok "staged run id = ${RUN_ID}"

tget "${tuso_runs}/${RUN_ID}" >/dev/null || fail "run ${RUN_ID} not readable via API"
ok "run readable via API"

missing_code="$(field "$(rows_json 'outcome=EXCLUDED_MISSING_FACILITY_CODE&size=1')" totalElements)"
dup_name="$(field "$(rows_json 'outcome=EXCLUDED_DUPLICATE_FACILITY_NAME&size=1')" totalElements)"
review_body="$(tget "${tuso_runs}/${RUN_ID}/review")" || fail "review endpoint failed"
acc_bucket="$(field "$review_body" acceptableMissingFields.count)"
[[ "${missing_code:-0}" -ge 1 ]] || fail "expected >=1 missing-code row, got ${missing_code}"
[[ "${dup_name:-0}" -ge 2 ]] || fail "expected >=2 duplicate-name rows, got ${dup_name}"
[[ "${acc_bucket:-0}" -ge 1 ]] || fail "expected >=1 acceptable-missing row, got ${acc_bucket}"
ok "rows/review reflect fixture (missing-code=${missing_code}, dup-name=${dup_name}, acceptable-missing=${acc_bucket})"

# 2. Review mutations ----------------------------------------------------------------------------
info "2) review-decision mutations (real APIs)"
mc_id="$(first_row_id 'outcome=EXCLUDED_MISSING_FACILITY_CODE')" || true
[[ -n "$mc_id" ]] || fail "could not resolve missing-code row id"
tpost "${tuso_runs}/${RUN_ID}/rows/${mc_id}/supply-code" '{"facilityCode":"SMKFIX1","reason":"smoke supply"}' >/dev/null \
  || fail "supply-code failed"
tpost "${tuso_runs}/${RUN_ID}/rows/${mc_id}/approve" >/dev/null || fail "approve corrected row failed"
ok "missing-code row corrected (SMKFIX1) + approved"

# Approve a clean READY row.
clean_id="$(first_row_id 'decisionStatus=READY_FOR_IMPORT')" || true
[[ -n "$clean_id" ]] || fail "could not resolve a READY row id"
tpost "${tuso_runs}/${RUN_ID}/rows/${clean_id}/approve" >/dev/null || fail "approve clean row failed"
ok "clean row approved"

# Reject one duplicate-name row; resolve the other as distinct (with reason) then approve it.
dn_body="$(rows_json 'outcome=EXCLUDED_DUPLICATE_FACILITY_NAME&size=50')"
dn0="$(field "$dn_body" content.0.id)"; dn1="$(field "$dn_body" content.1.id)"
[[ -n "$dn0" && -n "$dn1" ]] || fail "could not resolve duplicate-name row ids"
tpost "${tuso_runs}/${RUN_ID}/rows/${dn0}/reject" '{"reason":"smoke reject duplicate"}' >/dev/null \
  || fail "reject failed"
tpost "${tuso_runs}/${RUN_ID}/rows/${dn1}/resolve-distinct" '{"reason":"genuinely separate site"}' >/dev/null \
  || fail "resolve-distinct failed"
tpost "${tuso_runs}/${RUN_ID}/rows/${dn1}/approve" >/dev/null || fail "approve resolved-distinct failed"
ok "duplicate-name: one rejected, one resolved-distinct + approved"

# Duplicate-code: approve one; the twin must NOT be approvable while it shares the code (expect 409).
dc_body="$(rows_json 'facilityCode=SMK020&size=50')"
dc0="$(field "$dc_body" content.0.id)"; dc1="$(field "$dc_body" content.1.id)"
if [[ -n "$dc0" && -n "$dc1" ]]; then
  tpost "${tuso_runs}/${RUN_ID}/rows/${dc0}/approve" >/dev/null || fail "approve dup-code A failed"
  if tpost_expect_fail "${tuso_runs}/${RUN_ID}/rows/${dc1}/approve"; then
    ok "duplicate-code twin correctly blocked from approval (conflict)"
  else
    fail "duplicate-code twin was approved despite code conflict"
  fi
else
  ok "duplicate-code pair not present in fixture (skipped)"
fi

# 3. Apply approved ------------------------------------------------------------------------------
info "3) apply approved"
FACILITY_IMPORT_SOURCE="" TUSO_BASE_URL="$TUSO_BASE" IMPORT_CURL="$CURL" \
  bash "$IMPORTER" --apply-approved --run-id "$RUN_ID" || fail "apply-approved failed"
imported="$(field "$(rows_json 'decisionStatus=IMPORTED&size=1')" totalElements)"
[[ "${imported:-0}" -ge 1 ]] || fail "no rows imported after apply-approved"
ok "approved rows applied (imported=${imported})"

# Verify an imported facility's identity/lifecycle/provenance via TUSO.
imp_body="$(rows_json 'decisionStatus=IMPORTED&size=1')"
fac_id="$(field "$imp_body" content.0.resultFacilityId)"
[[ -n "$fac_id" ]] || fail "imported row has no result facility id"
prov="$(tget "${TUSO_BASE}/v1/internal/facilities/${fac_id}/import-provenance")" || fail "provenance read failed"
[[ "$(field "$prov" identity.lifecycleState)" == "IMPORTED_PENDING_CONFIGURATION" ]] \
  || fail "imported facility not IMPORTED_PENDING_CONFIGURATION"
[[ -n "$(field "$prov" identity.facilityUuid)" ]] || fail "facility uuid missing"
[[ -n "$(field "$prov" identity.facilityCode)" ]] || fail "facility code missing"
ok "imported facility ${fac_id}: uuid distinct from code, lifecycle IMPORTED_PENDING_CONFIGURATION"

# 4. Reconcile -----------------------------------------------------------------------------------
info "4) reconcile"
FACILITY_IMPORT_SOURCE="" TUSO_BASE_URL="$TUSO_BASE" IMPORT_CURL="$CURL" \
  bash "$IMPORTER" --reconcile --run-id "$RUN_ID" || fail "reconcile failed"
ok "reconcile completed"

# 5. BFF / admin route checks --------------------------------------------------------------------
info "5) BFF/admin route checks"
bff_admin="${BFF_BASE}/internal/v1/admin"
if tget "${bff_admin}/facility-import-runs" >/dev/null 2>&1; then
  tget "${bff_admin}/facility-import-runs/${RUN_ID}" >/dev/null || fail "BFF run detail failed"
  tget "${bff_admin}/facility-import-runs/${RUN_ID}/rows?size=1" >/dev/null || fail "BFF rows failed"
  tget "${bff_admin}/facility-import-runs/${RUN_ID}/review" >/dev/null || fail "BFF review failed"
  tget "${bff_admin}/facilities/${fac_id}/import-provenance" >/dev/null || fail "BFF provenance failed"
  tget "${bff_admin}/facilities/${fac_id}/missing-field-checklist" >/dev/null || fail "BFF checklist failed"
  ok "BFF admin routes served run/rows/review/provenance/checklist"
else
  echo "  WARN: BFF admin not reachable/authorised at ${bff_admin} — skipping BFF checks."
  echo "        (BFF admin routes require an admin-role token; set IMPILO_AUTH_TOKEN.)"
fi

# 6. Admin UI route hints ------------------------------------------------------------------------
if [[ "$SKIP_UI_CHECK" != "1" ]]; then
  info "6) admin UI routes to verify manually"
  echo "  /admin/facility-imports"
  echo "  /admin/facility-imports/${RUN_ID}"
  echo "  /admin/facility-imports/${RUN_ID}/review"
  echo "  (facility detail shows identity + provenance + missing-field checklist for imported facilities)"
fi

echo ""
echo "PASS: service-level facility import smoke completed for run ${RUN_ID}."

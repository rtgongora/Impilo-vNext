#!/usr/bin/env bash
#
# Tests for smoke-facility-import-service.sh using a fake TUSO/BFF (IMPORT_CURL). No running services.
# Validates orchestration/parsing/flow control — NOT a substitute for the real VM/preview smoke.
#
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SMOKE="$HERE/smoke-facility-import-service.sh"
FIXTURE="$HERE/fixtures/smoke_facility_import.csv"
WORK="$(mktemp -d)"; trap 'rm -rf "$WORK"' EXIT
PASS=0; FAIL=0
ck()  { if [[ "$1" == ok ]]; then PASS=$((PASS+1)); echo "  ok: $2"; else FAIL=$((FAIL+1)); echo "  FAIL: $2"; fi; }
aexit(){ if [[ "$1" == "$2" ]]; then ck ok "$3"; else ck fail "$3 (exit $1 != $2)"; fi; }
ahas() { if grep -qF -- "$2" <<<"$1"; then ck ok "$3"; else ck fail "$3 (missing: $2)"; echo "$1" | tail -8; fi; }

# ---- Fake TUSO/BFF curl ---------------------------------------------------------------------------
FAKE="$WORK/fake.sh"
cat >"$FAKE" <<'FAKE'
#!/usr/bin/env bash
method="GET"; url=""; prev=""
for a in "$@"; do
  [[ "$prev" == "-X" ]] && method="$a"
  url="$a"; prev="$a"
done
# Duplicate-code twin approval must fail (conflict).
if [[ "$url" == *"/rows/9016/approve" ]]; then exit 22; fi
case "$url" in
  *"/master-pack/dry-run"*)      echo '{"data":{"runId":9001,"recordsTotal":7,"recordsCreated":4,"recordsUpdated":0,"recordsSkipped":3,"recordsFailed":0,"qualitySummary":{"records_missing_facility_code":1,"records_duplicate_facility_name":2,"records_acceptable_missing":1}}}' ;;
  *"/apply-approved"*)           echo '{"data":{"applied":4,"skipped":0,"rows":[]}}' ;;
  *"/rows?"*"outcome=EXCLUDED_MISSING_FACILITY_CODE"*) echo '{"data":{"totalElements":1,"content":[{"id":9011}]}}' ;;
  *"/rows?"*"outcome=EXCLUDED_DUPLICATE_FACILITY_NAME"*) echo '{"data":{"totalElements":2,"content":[{"id":9013},{"id":9014}]}}' ;;
  *"/rows?"*"facilityCode=SMK020"*) echo '{"data":{"totalElements":2,"content":[{"id":9015},{"id":9016}]}}' ;;
  *"/rows?"*"decisionStatus=READY_FOR_IMPORT"*) echo '{"data":{"totalElements":4,"content":[{"id":9010}]}}' ;;
  *"/rows?"*"decisionStatus=APPROVED_FOR_IMPORT"*) echo '{"data":{"totalElements":4,"content":[]}}' ;;
  *"/rows?"*"decisionStatus=PENDING_REVIEW"*) echo '{"data":{"totalElements":1,"content":[]}}' ;;
  *"/rows?"*"decisionStatus=IMPORTED"*) echo "{\"data\":{\"totalElements\":${FAKE_IMPORTED:-4},\"content\":[{\"id\":9010,\"resultFacilityId\":5001}]}}" ;;
  *"/rows?"*"hasAcceptableMissingFields=true"*) echo "{\"data\":{\"totalElements\":${FAKE_ACC:-1},\"content\":[]}}" ;;
  *"/rows?size=1"*)              echo "{\"data\":{\"totalElements\":${FAKE_TOTAL:-7},\"content\":[]}}" ;;
  *"/rows?"*)                    echo '{"data":{"totalElements":4,"content":[{"id":9010,"resultFacilityId":5001}]}}' ;;
  *"/review"*)                   echo '{"data":{"importedRows":{"count":0},"missingFacilityCode":{"count":1},"duplicateFacilityNames":{"count":2},"acceptableMissingFields":{"count":1},"failedRows":{"count":0}}}' ;;
  *"/import-provenance"*)        echo '{"data":{"identity":{"facilityUuid":"uuid-5001","facilityCode":"SMK001","lifecycleState":"IMPORTED_PENDING_CONFIGURATION","importedFromMaster":true}}}' ;;
  *"/missing-field-checklist"*)  echo '{"data":{"checklist":[],"downstreamMaterialisationStatus":"PENDING_CONFIGURATION"}}' ;;
  *"/rows/"*"/supply-code"*|*"/rows/"*"/approve"|*"/rows/"*"/reject"|*"/rows/"*"/resolve-distinct"*) echo '{"data":{"decisionStatus":"OK"}}' ;;
  *"/admin/facility-import-runs")  echo '{"data":{"count":1,"runs":[{"runId":9001}]}}' ;;
  *"/admin/"*)                   echo '{"data":{"ok":true}}' ;;
  *"/import/runs/"[0-9]*)        echo '{"data":{"runId":9001}}' ;;
  *)                             echo '{"data":{}}' ;;
esac
FAKE
chmod +x "$FAKE"

run() { IMPORT_CURL="$FAKE" bash "$SMOKE" "$@" 2>&1; }

echo "== service-level smoke script =="

out="$(bash "$SMOKE" --help)"; ahas "$out" "Service-level facility master import smoke" "--help prints header"

# preflight (fake endpoints reachable) -> READY
out="$(run --preflight --base-url http://tuso.test --bff-base-url http://bff.test --token tkn)"; rc=$?
aexit "$rc" 0 "preflight exits 0 when ready"
ahas "$out" "PREFLIGHT: READY" "preflight reports READY"
ahas "$out" "ready pack present with 1773 rows" "preflight verifies 1773-row pack"

# fixture missing -> fail before hitting services
out="$(run --smoke-fixture "$WORK/nope.csv"; echo "rc=$?")"; ahas "$out" "rc=1" "missing fixture fails safely"

# happy-path fixture flow against fake service
out="$(run --base-url http://tuso.test --bff-base-url http://bff.test --token tkn)"; rc=$?
aexit "$rc" 0 "fixture flow exits 0 against fake service"
ahas "$out" "staged run id = 9001" "captures run id from stage-only"
ahas "$out" "missing-code row corrected (SMKFIX1) + approved" "supply-code + approve ran"
ahas "$out" "duplicate-code twin correctly blocked from approval" "dup-code conflict enforced"
ahas "$out" "lifecycle IMPORTED_PENDING_CONFIGURATION" "imported facility not operational"
ahas "$out" "BFF admin routes served" "BFF admin checks ran"
ahas "$out" "/admin/facility-imports/9001" "admin UI route hint printed"
ahas "$out" "PASS: service-level facility import smoke" "overall PASS"

# full-stage: real 1773 CSV, fake returns matching total; must not auto-apply
FULL_CSV="$HERE/../../data/source/zimbabwe/master-health-facility/2024-07-23/clean_tuso_facility_import.csv"
if [[ -f "$FULL_CSV" ]]; then
  out="$(FAKE_TOTAL=1773 FAKE_ACC=194 FAKE_IMPORTED=0 IMPORT_CURL="$FAKE" bash "$SMOKE" --full-stage --base-url http://tuso.test 2>&1)"; rc=$?
  aexit "$rc" 0 "full-stage exits 0"
  ahas "$out" "ready pack present with 1773 rows" "full-stage verifies 1773 rows"
  ahas "$out" "no rows auto-imported (stage-only)" "full-stage does not auto-apply"
else
  ck ok "full-stage row-count check (real CSV absent here — skipped)"
fi

# full-stage row-count mismatch -> fail
out="$(FAKE_TOTAL=7 IMPORT_CURL="$FAKE" bash "$SMOKE" --full-stage --source "$FIXTURE" --source-label X 2>&1; echo rc=$?)"
ahas "$out" "rc=1" "full-stage rejects wrong row count"

echo ""; echo "PASS=$PASS FAIL=$FAIL"; [[ "$FAIL" -eq 0 ]]

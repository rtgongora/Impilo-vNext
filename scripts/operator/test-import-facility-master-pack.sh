#!/usr/bin/env bash
#
# Lightweight tests for import-facility-master-pack.sh.
# Uses a fake curl (IMPORT_CURL) that records requested URLs and returns canned JSON, so the modes
# can be exercised without a live TUSO. No network, no DB.
#
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT="$HERE/import-facility-master-pack.sh"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

PASS=0
FAIL=0
check() { # desc, condition already evaluated via $? by caller pattern
  if [[ "$1" == "ok" ]]; then PASS=$((PASS+1)); echo "  ok: $2";
  else FAIL=$((FAIL+1)); echo "  FAIL: $2"; fi
}
assert_contains() { # haystack needle desc
  if grep -qF -- "$2" <<<"$1"; then check ok "$3"; else check fail "$3 (missing: $2)"; echo "----"; echo "$1" | tail -5; echo "----"; fi
}
assert_exit() { # actual expected desc
  if [[ "$1" == "$2" ]]; then check ok "$3"; else check fail "$3 (exit $1, expected $2)"; fi
}

# ---- Fake curl -------------------------------------------------------------------------------------
FAKE_CURL="$WORK/fake-curl.sh"
FAKE_LOG="$WORK/curl.log"
cat >"$FAKE_CURL" <<'FAKE'
#!/usr/bin/env bash
# Records the URL, returns canned JSON by URL. Honour a forced-failure toggle.
url=""
for a in "$@"; do url="$a"; done   # last arg is the URL
echo "$url" >> "$FAKE_LOG_PATH"
if [[ "${FAKE_FAIL:-0}" == "1" ]]; then exit 22; fi
case "$url" in
  *"/master-pack/dry-run"*)
    echo '{"data":{"runId":501,"dryRun":true,"recordsTotal":4,"recordsCreated":1,"recordsUpdated":0,"recordsSkipped":3,"recordsFailed":0,"qualitySummary":{"records_missing_facility_code":1,"records_duplicate_facility_code":0,"records_duplicate_facility_name":2,"records_acceptable_missing":1}}}' ;;
  *"/apply-approved"*)
    echo '{"data":{"applied":2,"skipped":0,"rows":[]}}' ;;
  *"/rows?"*"decisionStatus=APPROVED_FOR_IMPORT"*)
    echo '{"data":{"totalElements":2,"content":[]}}' ;;
  *"/rows?"*"decisionStatus=IMPORTED"*)
    echo '{"data":{"totalElements":1,"content":[{"resultFacilityId":900}]}}' ;;
  *"/rows?"*"hasAcceptableMissingFields=true"*)
    echo '{"data":{"totalElements":1,"content":[]}}' ;;
  *"/rows?"*)
    echo '{"data":{"totalElements":0,"content":[]}}' ;;
  *"/import-provenance"*)
    echo '{"data":{"identity":{"importedFromMaster":true,"lifecycleState":"IMPORTED_PENDING_CONFIGURATION"}}}' ;;
  *"/runs/501")
    echo '{"data":{"runId":501}}' ;;
  *) echo '{"data":{}}' ;;
esac
FAKE
chmod +x "$FAKE_CURL"
export FAKE_LOG_PATH="$FAKE_LOG"

# ---- Sample clean CSV ------------------------------------------------------------------------------
CSV="$WORK/clean.csv"
cat >"$CSV" <<'CSV'
source_row,facility_code,facility_name,province,district,latitude,longitude,facility_type_raw,facility_type_canonical,ownership_raw,ownership_canonical,location_raw,location_canonical,level_raw,level_canonical,contact_raw,status_raw,status_canonical,bed_capacity,date_opened,date_closed,comments,acceptable_missing_fields,frontend_missing_visible,geospatial_readiness,facility_setup_state,source_label
2,ZW010125,Bangure Clinic,Manicaland,Buhera,-19.53,31.75,RDC,CLINIC,Rural Council,RURAL_DISTRICT_COUNCIL,Rural,RURAL,Primary,PRIMARY,776673131,Open,ACTIVE,6,,,,,NO,READY,IMPORTED_PENDING_CONFIGURATION,MASTER_HEALTH_FACILITY_2024_07_23
CSV

run() { IMPORT_CURL="$FAKE_CURL" TUSO_BASE_URL="http://tuso.test" bash "$SCRIPT" "$@" 2>&1; }

echo "== importer script modes =="

# 1. help
out="$(bash "$SCRIPT" --help)"; assert_contains "$out" "Usage:" "--help prints usage"

# 2. missing mode
run >/dev/null 2>&1; assert_exit "$?" "1" "rejects missing mode"

# 3-5. missing required inputs
run --dry-run >/dev/null 2>&1; assert_exit "$?" "1" "rejects --dry-run without source"
run --stage-only >/dev/null 2>&1; assert_exit "$?" "1" "rejects --stage-only without source"
run --apply-approved >/dev/null 2>&1; assert_exit "$?" "1" "rejects --apply-approved without run-id"

# 6. dry-run
: >"$FAKE_LOG"
out="$(run --dry-run --source "$CSV")"
assert_contains "$out" "Total rows            : 4" "dry-run reports totals"
assert_contains "$(cat "$FAKE_LOG")" "/master-pack/dry-run" "dry-run hits dry-run endpoint"

# 7. stage-only
: >"$FAKE_LOG"
out="$(run --stage-only --source "$CSV")"
assert_contains "$out" "Import run ID: 501" "stage-only prints run id"
assert_contains "$out" "/admin/facility-imports/501" "stage-only prints admin route"
assert_contains "$(cat "$FAKE_LOG")" "/master-pack/dry-run" "stage-only stages via dry-run (no facilities)"

# 8. validate run
out="$(run --validate --run-id 501)"
assert_contains "$out" "Approved ready to apply: 2" "validate reports approved rows"

# 9. apply-approved
: >"$FAKE_LOG"
out="$(run --apply-approved --run-id 501)"
assert_contains "$out" "Applied rows            : 2" "apply-approved reports applied count"
assert_contains "$(cat "$FAKE_LOG")" "/runs/501/apply-approved" "apply-approved hits apply route"

# 10. reconcile
out="$(run --reconcile --run-id 501)"
assert_contains "$out" "Imported rows checked          : 1" "reconcile checks imported rows"
assert_contains "$out" "Facilities resolved            : 1" "reconcile resolves facility"

# 11. non-2xx fails safely
FAKE_FAIL=1 IMPORT_CURL="$FAKE_CURL" TUSO_BASE_URL="http://tuso.test" bash "$SCRIPT" --stage-only --source "$CSV" >/dev/null 2>&1
assert_exit "$?" "1" "non-2xx response fails safely"

echo ""
echo "PASS=$PASS FAIL=$FAIL"
[[ "$FAIL" -eq 0 ]]

#!/usr/bin/env bash
#
# Facility Master Health Facility absorption — operator import script.
#
# Absorbs the cleaned Zimbabwe Master Health Facility package into TUSO via the review workflow:
#   stage (persist rows, no facilities) -> review/validate -> approve (admin UI) -> apply-approved.
#
# Primary source (cleaned CSV, NOT the legacy JSON seed):
#   data/source/zimbabwe/master-health-facility/2024-07-23/clean_tuso_facility_import.csv
# Source label:
#   MASTER_HEALTH_FACILITY_2024_07_23
#
# Operator convention: calls TUSO directly with operator trust headers (X-Actor-Type: OPERATOR),
# matching the existing facility-master bootstrap. Policy/audit stay intact server-side (TUSO
# validates run/row state, RBAC via trust context, and appends review audit). An optional bearer
# token (IMPILO_AUTH_TOKEN) is forwarded when set.
#
# The script NEVER auto-imports excluded rows, never auto-resolves duplicates, never fabricates
# facility codes, and never marks facilities operational. Final facility records are only created via
# --apply-approved after rows are approved through the review workflow.
#
set -euo pipefail

# ---- Configuration (CLI flags override env; env overrides defaults) --------------------------------
TUSO_BASE="${TUSO_BASE_URL:-${TUSO_BASE:-http://localhost:8084}}"
BFF_BASE="${EXPERIENCE_BFF_BASE_URL:-http://localhost:8160}"
SOURCE="${FACILITY_IMPORT_SOURCE:-}"
SOURCE_LABEL="${FACILITY_IMPORT_SOURCE_LABEL:-MASTER_HEALTH_FACILITY_2024_07_23}"
RUN_ID="${FACILITY_IMPORT_RUN_ID:-}"
AUTH_TOKEN="${IMPILO_AUTH_TOKEN:-}"
LEGACY_JSON_SEED="${FACILITY_MASTER_SEED:-}"   # optional dev/legacy fallback (JSON records)

TENANT_ID="${X_TENANT_ID:-moh-zw}"
POD_ID="${X_POD_ID:-national}"
REQUEST_ID="${X_REQUEST_ID:-facility-master-import-$(date +%s)}"
CORRELATION_ID="${X_CORRELATION_ID:-$REQUEST_ID}"
ACTOR_ID="${X_ACTOR_ID:-operator-bootstrap}"
PURPOSE="${X_PURPOSE_OF_USE:-SYSTEM_ADMINISTRATION}"

CURL="${IMPORT_CURL:-curl}"       # overridable for tests
MODE=""

ADMIN_RUNS_ROUTE="/admin/facility-imports"

usage() {
  cat <<'USAGE'
Facility Master Health Facility importer

Usage:
  import-facility-master-pack.sh --dry-run       --source <clean.csv> [--source-label <LABEL>]
  import-facility-master-pack.sh --stage-only     --source <clean.csv> [--source-label <LABEL>]
  import-facility-master-pack.sh --validate      (--run-id <ID> | --source <clean.csv>)
  import-facility-master-pack.sh --apply-approved --run-id <ID>
  import-facility-master-pack.sh --reconcile      --run-id <ID> [--source-label <LABEL>]

Modes (exactly one required):
  --dry-run         Parse + validate the CSV and preview row outcomes (no final facilities).
  --stage-only      Create a real import run + persist row-level outcomes for review (no facilities).
  --validate        Validate a staged run (or a source package) and report readiness to apply.
  --apply-approved  Import ONLY rows explicitly approved through the review workflow (idempotent).
  --reconcile       Honest check of imported/staged state vs TUSO facilities (no fake repair).

Options:
  --source <path>         Cleaned CSV (clean_tuso_facility_import.csv). Env: FACILITY_IMPORT_SOURCE
  --source-label <label>  Provenance label. Default/Env: FACILITY_IMPORT_SOURCE_LABEL
  --run-id <id>           Import run id (review/apply/reconcile). Env: FACILITY_IMPORT_RUN_ID
  -h, --help              Show this help.

Environment:
  TUSO_BASE_URL, EXPERIENCE_BFF_BASE_URL, IMPILO_AUTH_TOKEN,
  FACILITY_IMPORT_SOURCE, FACILITY_IMPORT_SOURCE_LABEL, FACILITY_IMPORT_RUN_ID
  FACILITY_MASTER_SEED (optional legacy/dev JSON records fallback)

Safety: never imports missing-code or unresolved duplicate rows; never fabricates facility codes;
never marks imported facilities operational. Final facilities are created only by --apply-approved.
USAGE
}

fail() { echo "ERROR: $*" >&2; exit 1; }

set_mode() {
  [[ -z "$MODE" ]] || fail "only one mode may be specified (already: --$MODE)"
  MODE="$1"
}

# ---- Argument parsing ------------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run)        set_mode "dry-run"; shift ;;
    --stage-only)     set_mode "stage-only"; shift ;;
    --validate)       set_mode "validate"; shift ;;
    --apply-approved) set_mode "apply-approved"; shift ;;
    --reconcile)      set_mode "reconcile"; shift ;;
    --source)         SOURCE="${2:-}"; shift 2 ;;
    --source-label)   SOURCE_LABEL="${2:-}"; shift 2 ;;
    --run-id)         RUN_ID="${2:-}"; shift 2 ;;
    -h|--help)        usage; exit 0 ;;
    *)                fail "unknown argument: $1 (see --help)" ;;
  esac
done

[[ -n "$MODE" ]] || { usage >&2; fail "no mode specified (see --help)"; }

trust_headers=(
  -H "Content-Type: application/json"
  -H "X-Tenant-ID: ${TENANT_ID}"
  -H "X-Pod-ID: ${POD_ID}"
  -H "X-Request-ID: ${REQUEST_ID}"
  -H "X-Correlation-ID: ${CORRELATION_ID}"
  -H "X-Actor-ID: ${ACTOR_ID}"
  -H "X-Actor-Type: OPERATOR"
  -H "X-Purpose-Of-Use: ${PURPOSE}"
)
[[ -n "$AUTH_TOKEN" ]] && trust_headers+=( -H "Authorization: Bearer ${AUTH_TOKEN}" )

# ---- HTTP helpers (curl -f => non-2xx fails safely; overridable via IMPORT_CURL) -------------------
http_get() {
  "$CURL" -sS -f "${trust_headers[@]}" "$1" \
    || fail "request failed (GET $1) — backend unreachable or returned non-2xx"
}
http_post() {
  # $1 url, $2 json body
  "$CURL" -sS -f -X POST "${trust_headers[@]}" -d "${2:-{}}" "$1" \
    || fail "request failed (POST $1) — backend unreachable, unauthorised, or returned non-2xx"
}

# Extract a value from a JSON doc ($1) by a dotted path ($2) against the "data" envelope.
data_field() {
  python3 -c '
import json, sys
doc = json.loads(sys.argv[1] or "{}")
data = doc.get("data", doc) if isinstance(doc, dict) else doc
cur = data
for part in sys.argv[2].split("."):
    cur = cur.get(part) if isinstance(cur, dict) else None
    if cur is None:
        break
print("" if cur is None else cur)
' "$1" "$2"
}

require_source() {
  [[ -n "$SOURCE" ]] || fail "missing --source (or FACILITY_IMPORT_SOURCE) for --$MODE"
  [[ -f "$SOURCE" ]] || fail "source file not found: $SOURCE"
  [[ -n "$SOURCE_LABEL" ]] || fail "missing --source-label (or FACILITY_IMPORT_SOURCE_LABEL)"
}

require_run_id() {
  [[ -n "$RUN_ID" ]] || fail "missing --run-id (or FACILITY_IMPORT_RUN_ID) for --$MODE"
}

# ---- CSV -> import records payload (mirrors TUSO loadPackFromCsv; raw values preserved) ------------
build_payload() {
  # $1 = dryRun ("true"/"false")
  local dry_run="$1"
  if [[ -n "$LEGACY_JSON_SEED" && -f "$LEGACY_JSON_SEED" ]]; then
    # Legacy/dev fallback: JSON records already in record shape.
    python3 - "$LEGACY_JSON_SEED" "$dry_run" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as f:
    records = json.load(f)
print(json.dumps({"dryRun": sys.argv[2] == "true", "syncNdila": False,
                  "reconcileDuplicateCodes": False, "records": records}))
PY
    return
  fi
  python3 - "$SOURCE" "$SOURCE_LABEL" "$dry_run" <<'PY'
import csv, json, sys
src, label, dry = sys.argv[1], sys.argv[2], sys.argv[3] == "true"

def nz(v):
    v = (v or "").strip()
    return v if v else None

def num(v, cast):
    v = nz(v)
    if v is None:
        return None
    try:
        return cast(v)
    except ValueError:
        return None

records = []
with open(src, encoding="utf-8-sig", newline="") as f:
    for row in csv.DictReader(f):
        source_row = nz(row.get("source_row"))
        uid = f"MHF-{label}-row{source_row or len(records)+1}"
        raw = {}
        for k in ("source_row", "province", "district", "facility_name", "facility_code",
                  "latitude", "longitude", "facility_type_raw", "ownership_raw", "location_raw",
                  "level_raw", "contact_raw", "status_raw", "bed_capacity", "date_opened",
                  "date_closed", "comments"):
            val = nz(row.get(k))
            if val is not None:
                raw[k] = val
        records.append({
            "facility_uid": uid,
            "facility_code": nz(row.get("facility_code")),
            "facility_name": nz(row.get("facility_name")),
            "province": nz(row.get("province")),
            "district": nz(row.get("district")),
            "facility_type": nz(row.get("facility_type_canonical")) or nz(row.get("facility_type_raw")),
            "ownership": nz(row.get("ownership_canonical")) or nz(row.get("ownership_raw")),
            "location_context": nz(row.get("location_canonical")) or nz(row.get("location_raw")),
            "service_level": nz(row.get("level_canonical")) or nz(row.get("level_raw")),
            "status": nz(row.get("status_raw")),
            "bed_capacity": num(row.get("bed_capacity"), int),
            "latitude": num(row.get("latitude"), float),
            "longitude": num(row.get("longitude"), float),
            "contact_phone_e164": nz(row.get("contact_raw")),
            "source_dataset_date": "2024-07-23",
            "raw_values": raw,
        })
print(json.dumps({"dryRun": dry, "syncNdila": False,
                  "reconcileDuplicateCodes": False, "records": records}))
PY
}

# Print a stage/dry-run response summary block from a JSON response ($1).
print_import_summary() {
  python3 -c '
import json, sys
doc = json.loads(sys.argv[1] or "{}")
d = doc.get("data", doc)
q = d.get("qualitySummary") or {}
def g(*keys):
    for k in keys:
        if k in q and q[k] is not None:
            return q[k]
    return 0
total = d.get("recordsTotal", 0)
staged = d.get("recordsCreated", 0) + d.get("recordsUpdated", 0)
failed = d.get("recordsFailed", 0)
print("  Total rows            : %s" % total)
print("  Import-ready/staged   : %s" % staged)
print("  Missing-code rows     : %s" % g("records_missing_facility_code"))
print("  Duplicate-code rows   : %s" % g("records_duplicate_facility_code"))
print("  Duplicate-name rows   : %s" % g("records_duplicate_facility_name"))
print("  Acceptable-missing    : %s" % g("records_acceptable_missing"))
print("  Failed rows           : %s" % failed)
' "$1"
}

# ---- Modes -----------------------------------------------------------------------------------------
mode_dry_run() {
  require_source
  echo "==> DRY-RUN facility master import"
  echo "  Source label : $SOURCE_LABEL"
  echo "  Source file  : $SOURCE"
  local payload resp
  payload="$(build_payload true)"
  resp="$(http_post "${TUSO_BASE}/v1/internal/facilities/import/master-pack/dry-run" "$payload")"
  print_import_summary "$resp"
  local run_id
  run_id="$(data_field "$resp" runId)"
  echo "  Run id (preview): ${run_id:-<none>}"
  echo "  No final TUSO facilities were created; no facility marked operational."
  echo "  Next step: run with --stage-only to register a reviewable run."
}

mode_stage_only() {
  require_source
  echo "==> STAGE-ONLY facility master import (persist rows for review; no facilities created)"
  local payload resp run_id
  payload="$(build_payload true)"
  resp="$(http_post "${TUSO_BASE}/v1/internal/facilities/import/master-pack/dry-run" "$payload")"
  run_id="$(data_field "$resp" runId)"
  echo "  Source label : $SOURCE_LABEL"
  echo "  Source file  : $SOURCE"
  echo "  Import run ID: ${run_id:-<unknown>}"
  print_import_summary "$resp"
  echo "  Admin UI route: ${ADMIN_RUNS_ROUTE}/${run_id}"
  echo "  Next step: review rows in the admin UI, approve safe rows, then --apply-approved --run-id ${run_id}"
}

count_rows() {
  # $1 = query string (e.g. "decisionStatus=APPROVED_FOR_IMPORT"); prints totalElements
  local body
  body="$(http_get "${TUSO_BASE}/v1/internal/facilities/import/runs/${RUN_ID}/rows?${1}&size=1")"
  data_field "$body" totalElements
}

mode_validate() {
  local unresolved_pending unresolved_conflict approved acceptable
  if [[ -n "$RUN_ID" ]]; then
    echo "==> VALIDATE staged run ${RUN_ID}"
    # Confirm the run resolves.
    http_get "${TUSO_BASE}/v1/internal/facilities/import/runs/${RUN_ID}" >/dev/null
    unresolved_pending="$(count_rows "decisionStatus=PENDING_REVIEW")"
    unresolved_conflict="$(count_rows "decisionStatus=RESOLUTION_CONFLICT")"
    approved="$(count_rows "decisionStatus=APPROVED_FOR_IMPORT")"
    acceptable="$(count_rows "hasAcceptableMissingFields=true")"
    local unresolved=$(( ${unresolved_pending:-0} + ${unresolved_conflict:-0} ))
    echo "  Run validation status : $( [[ $unresolved -eq 0 ]] && echo OK || echo REVIEW_REQUIRED )"
    echo "  Critical blockers     : ${unresolved_conflict:-0} unresolved conflicts"
    echo "  Unresolved review rows: ${unresolved}"
    echo "  Approved ready to apply: ${approved:-0}"
    echo "  Acceptable-missing rows: ${acceptable:-0}"
    if [[ "${approved:-0}" -gt 0 ]]; then
      echo "  Recommendation: run --apply-approved --run-id ${RUN_ID} (${approved} row(s) ready)."
    elif [[ $unresolved -gt 0 ]]; then
      echo "  Recommendation: resolve review rows in the admin UI before applying."
      exit 2
    else
      echo "  Recommendation: nothing approved yet; approve rows in the admin UI."
    fi
  else
    require_source
    echo "==> VALIDATE source package"
    local resp
    resp="$(http_post "${TUSO_BASE}/v1/internal/facilities/import/master-pack/dry-run" "$(build_payload true)")"
    echo "  Source validation status: OK (parsed + evaluated)"
    print_import_summary "$resp"
    echo "  Recommendation: run --stage-only to register a reviewable run, then review + approve."
  fi
}

mode_apply_approved() {
  require_run_id
  echo "==> APPLY-APPROVED for run ${RUN_ID}"
  local approved_before resp applied skipped remaining
  approved_before="$(count_rows "decisionStatus=APPROVED_FOR_IMPORT")"
  resp="$(http_post "${TUSO_BASE}/v1/internal/facilities/import/runs/${RUN_ID}/apply-approved" '{"rowIds":null}')"
  applied="$(data_field "$resp" applied)"
  skipped="$(data_field "$resp" skipped)"
  remaining="$(count_rows "decisionStatus=PENDING_REVIEW")"
  echo "  Run ID                 : ${RUN_ID}"
  echo "  Approved rows (pre)     : ${approved_before:-0}"
  echo "  Applied rows            : ${applied:-0}"
  echo "  Skipped already-imported: ${skipped:-0}"
  echo "  Failed rows             : 0 (failures abort with a non-2xx above)"
  echo "  Remaining review rows   : ${remaining:-0}"
  echo "  Facility registry route : /registry/facilities"
  echo "  Admin import run route  : ${ADMIN_RUNS_ROUTE}/${RUN_ID}"
  echo "  Note: imported facilities remain IMPORTED_PENDING_CONFIGURATION (not operational)."
  echo "  Next step: configure imported facilities (service points, workspaces, PIC, etc.)."
}

mode_reconcile() {
  require_run_id
  echo "==> RECONCILE run ${RUN_ID} (honest check; no fake repair)"
  local report
  report="$(http_get "${TUSO_BASE}/v1/internal/facilities/import/runs/${RUN_ID}/rows?decisionStatus=IMPORTED&size=200")"
  # For each imported row, check the result facility still resolves + provenance/lifecycle.
  local rows_checked resolved missing_prov lifecycle_mismatch
  rows_checked=0; resolved=0; missing_prov=0; lifecycle_mismatch=0
  while IFS=$'\t' read -r fid; do
    [[ -z "$fid" || "$fid" == "None" ]] && continue
    rows_checked=$((rows_checked+1))
    local prov lifecycle imported_from
    if prov="$("$CURL" -sS -f "${trust_headers[@]}" "${TUSO_BASE}/v1/internal/facilities/${fid}/import-provenance" 2>/dev/null)"; then
      resolved=$((resolved+1))
      imported_from="$(data_field "$prov" identity.importedFromMaster)"
      lifecycle="$(data_field "$prov" identity.lifecycleState)"
      [[ "$imported_from" == "True" || "$imported_from" == "true" ]] || missing_prov=$((missing_prov+1))
      if [[ "$lifecycle" != "IMPORTED_PENDING_CONFIGURATION" && "$lifecycle" != "REGISTERED_ACTIVE" ]]; then
        lifecycle_mismatch=$((lifecycle_mismatch+1))
      fi
    fi
  done < <(echo "$report" | python3 -c 'import json,sys
d=json.load(sys.stdin); d=d.get("data",d)
for r in d.get("content",[]):
    print(r.get("resultFacilityId"))')
  echo "  Run / source label            : ${RUN_ID} / ${SOURCE_LABEL}"
  echo "  Imported rows checked          : ${rows_checked}"
  echo "  Facilities resolved            : ${resolved}"
  echo "  Missing provenance             : ${missing_prov}"
  echo "  Missing-field checklist        : surfaced per facility at /facility/<id> (provenance read-model)"
  echo "  Lifecycle-state mismatches     : ${lifecycle_mismatch}"
  local unresolved
  unresolved=$(( rows_checked - resolved ))
  if [[ $unresolved -gt 0 || $missing_prov -gt 0 || $lifecycle_mismatch -gt 0 ]]; then
    echo "  Recommended follow-up: investigate mismatches manually (no automatic repair performed)."
  else
    echo "  Recommended follow-up: none — imported state reconciles with TUSO facilities."
  fi
}

case "$MODE" in
  dry-run)        mode_dry_run ;;
  stage-only)     mode_stage_only ;;
  validate)       mode_validate ;;
  apply-approved) mode_apply_approved ;;
  reconcile)      mode_reconcile ;;
esac

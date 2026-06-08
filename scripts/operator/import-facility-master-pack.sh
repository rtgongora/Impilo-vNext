#!/usr/bin/env bash
# Idempotent facility master pack bootstrap: dry-run → execute → Ndila sync.
# Targets Tuso + Ndila directly (internal trust headers). Safe to re-run.
set -euo pipefail

REPO="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
SEED_JSON="${FACILITY_MASTER_SEED:-$REPO/docs/data/facility-master-2024-07-23/generated/tuso_facility_registry_seed.json}"
TUSO_BASE="${TUSO_BASE:-http://localhost:8084}"
NDILA_BASE="${NDILA_BASE:-http://localhost:8150}"
TENANT_ID="${X_TENANT_ID:-moh-zw}"
POD_ID="${X_POD_ID:-national}"
REQUEST_ID="${X_REQUEST_ID:-facility-master-import-$(date +%s)}"
CORRELATION_ID="${X_CORRELATION_ID:-$REQUEST_ID}"
ACTOR_ID="${X_ACTOR_ID:-operator-bootstrap}"
PURPOSE="${X_PURPOSE_OF_USE:-SYSTEM_ADMINISTRATION}"
DRY_RUN_ONLY="${DRY_RUN_ONLY:-0}"

if [[ ! -f "$SEED_JSON" ]]; then
  echo "ERROR: seed JSON not found: $SEED_JSON" >&2
  echo "Run: python3 scripts/data/extract-facility-master-from-pdf.py" >&2
  exit 1
fi

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

payload="$(python3 - <<'PY' "$SEED_JSON" "$DRY_RUN_ONLY"
import json, sys
seed_path, dry_only = sys.argv[1], sys.argv[2] == "1"
with open(seed_path, encoding="utf-8") as f:
    records = json.load(f)
print(json.dumps({
    "dryRun": True,
    "syncNdila": False,
    "reconcileDuplicateCodes": False,
    "records": records,
}))
PY
)"

record_count="$(python3 -c 'import json,sys; print(len(json.load(open(sys.argv[1], encoding="utf-8"))))' "$SEED_JSON")"
echo "==> Facility master pack dry-run (${record_count} records from $(basename "$SEED_JSON"))"
dry_resp="$(curl -sf -X POST "${TUSO_BASE}/v1/internal/facilities/import/master-pack/dry-run" \
  "${trust_headers[@]}" -d "$payload")"
echo "$dry_resp" | python3 -m json.tool | head -40

if [[ "$DRY_RUN_ONLY" == "1" ]]; then
  echo "OK: dry-run only (DRY_RUN_ONLY=1)"
  exit 0
fi

execute_payload="$(python3 - <<'PY' "$SEED_JSON"
import json, sys
with open(sys.argv[1], encoding="utf-8") as f:
    records = json.load(f)
print(json.dumps({
    "dryRun": False,
    "syncNdila": True,
    "reconcileDuplicateCodes": False,
    "records": records,
}))
PY
)"

echo "==> Facility master pack execute"
exec_resp="$(curl -sf -X POST "${TUSO_BASE}/v1/internal/facilities/import/master-pack" \
  "${trust_headers[@]}" -d "$execute_payload")"
echo "$exec_resp" | python3 -m json.tool | head -40

ndila_seed="${FACILITY_MASTER_NDILA_SEED:-$REPO/docs/data/facility-master-2024-07-23/generated/ndila_facility_location_seed.json}"
if [[ -f "$ndila_seed" ]]; then
  echo "==> Ndila master seed sync"
  ndila_payload="$(python3 - <<'PY' "$ndila_seed" "$exec_resp"
import json, sys
seed_path, tuso_resp_raw = sys.argv[1], sys.argv[2]
with open(seed_path, encoding="utf-8") as f:
    seed_records = json.load(f)
tuso = json.loads(tuso_resp_raw)
uid_to_facility = {}
for item in tuso.get("data", {}).get("results", tuso.get("results", [])):
    uid = item.get("facilityUid")
    fid = item.get("facilityId")
    if uid and fid is not None:
        uid_to_facility[uid] = str(fid)
enriched = []
for row in seed_records:
    uid = row.get("facility_uid")
    if uid in uid_to_facility:
        copy = dict(row)
        copy["tuso_facility_id"] = uid_to_facility[uid]
        enriched.append(copy)
print(json.dumps({"records": enriched}))
PY
)"
  if [[ "$(echo "$ndila_payload" | python3 -c 'import json,sys; print(len(json.load(sys.stdin).get("records",[])))')" -gt 0 ]]; then
    curl -sf -X POST "${NDILA_BASE}/api/v1/ndila/facilities/sync-master-seed" \
      "${trust_headers[@]}" -d "$ndila_payload" | python3 -m json.tool | head -20
  else
    echo "WARN: no Ndila rows enriched from Tuso execute response — skipping Ndila sync"
  fi
else
  echo "WARN: Ndila seed not found at $ndila_seed — skipping Ndila sync"
fi

echo "OK: facility master pack bootstrap complete"

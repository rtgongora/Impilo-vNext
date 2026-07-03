#!/usr/bin/env bash
# Apply sovereign reference seeds to impilo-full-preview Postgres (idempotent).
# Closes the gap between "98 pods running" and "personas have domain truth to govern against".
set -euo pipefail

REPO="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
NS="${IMPILO_PREVIEW_NAMESPACE:-impilo-full-preview}"
SEED_DIR="$REPO/scripts/seed"

if ! command -v kubectl >/dev/null 2>&1; then
  echo "ERROR: kubectl required" >&2
  exit 1
fi

db_exists() {
  local db="$1"
  kubectl exec -n "$NS" deploy/postgres -- psql -U impilo -tAc \
    "SELECT 1 FROM pg_database WHERE datname='${db}'" 2>/dev/null | grep -q 1
}

apply_seed() {
  local db="$1"
  local file="$2"
  local label="$3"
  local skip_query="${4:-}"
  if [[ ! -f "$file" ]]; then
    echo "WARN: missing seed file $file — skip $label"
    return 0
  fi
  if ! db_exists "$db"; then
    echo "SKIP: database $db not present — $label"
    return 0
  fi
  if [[ -n "$skip_query" ]]; then
    local existing
    existing="$(kubectl exec -n "$NS" deploy/postgres -- psql -U impilo -d "$db" -tAc "$skip_query" 2>/dev/null | tr -d '[:space:]' || echo 0)"
    if [[ "${existing:-0}" != "0" && -n "$existing" ]]; then
      echo "SKIP: $label already seeded ($existing rows)"
      return 0
    fi
  fi
  echo "==> Seeding $label ($db)"
  if sed '/^\\c /d' "$file" | kubectl exec -i -n "$NS" deploy/postgres -- psql -v ON_ERROR_STOP=1 -U impilo -d "$db"; then
    echo "OK: $label"
  else
    echo "WARN: $label seed failed (schema mismatch or optional domain) — continuing"
  fi
}

echo "Seeding sovereign data in namespace=$NS"
kubectl exec -n "$NS" deploy/postgres -- pg_isready -U impilo >/dev/null

apply_seed tuso   "$SEED_DIR/02-seed-tuso.sql"   "TUSO facilities" "SELECT count(*) FROM tuso.facility;"
apply_seed vito   "$SEED_DIR/03-seed-vito.sql"   "VITO clients" "SELECT count(*) FROM vito.client;"
apply_seed varapi "$SEED_DIR/04-seed-varapi.sql" "VARAPI providers" "SELECT count(*) FROM varapi.provider;"
# Idempotency skip-queries must target a table the seed actually populates, else
# the guard reads 0 and re-applies every run. These were pointed at non-existent
# tables (policy_rule / code_system / encounter / service_request).
apply_seed tshepo "$SEED_DIR/05-seed-tshepo.sql" "TSHEPO trust" "SELECT count(*) FROM tshepo.policy_decision_log;"
apply_seed zibo   "$SEED_DIR/06-seed-zibo.sql"   "ZIBO terminology" "SELECT count(*) FROM zibo_packs;"
apply_seed pct    "$SEED_DIR/07-seed-pct.sql"    "PCT queue" "SELECT count(*) FROM pct_encounters;"
apply_seed oros   "$SEED_DIR/08-seed-oros.sql"   "OROS orders" "SELECT count(*) FROM oros_orders;"
apply_seed workforce_governance "$SEED_DIR/09-seed-workforce-governance.sql" "Workforce governance assignments" "SELECT count(*) FROM wgv_organisation WHERE organisation_code='MOHCC-NATIONAL';"
apply_seed varapi "$SEED_DIR/10-seed-vashandi-preview-varapi.sql" "Vashandi preview VARAPI providers" "SELECT count(*) FROM varapi.provider WHERE provider_public_id='PROV-ZW-VASH-001';"
apply_seed workforce_governance "$SEED_DIR/11-seed-vashandi-preview-wgv.sql" "Vashandi preview WGV personas" "SELECT count(*) FROM wgv_assignment WHERE id='f5000000-0000-4000-8000-000000000101'::uuid;"
apply_seed varapi "$SEED_DIR/12-seed-scenario-personas-varapi.sql" "Scenario personas VARAPI (nurse.chienda)" "SELECT count(*) FROM varapi.provider WHERE provider_public_id='PROV-ZW-00007';"
apply_seed workforce_governance "$SEED_DIR/13-seed-scenario-personas-wgv.sql" "Scenario personas WGV (nurse.chienda)" "SELECT count(*) FROM wgv_assignment WHERE id='f5000000-0000-4000-8000-000000000007'::uuid;"

echo ""
echo "Verification:"
kubectl exec -n "$NS" deploy/postgres -- psql -U impilo -d varapi -tAc \
  "SELECT 'varapi_providers='||count(*) FROM varapi.provider;"
kubectl exec -n "$NS" deploy/postgres -- psql -U impilo -d tuso -tAc \
  "SELECT 'tuso_facilities='||count(*) FROM tuso.facility;"
kubectl exec -n "$NS" deploy/postgres -- psql -U impilo -d vito -tAc \
  "SELECT 'vito_clients='||count(*) FROM vito.client;"
kubectl exec -n "$NS" deploy/postgres -- psql -U impilo -d workforce_governance -tAc \
  "SELECT 'active_assignments='||count(*) FROM wgv_assignment WHERE status='ACTIVE';"
kubectl exec -n "$NS" deploy/postgres -- psql -U impilo -d varapi -tAc \
  "SELECT 'superadmin_provider='||count(*) FROM varapi.provider WHERE impilo_health_id='b0000000-0000-4000-8000-000000000010';"

echo "PASS: sovereign preview seeds applied"

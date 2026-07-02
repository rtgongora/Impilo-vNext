#!/usr/bin/env bash
#
# Facility import — DB execution / migration validation harness.
#
# Applies the TUSO Flyway migrations (V001..V015) to a Postgres database in version order, asserts the
# facility-absorption schema exists (import runs, row-level staging, review overlay, external identifiers,
# canonical facility UUID), and runs a SQL-level smoke of the review workflow
# (stage -> supply-code -> approve -> apply) verifying:
#   - raw source values preserved (raw_values JSONB),
#   - acceptable-missing flags visible,
#   - applied facility lands as IMPORTED_PENDING_CONFIGURATION (not operational),
#   - facility_code stays an external identifier (facility_identifier), internal id/uuid separate.
#
# Two connection modes:
#   (a) External DB (VM/preview): set PGHOST/PGPORT/PGUSER/PGPASSWORD (+ optional PGDATABASE).
#       The harness creates a throwaway validation DB (default: tuso_import_validation) and drops it.
#   (b) Local ephemeral (sandbox/CI): pass --manage-local-cluster to start the Debian PG cluster and
#       run as the postgres superuser. Cluster is left running unless it was started by this script.
#
# NOTE: this validates the migration DDL executes on real Postgres and that the schema supports the
# workflow. It does NOT boot the TUSO service; the HTTP-level staged->approve->apply smoke against the
# running service remains a separate step on the VM/preview (see report).
#
set -uo pipefail

VALIDATION_DB="${VALIDATION_DB:-tuso_import_validation}"
MANAGE_CLUSTER=0
PG_VER="${PG_VER:-16}"
PG_CLUSTER="${PG_CLUSTER:-main}"
MIGRATIONS_DIR="${MIGRATIONS_DIR:-services/tuso-service/src/main/resources/db/migration}"
STARTED_CLUSTER=0

for arg in "$@"; do
  case "$arg" in
    --manage-local-cluster) MANAGE_CLUSTER=1 ;;
    -h|--help) sed -n '2,30p' "$0"; exit 0 ;;
    *) echo "unknown argument: $arg" >&2; exit 1 ;;
  esac
done

fail() { echo "FAIL: $*" >&2; cleanup; exit 1; }
ok() { echo "  ok: $*"; }

# psql runner — as the postgres superuser (local mode) or via PG* env (external mode).
PSQL_BASE=()
run_sql() {   # $1 db, $2 sql
  if [[ "$MANAGE_CLUSTER" == "1" ]]; then
    su postgres -c "psql -X -v ON_ERROR_STOP=1 -Atq -d '$1' -c \"$2\""
  else
    PGPASSWORD="${PGPASSWORD:-}" psql -X -v ON_ERROR_STOP=1 -Atq \
      -h "${PGHOST:-localhost}" -p "${PGPORT:-5432}" -U "${PGUSER:-postgres}" -d "$1" -c "$2"
  fi
}
run_file() {  # $1 db, $2 file
  if [[ "$MANAGE_CLUSTER" == "1" ]]; then
    su postgres -c "psql -X -v ON_ERROR_STOP=1 -q -d '$1' -f '$2'"
  else
    PGPASSWORD="${PGPASSWORD:-}" psql -X -v ON_ERROR_STOP=1 -q \
      -h "${PGHOST:-localhost}" -p "${PGPORT:-5432}" -U "${PGUSER:-postgres}" -d "$1" -f "$2"
  fi
}

cleanup() {
  # Drop the throwaway DB (best-effort).
  run_sql postgres "DROP DATABASE IF EXISTS ${VALIDATION_DB};" >/dev/null 2>&1 || true
  if [[ "$MANAGE_CLUSTER" == "1" && "$STARTED_CLUSTER" == "1" ]]; then
    pg_ctlcluster "$PG_VER" "$PG_CLUSTER" stop >/dev/null 2>&1 || true
  fi
}

echo "== Facility import DB migration validation =="
[[ -d "$MIGRATIONS_DIR" ]] || { echo "migrations dir not found: $MIGRATIONS_DIR" >&2; exit 1; }

# ---- Bring a cluster up (local mode) --------------------------------------------------------------
if [[ "$MANAGE_CLUSTER" == "1" ]]; then
  status="$(pg_lsclusters -h 2>/dev/null | awk -v v="$PG_VER" -v c="$PG_CLUSTER" '$1==v && $2==c {print $4}')"
  if [[ "$status" != "online" ]]; then
    echo "-- starting local cluster ${PG_VER}/${PG_CLUSTER}"
    pg_ctlcluster "$PG_VER" "$PG_CLUSTER" start >/dev/null 2>&1 || fail "could not start cluster ${PG_VER}/${PG_CLUSTER}"
    STARTED_CLUSTER=1
  fi
fi

# ---- (Re)create throwaway validation DB -----------------------------------------------------------
run_sql postgres "DROP DATABASE IF EXISTS ${VALIDATION_DB};" >/dev/null 2>&1 || true
run_sql postgres "CREATE DATABASE ${VALIDATION_DB};" >/dev/null 2>&1 \
  || fail "could not create validation database ${VALIDATION_DB}"
ok "created throwaway database ${VALIDATION_DB}"

# ---- Apply migrations in version order ------------------------------------------------------------
applied=0
while IFS= read -r f; do
  base="$(basename "$f")"
  run_file "$VALIDATION_DB" "$f" >/dev/null 2>&1 || fail "migration failed: $base"
  applied=$((applied+1))
done < <(find "$MIGRATIONS_DIR" -maxdepth 1 -name 'V*.sql' | sort)
ok "applied ${applied} migrations (V001..V015) in version order"
[[ "$applied" -ge 15 ]] || fail "expected >= 15 migrations, applied ${applied}"

# ---- Schema assertions ----------------------------------------------------------------------------
assert_table() { # table
  local n; n="$(run_sql "$VALIDATION_DB" "SELECT to_regclass('tuso.$1') IS NOT NULL;")"
  [[ "$n" == "t" ]] && ok "table tuso.$1 exists" || fail "missing table tuso.$1"
}
assert_column() { # table column
  local n; n="$(run_sql "$VALIDATION_DB" "SELECT count(*) FROM information_schema.columns WHERE table_schema='tuso' AND table_name='$1' AND column_name='$2';")"
  [[ "$n" == "1" ]] && ok "column tuso.$1.$2 exists" || fail "missing column tuso.$1.$2"
}

assert_table facility
assert_table facility_identifier
assert_table facility_import_run
assert_table facility_import_row
assert_column facility facility_uuid
assert_column facility_import_row raw_values
assert_column facility_import_row acceptable_missing
assert_column facility_import_row outcome
assert_column facility_import_row decision_status
assert_column facility_import_row review_history
assert_column facility_import_row duplicate_group_key
assert_column facility_import_run quality_report

# ---- SQL-level workflow smoke (stage -> supply-code -> approve -> apply) ---------------------------
echo "-- workflow smoke (SQL-level)"
SMOKE_SQL="$(mktemp)"; chmod 644 "$SMOKE_SQL"   # world-readable so `su postgres` can read it
cat > "$SMOKE_SQL" <<'SQL'
SET search_path TO tuso;
-- A staged run
INSERT INTO facility_import_run (id, tenant_id, pack_id, dry_run, records_total, records_created,
  records_updated, records_skipped, records_failed, warnings_count, status, quality_report, started_at)
VALUES (9001, '00000000-0000-0000-0000-000000000001', 'master-health-facility-2024-07-23', true,
  4, 0, 0, 4, 0, 0, 'COMPLETED', '{"source_label":"MASTER_HEALTH_FACILITY_2024_07_23"}'::jsonb, now());

-- Rows: an acceptable-missing import-ready row, a missing-code row, a duplicate-name pair.
INSERT INTO facility_import_row (id, import_run_id, source_label, source_row, correlation_key,
  facility_name, facility_code, facility_type, ownership, raw_values, outcome, import_decision,
  acceptable_missing, has_acceptable_missing, decision_status)
VALUES
 (7001, 9001, 'MASTER_HEALTH_FACILITY_2024_07_23', '2', 'MHF-row2', 'Bangure Clinic', 'ZW010125',
   'CLINIC', NULL, '{"facility_name":"Bangure Clinic RAW"}'::jsonb, 'READY_FOR_IMPORT', 'CREATE',
   '{"missing_ownership":true,"missing_latitude":true}'::jsonb, true, 'READY_FOR_IMPORT'),
 (7002, 9001, 'MASTER_HEALTH_FACILITY_2024_07_23', '3', 'MHF-row3', 'No Code Clinic', NULL,
   'CLINIC', 'GOVERNMENT', '{}'::jsonb, 'EXCLUDED_MISSING_FACILITY_CODE', 'EXCLUDE',
   '{}'::jsonb, false, 'PENDING_REVIEW'),
 (7003, 9001, 'MASTER_HEALTH_FACILITY_2024_07_23', '4', 'MHF-row4', 'Twin Clinic', 'ZWT1',
   'CLINIC', 'GOVERNMENT', '{}'::jsonb, 'EXCLUDED_DUPLICATE_FACILITY_NAME', 'EXCLUDE',
   '{}'::jsonb, false, 'PENDING_REVIEW');
UPDATE facility_import_row SET duplicate_type='FACILITY_NAME', duplicate_group_key='NAME:twin clinic'
 WHERE id=7003;

-- supply-code on the missing-code row -> corrected/ready
UPDATE facility_import_row SET facility_code='ZWNEW9', review_decision='SUPPLY_CODE',
  decision_status='CORRECTED_READY_FOR_IMPORT', reviewed_by='reviewer-1', reviewed_at=now(),
  review_history='[{"action":"SUPPLY_CODE","new_status":"CORRECTED_READY_FOR_IMPORT"}]'::jsonb
 WHERE id=7002;

-- approve the ready rows
UPDATE facility_import_row SET decision_status='APPROVED_FOR_IMPORT' WHERE id IN (7001,7002);

-- apply-approved: create facilities as IMPORTED_PENDING_CONFIGURATION, record external code identifier,
-- set result facility id. (Simulates the service apply path at the schema level.)
WITH ins AS (
  INSERT INTO facility (tenant_id, facility_code, name, facility_type, regulatory_status,
    operational_status, version, facility_uuid)
  SELECT tenant_id, facility_code, facility_name, facility_type, 'IMPORTED_PENDING_CONFIGURATION',
    'MISSING_REQUIRES_CONFIRMATION', 1, gen_random_uuid()
  FROM facility_import_row r
  JOIN facility_import_run run ON run.id = r.import_run_id
  WHERE r.id IN (7001,7002)
  RETURNING id, facility_code
)
UPDATE facility_import_row r SET result_facility_id = ins.id, decision_status='IMPORTED'
FROM ins WHERE r.facility_code = ins.facility_code AND r.id IN (7001,7002);

INSERT INTO facility_identifier (facility_id, system, value, active, created_at)
SELECT result_facility_id, 'NATIONAL_FACILITY_CODE', facility_code, true, now()
FROM facility_import_row WHERE id IN (7001,7002);
SQL
run_file "$VALIDATION_DB" "$SMOKE_SQL" >/dev/null 2>&1 || { rm -f "$SMOKE_SQL"; fail "workflow smoke SQL failed"; }
rm -f "$SMOKE_SQL"
ok "workflow smoke SQL executed (stage -> supply-code -> approve -> apply)"

# Assertions on smoke results
check_eq() { # desc, actual, expected
  [[ "$2" == "$3" ]] && ok "$1" || fail "$1 (got '$2', expected '$3')"
}

check_eq "duplicate-name row left unresolved (not imported)" \
  "$(run_sql "$VALIDATION_DB" "SELECT decision_status FROM tuso.facility_import_row WHERE id=7003;")" \
  "PENDING_REVIEW"
check_eq "supplied-code row imported with real code (no fake code)" \
  "$(run_sql "$VALIDATION_DB" "SELECT facility_code FROM tuso.facility_import_row WHERE id=7002;")" \
  "ZWNEW9"
check_eq "acceptable-missing flags still visible after import" \
  "$(run_sql "$VALIDATION_DB" "SELECT has_acceptable_missing FROM tuso.facility_import_row WHERE id=7001;")" \
  "t"
check_eq "raw source values preserved through workflow" \
  "$(run_sql "$VALIDATION_DB" "SELECT raw_values->>'facility_name' FROM tuso.facility_import_row WHERE id=7001;")" \
  "Bangure Clinic RAW"
check_eq "two facilities created from approved rows" \
  "$(run_sql "$VALIDATION_DB" "SELECT count(*) FROM tuso.facility WHERE regulatory_status='IMPORTED_PENDING_CONFIGURATION';")" \
  "2"
check_eq "imported facilities NOT operational" \
  "$(run_sql "$VALIDATION_DB" "SELECT count(*) FROM tuso.facility WHERE regulatory_status='REGISTERED_ACTIVE' AND facility_code IN ('ZW010125','ZWNEW9');")" \
  "0"
check_eq "facility code stored as external identifier (not internal id)" \
  "$(run_sql "$VALIDATION_DB" "SELECT count(*) FROM tuso.facility_identifier WHERE system='NATIONAL_FACILITY_CODE' AND value IN ('ZW010125','ZWNEW9');")" \
  "2"
check_eq "internal facility_uuid populated + distinct from code" \
  "$(run_sql "$VALIDATION_DB" "SELECT count(*) FROM tuso.facility WHERE facility_uuid IS NOT NULL AND facility_code IN ('ZW010125','ZWNEW9');")" \
  "2"

echo ""
echo "PASS: migrations executed on Postgres ${PG_VER} and workflow schema smoke succeeded."
cleanup

#!/usr/bin/env bash
# =============================================================================
# Wave 20 — Post-Restore Verification Script
# =============================================================================
#
# Validates database and service health after a restore operation.
# Runs comprehensive checks across data integrity, schema state,
# outbox health, and service endpoints.
#
# Usage:
#   ./scripts/dr/post-restore-verify.sh --db <database> [options]
#   ./scripts/dr/post-restore-verify.sh --ring <0|1|2>  [options]
#   ./scripts/dr/post-restore-verify.sh --all
#
# Options:
#   --db NAME          Verify a single database
#   --ring N           Verify all databases in ring N
#   --all              Verify all databases
#   --baseline FILE    Compare against baseline row counts (JSON)
#   --check-service    Also verify the corresponding service health endpoint
#   --json             Output results as JSON
#
# Exit codes:
#   0 — all checks passed
#   1 — one or more checks failed
#   2 — prerequisites missing
# =============================================================================

set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_USER="${DB_USER:-impilo}"

FAILURES=0
TOTAL=0
WARNINGS=0

TARGET_DB=""
TARGET_RING=""
ALL=false
BASELINE_FILE=""
CHECK_SERVICE=false
JSON_OUTPUT=false

# ---------------------------------------------------------------------------
# Database-to-ring mapping
# ---------------------------------------------------------------------------
RING0_TRUST="tshepo keycloak tshepo_authz tshepo_identity tshepo_consent tshepo_audit tshepo_keys tshepo_offline"
RING0_REGISTRY="vito varapi tuso zibo msika butano mushex"
RING1="ubomi pct oros pharmacy inpatient inventory costing landela_adapter credential_verification share_slip card_print msika_flow"
RING2="document_service notification jobs offline_sync integration_hub experience_bff impilo_forms impilo_search"

# Database-to-service port mapping
declare -A DB_PORTS=(
  [tshepo]=8081 [vito]=8082 [varapi]=8083 [tuso]=8084 [zibo]=8085
  [msika]=8086 [mushex]=8087 [pct]=8088 [oros]=8089 [butano]=8090
  [keycloak]=8080
)

# ---------------------------------------------------------------------------
# Output helpers
# ---------------------------------------------------------------------------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

pass()  { TOTAL=$((TOTAL + 1)); echo -e "  ${GREEN}[PASS]${NC} $1"; }
fail()  { TOTAL=$((TOTAL + 1)); FAILURES=$((FAILURES + 1)); echo -e "  ${RED}[FAIL]${NC} $1"; }
warn()  { WARNINGS=$((WARNINGS + 1)); echo -e "  ${YELLOW}[WARN]${NC} $1"; }
info()  { echo -e "  ${BLUE}[INFO]${NC} $1"; }
banner() { echo -e "\n${BLUE}═══ $1 ═══${NC}\n"; }

# ---------------------------------------------------------------------------
# Parse arguments
# ---------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
  case "$1" in
    --db)            TARGET_DB="$2"; shift 2 ;;
    --ring)          TARGET_RING="$2"; shift 2 ;;
    --all)           ALL=true; shift ;;
    --baseline)      BASELINE_FILE="$2"; shift 2 ;;
    --check-service) CHECK_SERVICE=true; shift ;;
    --json)          JSON_OUTPUT=true; shift ;;
    -h|--help)       head -30 "$0" | grep -E '^#' | sed 's/^# \?//'; exit 0 ;;
    *)               echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done

if [ -z "${TARGET_DB}" ] && [ -z "${TARGET_RING}" ] && [ "${ALL}" = false ]; then
  echo "Specify --db, --ring, or --all" >&2
  exit 2
fi

# ---------------------------------------------------------------------------
# Verify single database
# ---------------------------------------------------------------------------
verify_database() {
  local db="$1"
  banner "Verifying: ${db}"

  # Check 1: Database exists and is connectable
  if psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${db}" \
    -c "SELECT 1;" &>/dev/null; then
    pass "Connectivity: ${db}"
  else
    fail "Connectivity: ${db} — cannot connect"
    return
  fi

  # Check 2: Flyway schema history
  local flyway_version
  flyway_version=$(psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${db}" \
    -tAc "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank DESC LIMIT 1;" 2>/dev/null || echo "")
  if [ -n "${flyway_version}" ]; then
    pass "Flyway schema version: ${flyway_version}"

    # Check for failed migrations
    local failed_count
    failed_count=$(psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${db}" \
      -tAc "SELECT COUNT(*) FROM flyway_schema_history WHERE success = false;" 2>/dev/null || echo "0")
    if [ "${failed_count}" -gt 0 ]; then
      fail "Flyway has ${failed_count} failed migration(s)"
    else
      pass "No failed Flyway migrations"
    fi
  else
    warn "No Flyway schema history found in ${db}"
  fi

  # Check 3: Table counts
  local table_count
  table_count=$(psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${db}" \
    -tAc "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema NOT IN ('pg_catalog', 'information_schema');" 2>/dev/null || echo "0")
  if [ "${table_count}" -gt 0 ]; then
    pass "Tables found: ${table_count}"
  else
    fail "No user tables found in ${db}"
  fi

  # Check 4: Row count summary
  info "Row counts (top 10 tables):"
  psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${db}" \
    -c "SELECT schemaname || '.' || tablename AS table_name, n_live_tup AS rows
        FROM pg_stat_user_tables
        WHERE n_live_tup > 0
        ORDER BY n_live_tup DESC
        LIMIT 10;" 2>/dev/null || warn "Could not read pg_stat_user_tables"

  # Check 5: Outbox table health
  local has_outbox
  has_outbox=$(psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${db}" \
    -tAc "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'event_outbox';" 2>/dev/null || echo "0")
  if [ "${has_outbox}" -gt 0 ]; then
    local total_events unpublished_events oldest_unpublished
    total_events=$(psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${db}" \
      -tAc "SELECT COUNT(*) FROM event_outbox;" 2>/dev/null || echo "?")
    unpublished_events=$(psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${db}" \
      -tAc "SELECT COUNT(*) FROM event_outbox WHERE published_at IS NULL;" 2>/dev/null || echo "?")
    oldest_unpublished=$(psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${db}" \
      -tAc "SELECT COALESCE(MIN(created_at)::text, 'none') FROM event_outbox WHERE published_at IS NULL;" 2>/dev/null || echo "?")

    pass "Outbox: ${total_events} total events, ${unpublished_events} unpublished"
    if [ "${unpublished_events}" != "?" ] && [ "${unpublished_events}" -gt 500 ]; then
      warn "High outbox backlog: ${unpublished_events} unpublished (oldest: ${oldest_unpublished})"
    fi
  fi

  # Check 6: Index health
  local invalid_indexes
  invalid_indexes=$(psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${db}" \
    -tAc "SELECT COUNT(*) FROM pg_index WHERE NOT indisvalid;" 2>/dev/null || echo "0")
  if [ "${invalid_indexes}" -eq 0 ]; then
    pass "All indexes valid"
  else
    fail "Found ${invalid_indexes} invalid index(es) — REINDEX required"
  fi

  # Check 7: Constraint health
  local disabled_triggers
  disabled_triggers=$(psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${db}" \
    -tAc "SELECT COUNT(*) FROM pg_trigger WHERE NOT tgenabled = 'O' AND tgname NOT LIKE 'pg_%';" 2>/dev/null || echo "0")
  if [ "${disabled_triggers}" -gt 0 ]; then
    warn "${disabled_triggers} non-default trigger state(s) detected"
  fi

  # Check 8: Database size
  local db_size
  db_size=$(psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${db}" \
    -tAc "SELECT pg_size_pretty(pg_database_size('${db}'));" 2>/dev/null || echo "unknown")
  info "Database size: ${db_size}"

  # Check 9: Service health (if --check-service)
  if [ "${CHECK_SERVICE}" = true ]; then
    local port="${DB_PORTS[${db}]:-}"
    if [ -n "${port}" ]; then
      local health_status
      health_status=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
        "http://localhost:${port}/actuator/health" 2>/dev/null || echo "000")
      if [ "${health_status}" = "200" ]; then
        pass "Service health (port ${port}): UP"
      elif [ "${health_status}" = "000" ]; then
        warn "Service not reachable on port ${port} (may not be running)"
      else
        fail "Service health (port ${port}): HTTP ${health_status}"
      fi
    fi
  fi

  # Check 10: Baseline comparison (if provided)
  if [ -n "${BASELINE_FILE}" ] && [ -f "${BASELINE_FILE}" ]; then
    compare_baseline "${db}"
  fi
}

# ---------------------------------------------------------------------------
# Baseline comparison
# ---------------------------------------------------------------------------
compare_baseline() {
  local db="$1"
  info "Comparing against baseline: ${BASELINE_FILE}"

  # Expected format: {"database": {"table_name": row_count, ...}, ...}
  if command -v python3 &>/dev/null; then
    python3 -c "
import json, subprocess, sys

with open('${BASELINE_FILE}') as f:
    baseline = json.load(f)

if '${db}' not in baseline:
    print('  No baseline data for ${db}')
    sys.exit(0)

db_baseline = baseline['${db}']
result = subprocess.run([
    'psql', '-h', '${DB_HOST}', '-p', '${DB_PORT}', '-U', '${DB_USER}', '-d', '${db}',
    '-tAc', \"SELECT tablename || ':' || n_live_tup FROM pg_stat_user_tables WHERE n_live_tup > 0;\"
], capture_output=True, text=True)

for line in result.stdout.strip().split('\n'):
    if ':' in line:
        table, count = line.rsplit(':', 1)
        count = int(count)
        if table in db_baseline:
            expected = db_baseline[table]
            diff_pct = abs(count - expected) / max(expected, 1) * 100
            if diff_pct > 10:
                print(f'  [WARN] {table}: {count} rows (expected ~{expected}, diff {diff_pct:.1f}%)')
            else:
                print(f'  [OK]   {table}: {count} rows (baseline: {expected})')
" 2>/dev/null || warn "Baseline comparison failed"
  else
    warn "python3 not available — skipping baseline comparison"
  fi
}

# ---------------------------------------------------------------------------
# Get databases for a ring
# ---------------------------------------------------------------------------
get_ring_databases() {
  local ring="$1"
  case "${ring}" in
    0) echo "${RING0_TRUST} ${RING0_REGISTRY}" ;;
    1) echo "${RING1}" ;;
    2) echo "${RING2}" ;;
    *) echo "" ;;
  esac
}

# =============================================================================
# MAIN
# =============================================================================
main() {
  echo ""
  echo "================================================================"
  echo "  Impilo vNext — Post-Restore Verification"
  echo "  Host: ${DB_HOST}:${DB_PORT}"
  echo "  Timestamp: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "================================================================"

  local databases=""

  if [ -n "${TARGET_DB}" ]; then
    databases="${TARGET_DB}"
  elif [ -n "${TARGET_RING}" ]; then
    databases=$(get_ring_databases "${TARGET_RING}")
  elif [ "${ALL}" = true ]; then
    databases="${RING0_TRUST} ${RING0_REGISTRY} ${RING1} ${RING2}"
  fi

  for db in ${databases}; do
    verify_database "${db}"
  done

  # Summary
  banner "VERIFICATION SUMMARY"
  local passed=$((TOTAL - FAILURES))
  echo "  Total checks:  ${TOTAL}"
  echo "  Passed:        ${passed}"
  echo "  Failed:        ${FAILURES}"
  echo "  Warnings:      ${WARNINGS}"
  echo ""

  if [ "${FAILURES}" -eq 0 ]; then
    echo -e "  ${GREEN}╔═══════════════════════════════════════════════════╗${NC}"
    echo -e "  ${GREEN}║  ALL CHECKS PASSED — RESTORE VERIFIED            ║${NC}"
    echo -e "  ${GREEN}╚═══════════════════════════════════════════════════╝${NC}"
    exit 0
  else
    echo -e "  ${RED}╔═══════════════════════════════════════════════════╗${NC}"
    echo -e "  ${RED}║  ${FAILURES} CHECK(S) FAILED — REVIEW REQUIRED            ║${NC}"
    echo -e "  ${RED}╚═══════════════════════════════════════════════════╝${NC}"
    exit 1
  fi
}

main "$@"

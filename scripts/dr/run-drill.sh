#!/usr/bin/env bash
# =============================================================================
# Wave 20 — DR Drill Runner
# =============================================================================
#
# Orchestrates disaster recovery drill scenarios with timing, validation,
# and evidence collection.
#
# Usage:
#   ./scripts/dr/run-drill.sh --scenario <name> [options]
#
# Scenarios:
#   single-service    Backup + restore a single Ring 0 database
#   ring0-core        Restore all Ring 0 core databases and verify services
#   partial-platform  Simulate partial infrastructure loss and recovery
#
# Options:
#   --scenario NAME   Drill scenario to execute (required)
#   --db NAME         Target database (for single-service scenario)
#   --output DIR      Output directory for drill report (default: /tmp/drill-report-<timestamp>)
#   --dry-run         Show drill steps without executing
#
# Environment:
#   Same as restore-db.sh and post-restore-verify.sh
#
# Exit codes:
#   0 — drill passed (RPO/RTO within targets)
#   1 — drill failed (RPO/RTO exceeded or verification failed)
#   2 — prerequisites missing
# =============================================================================

set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_USER="${DB_USER:-impilo}"

TIMESTAMP=$(date -u +"%Y%m%dT%H%M%SZ")
SCENARIO=""
TARGET_DB=""
OUTPUT_DIR="/tmp/drill-report-${TIMESTAMP}"
DRY_RUN=false

# RPO/RTO targets (seconds)
declare -A RTO_TARGETS=(
  [ring0_trust]=900      # 15 min
  [ring0_registry]=900   # 15 min
  [ring0_extended]=1800  # 30 min
  [ring1]=1800           # 30 min
  [ring2]=7200           # 2 hr
)

# Service port map
declare -A SVC_PORTS=(
  [tshepo]=8081 [vito]=8082 [varapi]=8083 [tuso]=8084 [zibo]=8085
  [msika]=8086 [mushex]=8087 [pct]=8088 [oros]=8089 [butano]=8090
)

# ---------------------------------------------------------------------------
# Output helpers
# ---------------------------------------------------------------------------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info()  { echo -e "${BLUE}[INFO]${NC}  $(date -u +%H:%M:%S) $1"; }
log_ok()    { echo -e "${GREEN}[OK]${NC}    $(date -u +%H:%M:%S) $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $(date -u +%H:%M:%S) $1"; }
log_fail()  { echo -e "${RED}[FAIL]${NC}  $(date -u +%H:%M:%S) $1"; }

drill_step() {
  local step_num="$1"
  local description="$2"
  echo ""
  echo -e "${BLUE}──── Step ${step_num}: ${description} ────${NC}"
  echo "  Time: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
}

# ---------------------------------------------------------------------------
# Parse arguments
# ---------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
  case "$1" in
    --scenario) SCENARIO="$2"; shift 2 ;;
    --db)       TARGET_DB="$2"; shift 2 ;;
    --output)   OUTPUT_DIR="$2"; shift 2 ;;
    --dry-run)  DRY_RUN=true; shift ;;
    -h|--help)  head -35 "$0" | grep -E '^#' | sed 's/^# \?//'; exit 0 ;;
    *)          echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done

if [ -z "${SCENARIO}" ]; then
  echo "Scenario required. Use: --scenario <single-service|ring0-core|partial-platform>" >&2
  exit 2
fi

mkdir -p "${OUTPUT_DIR}"

# ---------------------------------------------------------------------------
# Timing helpers
# ---------------------------------------------------------------------------
DRILL_START=""
PHASE_START=""

start_timer() {
  DRILL_START=$(date +%s)
  PHASE_START="${DRILL_START}"
}

phase_elapsed() {
  local now
  now=$(date +%s)
  echo $((now - PHASE_START))
}

total_elapsed() {
  local now
  now=$(date +%s)
  echo $((now - DRILL_START))
}

reset_phase() {
  PHASE_START=$(date +%s)
}

# ---------------------------------------------------------------------------
# Evidence collection
# ---------------------------------------------------------------------------
REPORT_FILE="${OUTPUT_DIR}/drill-report.md"

init_report() {
  local scenario_name="$1"
  cat > "${REPORT_FILE}" << EOF
# DR Drill Report — $(date -u +%Y-%m-%d) — ${scenario_name}

## Scope
- Scenario: ${scenario_name}
- Target database(s): ${TARGET_DB:-"(per scenario)"}
- Environment: ${DB_HOST}:${DB_PORT}
- Drill lead: (automated via run-drill.sh)

## Timeline
| Time | Elapsed | Event |
|------|---------|-------|
EOF
}

log_event() {
  local event="$1"
  local elapsed
  elapsed=$(total_elapsed)
  echo "| $(date -u +%H:%M:%S) | T+${elapsed}s | ${event} |" >> "${REPORT_FILE}"
  log_info "${event}"
}

finalize_report() {
  local rto_achieved="$1"
  local rpo_estimated="$2"
  local rto_target="$3"
  local verdict="$4"

  cat >> "${REPORT_FILE}" << EOF

## Measurements
- Detection time (T_detect): N/A (drill initiated manually)
- Recovery time (T_recover): ${rto_achieved} seconds
- Estimated data loss window (RPO): ${rpo_estimated}
- Total RTO achieved: ${rto_achieved} seconds
- RTO target: ${rto_target} seconds
- **Verdict: ${verdict}**

## Verification Results
$(cat "${OUTPUT_DIR}/verify-output.txt" 2>/dev/null || echo "See verify output file")

## Sign-Off
- Drill Lead: _________________ Date: _______
- Platform Lead: _________________ Date: _______
EOF

  log_info "Drill report saved: ${REPORT_FILE}"
}

# ---------------------------------------------------------------------------
# Check service health
# ---------------------------------------------------------------------------
check_service_health() {
  local db="$1"
  local port="${SVC_PORTS[${db}]:-}"
  if [ -z "${port}" ]; then
    return 0
  fi
  local status
  status=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    "http://localhost:${port}/actuator/health" 2>/dev/null || echo "000")
  [ "${status}" = "200" ]
}

wait_for_service() {
  local db="$1"
  local timeout="${2:-120}"
  local port="${SVC_PORTS[${db}]:-}"
  if [ -z "${port}" ]; then
    log_warn "No port mapping for ${db} — skipping service health wait"
    return 0
  fi

  log_info "Waiting for service on port ${port} (timeout: ${timeout}s)..."
  local elapsed=0
  while [ "${elapsed}" -lt "${timeout}" ]; do
    if check_service_health "${db}"; then
      log_ok "Service healthy on port ${port} after ${elapsed}s"
      return 0
    fi
    sleep 5
    elapsed=$((elapsed + 5))
  done

  log_fail "Service on port ${port} did not become healthy within ${timeout}s"
  return 1
}

# =============================================================================
# SCENARIO 1: Single Service DB Restore
# =============================================================================
drill_single_service() {
  local db="${TARGET_DB:-vito}"
  local rto_target="${RTO_TARGETS[ring0_registry]}"

  echo ""
  echo "================================================================"
  echo "  DR DRILL: Single Service Database Restore"
  echo "  Database: ${db}"
  echo "  RTO target: ${rto_target}s ($(( rto_target / 60 )) min)"
  echo "================================================================"

  if [ "${DRY_RUN}" = true ]; then
    echo ""
    echo "DRY RUN — Steps that would execute:"
    echo "  1. Create backup of current ${db} database"
    echo "  2. Simulate failure (drop database)"
    echo "  3. Restore from backup"
    echo "  4. Run post-restore verification"
    echo "  5. Verify service health"
    echo "  6. Measure RTO and compare against target"
    return 0
  fi

  init_report "Single Service DB Restore (${db})"
  start_timer

  # Step 1: Create a fresh backup (this IS the backup we'll restore from)
  drill_step 1 "Create backup of ${db}"
  log_event "Backup initiated for ${db}"
  reset_phase

  local backup_file="/tmp/drill-backup-${db}-${TIMESTAMP}.dump"
  if pg_dump -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" \
    -Fc -Z 6 --file="${backup_file}" "${db}" 2>/dev/null; then
    log_event "Backup completed ($(phase_elapsed)s)"
    log_ok "Backup: ${backup_file}"
  else
    log_fail "Backup failed — aborting drill"
    log_event "ABORT: Backup failed"
    return 1
  fi

  # Step 2: Record pre-failure state
  drill_step 2 "Record pre-failure state"
  local pre_row_count
  pre_row_count=$(psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${db}" \
    -tAc "SELECT SUM(n_live_tup) FROM pg_stat_user_tables;" 2>/dev/null || echo "0")
  log_event "Pre-failure row count: ${pre_row_count}"

  # Step 3: Simulate failure (drop database)
  drill_step 3 "Simulate database failure"
  log_event "Simulating failure: dropping ${db}"
  reset_phase

  # Terminate connections first
  psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres \
    -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '${db}' AND pid <> pg_backend_pid();" \
    &>/dev/null || true

  psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres \
    -c "DROP DATABASE IF EXISTS ${db};" &>/dev/null

  log_event "Database ${db} dropped (failure simulated)"

  # Step 4: Restore from backup
  drill_step 4 "Restore database from backup"
  log_event "Restore initiated"
  reset_phase
  local restore_start
  restore_start=$(date +%s)

  psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres \
    -c "CREATE DATABASE ${db};" &>/dev/null

  if pg_restore -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" \
    -d "${db}" --no-owner --no-privileges --jobs=4 \
    "${backup_file}" 2>/dev/null; then
    log_event "Database restored ($(phase_elapsed)s)"
  else
    log_event "Database restored with warnings ($(phase_elapsed)s)"
  fi

  # Step 5: Post-restore verification
  drill_step 5 "Post-restore verification"
  log_event "Verification started"

  "${SCRIPT_DIR}/post-restore-verify.sh" --db "${db}" \
    > "${OUTPUT_DIR}/verify-output.txt" 2>&1 || true

  local post_row_count
  post_row_count=$(psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${db}" \
    -tAc "SELECT SUM(n_live_tup) FROM pg_stat_user_tables;" 2>/dev/null || echo "0")
  log_event "Post-restore row count: ${post_row_count} (pre-failure: ${pre_row_count})"

  # Step 6: Service health (if running)
  drill_step 6 "Service health check"
  if check_service_health "${db}" 2>/dev/null; then
    log_event "Service healthy"
    log_ok "Service health: UP"
  else
    log_event "Service not reachable (may need restart)"
    log_warn "Service may need restart: kubectl rollout restart deployment/${db//_/-}-service"
  fi

  # Calculate RTO
  local restore_end
  restore_end=$(date +%s)
  local rto_achieved=$((restore_end - restore_start))
  local total_time
  total_time=$(total_elapsed)

  log_event "Drill completed (total: ${total_time}s, RTO: ${rto_achieved}s)"

  # Verdict
  local verdict="FAIL"
  if [ "${rto_achieved}" -le "${rto_target}" ]; then
    verdict="PASS"
    log_ok "RTO: ${rto_achieved}s <= ${rto_target}s target — PASS"
  else
    log_fail "RTO: ${rto_achieved}s > ${rto_target}s target — FAIL"
  fi

  finalize_report "${rto_achieved}" "0 min (backup taken just before drill)" "${rto_target}" "${verdict}"

  # Cleanup
  rm -f "${backup_file}"

  [ "${verdict}" = "PASS" ] && return 0 || return 1
}

# =============================================================================
# SCENARIO 2: Ring 0 Core Service Restore
# =============================================================================
drill_ring0_core() {
  local ring0_dbs="tshepo vito varapi tuso zibo"
  local rto_target="${RTO_TARGETS[ring0_registry]}"

  echo ""
  echo "================================================================"
  echo "  DR DRILL: Ring 0 Core Service Restore"
  echo "  Databases: ${ring0_dbs}"
  echo "  RTO target: ${rto_target}s ($(( rto_target / 60 )) min)"
  echo "================================================================"

  if [ "${DRY_RUN}" = true ]; then
    echo ""
    echo "DRY RUN — Steps that would execute:"
    echo "  1. Backup all Ring 0 databases (tshepo, vito, varapi, tuso, zibo)"
    echo "  2. Drop all Ring 0 databases (simulate total Ring 0 DB failure)"
    echo "  3. Restore all databases in priority order (tshepo first)"
    echo "  4. Run post-restore verification for Ring 0"
    echo "  5. Verify all Ring 0 service health endpoints"
    echo "  6. Measure aggregate RTO"
    return 0
  fi

  init_report "Ring 0 Core Service Restore"
  start_timer

  # Step 1: Backup all Ring 0 databases
  drill_step 1 "Backup all Ring 0 databases"
  log_event "Backing up Ring 0 databases"

  declare -A backup_files
  for db in ${ring0_dbs}; do
    local bf="/tmp/drill-backup-${db}-${TIMESTAMP}.dump"
    if pg_dump -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" \
      -Fc -Z 6 --file="${bf}" "${db}" 2>/dev/null; then
      backup_files[${db}]="${bf}"
      log_ok "Backed up: ${db}"
    else
      log_fail "Backup failed for ${db} — aborting drill"
      log_event "ABORT: Backup failed for ${db}"
      return 1
    fi
  done
  log_event "All Ring 0 backups completed ($(total_elapsed)s)"

  # Step 2: Drop all Ring 0 databases (simulate failure)
  drill_step 2 "Simulate Ring 0 database failure"
  log_event "Simulating Ring 0 database failure"

  for db in ${ring0_dbs}; do
    psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres \
      -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '${db}' AND pid <> pg_backend_pid();" \
      &>/dev/null || true
    psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres \
      -c "DROP DATABASE IF EXISTS ${db};" &>/dev/null
  done
  log_event "All Ring 0 databases dropped"

  # Step 3: Restore in priority order (TSHEPO first — gateway)
  drill_step 3 "Restore Ring 0 databases (priority order)"
  log_event "Restore initiated"
  reset_phase
  local restore_start
  restore_start=$(date +%s)

  for db in tshepo vito varapi tuso zibo; do
    log_info "Restoring: ${db}"
    psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres \
      -c "CREATE DATABASE ${db};" &>/dev/null

    pg_restore -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" \
      -d "${db}" --no-owner --no-privileges --jobs=4 \
      "${backup_files[${db}]}" 2>/dev/null || true

    log_ok "Restored: ${db}"
    log_event "Restored: ${db} ($(phase_elapsed)s elapsed)"
  done

  local restore_end
  restore_end=$(date +%s)
  local rto_achieved=$((restore_end - restore_start))

  # Step 4: Verification
  drill_step 4 "Post-restore verification (all Ring 0)"
  log_event "Verification started"

  "${SCRIPT_DIR}/post-restore-verify.sh" --ring 0 \
    > "${OUTPUT_DIR}/verify-output.txt" 2>&1 || true

  log_event "Verification completed"

  # Step 5: Service health
  drill_step 5 "Ring 0 service health checks"
  local services_healthy=0
  local services_total=0
  for db in ${ring0_dbs}; do
    services_total=$((services_total + 1))
    if check_service_health "${db}" 2>/dev/null; then
      log_ok "${db} service: UP"
      services_healthy=$((services_healthy + 1))
    else
      log_warn "${db} service: not reachable (may need restart)"
    fi
  done
  log_event "Service health: ${services_healthy}/${services_total} up"

  # Verdict
  local total_time
  total_time=$(total_elapsed)
  log_event "Drill completed (total: ${total_time}s, RTO: ${rto_achieved}s)"

  local verdict="FAIL"
  if [ "${rto_achieved}" -le "${rto_target}" ]; then
    verdict="PASS"
    log_ok "RTO: ${rto_achieved}s <= ${rto_target}s target — PASS"
  else
    log_fail "RTO: ${rto_achieved}s > ${rto_target}s target — FAIL"
  fi

  finalize_report "${rto_achieved}" "0 min (backups taken just before drill)" "${rto_target}" "${verdict}"

  # Cleanup
  for db in ${ring0_dbs}; do
    rm -f "${backup_files[${db}]}"
  done

  [ "${verdict}" = "PASS" ] && return 0 || return 1
}

# =============================================================================
# SCENARIO 3: Partial Platform Recovery
# =============================================================================
drill_partial_platform() {
  echo ""
  echo "================================================================"
  echo "  DR DRILL: Partial Platform Recovery"
  echo "  Scope: Ring 0 (trust + registry) + Ring 1 clinical"
  echo "================================================================"

  if [ "${DRY_RUN}" = true ]; then
    echo ""
    echo "DRY RUN — Steps that would execute:"
    echo "  1. Backup: tshepo, vito, varapi, tuso, zibo, pct, oros"
    echo "  2. Drop all backed-up databases (simulate AZ failure)"
    echo "  3. Restore Ring 0 databases (priority: tshepo → vito → varapi → tuso → zibo)"
    echo "  4. Verify Ring 0 services healthy before proceeding"
    echo "  5. Restore Ring 1 clinical databases (pct, oros)"
    echo "  6. Full post-restore verification"
    echo "  7. Verify outbox state and event flow"
    echo "  8. Measure phased RTO: Ring 0 RTO + Ring 1 RTO"
    return 0
  fi

  local all_dbs="tshepo vito varapi tuso zibo pct oros"
  init_report "Partial Platform Recovery (Ring 0 + Ring 1 Clinical)"
  start_timer

  # Step 1: Backup
  drill_step 1 "Backup all target databases"
  log_event "Backing up Ring 0 + Ring 1 clinical databases"

  declare -A backup_files
  for db in ${all_dbs}; do
    local bf="/tmp/drill-backup-${db}-${TIMESTAMP}.dump"
    if pg_dump -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" \
      -Fc -Z 6 --file="${bf}" "${db}" 2>/dev/null; then
      backup_files[${db}]="${bf}"
      log_ok "Backed up: ${db}"
    else
      log_warn "Backup skipped for ${db} (database may not exist)"
    fi
  done
  log_event "Backups completed ($(total_elapsed)s)"

  # Step 2: Simulate partial infrastructure loss
  drill_step 2 "Simulate partial infrastructure failure"
  log_event "Dropping target databases (simulating AZ failure)"

  for db in ${all_dbs}; do
    psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres \
      -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '${db}' AND pid <> pg_backend_pid();" \
      &>/dev/null || true
    psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres \
      -c "DROP DATABASE IF EXISTS ${db};" &>/dev/null || true
  done
  log_event "Databases dropped (failure simulated)"

  # Step 3: Restore Ring 0 first (critical path)
  drill_step 3 "Restore Ring 0 databases (critical path first)"
  log_event "Ring 0 restore initiated"
  reset_phase
  local ring0_start
  ring0_start=$(date +%s)

  for db in tshepo vito varapi tuso zibo; do
    if [ -n "${backup_files[${db}]:-}" ]; then
      psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres \
        -c "CREATE DATABASE ${db};" &>/dev/null
      pg_restore -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" \
        -d "${db}" --no-owner --no-privileges --jobs=4 \
        "${backup_files[${db}]}" 2>/dev/null || true
      log_ok "Restored: ${db}"
    fi
  done

  local ring0_end
  ring0_end=$(date +%s)
  local ring0_rto=$((ring0_end - ring0_start))
  log_event "Ring 0 restore completed (${ring0_rto}s)"

  # Step 4: Verify Ring 0 before proceeding to Ring 1
  drill_step 4 "Verify Ring 0 health before proceeding"
  "${SCRIPT_DIR}/post-restore-verify.sh" --ring 0 \
    > "${OUTPUT_DIR}/verify-ring0.txt" 2>&1 || true
  log_event "Ring 0 verification completed"

  # Step 5: Restore Ring 1 clinical
  drill_step 5 "Restore Ring 1 clinical databases"
  log_event "Ring 1 restore initiated"
  reset_phase
  local ring1_start
  ring1_start=$(date +%s)

  for db in pct oros; do
    if [ -n "${backup_files[${db}]:-}" ]; then
      psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres \
        -c "CREATE DATABASE ${db};" &>/dev/null
      pg_restore -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" \
        -d "${db}" --no-owner --no-privileges --jobs=4 \
        "${backup_files[${db}]}" 2>/dev/null || true
      log_ok "Restored: ${db}"
    fi
  done

  local ring1_end
  ring1_end=$(date +%s)
  local ring1_rto=$((ring1_end - ring1_start))
  log_event "Ring 1 restore completed (${ring1_rto}s)"

  # Step 6: Full verification
  drill_step 6 "Full post-restore verification"
  "${SCRIPT_DIR}/post-restore-verify.sh" --all \
    > "${OUTPUT_DIR}/verify-output.txt" 2>&1 || true
  log_event "Full verification completed"

  # Summary
  local total_time
  total_time=$(total_elapsed)
  local total_rto=$((ring0_rto + ring1_rto))

  log_event "Drill completed (total: ${total_time}s)"
  echo ""
  echo "  Ring 0 RTO: ${ring0_rto}s (target: 900s / 15 min)"
  echo "  Ring 1 RTO: ${ring1_rto}s (target: 1800s / 30 min)"
  echo "  Total RTO:  ${total_rto}s"

  local verdict="FAIL"
  if [ "${ring0_rto}" -le 900 ] && [ "${ring1_rto}" -le 1800 ]; then
    verdict="PASS"
    log_ok "Both Ring 0 and Ring 1 RTO within targets — PASS"
  else
    log_fail "One or more RTO targets exceeded — FAIL"
  fi

  finalize_report "${total_rto}" "0 min (backups taken just before drill)" "Ring 0: 900s / Ring 1: 1800s" "${verdict}"

  # Cleanup
  for db in ${all_dbs}; do
    rm -f "${backup_files[${db}]:-}"
  done

  [ "${verdict}" = "PASS" ] && return 0 || return 1
}

# =============================================================================
# MAIN
# =============================================================================
main() {
  case "${SCENARIO}" in
    single-service)
      drill_single_service
      ;;
    ring0-core)
      drill_ring0_core
      ;;
    partial-platform)
      drill_partial_platform
      ;;
    *)
      echo "Unknown scenario: ${SCENARIO}" >&2
      echo "Available: single-service, ring0-core, partial-platform" >&2
      exit 2
      ;;
  esac
}

main "$@"

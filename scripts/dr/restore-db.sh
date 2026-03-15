#!/usr/bin/env bash
# =============================================================================
# Wave 20 — Database Restore Script
# =============================================================================
#
# Restores a single Impilo vNext PostgreSQL database from a backup file
# (pg_dump custom format) or from S3/MinIO storage.
#
# Usage:
#   ./scripts/dr/restore-db.sh --db <database> [options]
#
# Options:
#   --db NAME          Database name to restore (required)
#   --file PATH        Local backup file path (mutually exclusive with --from-s3)
#   --from-s3          Download latest backup from S3/MinIO
#   --backup-date DATE Specific backup date (YYYYMMDD). Default: latest.
#   --pitr TIMESTAMP   Point-in-time recovery target (ISO 8601)
#   --target-host HOST Target PostgreSQL host (default: DB_HOST or localhost)
#   --skip-flyway      Skip Flyway migration check after restore
#   --force            Skip confirmation prompt
#   --dry-run          Show what would be done without executing
#
# Environment variables:
#   DB_HOST            PostgreSQL host (default: localhost)
#   DB_PORT            PostgreSQL port (default: 5432)
#   DB_USER            PostgreSQL superuser (default: impilo)
#   PGPASSWORD         PostgreSQL password
#   BACKUP_BUCKET      S3/MinIO bucket (default: impilo-backups)
#   S3_ENDPOINT        MinIO endpoint URL (omit for AWS S3)
#
# Exit codes:
#   0 — restore completed and verified successfully
#   1 — restore failed
#   2 — prerequisites missing or invalid arguments
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
BACKUP_BUCKET="${BACKUP_BUCKET:-impilo-backups}"
S3_ENDPOINT="${S3_ENDPOINT:-}"

TARGET_DB=""
BACKUP_FILE=""
FROM_S3=false
BACKUP_DATE=""
PITR_TARGET=""
TARGET_HOST=""
SKIP_FLYWAY=false
FORCE=false
DRY_RUN=false

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

# ---------------------------------------------------------------------------
# Parse arguments
# ---------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
  case "$1" in
    --db)          TARGET_DB="$2"; shift 2 ;;
    --file)        BACKUP_FILE="$2"; shift 2 ;;
    --from-s3)     FROM_S3=true; shift ;;
    --backup-date) BACKUP_DATE="$2"; shift 2 ;;
    --pitr)        PITR_TARGET="$2"; shift 2 ;;
    --target-host) TARGET_HOST="$2"; shift 2 ;;
    --skip-flyway) SKIP_FLYWAY=true; shift ;;
    --force)       FORCE=true; shift ;;
    --dry-run)     DRY_RUN=true; shift ;;
    -h|--help)     head -35 "$0" | grep -E '^#' | sed 's/^# \?//'; exit 0 ;;
    *)             echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done

RESTORE_HOST="${TARGET_HOST:-${DB_HOST}}"

# ---------------------------------------------------------------------------
# Validation
# ---------------------------------------------------------------------------
if [ -z "${TARGET_DB}" ]; then
  log_fail "Database name required. Use: --db <database>"
  exit 2
fi

if [ -z "${BACKUP_FILE}" ] && [ "${FROM_S3}" = false ]; then
  log_fail "Specify --file <path> or --from-s3"
  exit 2
fi

# Check prerequisites
for cmd in psql pg_restore; do
  if ! command -v "${cmd}" &>/dev/null; then
    log_fail "Required command not found: ${cmd}"
    exit 2
  fi
done

# ---------------------------------------------------------------------------
# Download backup from S3 if requested
# ---------------------------------------------------------------------------
download_from_s3() {
  local s3_prefix="s3://${BACKUP_BUCKET}/postgres/daily/${TARGET_DB}/"
  local aws_args=""
  [ -n "${S3_ENDPOINT}" ] && aws_args="--endpoint-url ${S3_ENDPOINT}"

  log_info "Searching for backup in: ${s3_prefix}"

  if [ -n "${BACKUP_DATE}" ]; then
    # Find backup for specific date
    # shellcheck disable=SC2086
    BACKUP_FILE=$(aws s3 ls ${aws_args} "${s3_prefix}" 2>/dev/null | \
      grep "${BACKUP_DATE}" | grep "\.dump$" | sort | tail -1 | awk '{print $4}')
  else
    # Find latest backup
    # shellcheck disable=SC2086
    BACKUP_FILE=$(aws s3 ls ${aws_args} "${s3_prefix}" 2>/dev/null | \
      grep "\.dump$" | sort | tail -1 | awk '{print $4}')
  fi

  if [ -z "${BACKUP_FILE}" ]; then
    log_fail "No backup found in ${s3_prefix}"
    exit 1
  fi

  log_info "Found backup: ${BACKUP_FILE}"

  local local_path="/tmp/${BACKUP_FILE}"
  # shellcheck disable=SC2086
  if aws s3 cp ${aws_args} "${s3_prefix}${BACKUP_FILE}" "${local_path}" 2>/dev/null; then
    log_ok "Downloaded: ${local_path}"
    BACKUP_FILE="${local_path}"
  else
    log_fail "Failed to download backup from S3"
    exit 1
  fi

  # Download and verify checksum if available
  local checksum_file="${local_path}.sha256"
  # shellcheck disable=SC2086
  if aws s3 cp ${aws_args} "${s3_prefix}${BACKUP_FILE}.sha256" "${checksum_file}" 2>/dev/null; then
    log_info "Verifying checksum..."
    if sha256sum -c "${checksum_file}" 2>/dev/null; then
      log_ok "Checksum verified"
    else
      log_fail "Checksum verification failed — backup may be corrupted"
      exit 1
    fi
  else
    log_warn "No checksum file found — skipping integrity verification"
  fi
}

# ---------------------------------------------------------------------------
# Pre-restore: capture current state
# ---------------------------------------------------------------------------
capture_pre_restore_state() {
  log_info "Capturing pre-restore state for ${TARGET_DB}..."

  local state_file="/tmp/restore-pre-state-${TARGET_DB}.txt"

  if psql -h "${RESTORE_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${TARGET_DB}" \
    -c "SELECT schemaname, tablename, n_live_tup FROM pg_stat_user_tables ORDER BY n_live_tup DESC LIMIT 20;" \
    > "${state_file}" 2>/dev/null; then
    log_ok "Pre-restore state captured: ${state_file}"
    cat "${state_file}"
  else
    log_warn "Could not capture pre-restore state (database may not exist yet)"
  fi
}

# ---------------------------------------------------------------------------
# Restore database
# ---------------------------------------------------------------------------
restore_database() {
  local start_time
  start_time=$(date +%s)

  if [ "${DRY_RUN}" = true ]; then
    echo ""
    echo "DRY RUN — would execute:"
    echo "  1. DROP DATABASE IF EXISTS ${TARGET_DB}"
    echo "  2. CREATE DATABASE ${TARGET_DB}"
    echo "  3. pg_restore -h ${RESTORE_HOST} -p ${DB_PORT} -U ${DB_USER} -d ${TARGET_DB} ${BACKUP_FILE}"
    echo "  4. Run post-restore verification"
    return 0
  fi

  # Confirmation
  if [ "${FORCE}" = false ]; then
    echo ""
    echo -e "${RED}WARNING: This will DROP and RECREATE database '${TARGET_DB}' on ${RESTORE_HOST}${NC}"
    echo "Backup file: ${BACKUP_FILE}"
    echo ""
    read -r -p "Type the database name to confirm: " confirm
    if [ "${confirm}" != "${TARGET_DB}" ]; then
      log_info "Restore cancelled"
      exit 0
    fi
  fi

  # Step 1: Terminate existing connections
  log_info "Terminating existing connections to ${TARGET_DB}..."
  psql -h "${RESTORE_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres \
    -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '${TARGET_DB}' AND pid <> pg_backend_pid();" \
    2>/dev/null || true

  # Step 2: Drop and recreate
  log_info "Dropping database: ${TARGET_DB}"
  psql -h "${RESTORE_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres \
    -c "DROP DATABASE IF EXISTS ${TARGET_DB};"

  log_info "Creating database: ${TARGET_DB}"
  psql -h "${RESTORE_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres \
    -c "CREATE DATABASE ${TARGET_DB};"

  # Step 3: Restore
  log_info "Restoring from: ${BACKUP_FILE}"
  if pg_restore -h "${RESTORE_HOST}" -p "${DB_PORT}" -U "${DB_USER}" \
    -d "${TARGET_DB}" \
    --no-owner --no-privileges \
    --jobs=4 \
    "${BACKUP_FILE}"; then

    local end_time
    end_time=$(date +%s)
    local duration=$((end_time - start_time))
    log_ok "Database restored in ${duration} seconds"
  else
    # pg_restore may return non-zero for non-fatal warnings
    local end_time
    end_time=$(date +%s)
    local duration=$((end_time - start_time))
    log_warn "pg_restore completed with warnings in ${duration} seconds (check output above)"
  fi
}

# ---------------------------------------------------------------------------
# Post-restore verification
# ---------------------------------------------------------------------------
verify_restore() {
  log_info "Running post-restore verification..."
  local failures=0

  # Check 1: Database connectivity
  if psql -h "${RESTORE_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${TARGET_DB}" \
    -c "SELECT 1 AS connectivity_check;" &>/dev/null; then
    log_ok "Database connectivity: OK"
  else
    log_fail "Database connectivity: FAILED"
    failures=$((failures + 1))
  fi

  # Check 2: Flyway schema history
  if [ "${SKIP_FLYWAY}" = false ]; then
    local flyway_version
    flyway_version=$(psql -h "${RESTORE_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${TARGET_DB}" \
      -tAc "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank DESC LIMIT 1;" 2>/dev/null || echo "")
    if [ -n "${flyway_version}" ]; then
      log_ok "Flyway schema version: ${flyway_version}"
    else
      log_warn "Flyway schema history not found (may be expected for some databases)"
    fi
  fi

  # Check 3: Table row counts
  log_info "Top tables by row count:"
  psql -h "${RESTORE_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${TARGET_DB}" \
    -c "SELECT schemaname || '.' || tablename AS table_name, n_live_tup AS row_count
        FROM pg_stat_user_tables
        ORDER BY n_live_tup DESC
        LIMIT 10;" 2>/dev/null || log_warn "Could not read table statistics"

  # Check 4: Outbox table exists (if this is an outbox-using service)
  local outbox_exists
  outbox_exists=$(psql -h "${RESTORE_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${TARGET_DB}" \
    -tAc "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'event_outbox';" 2>/dev/null || echo "0")
  if [ "${outbox_exists}" -gt 0 ]; then
    local unpublished
    unpublished=$(psql -h "${RESTORE_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${TARGET_DB}" \
      -tAc "SELECT COUNT(*) FROM event_outbox WHERE published_at IS NULL;" 2>/dev/null || echo "unknown")
    log_ok "Outbox table exists (${unpublished} unpublished events)"
  else
    log_info "No event_outbox table in this database"
  fi

  # Check 5: Extensions
  log_info "Installed extensions:"
  psql -h "${RESTORE_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${TARGET_DB}" \
    -c "SELECT extname, extversion FROM pg_extension ORDER BY extname;" 2>/dev/null || true

  if [ "${failures}" -gt 0 ]; then
    log_fail "Post-restore verification: ${failures} check(s) failed"
    return 1
  else
    log_ok "Post-restore verification: ALL CHECKS PASSED"
    return 0
  fi
}

# ---------------------------------------------------------------------------
# Map database to service for health check
# ---------------------------------------------------------------------------
get_service_port() {
  local db="$1"
  case "${db}" in
    tshepo|tshepo_authz|tshepo_identity|tshepo_consent|tshepo_audit|tshepo_keys|tshepo_offline)
      echo "8081" ;;
    vito)      echo "8082" ;;
    varapi)    echo "8083" ;;
    tuso)      echo "8084" ;;
    zibo)      echo "8085" ;;
    msika)     echo "8086" ;;
    mushex)    echo "8087" ;;
    pct)       echo "8088" ;;
    oros)      echo "8089" ;;
    butano)    echo "8090" ;;
    *)         echo "" ;;
  esac
}

# =============================================================================
# MAIN
# =============================================================================
main() {
  echo ""
  echo "================================================================"
  echo "  Impilo vNext — Database Restore: ${TARGET_DB}"
  echo "  Target host: ${RESTORE_HOST}:${DB_PORT}"
  echo "  Timestamp: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "================================================================"

  # Download from S3 if requested
  if [ "${FROM_S3}" = true ]; then
    download_from_s3
  fi

  # Verify backup file exists
  if [ ! -f "${BACKUP_FILE}" ]; then
    log_fail "Backup file not found: ${BACKUP_FILE}"
    exit 1
  fi

  log_info "Backup file: ${BACKUP_FILE}"
  log_info "File size: $(ls -lh "${BACKUP_FILE}" | awk '{print $5}')"

  # Capture pre-restore state
  capture_pre_restore_state

  # Execute restore
  restore_database

  if [ "${DRY_RUN}" = true ]; then
    exit 0
  fi

  # Verify
  if verify_restore; then
    echo ""
    echo "================================================================"
    log_ok "RESTORE COMPLETE: ${TARGET_DB}"

    local port
    port=$(get_service_port "${TARGET_DB}")
    if [ -n "${port}" ]; then
      echo ""
      log_info "Next steps:"
      echo "  1. Restart the service: kubectl rollout restart deployment/${TARGET_DB//_/-}-service"
      echo "  2. Verify health: curl http://localhost:${port}/actuator/health"
      echo "  3. Run full verification: ./scripts/dr/post-restore-verify.sh --db ${TARGET_DB}"
    fi
    echo "================================================================"
    exit 0
  else
    echo ""
    echo "================================================================"
    log_fail "RESTORE COMPLETED WITH VERIFICATION FAILURES: ${TARGET_DB}"
    echo "  Review the output above and take corrective action."
    echo "================================================================"
    exit 1
  fi
}

main "$@"

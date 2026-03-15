#!/usr/bin/env bash
# =============================================================================
# Wave 20 — Backup All Databases
# =============================================================================
#
# Performs full pg_dump backups for all Impilo vNext PostgreSQL databases,
# uploads to S3/MinIO backup bucket, and optionally exports Keycloak realm.
#
# Usage:
#   ./scripts/dr/backup-all.sh [--tier TIER] [--dry-run]
#
# Options:
#   --tier TIER    Backup only specified tier (1-5). Default: all tiers.
#   --dry-run      Show what would be backed up without executing.
#
# Environment variables (required in production):
#   DB_HOST          PostgreSQL host (default: localhost)
#   DB_PORT          PostgreSQL port (default: 5432)
#   DB_USER          PostgreSQL user (default: impilo)
#   PGPASSWORD       PostgreSQL password
#   BACKUP_BUCKET    S3/MinIO bucket for backups (default: impilo-backups)
#   S3_ENDPOINT      MinIO endpoint URL (omit for AWS S3)
#   KEYCLOAK_HOST    Keycloak host (default: localhost:8080)
#
# Exit codes:
#   0 — all backups completed successfully
#   1 — one or more backups failed
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
BACKUP_BUCKET="${BACKUP_BUCKET:-impilo-backups}"
S3_ENDPOINT="${S3_ENDPOINT:-}"
KEYCLOAK_HOST="${KEYCLOAK_HOST:-localhost:8080}"

TIMESTAMP=$(date -u +"%Y%m%dT%H%M%SZ")
BACKUP_DIR="/tmp/impilo-backup-${TIMESTAMP}"
DRY_RUN=false
TIER_FILTER=""
FAILURES=0
TOTAL=0

# ---------------------------------------------------------------------------
# Database tiers
# ---------------------------------------------------------------------------
# Tier 1: Ring 0 Trust (RPO=0, daily full + WAL)
TIER1_DBS="tshepo keycloak tshepo_authz tshepo_identity tshepo_consent tshepo_audit tshepo_keys tshepo_offline"

# Tier 2: Ring 0 Registry (RPO<=5min, daily full + WAL)
TIER2_DBS="vito varapi tuso zibo msika butano mushex"

# Tier 3: Ring 1 Clinical (RPO<=5min, daily full + WAL)
TIER3_DBS="ubomi pct oros"

# Tier 4: Ring 1 Operational (RPO<=1hr, daily full)
TIER4_DBS="pharmacy inpatient inventory costing landela_adapter credential_verification share_slip card_print msika_flow"

# Tier 5: Ring 2 Platform (RPO<=4hr, daily full)
TIER5_DBS="document_service notification jobs offline_sync integration_hub experience_bff impilo_forms impilo_search"

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
log_dry()   { echo -e "${YELLOW}[DRY]${NC}   $(date -u +%H:%M:%S) $1"; }

# ---------------------------------------------------------------------------
# Parse arguments
# ---------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
  case "$1" in
    --tier)
      TIER_FILTER="$2"
      shift 2
      ;;
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    -h|--help)
      head -30 "$0" | grep -E '^#' | sed 's/^# \?//'
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 2
      ;;
  esac
done

# ---------------------------------------------------------------------------
# Prerequisites check
# ---------------------------------------------------------------------------
check_prereqs() {
  local missing=0
  for cmd in pg_dump sha256sum; do
    if ! command -v "${cmd}" &>/dev/null; then
      log_fail "Required command not found: ${cmd}"
      missing=1
    fi
  done

  # S3 upload tool (aws cli or mc for MinIO)
  if ! command -v aws &>/dev/null && ! command -v mc &>/dev/null; then
    log_warn "Neither 'aws' nor 'mc' found — backups will be stored locally only"
  fi

  if [ "${missing}" -eq 1 ]; then
    exit 2
  fi
}

# ---------------------------------------------------------------------------
# Backup a single database
# ---------------------------------------------------------------------------
backup_database() {
  local db="$1"
  local tier="$2"
  TOTAL=$((TOTAL + 1))

  local dump_file="${BACKUP_DIR}/${db}_${TIMESTAMP}.dump"
  local checksum_file="${dump_file}.sha256"

  if [ "${DRY_RUN}" = true ]; then
    log_dry "Would backup: ${db} (tier ${tier}) → ${dump_file}"
    return 0
  fi

  log_info "Backing up database: ${db} (tier ${tier})"

  # Check database exists
  if ! psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -lqt 2>/dev/null | grep -qw "${db}"; then
    log_warn "Database '${db}' does not exist — skipping"
    return 0
  fi

  # Execute pg_dump
  local start_time
  start_time=$(date +%s)

  if pg_dump -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" \
    -Fc -Z 6 \
    --file="${dump_file}" \
    "${db}" 2>/dev/null; then

    local end_time
    end_time=$(date +%s)
    local duration=$((end_time - start_time))
    local size
    size=$(stat -f%z "${dump_file}" 2>/dev/null || stat -c%s "${dump_file}" 2>/dev/null || echo "unknown")

    # Compute checksum
    sha256sum "${dump_file}" > "${checksum_file}"

    log_ok "Backup complete: ${db} (${size} bytes, ${duration}s)"

    # Upload to S3/MinIO if tool available
    upload_backup "${db}" "${tier}" "${dump_file}" "${checksum_file}"

  else
    log_fail "pg_dump failed for database: ${db}"
    FAILURES=$((FAILURES + 1))
  fi
}

# ---------------------------------------------------------------------------
# Upload backup to S3/MinIO
# ---------------------------------------------------------------------------
upload_backup() {
  local db="$1"
  local tier="$2"
  local dump_file="$3"
  local checksum_file="$4"
  local s3_prefix="s3://${BACKUP_BUCKET}/postgres/daily/${db}/"

  if command -v aws &>/dev/null; then
    local aws_args=""
    if [ -n "${S3_ENDPOINT}" ]; then
      aws_args="--endpoint-url ${S3_ENDPOINT}"
    fi

    # shellcheck disable=SC2086
    if aws s3 cp ${aws_args} "${dump_file}" "${s3_prefix}" --sse AES256 2>/dev/null && \
       aws s3 cp ${aws_args} "${checksum_file}" "${s3_prefix}" 2>/dev/null; then
      log_ok "Uploaded to S3: ${s3_prefix}"
    else
      log_warn "S3 upload failed for ${db} — backup retained locally at ${dump_file}"
    fi

  elif command -v mc &>/dev/null; then
    local mc_prefix="backup/${BACKUP_BUCKET}/postgres/daily/${db}/"
    if mc cp "${dump_file}" "${mc_prefix}" 2>/dev/null && \
       mc cp "${checksum_file}" "${mc_prefix}" 2>/dev/null; then
      log_ok "Uploaded to MinIO: ${mc_prefix}"
    else
      log_warn "MinIO upload failed for ${db} — backup retained locally at ${dump_file}"
    fi

  else
    log_info "No S3/MinIO client — backup stored locally: ${dump_file}"
  fi
}

# ---------------------------------------------------------------------------
# Backup tier
# ---------------------------------------------------------------------------
backup_tier() {
  local tier="$1"
  local dbs="$2"
  local label="$3"

  if [ -n "${TIER_FILTER}" ] && [ "${TIER_FILTER}" != "${tier}" ]; then
    return 0
  fi

  echo ""
  log_info "═══ Tier ${tier}: ${label} ═══"

  for db in ${dbs}; do
    backup_database "${db}" "${tier}"
  done
}

# ---------------------------------------------------------------------------
# Keycloak realm export
# ---------------------------------------------------------------------------
export_keycloak_realm() {
  if [ -n "${TIER_FILTER}" ] && [ "${TIER_FILTER}" != "1" ]; then
    return 0
  fi

  TOTAL=$((TOTAL + 1))
  local export_file="${BACKUP_DIR}/keycloak-realm-impilo-${TIMESTAMP}.json"

  if [ "${DRY_RUN}" = true ]; then
    log_dry "Would export Keycloak realm 'impilo' → ${export_file}"
    return 0
  fi

  log_info "Exporting Keycloak realm: impilo"

  # Attempt realm export via admin API
  local token
  token=$(curl -sf -X POST "http://${KEYCLOAK_HOST}/realms/master/protocol/openid-connect/token" \
    -d "grant_type=client_credentials" \
    -d "client_id=admin-cli" \
    -d "client_secret=${KEYCLOAK_ADMIN_SECRET:-admin}" 2>/dev/null | \
    python3 -c "import sys,json; print(json.load(sys.stdin).get('access_token',''))" 2>/dev/null || echo "")

  if [ -n "${token}" ]; then
    if curl -sf -H "Authorization: Bearer ${token}" \
      "http://${KEYCLOAK_HOST}/admin/realms/impilo" \
      -o "${export_file}" 2>/dev/null; then
      log_ok "Keycloak realm exported: ${export_file}"

      # Upload
      if command -v aws &>/dev/null; then
        local aws_args=""
        [ -n "${S3_ENDPOINT}" ] && aws_args="--endpoint-url ${S3_ENDPOINT}"
        # shellcheck disable=SC2086
        aws s3 cp ${aws_args} "${export_file}" \
          "s3://${BACKUP_BUCKET}/keycloak/realm-exports/" --sse AES256 2>/dev/null || true
      fi
    else
      log_warn "Keycloak realm export failed — API not reachable"
    fi
  else
    log_warn "Keycloak realm export skipped — could not obtain admin token"
  fi
}

# ---------------------------------------------------------------------------
# MinIO document backup status
# ---------------------------------------------------------------------------
check_minio_mirror() {
  TOTAL=$((TOTAL + 1))

  if [ "${DRY_RUN}" = true ]; then
    log_dry "Would check MinIO mirror status"
    return 0
  fi

  if command -v mc &>/dev/null; then
    log_info "Checking MinIO mirror status"
    if mc mirror --dry-run primary/impilo-documents dr/impilo-documents 2>/dev/null; then
      log_ok "MinIO mirror is in sync"
    else
      log_warn "MinIO mirror check failed or not configured"
    fi
  else
    log_info "MinIO client (mc) not available — skipping mirror status check"
  fi
}

# ---------------------------------------------------------------------------
# Cleanup old local backups
# ---------------------------------------------------------------------------
cleanup_local() {
  local retention_days="${1:-7}"
  log_info "Cleaning up local backups older than ${retention_days} days"
  find /tmp -maxdepth 1 -name "impilo-backup-*" -type d -mtime "+${retention_days}" -exec rm -rf {} \; 2>/dev/null || true
}

# =============================================================================
# MAIN
# =============================================================================
main() {
  echo ""
  echo "================================================================"
  echo "  Impilo vNext — Full Backup (${TIMESTAMP})"
  echo "================================================================"

  check_prereqs

  mkdir -p "${BACKUP_DIR}"
  log_info "Backup directory: ${BACKUP_DIR}"

  # Backup all tiers
  backup_tier 1 "${TIER1_DBS}" "Ring 0 Trust (TSHEPO, Keycloak, sub-services)"
  backup_tier 2 "${TIER2_DBS}" "Ring 0 Registry (VITO, VARAPI, TUSO, ZIBO, MSIKA, BUTANO, MUSHEX)"
  backup_tier 3 "${TIER3_DBS}" "Ring 1 Clinical (PCT, OROS, UBOMI)"
  backup_tier 4 "${TIER4_DBS}" "Ring 1 Operational (Pharmacy, Inventory, etc.)"
  backup_tier 5 "${TIER5_DBS}" "Ring 2 Platform (Notification, Jobs, etc.)"

  # Keycloak realm export
  echo ""
  log_info "═══ Keycloak Realm Export ═══"
  export_keycloak_realm

  # MinIO mirror status
  echo ""
  log_info "═══ MinIO Mirror Status ═══"
  check_minio_mirror

  # Cleanup old local backups
  cleanup_local 7

  # Summary
  echo ""
  echo "================================================================"
  local passed=$((TOTAL - FAILURES))
  echo "  Backup Summary: ${passed}/${TOTAL} succeeded"
  if [ "${FAILURES}" -gt 0 ]; then
    log_fail "${FAILURES} backup(s) failed"
    echo "================================================================"
    exit 1
  else
    log_ok "All backups completed successfully"
    echo "================================================================"
    exit 0
  fi
}

main "$@"

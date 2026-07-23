#!/usr/bin/env bash
# =============================================================================
# Impilo vNext — Seed Data Script
# =============================================================================
#
# Applies seed data to downstream service databases AFTER Flyway migrations
# have run. Must be executed once services are healthy.
#
# Usage:
#   ./scripts/seed-data.sh
#
# Prerequisites:
#   docker compose -f docker-compose.runtime.yml up -d
#   (wait for services to be healthy, or run this script which waits)
#
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SEED_DIR="$SCRIPT_DIR/seed"

POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-impilo-vnext-postgres-1}"
POSTGRES_USER="${POSTGRES_USER:-impilo}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

log_info()  { echo -e "${CYAN}[seed]${NC} $*"; }
log_ok()    { echo -e "${GREEN}[seed]${NC} $*"; }
log_warn()  { echo -e "${YELLOW}[seed]${NC} $*"; }
log_error() { echo -e "${RED}[seed]${NC} $*"; }

wait_for_service() {
  local name=$1
  local port=$2
  local max_attempts=30
  local attempt=0

  log_info "Waiting for $name (port $port) to be healthy..."
  while ! curl -sf "http://localhost:$port/actuator/health" | grep -q '"status":"UP"' 2>/dev/null; do
    attempt=$((attempt + 1))
    if [[ $attempt -ge $max_attempts ]]; then
      log_error "$name did not become healthy after ${max_attempts}s — skipping its seed"
      return 1
    fi
    sleep 2
  done
  log_ok "$name is healthy"
  return 0
}

apply_seed() {
  local db=$1
  local file=$2
  local label=$3

  if [[ ! -f "$file" ]]; then
    log_warn "Seed file not found: $file — skipping $label"
    return 0
  fi

  log_info "Seeding $label ($db)..."
  if docker exec -i "$POSTGRES_CONTAINER" psql -U "$POSTGRES_USER" -d "$db" < "$file"; then
    log_ok "$label seeded successfully"
  else
    log_error "$label seed FAILED — check output above"
    return 1
  fi
}

# =============================================================================
# Main
# =============================================================================

log_info "Impilo vNext — Seed Data"
log_info "Postgres container : $POSTGRES_CONTAINER"
log_info "Seed directory     : $SEED_DIR"
echo

# Verify postgres container is running
if ! docker inspect "$POSTGRES_CONTAINER" &>/dev/null; then
  log_error "Postgres container '$POSTGRES_CONTAINER' not found. Is the stack running?"
  exit 1
fi

# Wait for each service (Flyway runs on startup; we wait until actuator is UP)
declare -A SERVICES=(
  [tuso]=8084
  [vito]=8092
  [varapi]=8083
  [tshepo]=18081
  [zibo]=8085
  [pct]=8088
  [oros]=18089
)

FAILED_SERVICES=()
for svc in "${!SERVICES[@]}"; do
  port="${SERVICES[$svc]}"
  if ! wait_for_service "$svc" "$port"; then
    FAILED_SERVICES+=("$svc")
  fi
done

echo

# Apply seeds (skip those whose service failed health check)
[[ ! " ${FAILED_SERVICES[*]} " =~ " tuso "   ]] && apply_seed tuso   "$SEED_DIR/02-seed-tuso.sql"   "TUSO (Facility Registry)"
[[ ! " ${FAILED_SERVICES[*]} " =~ " vito "   ]] && apply_seed vito   "$SEED_DIR/03-seed-vito.sql"   "VITO (Client Registry)"
[[ ! " ${FAILED_SERVICES[*]} " =~ " varapi " ]] && apply_seed varapi "$SEED_DIR/04-seed-varapi.sql" "VARAPI (Provider Registry)"
[[ ! " ${FAILED_SERVICES[*]} " =~ " tshepo " ]] && apply_seed tshepo "$SEED_DIR/05-seed-tshepo.sql" "TSHEPO (Trust & Governance)"
[[ ! " ${FAILED_SERVICES[*]} " =~ " zibo "   ]] && apply_seed zibo   "$SEED_DIR/06-seed-zibo.sql"   "ZIBO (Terminology)"
[[ ! " ${FAILED_SERVICES[*]} " =~ " pct "    ]] && apply_seed pct    "$SEED_DIR/07-seed-pct.sql"    "PCT (Patient Care Tracker)"
[[ ! " ${FAILED_SERVICES[*]} " =~ " oros "   ]] && apply_seed oros   "$SEED_DIR/08-seed-oros.sql"   "OROS (Orders & Results)"

# OF-B M3 wave-close seeds (idempotent; best-effort — these DBs may not exist
# on a partial estate, hence no health-gate entry):
apply_seed nhume "$SEED_DIR/22-seed-nhume-proof-courier.sql" "NHUME (preview courier profile)" || true
apply_seed credential_verification "$SEED_DIR/23-seed-credential-facility-payee.sql" "CREDENTIAL-VERIFICATION (facility payee)" || true

echo
if [[ ${#FAILED_SERVICES[@]} -eq 0 ]]; then
  log_ok "All seed data applied successfully"
else
  log_warn "Completed with skipped services: ${FAILED_SERVICES[*]}"
  exit 1
fi

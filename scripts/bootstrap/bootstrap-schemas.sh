#!/usr/bin/env bash
# =============================================================================
# Impilo vNext — Bootstrap: Schemas & Registry State
# =============================================================================
#
# Validates that all service databases exist and are accessible.
# Verifies the HAPI FHIR server schema is initialized.
# Idempotent — safe to run multiple times.
#
# Usage:
#   ./scripts/bootstrap/bootstrap-schemas.sh
#
# Environment:
#   POSTGRES_HOST     (default: localhost)
#   POSTGRES_PORT     (default: 5432)
#   POSTGRES_USER     (default: impilo)
#   POSTGRES_PASSWORD (default: changeme)
#   HAPI_FHIR_URL     (default: http://localhost:8090/fhir)
#
# =============================================================================

set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'
NC='\033[0m'

info() { printf "${CYAN}[INFO]${NC}  %s\n" "$*"; }
ok()   { printf "${GREEN}[OK]${NC}    %s\n" "$*"; }
warn() { printf "${YELLOW}[WARN]${NC}  %s\n" "$*"; }
err()  { printf "${RED}[ERROR]${NC} %s\n" "$*" >&2; }

POSTGRES_HOST="${POSTGRES_HOST:-localhost}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
POSTGRES_USER="${POSTGRES_USER:-impilo}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-changeme}"
HAPI_FHIR_URL="${HAPI_FHIR_URL:-http://localhost:8090/fhir}"

export PGPASSWORD="${POSTGRES_PASSWORD}"

# ── 1. Wait for Postgres ────────────────────────────────────────────────────
info "1/3 Waiting for PostgreSQL at ${POSTGRES_HOST}:${POSTGRES_PORT}..."
MAX_WAIT=60
ELAPSED=0

while (( ELAPSED < MAX_WAIT )); do
    if pg_isready -h "${POSTGRES_HOST}" -p "${POSTGRES_PORT}" -U "${POSTGRES_USER}" > /dev/null 2>&1; then
        ok "PostgreSQL ready (${ELAPSED}s)"
        break
    fi
    # Fallback: try TCP connection
    if (echo > /dev/tcp/"${POSTGRES_HOST}"/"${POSTGRES_PORT}") 2>/dev/null; then
        ok "PostgreSQL TCP reachable (${ELAPSED}s)"
        break
    fi
    sleep 2
    ELAPSED=$(( ELAPSED + 2 ))
done

if (( ELAPSED >= MAX_WAIT )); then
    err "PostgreSQL not ready within ${MAX_WAIT}s"
    exit 1
fi

# ── 2. Verify databases exist ───────────────────────────────────────────────
info "2/3 Verifying service databases..."

# Core databases from init-databases.sql
DATABASES=(
    tshepo vito varapi tuso zibo msika ubomi pct oros pharmacy
    inpatient inventory mushex costing document_service notification
    jobs offline_sync integration_hub keycloak experience_bff
    tshepo_authz tshepo_identity tshepo_consent tshepo_audit tshepo_keys tshepo_offline
    landela_adapter credential_verification share_slip card_print
    msika_flow product_registry butano butano_fhir fhir_gateway
    reporting ndr impilo_data_warehouse
)

DB_OK=0
DB_MISSING=0

for db in "${DATABASES[@]}"; do
    if psql -h "${POSTGRES_HOST}" -p "${POSTGRES_PORT}" -U "${POSTGRES_USER}" \
        -d "${db}" -c "SELECT 1" > /dev/null 2>&1; then
        DB_OK=$(( DB_OK + 1 ))
    else
        warn "Database '${db}' not accessible"
        DB_MISSING=$(( DB_MISSING + 1 ))
    fi
done

if (( DB_MISSING == 0 )); then
    ok "All ${DB_OK} databases verified"
else
    warn "${DB_MISSING} databases not accessible (may need pg init)"
    info "Run: docker compose restart postgres  (to re-run init SQL)"
fi

# ── 3. Verify HAPI FHIR ─────────────────────────────────────────────────────
info "3/3 Verifying HAPI FHIR schema..."

FHIR_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${HAPI_FHIR_URL}/metadata" 2>/dev/null || echo "000")

if [[ "${FHIR_CODE}" == "200" ]]; then
    ok "HAPI FHIR metadata endpoint accessible — schema initialized"
else
    warn "HAPI FHIR not accessible (HTTP ${FHIR_CODE}) — may still be starting"
fi

# ── Summary ──────────────────────────────────────────────────────────────────
echo ""
echo "=============================================="
echo "  Schema Bootstrap Summary"
echo "=============================================="
echo -e "  ${GREEN}Databases OK${NC}:    ${DB_OK}"
echo -e "  ${YELLOW}Databases Missing${NC}: ${DB_MISSING}"
echo -e "  HAPI FHIR:         HTTP ${FHIR_CODE}"
echo "=============================================="

if (( DB_MISSING > 0 )) || [[ "${FHIR_CODE}" != "200" ]]; then
    warn "Some schema checks incomplete — see warnings above"
    exit 0  # Non-fatal — schemas auto-initialize on service startup
fi

ok "Schema bootstrap complete"

#!/usr/bin/env bash
# =============================================================================
# Impilo vNext — Bootstrap: Application Configuration
# =============================================================================
#
# Ensures all required configuration artifacts are in place:
#   1. .env file exists (copies from .env.example if missing)
#   2. Vendor cache directories exist (Maven, npm)
#   3. Envoy config is present
#   4. OPA policies are present
#   5. Realm JSON is present
#
# Idempotent — safe to run multiple times.
#
# Usage:
#   ./scripts/bootstrap/bootstrap-app-config.sh
#
# =============================================================================

set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'
NC='\033[0m'

info() { printf "${CYAN}[INFO]${NC}  %s\n" "$*"; }
ok()   { printf "${GREEN}[OK]${NC}    %s\n" "$*"; }
warn() { printf "${YELLOW}[WARN]${NC}  %s\n" "$*"; }
err()  { printf "${RED}[ERROR]${NC} %s\n" "$*" >&2; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

CHECKS_OK=0
CHECKS_WARN=0
CHECKS_FAIL=0

check_pass() { ok "$1"; CHECKS_OK=$(( CHECKS_OK + 1 )); }
check_warn() { warn "$1"; CHECKS_WARN=$(( CHECKS_WARN + 1 )); }
check_fail() { err "$1"; CHECKS_FAIL=$(( CHECKS_FAIL + 1 )); }

echo ""
echo "=============================================="
echo "  Impilo vNext — App Config Bootstrap"
echo "=============================================="
echo ""

# ── 1. .env file ────────────────────────────────────────────────────────────
info "1/6 Checking .env file..."

if [[ -f "${PROJECT_ROOT}/.env" ]]; then
    check_pass ".env file exists"
else
    if [[ -f "${PROJECT_ROOT}/.env.example" ]]; then
        cp "${PROJECT_ROOT}/.env.example" "${PROJECT_ROOT}/.env"
        check_pass ".env created from .env.example"
    else
        check_fail ".env.example not found — cannot create .env"
    fi
fi

# ── 2. Vendor cache directories ─────────────────────────────────────────────
info "2/6 Ensuring vendor cache directories..."

mkdir -p "${PROJECT_ROOT}/vendor/m2/repository"
mkdir -p "${PROJECT_ROOT}/vendor/node-cache"
check_pass "Vendor cache directories ready"

# ── 3. Envoy config ─────────────────────────────────────────────────────────
info "3/6 Checking Envoy configuration..."

if [[ -f "${PROJECT_ROOT}/infra/envoy/envoy-runtime.yaml" ]]; then
    check_pass "Envoy runtime config present"
else
    check_fail "Envoy runtime config missing at infra/envoy/envoy-runtime.yaml"
fi

# ── 4. OPA policies ─────────────────────────────────────────────────────────
info "4/6 Checking OPA policies..."

OPA_DIR="${PROJECT_ROOT}/tools/ops/gateway/opa"
if [[ -d "${OPA_DIR}" ]]; then
    REGO_COUNT=$(find "${OPA_DIR}" -name "*.rego" | wc -l)
    if (( REGO_COUNT > 0 )); then
        check_pass "OPA policies found (${REGO_COUNT} .rego files)"
    else
        check_warn "OPA policy directory exists but no .rego files found"
    fi
else
    check_fail "OPA policy directory missing at tools/ops/gateway/opa"
fi

# ── 5. Keycloak realm JSON ──────────────────────────────────────────────────
info "5/6 Checking Keycloak realm JSON..."

if [[ -f "${PROJECT_ROOT}/tools/auth/impilo-realm.json" ]]; then
    check_pass "Keycloak realm JSON present"
else
    check_fail "Keycloak realm JSON missing at tools/auth/impilo-realm.json"
fi

# ── 6. Init databases SQL ───────────────────────────────────────────────────
info "6/6 Checking database init SQL..."

if [[ -f "${PROJECT_ROOT}/scripts/seed/init-databases.sql" ]]; then
    DB_COUNT=$(grep -c "CREATE DATABASE" "${PROJECT_ROOT}/scripts/seed/init-databases.sql")
    check_pass "Database init SQL present (${DB_COUNT} databases defined)"
else
    check_fail "Database init SQL missing at scripts/seed/init-databases.sql"
fi

# ── Summary ──────────────────────────────────────────────────────────────────
echo ""
echo "=============================================="
echo "  App Config Bootstrap Summary"
echo "=============================================="
echo -e "  ${GREEN}OK${NC}:      ${CHECKS_OK}"
echo -e "  ${YELLOW}Warn${NC}:    ${CHECKS_WARN}"
echo -e "  ${RED}Fail${NC}:    ${CHECKS_FAIL}"
echo "=============================================="

if (( CHECKS_FAIL > 0 )); then
    err "Some configuration checks failed — fix before starting platform"
    exit 1
fi

ok "App configuration bootstrap complete"

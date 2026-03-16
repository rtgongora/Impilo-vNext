#!/usr/bin/env bash
# =============================================================================
# Impilo vNext — Bootstrap: Authentication (Keycloak Realm)
# =============================================================================
#
# Bootstraps the Keycloak impilo realm, verifies test users and clients,
# and obtains a test token. Idempotent — safe to run multiple times.
#
# Usage:
#   ./scripts/bootstrap/bootstrap-auth.sh
#   KEYCLOAK_URL=http://custom:8080 ./scripts/bootstrap/bootstrap-auth.sh
#
# Environment variables:
#   KEYCLOAK_URL              (default: http://localhost:8080)
#   KEYCLOAK_ADMIN            (default: admin)
#   KEYCLOAK_ADMIN_PASSWORD   (default: admin)
#
# Exits 0 on success, 1 on failure.
# =============================================================================

set -euo pipefail

# ── Colors ───────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'
BOLD='\033[1m'; NC='\033[0m'

info()   { printf "${CYAN}[INFO]${NC}  %s\n" "$*"; }
ok()     { printf "${GREEN}[OK]${NC}    %s\n" "$*"; }
warn()   { printf "${YELLOW}[WARN]${NC}  %s\n" "$*"; }
err()    { printf "${RED}[ERROR]${NC} %s\n" "$*" >&2; }
header() { printf "\n${BOLD}── %s ──${NC}\n" "$*"; }

# ── Configuration ────────────────────────────────────────────────────────────
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8080}"
KEYCLOAK_ADMIN="${KEYCLOAK_ADMIN:-admin}"
KEYCLOAK_ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
REALM_NAME="impilo"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
REALM_JSON="${PROJECT_ROOT}/tools/auth/impilo-realm.json"

# ── Pre-flight ───────────────────────────────────────────────────────────────
header "Bootstrap Auth — Pre-flight"

if [[ ! -f "${REALM_JSON}" ]]; then
    err "Realm JSON not found at: ${REALM_JSON}"
    exit 1
fi
info "Realm JSON: ${REALM_JSON}"
info "Keycloak URL: ${KEYCLOAK_URL}"

# ── 1. Wait for Keycloak ────────────────────────────────────────────────────
header "1/6 Waiting for Keycloak"

MAX_WAIT=120
ELAPSED=0

while (( ELAPSED < MAX_WAIT )); do
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${KEYCLOAK_URL}/health/ready" 2>/dev/null || true)
    if [[ "${HTTP_CODE}" == "200" ]]; then
        ok "Keycloak ready (${ELAPSED}s)"
        break
    fi
    printf "  waiting... (%ds/%ds) HTTP=%s\r" "$ELAPSED" "$MAX_WAIT" "$HTTP_CODE"
    sleep 2
    ELAPSED=$(( ELAPSED + 2 ))
done

if (( ELAPSED >= MAX_WAIT )); then
    err "Keycloak not ready within ${MAX_WAIT}s"
    err "Is Keycloak running? Check: docker ps | grep keycloak"
    exit 1
fi

# ── 2. Obtain admin token ───────────────────────────────────────────────────
header "2/6 Obtaining admin token"

ADMIN_TOKEN_RESPONSE=$(curl -s -X POST \
    "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password&client_id=admin-cli&username=${KEYCLOAK_ADMIN}&password=${KEYCLOAK_ADMIN_PASSWORD}" \
)

ADMIN_TOKEN=$(echo "${ADMIN_TOKEN_RESPONSE}" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)

if [[ -z "${ADMIN_TOKEN}" ]]; then
    err "Failed to obtain admin token"
    err "Response: ${ADMIN_TOKEN_RESPONSE}"
    exit 1
fi
ok "Admin token obtained"

# ── 3. Check existing realm (idempotent) ────────────────────────────────────
header "3/6 Checking realm state"

REALM_CHECK=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}" \
)

if [[ "${REALM_CHECK}" == "200" ]]; then
    ok "Realm '${REALM_NAME}' already exists — skipping import (idempotent)"
    REALM_EXISTED=true
else
    info "Realm '${REALM_NAME}' does not exist — will import"
    REALM_EXISTED=false
fi

# ── 4. Import realm if needed ───────────────────────────────────────────────
if [[ "${REALM_EXISTED}" == "false" ]]; then
    header "4/6 Importing realm"

    IMPORT_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
        "${KEYCLOAK_URL}/admin/realms" \
        -H "Authorization: Bearer ${ADMIN_TOKEN}" \
        -H "Content-Type: application/json" \
        -d "@${REALM_JSON}" \
    )

    if [[ "${IMPORT_CODE}" == "201" ]]; then
        ok "Realm '${REALM_NAME}' imported"
    else
        err "Realm import failed (HTTP ${IMPORT_CODE})"
        exit 1
    fi
else
    info "4/6 Skipped — realm already present"
fi

# ── 5. Verify realm contents ────────────────────────────────────────────────
header "5/6 Verifying realm"

# Realm accessible
VERIFY_REALM=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}" \
)
if [[ "${VERIFY_REALM}" == "200" ]]; then
    ok "Realm '${REALM_NAME}' accessible"
else
    err "Realm verification failed (HTTP ${VERIFY_REALM})"
    exit 1
fi

# Test user
USER_RESPONSE=$(curl -s \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/users?username=dr.mapfumo" \
)
USER_COUNT=$(echo "${USER_RESPONSE}" | grep -o '"username"' | wc -l)
if (( USER_COUNT >= 1 )); then
    ok "Test user 'dr.mapfumo' found"
else
    warn "Test user 'dr.mapfumo' not found — realm may need manual setup"
fi

# Test client
CLIENT_RESPONSE=$(curl -s \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/clients?clientId=integration-test" \
)
CLIENT_COUNT=$(echo "${CLIENT_RESPONSE}" | grep -o '"clientId"' | wc -l)
if (( CLIENT_COUNT >= 1 )); then
    ok "Client 'integration-test' found"
else
    warn "Client 'integration-test' not found — realm may need manual setup"
fi

# ── 6. Obtain test token ────────────────────────────────────────────────────
header "6/6 Obtaining test token"

TEST_TOKEN_RESPONSE=$(curl -s -X POST \
    "${KEYCLOAK_URL}/realms/${REALM_NAME}/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password&client_id=integration-test&username=dr.mapfumo&password=test" \
)

TEST_TOKEN=$(echo "${TEST_TOKEN_RESPONSE}" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)

if [[ -n "${TEST_TOKEN}" ]]; then
    ok "Test token obtained for dr.mapfumo"
    echo ""
    echo "TEST_TOKEN=${TEST_TOKEN}"
else
    warn "Could not obtain test token (users/clients may not be configured)"
    warn "Response: ${TEST_TOKEN_RESPONSE}"
fi

# ── Summary ──────────────────────────────────────────────────────────────────
header "Bootstrap Auth Complete"
ok "Keycloak URL:  ${KEYCLOAK_URL}"
ok "Realm:         ${REALM_NAME}"
ok "Idempotent:    yes (re-run safe)"

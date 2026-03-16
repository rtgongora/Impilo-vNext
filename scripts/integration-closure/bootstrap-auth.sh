#!/usr/bin/env bash
set -euo pipefail

# ---------------------------------------------------------------------------
# bootstrap-auth.sh
# Bootstraps the Keycloak auth environment for integration testing.
# Imports the impilo realm, verifies users/clients, and prints a test token.
# ---------------------------------------------------------------------------

# ── Colour helpers ─────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Colour

info()    { printf "${CYAN}[INFO]${NC}  %s\n" "$*"; }
ok()      { printf "${GREEN}[OK]${NC}    %s\n" "$*"; }
warn()    { printf "${YELLOW}[WARN]${NC}  %s\n" "$*"; }
err()     { printf "${RED}[ERROR]${NC} %s\n" "$*" >&2; }
header()  { printf "\n${BOLD}── %s ──${NC}\n" "$*"; }

# ── Variables ──────────────────────────────────────────────────────────────
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8080}"
KEYCLOAK_ADMIN="${KEYCLOAK_ADMIN:-admin}"
KEYCLOAK_ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
REALM_NAME="impilo"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REALM_JSON="${SCRIPT_DIR}/../../tools/auth/impilo-realm.json"

# ── Pre-flight checks ─────────────────────────────────────────────────────
if [[ ! -f "${REALM_JSON}" ]]; then
  err "Realm JSON not found at: ${REALM_JSON}"
  err "Expected path relative to script: ../../tools/auth/impilo-realm.json"
  exit 1
fi

REALM_JSON="$(realpath "${REALM_JSON}")"
info "Realm JSON resolved to: ${REALM_JSON}"

# ── 1. Wait for Keycloak to be healthy ─────────────────────────────────────
header "Waiting for Keycloak to be healthy"

MAX_WAIT=60
ELAPSED=0
INTERVAL=2

while (( ELAPSED < MAX_WAIT )); do
  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${KEYCLOAK_URL}/health/ready" 2>/dev/null || true)
  if [[ "${HTTP_CODE}" == "200" ]]; then
    ok "Keycloak is ready (${ELAPSED}s elapsed)"
    break
  fi
  info "Keycloak not ready yet (HTTP ${HTTP_CODE})… retrying in ${INTERVAL}s"
  sleep "${INTERVAL}"
  ELAPSED=$(( ELAPSED + INTERVAL ))
done

if (( ELAPSED >= MAX_WAIT )); then
  err "Keycloak did not become healthy within ${MAX_WAIT}s"
  exit 1
fi

# ── 2. Obtain admin access token ──────────────────────────────────────────
header "Obtaining admin access token"

ADMIN_TOKEN_RESPONSE=$(curl -s -X POST \
  "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=admin-cli&username=${KEYCLOAK_ADMIN}&password=${KEYCLOAK_ADMIN_PASSWORD}&grant_type=password" \
)

ADMIN_TOKEN=$(echo "${ADMIN_TOKEN_RESPONSE}" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)

if [[ -z "${ADMIN_TOKEN}" ]]; then
  err "Failed to obtain admin access token"
  err "Response: ${ADMIN_TOKEN_RESPONSE}"
  exit 1
fi

ok "Admin access token obtained"

# ── 3. Check / delete existing realm ──────────────────────────────────────
header "Checking for existing '${REALM_NAME}' realm"

REALM_CHECK=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}" \
)

if [[ "${REALM_CHECK}" == "200" ]]; then
  warn "Realm '${REALM_NAME}' already exists — deleting for deterministic state"
  DELETE_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}" \
  )
  if [[ "${DELETE_CODE}" == "204" ]]; then
    ok "Existing realm deleted"
  else
    err "Failed to delete existing realm (HTTP ${DELETE_CODE})"
    exit 1
  fi
else
  info "Realm '${REALM_NAME}' does not exist yet — proceeding with import"
fi

# ── 4. Import realm ──────────────────────────────────────────────────────
header "Importing realm from ${REALM_JSON}"

IMPORT_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
  "${KEYCLOAK_URL}/admin/realms" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "@${REALM_JSON}" \
)

if [[ "${IMPORT_CODE}" == "201" ]]; then
  ok "Realm '${REALM_NAME}' imported successfully"
else
  err "Realm import failed (HTTP ${IMPORT_CODE})"
  exit 1
fi

# ── 5. Verify import ────────────────────────────────────────────────────
header "Verifying realm import"

# 5a. Realm exists
VERIFY_REALM=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}" \
)

if [[ "${VERIFY_REALM}" == "200" ]]; then
  ok "Realm '${REALM_NAME}' exists"
else
  err "Realm verification failed (HTTP ${VERIFY_REALM})"
  exit 1
fi

# 5b. Test user exists
USER_RESPONSE=$(curl -s \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/users?username=dr.mapfumo" \
)

USER_COUNT=$(echo "${USER_RESPONSE}" | grep -o '"username"' | wc -l)

if (( USER_COUNT >= 1 )); then
  ok "Test user 'dr.mapfumo' found"
else
  err "Test user 'dr.mapfumo' not found in realm"
  exit 1
fi

# 5c. Test client exists
CLIENT_RESPONSE=$(curl -s \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/clients?clientId=integration-test" \
)

CLIENT_COUNT=$(echo "${CLIENT_RESPONSE}" | grep -o '"clientId"' | wc -l)

if (( CLIENT_COUNT >= 1 )); then
  ok "Client 'integration-test' found"
else
  err "Client 'integration-test' not found in realm"
  exit 1
fi

# ── 6. Obtain test token for dr.mapfumo ──────────────────────────────────
header "Obtaining test token for dr.mapfumo"

TEST_TOKEN_RESPONSE=$(curl -s -X POST \
  "${KEYCLOAK_URL}/realms/${REALM_NAME}/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=integration-test&username=dr.mapfumo&password=test" \
)

TEST_TOKEN=$(echo "${TEST_TOKEN_RESPONSE}" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)

if [[ -z "${TEST_TOKEN}" ]]; then
  err "Failed to obtain test token for dr.mapfumo"
  err "Response: ${TEST_TOKEN_RESPONSE}"
  exit 1
fi

ok "Test token obtained for dr.mapfumo"

# Print the token to stdout for use by integration tests
echo ""
echo "${TEST_TOKEN}"

# ── 7. Summary ───────────────────────────────────────────────────────────
header "Bootstrap Summary"
ok "Keycloak URL:      ${KEYCLOAK_URL}"
ok "Realm:             ${REALM_NAME}"
ok "Realm JSON:        ${REALM_JSON}"
ok "Test user:         dr.mapfumo"
ok "Test client:       integration-test"
ok "Test token:        (printed above)"
echo ""
info "Integration auth environment is ready."

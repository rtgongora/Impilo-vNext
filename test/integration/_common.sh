#!/usr/bin/env bash
# Common helpers for integration tests

# Colors
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'; NC='\033[0m'

# Defaults
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8080}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:10000}"
TSHEPO_URL="${TSHEPO_URL:-http://localhost:8081}"
VITO_URL="${VITO_URL:-http://localhost:8082}"
VARAPI_URL="${VARAPI_URL:-http://localhost:8083}"
TUSO_URL="${TUSO_URL:-http://localhost:8084}"
BFF_URL="${BFF_URL:-http://localhost:8160}"
SUPPORT_URL="${SUPPORT_URL:-http://localhost:8340}"
NOTIFICATION_URL="${NOTIFICATION_URL:-http://localhost:8111}"
REALM="impilo"
CLIENT_ID="integration-test"
CLIENT_SECRET="integration-test-secret"

PASS_COUNT=0
FAIL_COUNT=0

# Get a Keycloak token for a given user
get_token() {
  local username="$1"
  local password="${2:-test123}"
  curl -sf -X POST "${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token" \
    -d "grant_type=password" \
    -d "client_id=${CLIENT_ID}" \
    -d "client_secret=${CLIENT_SECRET}" \
    -d "username=${username}" \
    -d "password=${password}" | jq -r '.access_token'
}

# Assert HTTP status code
assert_status() {
  local test_name="$1"
  local expected="$2"
  local actual="$3"
  if [[ "$actual" == "$expected" ]]; then
    echo -e "${GREEN}[PASS]${NC} $test_name (HTTP $actual)"
    PASS_COUNT=$((PASS_COUNT + 1))
  else
    echo -e "${RED}[FAIL]${NC} $test_name — expected HTTP $expected, got $actual"
    FAIL_COUNT=$((FAIL_COUNT + 1))
  fi
}

# Assert JSON field exists and is not empty
assert_json_field() {
  local test_name="$1"
  local json="$2"
  local field="$3"
  local value
  value=$(echo "$json" | jq -r "$field" 2>/dev/null)
  if [[ -n "$value" && "$value" != "null" ]]; then
    echo -e "${GREEN}[PASS]${NC} $test_name — $field = $value"
    PASS_COUNT=$((PASS_COUNT + 1))
  else
    echo -e "${RED}[FAIL]${NC} $test_name — $field missing or null"
    FAIL_COUNT=$((FAIL_COUNT + 1))
  fi
}

# Assert JSON field equals expected value
assert_json_equals() {
  local test_name="$1"
  local json="$2"
  local field="$3"
  local expected="$4"
  local value
  value=$(echo "$json" | jq -r "$field" 2>/dev/null)
  if [[ "$value" == "$expected" ]]; then
    echo -e "${GREEN}[PASS]${NC} $test_name — $field = $expected"
    PASS_COUNT=$((PASS_COUNT + 1))
  else
    echo -e "${RED}[FAIL]${NC} $test_name — expected $field=$expected, got $value"
    FAIL_COUNT=$((FAIL_COUNT + 1))
  fi
}

# Generate trust headers as curl args
trust_headers() {
  local request_id="${1:-req-$(date +%s%N)}"
  local correlation_id="${2:-corr-$(date +%s%N)}"
  echo "-H 'X-Tenant-ID: moh-zw' -H 'X-Pod-ID: national' -H 'X-Request-ID: $request_id' -H 'X-Correlation-ID: $correlation_id'"
}

# Print test summary
print_summary() {
  local total=$((PASS_COUNT + FAIL_COUNT))
  echo ""
  echo "========================================="
  echo -e "Results: ${GREEN}${PASS_COUNT} passed${NC}, ${RED}${FAIL_COUNT} failed${NC} out of $total"
  echo "========================================="
  if [[ $FAIL_COUNT -gt 0 ]]; then
    exit 1
  fi
}

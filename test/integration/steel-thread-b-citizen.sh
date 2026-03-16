#!/usr/bin/env bash
set -euo pipefail
#
# Steel Thread B — Citizen Flow
#
# Tests the citizen-facing path through the BFF and VITO, verifying:
#   1. Keycloak token acquisition for a citizen user
#   2. BFF login endpoint returns token + trust metadata
#   3. VITO patient listing with trust headers
#   4. Trust header enforcement on VITO (missing headers → 400)
#

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/_common.sh"

echo "==========================================="
echo " Steel Thread B — Citizen Flow"
echo "==========================================="
echo ""

# ---- Step 1: Obtain Keycloak token for citizen.moyo ----
echo -e "${YELLOW}[INFO]${NC} Obtaining Keycloak token for citizen.moyo..."
TOKEN=$(get_token "citizen.moyo")
if [[ -z "$TOKEN" || "$TOKEN" == "null" ]]; then
  echo -e "${RED}[FAIL]${NC} Could not obtain Keycloak token for citizen.moyo"
  FAIL_COUNT=$((FAIL_COUNT + 1))
  print_summary
fi
echo -e "${GREEN}[PASS]${NC} Obtained Keycloak token for citizen.moyo"
PASS_COUNT=$((PASS_COUNT + 1))

# ---- Step 2: POST /internal/v1/auth/login via BFF → 200 ----
REQUEST_ID="req-$(date +%s%N)"
CORRELATION_ID="corr-$(date +%s%N)"

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: ${REQUEST_ID}" \
  -H "X-Correlation-ID: ${CORRELATION_ID}" \
  "${BFF_URL}/internal/v1/auth/login")

HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')

assert_status "POST /internal/v1/auth/login" "200" "$HTTP_CODE"

# ---- Step 3: Verify response has data.attributes.token ----
assert_json_field "Login response has data.attributes.token" "$BODY" ".data.attributes.token"

# ---- Step 4: Verify response has meta.request_id and meta.correlation_id ----
assert_json_field "Login response has meta.request_id" "$BODY" ".meta.request_id"
assert_json_field "Login response has meta.correlation_id" "$BODY" ".meta.correlation_id"

# ---- Step 5: GET /api/v1.1/patients via VITO with trust headers → 200 ----
REQUEST_ID_VITO="req-$(date +%s%N)"
CORRELATION_ID_VITO="corr-$(date +%s%N)"

RESPONSE=$(curl -s -w "\n%{http_code}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: ${REQUEST_ID_VITO}" \
  -H "X-Correlation-ID: ${CORRELATION_ID_VITO}" \
  "${VITO_URL}/api/v1.1/patients")

HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')

assert_status "GET /api/v1.1/patients with trust headers" "200" "$HTTP_CODE"

# ---- Step 6: GET /api/v1.1/patients WITHOUT trust headers → 400 ----
RESPONSE=$(curl -s -w "\n%{http_code}" \
  -H "Authorization: Bearer ${TOKEN}" \
  "${VITO_URL}/api/v1.1/patients")

HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')

assert_status "GET /api/v1.1/patients without trust headers" "400" "$HTTP_CODE"

# Verify error envelope structure
assert_json_field "Error envelope has code" "$BODY" ".code"
assert_json_field "Error envelope has message" "$BODY" ".message"

print_summary

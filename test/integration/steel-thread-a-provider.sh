#!/usr/bin/env bash
set -euo pipefail
#
# Steel Thread A — Auth Bootstrap + Provider Flow
#
# Tests the provider registry path through VARAPI, verifying:
#   1. Keycloak token acquisition for a provider user
#   2. Authenticated GET to the providers endpoint with trust headers
#   3. Trust header enforcement (missing X-Tenant-ID → 400)
#   4. Error envelope correctness (code, request_id, correlation_id)
#   5. Idempotency-Key enforcement on POST (missing key → 400)
#

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/_common.sh"

echo "==========================================="
echo " Steel Thread A — Auth Bootstrap + Provider"
echo "==========================================="
echo ""

# ---- Step 1: Obtain Keycloak token for dr.mapfumo ----
echo -e "${YELLOW}[INFO]${NC} Obtaining Keycloak token for dr.mapfumo..."
TOKEN=$(get_token "dr.mapfumo")
if [[ -z "$TOKEN" || "$TOKEN" == "null" ]]; then
  echo -e "${RED}[FAIL]${NC} Could not obtain Keycloak token for dr.mapfumo"
  FAIL_COUNT=$((FAIL_COUNT + 1))
  print_summary
fi
echo -e "${GREEN}[PASS]${NC} Obtained Keycloak token for dr.mapfumo"
PASS_COUNT=$((PASS_COUNT + 1))

# ---- Step 2: GET /api/v1.1/providers with trust headers → 200 ----
REQUEST_ID="req-$(date +%s%N)"
CORRELATION_ID="corr-$(date +%s%N)"

RESPONSE=$(curl -s -w "\n%{http_code}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: ${REQUEST_ID}" \
  -H "X-Correlation-ID: ${CORRELATION_ID}" \
  "${VARAPI_URL}/api/v1.1/providers")

HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')

assert_status "GET /api/v1.1/providers with trust headers" "200" "$HTTP_CODE"

# ---- Step 3: GET /api/v1.1/providers WITHOUT X-Tenant-ID → 400 ----
REQUEST_ID_NO_TENANT="req-$(date +%s%N)"
CORRELATION_ID_NO_TENANT="corr-$(date +%s%N)"

RESPONSE=$(curl -s -w "\n%{http_code}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: ${REQUEST_ID_NO_TENANT}" \
  -H "X-Correlation-ID: ${CORRELATION_ID_NO_TENANT}" \
  "${VARAPI_URL}/api/v1.1/providers")

HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')

assert_status "GET /api/v1.1/providers without X-Tenant-ID" "400" "$HTTP_CODE"

# ---- Step 4: Verify error envelope has code=MISSING_REQUIRED_HEADER ----
assert_json_equals "Error code is MISSING_REQUIRED_HEADER" "$BODY" ".code" "MISSING_REQUIRED_HEADER"

# ---- Step 5: Verify error envelope has request_id and correlation_id ----
assert_json_field "Error envelope has request_id" "$BODY" ".request_id"
assert_json_field "Error envelope has correlation_id" "$BODY" ".correlation_id"

# ---- Step 6: POST /api/v1.1/providers without Idempotency-Key → 400 ----
REQUEST_ID_NO_IDEMP="req-$(date +%s%N)"
CORRELATION_ID_NO_IDEMP="corr-$(date +%s%N)"

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: ${REQUEST_ID_NO_IDEMP}" \
  -H "X-Correlation-ID: ${CORRELATION_ID_NO_IDEMP}" \
  -d '{"name": "Test Provider", "type": "CLINIC"}' \
  "${VARAPI_URL}/api/v1.1/providers")

HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')

assert_status "POST /api/v1.1/providers without Idempotency-Key" "400" "$HTTP_CODE"

# ---- Step 7: Verify code=IDEMPOTENCY_KEY_REQUIRED ----
assert_json_equals "Error code is IDEMPOTENCY_KEY_REQUIRED" "$BODY" ".code" "IDEMPOTENCY_KEY_REQUIRED"

print_summary

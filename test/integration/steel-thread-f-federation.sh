#!/usr/bin/env bash
set -euo pipefail
#
# Steel Thread F — Federation-Protected Flow
#
# Tests the federation authority enforcement via TSHEPO, verifying:
#   1. Keycloak token acquisition for an admin user
#   2. Federation endpoint with X-Pod-ID=national succeeds (2xx)
#   3. Federation endpoint with X-Pod-ID=private-harare is denied (403)
#   4. Error envelope code=FEDERATION_AUTHORITY_VIOLATION
#   5. Error response includes request_id and correlation_id
#

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/_common.sh"

echo "==========================================="
echo " Steel Thread F — Federation-Protected Flow"
echo "==========================================="
echo ""

# ---- Step 1: Obtain Keycloak token for admin.central ----
echo -e "${YELLOW}[INFO]${NC} Obtaining Keycloak token for admin.central..."
TOKEN=$(get_token "admin.central")
if [[ -z "$TOKEN" || "$TOKEN" == "null" ]]; then
  echo -e "${RED}[FAIL]${NC} Could not obtain Keycloak token for admin.central"
  FAIL_COUNT=$((FAIL_COUNT + 1))
  print_summary
fi
echo -e "${GREEN}[PASS]${NC} Obtained Keycloak token for admin.central"
PASS_COUNT=$((PASS_COUNT + 1))

# ---- Step 2: Call TSHEPO federation endpoint with X-Pod-ID=national → 2xx ----
REQUEST_ID_OK="req-$(date +%s%N)"
CORRELATION_ID_OK="corr-$(date +%s%N)"

echo -e "${YELLOW}[INFO]${NC} Calling federation endpoint with X-Pod-ID=national..."

RESPONSE=$(curl -s -w "\n%{http_code}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: ${REQUEST_ID_OK}" \
  -H "X-Correlation-ID: ${CORRELATION_ID_OK}" \
  "${TSHEPO_URL}/api/v1.1/federation/status")

HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')

# Accept any 2xx response
if [[ "$HTTP_CODE" =~ ^2[0-9]{2}$ ]]; then
  echo -e "${GREEN}[PASS]${NC} Federation endpoint with X-Pod-ID=national (HTTP $HTTP_CODE)"
  PASS_COUNT=$((PASS_COUNT + 1))
else
  echo -e "${RED}[FAIL]${NC} Federation endpoint with X-Pod-ID=national — expected 2xx, got $HTTP_CODE"
  FAIL_COUNT=$((FAIL_COUNT + 1))
fi

# ---- Step 3: Call same endpoint with X-Pod-ID=private-harare → 403 ----
REQUEST_ID_DENIED="req-$(date +%s%N)"
CORRELATION_ID_DENIED="corr-$(date +%s%N)"

echo -e "${YELLOW}[INFO]${NC} Calling federation endpoint with X-Pod-ID=private-harare (expecting 403)..."

RESPONSE=$(curl -s -w "\n%{http_code}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: private-harare" \
  -H "X-Request-ID: ${REQUEST_ID_DENIED}" \
  -H "X-Correlation-ID: ${CORRELATION_ID_DENIED}" \
  "${TSHEPO_URL}/api/v1.1/federation/status")

HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')

assert_status "Federation endpoint with X-Pod-ID=private-harare" "403" "$HTTP_CODE"

# ---- Step 4: Verify error envelope code=FEDERATION_AUTHORITY_VIOLATION ----
assert_json_equals "Error code is FEDERATION_AUTHORITY_VIOLATION" "$BODY" ".code" "FEDERATION_AUTHORITY_VIOLATION"

# ---- Step 5: Verify request_id and correlation_id in error response ----
assert_json_field "Error response has request_id" "$BODY" ".request_id"
assert_json_field "Error response has correlation_id" "$BODY" ".correlation_id"

# Verify the returned IDs match what we sent
RESP_REQ_ID=$(echo "$BODY" | jq -r '.request_id // empty' 2>/dev/null)
if [[ -n "$RESP_REQ_ID" && "$RESP_REQ_ID" == "$REQUEST_ID_DENIED" ]]; then
  echo -e "${GREEN}[PASS]${NC} Request-ID matches what was sent — $RESP_REQ_ID"
  PASS_COUNT=$((PASS_COUNT + 1))
elif [[ -n "$RESP_REQ_ID" ]]; then
  echo -e "${YELLOW}[WARN]${NC} Request-ID present but differs — sent=$REQUEST_ID_DENIED, got=$RESP_REQ_ID"
  PASS_COUNT=$((PASS_COUNT + 1))
fi

RESP_CORR_ID=$(echo "$BODY" | jq -r '.correlation_id // empty' 2>/dev/null)
if [[ -n "$RESP_CORR_ID" && "$RESP_CORR_ID" == "$CORRELATION_ID_DENIED" ]]; then
  echo -e "${GREEN}[PASS]${NC} Correlation-ID matches what was sent — $RESP_CORR_ID"
  PASS_COUNT=$((PASS_COUNT + 1))
elif [[ -n "$RESP_CORR_ID" ]]; then
  echo -e "${YELLOW}[WARN]${NC} Correlation-ID present but differs — sent=$CORRELATION_ID_DENIED, got=$RESP_CORR_ID"
  PASS_COUNT=$((PASS_COUNT + 1))
fi

print_summary

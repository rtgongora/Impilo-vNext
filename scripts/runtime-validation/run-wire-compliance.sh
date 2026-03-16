#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
TEST_DIR="$ROOT_DIR/test/integration"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'; BOLD='\033[1m'; NC='\033[0m'

# Source test helpers
if [[ -f "$TEST_DIR/_common.sh" ]]; then
  source "$TEST_DIR/_common.sh"
else
  echo -e "${RED}[ERROR] test/integration/_common.sh not found${NC}"
  exit 1
fi

echo -e "${BOLD}=== Wire Compliance Checks ===${NC}"

# Step 1: Get auth token
echo ""
echo -e "${BOLD}--- Obtain auth token ---${NC}"
TOKEN=$(get_token "admin" "admin")
if [[ -z "$TOKEN" || "$TOKEN" == "null" ]]; then
  echo -e "${RED}[FAIL] Could not obtain auth token${NC}"
  exit 1
fi
echo -e "${GREEN}[OK]${NC} Auth token obtained"

# --------------------------------------------------------------------------
# Test 1: Missing X-Tenant-ID on VARAPI → expect 400 MISSING_REQUIRED_HEADER
# --------------------------------------------------------------------------
echo ""
echo -e "${BOLD}--- Test 1: Missing X-Tenant-ID → 400 ---${NC}"
RESPONSE=$(curl -sf -w "\n%{http_code}" -X GET "${VARAPI_URL}/api/v1/providers" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: req-wire-$(date +%s%N)" \
  -H "X-Correlation-ID: corr-wire-$(date +%s%N)" \
  2>/dev/null) || RESPONSE=$'\n000'

HTTP_STATUS=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')

assert_status "Missing X-Tenant-ID returns 400" "400" "$HTTP_STATUS"

if echo "$BODY" | jq -r '.code' 2>/dev/null | grep -q "MISSING_REQUIRED_HEADER"; then
  echo -e "${GREEN}[PASS]${NC} Error code is MISSING_REQUIRED_HEADER"
  PASS_COUNT=$((PASS_COUNT + 1))
else
  echo -e "${RED}[FAIL]${NC} Expected error code MISSING_REQUIRED_HEADER, got: $(echo "$BODY" | jq -r '.code' 2>/dev/null || echo 'parse error')"
  FAIL_COUNT=$((FAIL_COUNT + 1))
fi

# --------------------------------------------------------------------------
# Test 2: Missing Idempotency-Key on VARAPI POST → 400 IDEMPOTENCY_KEY_REQUIRED
# --------------------------------------------------------------------------
echo ""
echo -e "${BOLD}--- Test 2: Missing Idempotency-Key on POST → 400 ---${NC}"
RESPONSE=$(curl -sf -w "\n%{http_code}" -X POST "${VARAPI_URL}/api/v1/providers" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: req-wire-$(date +%s%N)" \
  -H "X-Correlation-ID: corr-wire-$(date +%s%N)" \
  -d '{"name":"Wire Test Provider","type":"clinic"}' \
  2>/dev/null) || RESPONSE=$'\n000'

HTTP_STATUS=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')

assert_status "Missing Idempotency-Key returns 400" "400" "$HTTP_STATUS"

if echo "$BODY" | jq -r '.code' 2>/dev/null | grep -q "IDEMPOTENCY_KEY_REQUIRED"; then
  echo -e "${GREEN}[PASS]${NC} Error code is IDEMPOTENCY_KEY_REQUIRED"
  PASS_COUNT=$((PASS_COUNT + 1))
else
  echo -e "${RED}[FAIL]${NC} Expected error code IDEMPOTENCY_KEY_REQUIRED, got: $(echo "$BODY" | jq -r '.code' 2>/dev/null || echo 'parse error')"
  FAIL_COUNT=$((FAIL_COUNT + 1))
fi

# --------------------------------------------------------------------------
# Test 3: Same Idempotency-Key + same body → replay (same status + body)
# --------------------------------------------------------------------------
echo ""
echo -e "${BOLD}--- Test 3: Idempotency replay (same key + same body) ---${NC}"
IDEM_KEY="idem-wire-replay-$(date +%s%N)"
PROVIDER_BODY='{"name":"Wire Replay Provider","type":"clinic"}'

# First request
RESPONSE1=$(curl -sf -w "\n%{http_code}" -X POST "${VARAPI_URL}/api/v1/providers" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: req-wire-$(date +%s%N)" \
  -H "X-Correlation-ID: corr-wire-$(date +%s%N)" \
  -H "Idempotency-Key: ${IDEM_KEY}" \
  -d "$PROVIDER_BODY" \
  2>/dev/null) || RESPONSE1=$'\n000'

STATUS1=$(echo "$RESPONSE1" | tail -1)
BODY1=$(echo "$RESPONSE1" | sed '$d')
echo "  First request: HTTP $STATUS1"

# Second request (replay with same key + same body)
RESPONSE2=$(curl -sf -w "\n%{http_code}" -X POST "${VARAPI_URL}/api/v1/providers" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: req-wire-$(date +%s%N)" \
  -H "X-Correlation-ID: corr-wire-$(date +%s%N)" \
  -H "Idempotency-Key: ${IDEM_KEY}" \
  -d "$PROVIDER_BODY" \
  2>/dev/null) || RESPONSE2=$'\n000'

STATUS2=$(echo "$RESPONSE2" | tail -1)
BODY2=$(echo "$RESPONSE2" | sed '$d')
echo "  Replay request: HTTP $STATUS2"

if [[ "$STATUS1" == "$STATUS2" ]]; then
  echo -e "${GREEN}[PASS]${NC} Replay returned same status ($STATUS1 == $STATUS2)"
  PASS_COUNT=$((PASS_COUNT + 1))
else
  echo -e "${RED}[FAIL]${NC} Replay status mismatch ($STATUS1 != $STATUS2)"
  FAIL_COUNT=$((FAIL_COUNT + 1))
fi

if [[ "$BODY1" == "$BODY2" ]]; then
  echo -e "${GREEN}[PASS]${NC} Replay returned same body"
  PASS_COUNT=$((PASS_COUNT + 1))
else
  echo -e "${RED}[FAIL]${NC} Replay body mismatch"
  FAIL_COUNT=$((FAIL_COUNT + 1))
fi

# --------------------------------------------------------------------------
# Test 4: Same Idempotency-Key + different body → 409 IDENTITY_CONFLICT
# --------------------------------------------------------------------------
echo ""
echo -e "${BOLD}--- Test 4: Same Idempotency-Key + different body → 409 ---${NC}"
DIFFERENT_BODY='{"name":"Wire Different Provider","type":"hospital"}'

RESPONSE3=$(curl -sf -w "\n%{http_code}" -X POST "${VARAPI_URL}/api/v1/providers" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: req-wire-$(date +%s%N)" \
  -H "X-Correlation-ID: corr-wire-$(date +%s%N)" \
  -H "Idempotency-Key: ${IDEM_KEY}" \
  -d "$DIFFERENT_BODY" \
  2>/dev/null) || RESPONSE3=$'\n000'

STATUS3=$(echo "$RESPONSE3" | tail -1)
BODY3=$(echo "$RESPONSE3" | sed '$d')

assert_status "Same key + different body returns 409" "409" "$STATUS3"

if echo "$BODY3" | jq -r '.code' 2>/dev/null | grep -q "IDENTITY_CONFLICT"; then
  echo -e "${GREEN}[PASS]${NC} Error code is IDENTITY_CONFLICT"
  PASS_COUNT=$((PASS_COUNT + 1))
else
  echo -e "${RED}[FAIL]${NC} Expected error code IDENTITY_CONFLICT, got: $(echo "$BODY3" | jq -r '.code' 2>/dev/null || echo 'parse error')"
  FAIL_COUNT=$((FAIL_COUNT + 1))
fi

# --------------------------------------------------------------------------
# Test 5: TSHEPO federation with X-Pod-ID=private-harare → 403
# --------------------------------------------------------------------------
echo ""
echo -e "${BOLD}--- Test 5: Federation authority violation → 403 ---${NC}"
RESPONSE4=$(curl -sf -w "\n%{http_code}" -X GET "${TSHEPO_URL}/api/v1/federation/peers" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: private-harare" \
  -H "X-Request-ID: req-wire-$(date +%s%N)" \
  -H "X-Correlation-ID: corr-wire-$(date +%s%N)" \
  2>/dev/null) || RESPONSE4=$'\n000'

STATUS4=$(echo "$RESPONSE4" | tail -1)
BODY4=$(echo "$RESPONSE4" | sed '$d')

assert_status "Federation with private-harare returns 403" "403" "$STATUS4"

if echo "$BODY4" | jq -r '.code' 2>/dev/null | grep -q "FEDERATION_AUTHORITY_VIOLATION"; then
  echo -e "${GREEN}[PASS]${NC} Error code is FEDERATION_AUTHORITY_VIOLATION"
  PASS_COUNT=$((PASS_COUNT + 1))
else
  echo -e "${RED}[FAIL]${NC} Expected error code FEDERATION_AUTHORITY_VIOLATION, got: $(echo "$BODY4" | jq -r '.code' 2>/dev/null || echo 'parse error')"
  FAIL_COUNT=$((FAIL_COUNT + 1))
fi

# Summary
print_summary

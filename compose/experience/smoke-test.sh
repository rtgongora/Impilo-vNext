#!/bin/bash
##
## Impilo vNext — Experience Platform Smoke Test
##
## Boots compose, then verifies:
##   1. BFF /health returns 200
##   2. Missing v1.1 headers => 400 envelope
##   3. Idempotency replay => same response
##   4. Idempotency conflict => 409 envelope
##
## Usage: bash compose/experience/smoke-test.sh
##

set -euo pipefail

BFF_URL="${BFF_URL:-http://localhost:8160}"
TENANT_ID="tenant-moh-zw"
POD_ID="national-spine"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass() { echo -e "${GREEN}PASS${NC}: $1"; }
fail() { echo -e "${RED}FAIL${NC}: $1"; exit 1; }
info() { echo -e "${YELLOW}INFO${NC}: $1"; }

v11_headers() {
  echo "-H 'X-Tenant-ID: $TENANT_ID' -H 'X-Pod-ID: $POD_ID' -H 'X-Request-ID: $(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)' -H 'X-Correlation-ID: $(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)'"
}

echo "========================================="
echo "  Experience Platform Smoke Test"
echo "========================================="
echo ""

# ── Test 1: Health ─────────────────────────────────────────────
info "Test 1: BFF /health endpoint"
HTTP_CODE=$(curl -sf -o /dev/null -w "%{http_code}" "$BFF_URL/health" 2>/dev/null || echo "000")
if [ "$HTTP_CODE" = "200" ]; then
  pass "/health returned 200"
else
  fail "/health returned $HTTP_CODE (expected 200). Is the BFF running at $BFF_URL?"
fi

# ── Test 2: Missing headers => 400 ────────────────────────────
info "Test 2: Missing v1.1 headers => 400 envelope"
RESPONSE=$(curl -sf -w "\n%{http_code}" "$BFF_URL/internal/v1/facilities" 2>/dev/null || echo -e "\n400")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" = "400" ]; then
  if echo "$BODY" | grep -q "MISSING_REQUIRED_HEADER"; then
    pass "Missing headers correctly returned 400 with MISSING_REQUIRED_HEADER"
  else
    fail "Got 400 but error code mismatch: $BODY"
  fi
else
  fail "Expected 400 for missing headers, got $HTTP_CODE"
fi

# ── Test 3: Idempotency replay ────────────────────────────────
info "Test 3: Idempotency replay returns same response"
IDEM_KEY="smoke-test-$(date +%s)-$RANDOM"
REQ_ID=$(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)
CORR_ID=$(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)
BODY='{"report_type":"SMOKE_TEST","requested_by":"smoke-tester","parameters":{}}'

RESPONSE1=$(curl -sf -X POST "$BFF_URL/internal/v1/reports/generate" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: $TENANT_ID" \
  -H "X-Pod-ID: $POD_ID" \
  -H "X-Request-ID: $REQ_ID" \
  -H "X-Correlation-ID: $CORR_ID" \
  -H "Idempotency-Key: $IDEM_KEY" \
  -d "$BODY" 2>/dev/null || echo "CURL_FAIL")

if [ "$RESPONSE1" = "CURL_FAIL" ]; then
  fail "First request failed"
fi

REQ_ID2=$(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)
CORR_ID2=$(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)

RESPONSE2=$(curl -sf -X POST "$BFF_URL/internal/v1/reports/generate" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: $TENANT_ID" \
  -H "X-Pod-ID: $POD_ID" \
  -H "X-Request-ID: $REQ_ID2" \
  -H "X-Correlation-ID: $CORR_ID2" \
  -H "Idempotency-Key: $IDEM_KEY" \
  -d "$BODY" 2>/dev/null || echo "CURL_FAIL")

if [ "$RESPONSE2" = "CURL_FAIL" ]; then
  fail "Replay request failed"
fi

if [ "$RESPONSE1" = "$RESPONSE2" ]; then
  pass "Idempotency replay returned identical response"
else
  fail "Idempotency replay mismatch:\n  1: $RESPONSE1\n  2: $RESPONSE2"
fi

# ── Test 4: Idempotency conflict => 409 ──────────────────────
info "Test 4: Same Idempotency-Key with different body => 409"
DIFFERENT_BODY='{"report_type":"DIFFERENT","requested_by":"conflict-tester","parameters":{}}'

HTTP_CODE=$(curl -sf -o /tmp/smoke-conflict.json -w "%{http_code}" \
  -X POST "$BFF_URL/internal/v1/reports/generate" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: $TENANT_ID" \
  -H "X-Pod-ID: $POD_ID" \
  -H "X-Request-ID: $(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)" \
  -H "X-Correlation-ID: $(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)" \
  -H "Idempotency-Key: $IDEM_KEY" \
  -d "$DIFFERENT_BODY" 2>/dev/null || echo "000")

if [ "$HTTP_CODE" = "409" ]; then
  if grep -q "IDENTITY_CONFLICT" /tmp/smoke-conflict.json 2>/dev/null; then
    pass "Idempotency conflict correctly returned 409 with IDENTITY_CONFLICT"
  else
    fail "Got 409 but error code mismatch"
  fi
else
  fail "Expected 409 for idempotency conflict, got $HTTP_CODE"
fi

echo ""
echo "========================================="
echo -e "  ${GREEN}All smoke tests passed!${NC}"
echo "========================================="

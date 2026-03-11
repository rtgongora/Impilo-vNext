#!/usr/bin/env bash
##
## Impilo vNext — Experience Platform Smoke Test
##
## Runs a comprehensive smoke test against a running Experience Platform.
## Tests cover: health, v1.1 headers, idempotency, golden path endpoints.
##
## Usage:
##   ./tools/dev/smoke.sh                           # Default: localhost:8160
##   BFF_URL=http://10.0.0.5:8160 ./tools/dev/smoke.sh
##
set -euo pipefail

BFF_URL="${BFF_URL:-http://localhost:8160}"
UI_URL="${UI_URL:-http://localhost:3020}"
TENANT_ID="moh-zw"
POD_ID="national"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

PASS_COUNT=0
FAIL_COUNT=0
SKIP_COUNT=0

pass() { echo -e "  ${GREEN}PASS${NC}: $1"; PASS_COUNT=$((PASS_COUNT + 1)); }
fail() { echo -e "  ${RED}FAIL${NC}: $1"; FAIL_COUNT=$((FAIL_COUNT + 1)); }
skip() { echo -e "  ${YELLOW}SKIP${NC}: $1"; SKIP_COUNT=$((SKIP_COUNT + 1)); }
info() { echo -e "${YELLOW}──${NC} $1"; }

req_id() { cat /proc/sys/kernel/random/uuid 2>/dev/null || python3 -c "import uuid; print(uuid.uuid4())" 2>/dev/null || echo "req-$(date +%s%N)"; }

v11_get() {
  local path="$1"
  curl -sf -w "\n%{http_code}" \
    -H "X-Tenant-ID: $TENANT_ID" \
    -H "X-Pod-ID: $POD_ID" \
    -H "X-Request-ID: $(req_id)" \
    -H "X-Correlation-ID: $(req_id)" \
    "$BFF_URL$path" 2>/dev/null
}

v11_post() {
  local path="$1"
  local body="$2"
  local idem_key="${3:-$(req_id)}"
  curl -sf -w "\n%{http_code}" \
    -X POST \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: $TENANT_ID" \
    -H "X-Pod-ID: $POD_ID" \
    -H "X-Request-ID: $(req_id)" \
    -H "X-Correlation-ID: $(req_id)" \
    -H "Idempotency-Key: $idem_key" \
    -d "$body" \
    "$BFF_URL$path" 2>/dev/null
}

echo "=============================================="
echo "  Impilo vNext — Experience Platform Smoke"
echo "=============================================="
echo ""

# ── Section 1: Infrastructure Health ─────────────────────────
info "Section 1: Infrastructure Health"

HTTP_CODE=$(curl -sf -o /dev/null -w "%{http_code}" "$BFF_URL/health" 2>/dev/null || echo "000")
if [ "$HTTP_CODE" = "200" ]; then
  pass "BFF /health => 200"
else
  fail "BFF /health => $HTTP_CODE (expected 200)"
fi

HTTP_CODE=$(curl -sf -o /dev/null -w "%{http_code}" "$UI_URL" 2>/dev/null || echo "000")
if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "307" ] || [ "$HTTP_CODE" = "308" ]; then
  pass "UI root => $HTTP_CODE"
else
  skip "UI not reachable at $UI_URL ($HTTP_CODE)"
fi
echo ""

# ── Section 2: v1.1 Header Enforcement ──────────────────────
info "Section 2: v1.1 Header Enforcement"

RESPONSE=$(curl -sf -w "\n%{http_code}" "$BFF_URL/internal/v1/facilities" 2>/dev/null || echo -e "\n400")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" = "400" ] && echo "$BODY" | grep -q "MISSING_REQUIRED_HEADER"; then
  pass "Missing headers => 400 MISSING_REQUIRED_HEADER"
else
  fail "Missing headers => $HTTP_CODE (expected 400)"
fi
echo ""

# ── Section 3: Read Endpoints ───────────────────────────────
info "Section 3: Read Endpoints (Golden Path Data)"

for endpoint in "/internal/v1/facilities" "/internal/v1/queue/entries" "/internal/v1/patients" "/internal/v1/shifts/current" "/internal/v1/admin/users"; do
  RESPONSE=$(v11_get "$endpoint")
  HTTP_CODE=$(echo "$RESPONSE" | tail -1)
  if [ "$HTTP_CODE" = "200" ]; then
    pass "GET $endpoint => 200"
  elif [ "$HTTP_CODE" = "404" ]; then
    skip "GET $endpoint => 404 (not implemented yet)"
  else
    fail "GET $endpoint => $HTTP_CODE"
  fi
done
echo ""

# ── Section 4: Command Endpoints + Idempotency ──────────────
info "Section 4: Command Endpoints + Idempotency"

IDEM_KEY="smoke-$(date +%s)-$RANDOM"
BODY='{"report_type":"SMOKE_TEST","requested_by":"smoke-tester","parameters":{}}'

RESPONSE1=$(v11_post "/internal/v1/reports/generate" "$BODY" "$IDEM_KEY")
HTTP_CODE1=$(echo "$RESPONSE1" | tail -1)
BODY1=$(echo "$RESPONSE1" | sed '$d')

if [ "$HTTP_CODE1" = "201" ] || [ "$HTTP_CODE1" = "200" ]; then
  pass "POST /internal/v1/reports/generate => $HTTP_CODE1"

  # Replay test
  RESPONSE2=$(v11_post "/internal/v1/reports/generate" "$BODY" "$IDEM_KEY")
  BODY2=$(echo "$RESPONSE2" | sed '$d')
  if [ "$BODY1" = "$BODY2" ]; then
    pass "Idempotency replay => identical response"
  else
    fail "Idempotency replay => response mismatch"
  fi

  # Conflict test
  DIFFERENT='{"report_type":"DIFFERENT","requested_by":"conflict","parameters":{}}'
  RESPONSE3=$(curl -sf -o /dev/null -w "%{http_code}" \
    -X POST \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: $TENANT_ID" \
    -H "X-Pod-ID: $POD_ID" \
    -H "X-Request-ID: $(req_id)" \
    -H "X-Correlation-ID: $(req_id)" \
    -H "Idempotency-Key: $IDEM_KEY" \
    -d "$DIFFERENT" \
    "$BFF_URL/internal/v1/reports/generate" 2>/dev/null || echo "000")
  if [ "$RESPONSE3" = "409" ]; then
    pass "Idempotency conflict => 409"
  else
    fail "Idempotency conflict => $RESPONSE3 (expected 409)"
  fi
else
  fail "POST /internal/v1/reports/generate => $HTTP_CODE1"
fi
echo ""

# ── Summary ──────────────────────────────────────────────────
echo "=============================================="
echo -e "  Results: ${GREEN}$PASS_COUNT passed${NC}, ${RED}$FAIL_COUNT failed${NC}, ${YELLOW}$SKIP_COUNT skipped${NC}"
echo "=============================================="

if [ "$FAIL_COUNT" -gt 0 ]; then
  exit 1
fi

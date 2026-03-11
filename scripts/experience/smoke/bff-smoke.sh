#!/usr/bin/env bash
# =============================================================================
# Impilo vNext — Experience BFF Smoke Tests
# =============================================================================
# Hits BFF health and command endpoints to verify basic connectivity and
# v1.1 error envelope structure on negative cases.
#
# Expects: BFF_BASE env var (default: http://localhost:8086)
# Exit code: 0 = all smoke tests pass, non-zero = failure
# =============================================================================
set -euo pipefail

BFF_BASE="${BFF_BASE:-http://localhost:8086}"

echo "  BFF Smoke Tests — targeting $BFF_BASE"
echo ""

PASS=0
FAIL=0

pass() { echo "    ✅ $1"; PASS=$((PASS + 1)); }
fail() { echo "    ❌ $1"; FAIL=$((FAIL + 1)); }

# ── 1. Actuator health ──
echo "  1. Actuator health endpoint"
HTTP_CODE=$(curl -sf -o /dev/null -w "%{http_code}" "${BFF_BASE}/actuator/health" 2>/dev/null) || HTTP_CODE="000"
if [ "$HTTP_CODE" = "200" ]; then
  pass "/actuator/health returns 200"
else
  fail "/actuator/health returned $HTTP_CODE (expected 200)"
fi

# ── 2. BFF custom health (if present) ──
echo "  2. BFF /internal/v1/health endpoint"
HTTP_CODE=$(curl -sf -o /dev/null -w "%{http_code}" "${BFF_BASE}/internal/v1/health" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: smoke-req-1" \
  -H "X-Correlation-ID: smoke-corr-1" 2>/dev/null) || HTTP_CODE="000"
if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "404" ]; then
  pass "/internal/v1/health returns $HTTP_CODE (acceptable)"
else
  fail "/internal/v1/health returned $HTTP_CODE"
fi

# ── 3. Command endpoint — negative case (missing headers → error envelope) ──
echo "  3. Command endpoint negative case (missing v1.1 headers)"
# Hit a command endpoint without required headers
BODY=$(curl -s -X POST "${BFF_BASE}/internal/v1/workspaces" \
  -H "Content-Type: application/json" \
  -d '{"name":"smoke-test"}' 2>/dev/null) || BODY=""

if echo "$BODY" | grep -q '"error"'; then
  pass "Error response contains 'error' field"
else
  # Try report-jobs endpoint as fallback
  BODY=$(curl -s -X POST "${BFF_BASE}/internal/v1/report-jobs" \
    -H "Content-Type: application/json" \
    -d '{"name":"smoke-test"}' 2>/dev/null) || BODY=""
  if echo "$BODY" | grep -q '"error"'; then
    pass "Error response contains 'error' field (report-jobs endpoint)"
  else
    fail "No error envelope found in response: $BODY"
  fi
fi

# Validate error envelope structure
if echo "$BODY" | grep -q '"code"' && echo "$BODY" | grep -q '"message"' && echo "$BODY" | grep -q '"request_id"' && echo "$BODY" | grep -q '"correlation_id"'; then
  pass "Error envelope has all required fields (code, message, request_id, correlation_id)"
else
  fail "Error envelope missing required fields: $BODY"
fi

# ── 4. Command endpoint — positive case with all headers ──
echo "  4. Command endpoint positive case (all v1.1 headers present)"
HTTP_CODE=$(curl -sf -o /dev/null -w "%{http_code}" -X POST "${BFF_BASE}/internal/v1/workspaces" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: smoke-req-2" \
  -H "X-Correlation-ID: smoke-corr-2" \
  -H "Idempotency-Key: smoke-idem-$(date +%s)" \
  -d '{"name":"smoke-workspace"}' 2>/dev/null) || HTTP_CODE="000"
if [ "$HTTP_CODE" = "201" ] || [ "$HTTP_CODE" = "200" ]; then
  pass "Command with all headers returns $HTTP_CODE"
else
  fail "Command with all headers returned $HTTP_CODE (expected 200/201)"
fi

# ── Summary ──
echo ""
TOTAL=$((PASS + FAIL))
if [ "$FAIL" -eq 0 ]; then
  echo "  BFF Smoke: All $TOTAL checks passed"
  exit 0
else
  echo "  BFF Smoke: $FAIL of $TOTAL checks failed"
  exit 1
fi

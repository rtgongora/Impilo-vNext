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

# ── Test 5: Fundo catalog via BFF (learning-service) ───────────
info "Test 5: Fundo v11 catalog via BFF"
FUNDO_CODE=$(curl -sf -o /tmp/smoke-fundo.json -w "%{http_code}" \
  "$BFF_URL/internal/v1/learning/v11/catalog?status=PUBLISHED&limit=5" \
  -H "X-Tenant-ID: 00000000-0000-4000-8000-000000000001" \
  -H "X-Pod-ID: $POD_ID" \
  -H "X-Request-ID: $(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)" \
  -H "X-Correlation-ID: $(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)" 2>/dev/null || echo "000")

if [ "$FUNDO_CODE" = "200" ] && grep -q "FUNDO-EHR-101\|Introduction to Impilo EHR" /tmp/smoke-fundo.json 2>/dev/null; then
  pass "Fundo catalog returned governed seed courses"
else
  fail "Fundo catalog check failed (HTTP $FUNDO_CODE). Is learning-service healthy on :8235?"
fi

# ── Test 6: Coverage plans via BFF (coverage-service) ────────────
info "Test 6: Coverage plans via BFF"
COV_CODE=$(curl -sf -o /tmp/smoke-coverage.json -w "%{http_code}" \
  "$BFF_URL/internal/v1/coverage/plans" \
  -H "X-Tenant-ID: 00000000-0000-4000-8000-000000000001" \
  -H "X-Pod-ID: $POD_ID" \
  -H "X-Request-ID: $(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)" \
  -H "X-Correlation-ID: $(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)" 2>/dev/null || echo "000")

if [ "$COV_CODE" = "200" ] && grep -q "COV-MOHCC-CORE\|MOHCC National Core Scheme" /tmp/smoke-coverage.json 2>/dev/null; then
  pass "Coverage plans returned governed seed schemes"
else
  fail "Coverage plans check failed (HTTP $COV_CODE). Is coverage-service healthy on :8140?"
fi

# ── Test 7: Simba wellness clubs via BFF (simba-service) ─────────
info "Test 7: Simba wellness clubs via BFF"
SIMBA_CODE=$(curl -sf -o /tmp/smoke-simba.json -w "%{http_code}" \
  "$BFF_URL/internal/v1/wellness/clubs" \
  -H "X-Tenant-ID: 00000000-0000-4000-8000-000000000001" \
  -H "X-Pod-ID: $POD_ID" \
  -H "X-Request-ID: $(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)" \
  -H "X-Correlation-ID: $(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)" 2>/dev/null || echo "000")

if [ "$SIMBA_CODE" = "200" ] && grep -q "Harare Morning Walkers\|Walking" /tmp/smoke-simba.json 2>/dev/null; then
  pass "Simba clubs returned governed seed catalogue"
else
  fail "Simba clubs check failed (HTTP $SIMBA_CODE). Is simba-service healthy on :8125?"
fi

# ── Test 8: PCT queue via BFF (pct-service) ─────────────────────
info "Test 8: PCT queue entries via BFF"
PCT_CODE=$(curl -sf -o /tmp/smoke-pct.json -w "%{http_code}" \
  "$BFF_URL/internal/v1/queue/entries?facility_id=f1000000-0000-0000-0000-000000000001" \
  -H "X-Tenant-ID: 00000000-0000-4000-8000-000000000001" \
  -H "X-Pod-ID: $POD_ID" \
  -H "X-Request-ID: $(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)" \
  -H "X-Correlation-ID: $(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)" 2>/dev/null || echo "000")

if [ "$PCT_CODE" = "200" ] && grep -q "Harare Central OPD\|CPID-ZW-00001" /tmp/smoke-pct.json 2>/dev/null; then
  pass "PCT queue returned governed seed entries"
else
  fail "PCT queue check failed (HTTP $PCT_CODE). Is pct-service healthy on :8088?"
fi

# ── Test 9: Inpatient admissions + ward rounds via BFF ───────────
info "Test 9: Inpatient admissions via BFF"
INP_CODE=$(curl -sf -o /tmp/smoke-inpatient.json -w "%{http_code}" \
  "$BFF_URL/internal/v1/inpatient/admissions" \
  -H "X-Tenant-ID: 00000000-0000-4000-8000-000000000001" \
  -H "X-Pod-ID: $POD_ID" \
  -H "X-Request-ID: $(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)" \
  -H "X-Correlation-ID: $(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)" 2>/dev/null || echo "000")

if [ "$INP_CODE" = "200" ] && grep -q "CPID-ZW-00001\|f2000000-0000-0000-0000-000000000001" /tmp/smoke-inpatient.json 2>/dev/null; then
  pass "Inpatient admissions returned governed seed row"
else
  fail "Inpatient admissions check failed (HTTP $INP_CODE). Is inpatient-service healthy on :8121?"
fi

ROUNDS_CODE=$(curl -sf -o /tmp/smoke-rounds.json -w "%{http_code}" \
  "$BFF_URL/internal/v1/inpatient/admissions/f2000000-0000-0000-0000-000000000001/ward-rounds" \
  -H "X-Tenant-ID: 00000000-0000-4000-8000-000000000001" \
  -H "X-Pod-ID: $POD_ID" \
  -H "X-Request-ID: $(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)" \
  -H "X-Correlation-ID: $(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)" 2>/dev/null || echo "000")

if [ "$ROUNDS_CODE" = "200" ] && grep -q "Dr. Tendai Mapfumo\|MORNING" /tmp/smoke-rounds.json 2>/dev/null; then
  pass "Inpatient ward rounds returned governed seed documentation"
else
  fail "Inpatient ward rounds check failed (HTTP $ROUNDS_CODE)"
fi

# ── Test 10: Telemedicine sessions via BFF ───────────────────────
info "Test 10: Telemedicine sessions via BFF"
TEL_CODE=$(curl -sf -o /tmp/smoke-telemed.json -w "%{http_code}" \
  "$BFF_URL/internal/v1/teleconsult/sessions?patientId=CPID-ZW-00001" \
  -H "X-Tenant-ID: 00000000-0000-4000-8000-000000000001" \
  -H "X-Pod-ID: $POD_ID" \
  -H "X-Request-ID: $(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)" \
  -H "X-Correlation-ID: $(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)" 2>/dev/null || echo "000")

if [ "$TEL_CODE" = "200" ] && grep -q "CPID-ZW-00001\|VIDEO\|SCHEDULED" /tmp/smoke-telemed.json 2>/dev/null; then
  pass "Telemedicine sessions returned governed seed session"
else
  fail "Telemedicine sessions check failed (HTTP $TEL_CODE). Is pct-service + rtc-gateway healthy?"
fi

RTC_CODE=$(curl -sf -o /tmp/smoke-rtc.json -w "%{http_code}" \
  "$BFF_URL/internal/v1/teleconsult/ops/rtc-health" \
  -H "X-Tenant-ID: 00000000-0000-4000-8000-000000000001" \
  -H "X-Pod-ID: $POD_ID" \
  -H "X-Request-ID: $(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)" \
  -H "X-Correlation-ID: $(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)" 2>/dev/null || echo "000")

if [ "$RTC_CODE" = "200" ]; then
  pass "RTC health probe reachable via BFF"
else
  fail "RTC health check failed (HTTP $RTC_CODE). Is rtc-gateway healthy on :8196?"
fi

# ── Test 11: Governed imaging studies via BFF (pacs-adapter) ─────
info "Test 11: Imaging studies via BFF"
IMG_CODE=$(curl -sf -o /tmp/smoke-imaging.json -w "%{http_code}" \
  "$BFF_URL/internal/v1/imaging/studies?patient_cpid=CPID-ZW-00001" \
  -H "X-Tenant-ID: 00000000-0000-4000-8000-000000000001" \
  -H "X-Pod-ID: $POD_ID" \
  -H "X-Request-ID: $(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)" \
  -H "X-Correlation-ID: $(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)" 2>/dev/null || echo "000")

if [ "$IMG_CODE" = "200" ] && grep -q "Chest X-ray\|CPID-ZW-00001\|1.2.840.IMPILO" /tmp/smoke-imaging.json 2>/dev/null; then
  pass "Imaging studies returned governed seed study"
else
  fail "Imaging studies check failed (HTTP $IMG_CODE). Is pacs-adapter-service healthy on :8113?"
fi

echo ""
echo "========================================="
echo -e "  ${GREEN}All smoke tests passed!${NC}"
echo "========================================="

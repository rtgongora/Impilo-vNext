#!/usr/bin/env bash
##
## Impilo vNext — Golden Path Smoke Test
##
## Exercises all 6 golden paths from 06_golden_paths.md against a running BFF.
##
## Path A: Email Login
## Path B: Provider ID + Biometric Login
## Path C: Queue → Encounter → Close
## Path D: Admin → TSHEPO Governance
## Path E: Marketplace Integration
## Path F: Registry Data Management
##
## Usage:
##   ./tools/dev/golden-path-smoke.sh
##   BFF_URL=http://10.0.0.5:8160 ./tools/dev/golden-path-smoke.sh
##
set -euo pipefail

BFF_URL="${BFF_URL:-http://localhost:8160}"
TENANT="moh-zw"
POD="national"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

PASS=0
FAIL=0

pass() { echo -e "  ${GREEN}PASS${NC}: $1"; PASS=$((PASS + 1)); }
fail() { echo -e "  ${RED}FAIL${NC}: $1"; FAIL=$((FAIL + 1)); }
path_header() { echo -e "\n${BLUE}── $1 ──${NC}"; }

req_id() { cat /proc/sys/kernel/random/uuid 2>/dev/null || echo "req-$(date +%s%N)"; }

v11_get() {
  curl -sf -w "\n%{http_code}" \
    -H "X-Tenant-ID: $TENANT" -H "X-Pod-ID: $POD" \
    -H "X-Request-ID: $(req_id)" -H "X-Correlation-ID: $(req_id)" \
    "$BFF_URL$1" 2>/dev/null
}

v11_post() {
  curl -sf -w "\n%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: $TENANT" -H "X-Pod-ID: $POD" \
    -H "X-Request-ID: $(req_id)" -H "X-Correlation-ID: $(req_id)" \
    -H "Idempotency-Key: $(req_id)" \
    -d "$2" "$BFF_URL$1" 2>/dev/null
}

get_status() { echo "$1" | tail -1; }
get_body() { echo "$1" | sed '$d'; }

echo "=============================================="
echo "  Golden Path Smoke Test (06_golden_paths.md)"
echo "=============================================="

# ── Path A: Email Login ──────────────────────────────────────
path_header "Path A: Email Login"
RESP=$(v11_post "/internal/v1/auth/login" '{"method":"email","identifier":"test@mohcc.gov.zw"}')
CODE=$(get_status "$RESP")
BODY=$(get_body "$RESP")
if [ "$CODE" = "200" ] && echo "$BODY" | grep -q '"token"'; then
  pass "POST /auth/login (email) => 200 with token"
else
  fail "POST /auth/login (email) => $CODE"
fi

# ── Path B: Provider ID + Biometric ──────────────────────────
path_header "Path B: Provider ID + Biometric Login"
RESP=$(v11_post "/internal/v1/auth/login" '{"method":"provider_id","identifier":"PROV-ZW-001"}')
CODE=$(get_status "$RESP")
if [ "$CODE" = "200" ]; then
  pass "POST /auth/login (provider_id) => 200"
else
  fail "POST /auth/login (provider_id) => $CODE"
fi

# ── Path C: Queue → Encounter → Close ────────────────────────
path_header "Path C: Queue → Encounter → Close"

# C1: List queue
RESP=$(v11_get "/internal/v1/queue/entries")
CODE=$(get_status "$RESP")
if [ "$CODE" = "200" ]; then pass "GET /queue/entries => 200"; else fail "GET /queue/entries => $CODE"; fi

# C2: List patients
RESP=$(v11_get "/internal/v1/patients")
CODE=$(get_status "$RESP")
if [ "$CODE" = "200" ]; then pass "GET /patients => 200"; else fail "GET /patients => $CODE"; fi

# C3: Create encounter
RESP=$(v11_post "/internal/v1/encounters" '{"patient_id":"a1000000-0000-0000-0000-000000000001","facility_id":"f1000000-0000-0000-0000-000000000001","encounter_type":"OUTPATIENT","chief_complaint":"Fever"}')
CODE=$(get_status "$RESP")
BODY=$(get_body "$RESP")
if [ "$CODE" = "201" ]; then
  pass "POST /encounters => 201"
  ENC_ID=$(echo "$BODY" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
  if [ -n "$ENC_ID" ]; then
    # C4: Close encounter
    RESP=$(v11_post "/internal/v1/encounters/$ENC_ID/close" '{"diagnosis":"Malaria"}')
    CODE=$(get_status "$RESP")
    if [ "$CODE" = "200" ]; then pass "POST /encounters/$ENC_ID/close => 200"; else fail "POST /encounters/close => $CODE"; fi
  fi
else
  fail "POST /encounters => $CODE"
fi

# ── Path D: Admin → TSHEPO Governance ────────────────────────
path_header "Path D: Admin → TSHEPO Governance"

RESP=$(v11_get "/internal/v1/admin/users")
CODE=$(get_status "$RESP")
if [ "$CODE" = "200" ]; then pass "GET /admin/users => 200"; else fail "GET /admin/users => $CODE"; fi

RESP=$(v11_get "/internal/v1/admin/audit")
CODE=$(get_status "$RESP")
if [ "$CODE" = "200" ]; then pass "GET /admin/audit => 200"; else fail "GET /admin/audit => $CODE"; fi

# ── Path E: Marketplace ──────────────────────────────────────
path_header "Path E: Marketplace Integration"

RESP=$(v11_post "/internal/v1/marketplace/orders" "{\"facility_id\":\"f1000000-0000-0000-0000-000000000001\",\"order_number\":\"ORD-SMOKE-$(date +%s)\",\"items\":[],\"ordered_by\":\"smoke-tester\"}")
CODE=$(get_status "$RESP")
if [ "$CODE" = "201" ]; then pass "POST /marketplace/orders => 201"; else fail "POST /marketplace/orders => $CODE"; fi

RESP=$(v11_get "/internal/v1/marketplace/orders")
CODE=$(get_status "$RESP")
if [ "$CODE" = "200" ]; then pass "GET /marketplace/orders => 200"; else fail "GET /marketplace/orders => $CODE"; fi

# ── Path F: Registry ─────────────────────────────────────────
path_header "Path F: Registry Data Management"

RESP=$(v11_get "/internal/v1/registry/providers")
CODE=$(get_status "$RESP")
if [ "$CODE" = "200" ]; then pass "GET /registry/providers => 200"; else fail "GET /registry/providers => $CODE"; fi

RESP=$(v11_get "/internal/v1/registry/facilities")
CODE=$(get_status "$RESP")
if [ "$CODE" = "200" ]; then pass "GET /registry/facilities => 200"; else fail "GET /registry/facilities => $CODE"; fi

RESP=$(v11_get "/internal/v1/facilities")
CODE=$(get_status "$RESP")
if [ "$CODE" = "200" ]; then pass "GET /facilities => 200"; else fail "GET /facilities => $CODE"; fi

# ── Summary ──────────────────────────────────────────────────
echo ""
echo "=============================================="
echo -e "  Results: ${GREEN}$PASS passed${NC}, ${RED}$FAIL failed${NC}"
echo "=============================================="
[ "$FAIL" -gt 0 ] && exit 1 || exit 0

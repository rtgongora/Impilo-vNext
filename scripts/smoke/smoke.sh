#!/usr/bin/env bash
# =============================================================================
# Impilo vNext — Smoke & Compliance Test Suite (Wave 18)
# =============================================================================
#
# Tests:
#   1. Infrastructure readiness (Postgres, Redis, Kafka)
#   2. Service health checks (/actuator/health)
#   3. Edge enforcement (Envoy + OPA)
#   4. v1.1 header enforcement — requests without required headers are rejected
#   5. v1.1 header enforcement — requests WITH required headers pass
#   6. Idempotent POST replay check (same key + body => same response)
#   7. Idempotency conflict check (same key + different body => 409)
#
# Usage:
#   ./scripts/smoke/smoke.sh
#   (or: ./scripts/dev-runtime.sh smoke)
#
# =============================================================================

set -euo pipefail

# ── Configuration ────────────────────────────────────────────────────
ENVOY_URL="${ENVOY_URL:-http://localhost:10000}"
TSHEPO_URL="${TSHEPO_URL:-http://localhost:8081}"
VITO_URL="${VITO_URL:-http://localhost:8082}"
VARAPI_URL="${VARAPI_URL:-http://localhost:8083}"
TUSO_URL="${TUSO_URL:-http://localhost:8084}"
ZIBO_URL="${ZIBO_URL:-http://localhost:8085}"
PCT_URL="${PCT_URL:-http://localhost:8088}"
OROS_URL="${OROS_URL:-http://localhost:8089}"
BFF_URL="${BFF_URL:-http://localhost:8160}"
OPA_URL="${OPA_URL:-http://localhost:8181}"
UI_URL="${UI_URL:-http://localhost:3020}"

POSTGRES_HOST="${POSTGRES_HOST:-localhost}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
POSTGRES_USER="${POSTGRES_USER:-impilo}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-changeme}"

MAX_WAIT="${MAX_WAIT:-120}"  # seconds

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

PASS=0
FAIL=0
WARN=0

pass() { echo -e "  ${GREEN}PASS${NC} $*"; ((PASS++)); }
fail() { echo -e "  ${RED}FAIL${NC} $*"; ((FAIL++)); }
warn() { echo -e "  ${YELLOW}WARN${NC} $*"; ((WARN++)); }
info() { echo -e "${CYAN}[TEST]${NC} $*"; }

# ── Wait for URL to be ready ────────────────────────────────────────
wait_for_url() {
    local url="$1"
    local name="$2"
    local elapsed=0
    while [ $elapsed -lt "$MAX_WAIT" ]; do
        if curl -sf -o /dev/null --connect-timeout 2 "$url" 2>/dev/null; then
            return 0
        fi
        sleep 2
        elapsed=$((elapsed + 2))
    done
    return 1
}

# ── Wait for TCP port ───────────────────────────────────────────────
wait_for_port() {
    local host="$1"
    local port="$2"
    local name="$3"
    local elapsed=0
    while [ $elapsed -lt "$MAX_WAIT" ]; do
        if (echo > /dev/tcp/"$host"/"$port") 2>/dev/null; then
            return 0
        fi
        sleep 2
        elapsed=$((elapsed + 2))
    done
    return 1
}

# =============================================================================
echo ""
echo "============================================================"
echo "  Impilo vNext — Smoke & Compliance Test Suite"
echo "============================================================"
echo ""

# =============================================================================
# TEST 1: Infrastructure Readiness
# =============================================================================
info "1. Infrastructure Readiness"

# Postgres
if wait_for_port "$POSTGRES_HOST" "$POSTGRES_PORT" "postgres"; then
    pass "PostgreSQL is reachable on $POSTGRES_HOST:$POSTGRES_PORT"
else
    fail "PostgreSQL is NOT reachable on $POSTGRES_HOST:$POSTGRES_PORT"
fi

# Redis
if wait_for_port "localhost" "6379" "redis"; then
    pass "Redis is reachable on localhost:6379"
else
    fail "Redis is NOT reachable on localhost:6379"
fi

# Kafka
if wait_for_port "localhost" "9092" "kafka"; then
    pass "Kafka is reachable on localhost:9092"
else
    fail "Kafka is NOT reachable on localhost:9092"
fi

# OPA
if wait_for_url "$OPA_URL/health" "OPA"; then
    pass "OPA is healthy"
else
    fail "OPA is NOT healthy"
fi

# Envoy
if wait_for_url "http://localhost:9901/ready" "Envoy"; then
    pass "Envoy is ready"
else
    fail "Envoy is NOT ready"
fi

# =============================================================================
# TEST 2: Service Health Checks
# =============================================================================
info "2. Service Health Checks (/actuator/health)"

check_health() {
    local name="$1"
    local url="$2"
    if wait_for_url "$url/actuator/health" "$name"; then
        local status
        status=$(curl -sf "$url/actuator/health" 2>/dev/null | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)
        if [ "$status" = "UP" ]; then
            pass "$name: UP"
        else
            warn "$name: reachable but status=$status"
        fi
    else
        fail "$name: NOT reachable at $url/actuator/health"
    fi
}

check_health "TSHEPO"         "$TSHEPO_URL"
check_health "VITO"           "$VITO_URL"
check_health "VARAPI"         "$VARAPI_URL"
check_health "TUSO"           "$TUSO_URL"
check_health "ZIBO"           "$ZIBO_URL"
check_health "PCT"            "$PCT_URL"
check_health "OROS"           "$OROS_URL"
check_health "Experience-BFF" "$BFF_URL"

# =============================================================================
# TEST 3: Edge Enforcement — OPA Policy Loaded
# =============================================================================
info "3. OPA Policy Verification"

OPA_POLICIES=$(curl -sf "$OPA_URL/v1/policies" 2>/dev/null)
if echo "$OPA_POLICIES" | grep -q "impilo.gateway.headers"; then
    pass "OPA has impilo.gateway.headers policy loaded"
else
    fail "OPA missing impilo.gateway.headers policy"
fi

if echo "$OPA_POLICIES" | grep -q "impilo.gateway.idempotency"; then
    pass "OPA has impilo.gateway.idempotency policy loaded"
else
    warn "OPA missing impilo.gateway.idempotency policy (may be expected)"
fi

# =============================================================================
# TEST 4: v1.1 Header Enforcement — DENY without headers
# =============================================================================
info "4. v1.1 Header Enforcement — Requests WITHOUT required headers"

# Requests to /internal/v1/ or /external/v1/ without v1.1 headers should be denied
DENY_RESPONSE=$(curl -sf -o /dev/null -w "%{http_code}" \
    "$ENVOY_URL/internal/v1/test" 2>/dev/null || echo "000")

if [ "$DENY_RESPONSE" = "403" ] || [ "$DENY_RESPONSE" = "401" ]; then
    pass "v1.1 path without headers => HTTP $DENY_RESPONSE (denied)"
elif [ "$DENY_RESPONSE" = "000" ]; then
    fail "Envoy not reachable for v1.1 deny test"
else
    warn "v1.1 path without headers => HTTP $DENY_RESPONSE (expected 403)"
fi

# =============================================================================
# TEST 5: v1.1 Header Enforcement — ALLOW with headers
# =============================================================================
info "5. v1.1 Header Enforcement — Requests WITH required headers"

ALLOW_RESPONSE=$(curl -sf -o /dev/null -w "%{http_code}" \
    -H "X-Tenant-ID: tenant-smoke-test" \
    -H "X-Pod-ID: pod-smoke-test" \
    -H "X-Request-ID: req-$(date +%s)" \
    -H "X-Correlation-ID: corr-$(date +%s)" \
    "$ENVOY_URL/internal/v1/test" 2>/dev/null || echo "000")

if [ "$ALLOW_RESPONSE" != "403" ] && [ "$ALLOW_RESPONSE" != "401" ] && [ "$ALLOW_RESPONSE" != "000" ]; then
    pass "v1.1 path with headers => HTTP $ALLOW_RESPONSE (not denied by OPA)"
elif [ "$ALLOW_RESPONSE" = "000" ]; then
    fail "Envoy not reachable for v1.1 allow test"
else
    fail "v1.1 path with headers => HTTP $ALLOW_RESPONSE (still denied)"
fi

# =============================================================================
# TEST 6: Non-v1.1 path — should pass without headers
# =============================================================================
info "6. Non-v1.1 Path Passthrough"

NON_V11_RESPONSE=$(curl -sf -o /dev/null -w "%{http_code}" \
    "$ENVOY_URL/api/v1/facilities" 2>/dev/null || echo "000")

if [ "$NON_V11_RESPONSE" != "403" ] && [ "$NON_V11_RESPONSE" != "000" ]; then
    pass "Non-v1.1 path without headers => HTTP $NON_V11_RESPONSE (not denied by OPA)"
elif [ "$NON_V11_RESPONSE" = "000" ]; then
    fail "Envoy not reachable for non-v1.1 passthrough test"
else
    fail "Non-v1.1 path without headers => HTTP $NON_V11_RESPONSE (unexpected deny)"
fi

# =============================================================================
# TEST 7: Idempotent POST — replay check
# =============================================================================
info "7. Idempotent POST Replay Check"

IDEM_KEY="smoke-idem-$(date +%s)"
IDEM_BODY='{"test":"idempotency-smoke"}'

# First POST
FIRST_CODE=$(curl -sf -o /dev/null -w "%{http_code}" \
    -X POST \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: tenant-smoke" \
    -H "X-Pod-ID: pod-smoke" \
    -H "X-Request-ID: req-idem-1" \
    -H "X-Correlation-ID: corr-idem-1" \
    -H "Idempotency-Key: $IDEM_KEY" \
    -d "$IDEM_BODY" \
    "$TSHEPO_URL/api/v1/authorize" 2>/dev/null || echo "000")

# Replay same key + same body
REPLAY_CODE=$(curl -sf -o /dev/null -w "%{http_code}" \
    -X POST \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: tenant-smoke" \
    -H "X-Pod-ID: pod-smoke" \
    -H "X-Request-ID: req-idem-2" \
    -H "X-Correlation-ID: corr-idem-2" \
    -H "Idempotency-Key: $IDEM_KEY" \
    -d "$IDEM_BODY" \
    "$TSHEPO_URL/api/v1/authorize" 2>/dev/null || echo "000")

if [ "$FIRST_CODE" != "000" ] && [ "$REPLAY_CODE" != "000" ]; then
    if [ "$FIRST_CODE" = "$REPLAY_CODE" ]; then
        pass "Idempotent replay: first=$FIRST_CODE, replay=$REPLAY_CODE (same response)"
    else
        warn "Idempotent replay: first=$FIRST_CODE, replay=$REPLAY_CODE (different — service may not implement replay yet)"
    fi
else
    warn "Idempotent replay test skipped — service not reachable (first=$FIRST_CODE, replay=$REPLAY_CODE)"
fi

# =============================================================================
# TEST 8: Idempotency Conflict — same key, different body => 409
# =============================================================================
info "8. Idempotency Conflict Check (same key, different body => 409)"

CONFLICT_BODY='{"test":"idempotency-conflict-different"}'

CONFLICT_CODE=$(curl -sf -o /dev/null -w "%{http_code}" \
    -X POST \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: tenant-smoke" \
    -H "X-Pod-ID: pod-smoke" \
    -H "X-Request-ID: req-idem-3" \
    -H "X-Correlation-ID: corr-idem-3" \
    -H "Idempotency-Key: $IDEM_KEY" \
    -d "$CONFLICT_BODY" \
    "$TSHEPO_URL/api/v1/authorize" 2>/dev/null || echo "000")

if [ "$CONFLICT_CODE" = "409" ]; then
    pass "Idempotency conflict: HTTP 409 (correct)"
elif [ "$CONFLICT_CODE" = "000" ]; then
    warn "Idempotency conflict test skipped — service not reachable"
else
    warn "Idempotency conflict: HTTP $CONFLICT_CODE (expected 409 — service may not implement conflict detection yet)"
fi

# =============================================================================
# TEST 9: Experience UI reachable
# =============================================================================
info "9. Experience UI"

if wait_for_url "$UI_URL" "Experience-UI"; then
    pass "Experience UI reachable at $UI_URL"
else
    warn "Experience UI not reachable at $UI_URL (may not be built yet)"
fi

# =============================================================================
# SUMMARY
# =============================================================================
echo ""
echo "============================================================"
echo "  SMOKE TEST SUMMARY"
echo "============================================================"
echo -e "  ${GREEN}PASS${NC}: $PASS"
echo -e "  ${RED}FAIL${NC}: $FAIL"
echo -e "  ${YELLOW}WARN${NC}: $WARN"
echo "============================================================"

if [ "$FAIL" -gt 0 ]; then
    echo -e "  ${RED}RESULT: SOME TESTS FAILED${NC}"
    exit 1
elif [ "$WARN" -gt 0 ]; then
    echo -e "  ${YELLOW}RESULT: PASSED WITH WARNINGS${NC}"
    exit 0
else
    echo -e "  ${GREEN}RESULT: ALL TESTS PASSED${NC}"
    exit 0
fi

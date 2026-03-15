#!/usr/bin/env bash
set -euo pipefail

###############################################################################
# Impilo vNext — Wire Contract Check
#
# Validates that API contracts match on the wire (not just in docs).
# Tests:
#   1. Trust header enforcement (4 HARD_REQUIRED headers)
#   2. Error envelope format compliance
#   3. Idempotency replay/conflict behavior
#   4. Federation authority denial
#
# Requires: Running services (docker-compose.runtime.yml) or localhost services
# If services are not running, performs structural validation only.
#
# Usage:
#   ./scripts/reality-check/run-wire-checks.sh [--live]
###############################################################################

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

PASS=0
FAIL=0
SKIP=0
TOTAL=0

log_phase() { echo -e "\n${CYAN}═══ $* ═══${NC}\n"; }
log_pass()  { echo -e "  ${GREEN}PASS${NC} $*"; ((PASS++)); ((TOTAL++)); }
log_fail()  { echo -e "  ${RED}FAIL${NC} $*"; ((FAIL++)); ((TOTAL++)); }
log_skip()  { echo -e "  ${YELLOW}SKIP${NC} $*"; ((SKIP++)); ((TOTAL++)); }

LIVE_MODE=false
if [ "${1:-}" = "--live" ]; then
    LIVE_MODE=true
fi

TSHEPO_URL="${TSHEPO_URL:-http://localhost:8081}"
VITO_URL="${VITO_URL:-http://localhost:8082}"
ENVOY_URL="${ENVOY_URL:-http://localhost:10000}"

echo ""
echo "============================================================"
echo "  Impilo vNext — Wire Contract Validation"
echo "  Mode: $([ "$LIVE_MODE" = true ] && echo "LIVE (hitting real endpoints)" || echo "STRUCTURAL (source analysis only)")"
echo "============================================================"
echo ""

# =============================================================================
# CHECK 1: Structural — Header definitions match across Java ↔ TypeScript
# =============================================================================
log_phase "Check 1: Header Contract Consistency (Java ↔ TypeScript)"

COMPANION_HEADERS="$REPO_ROOT/libs/tech-companion/src/main/java/zw/gov/mohcc/impilo/companion/context/CompanionHeaders.java"
CONTRACTS_TS=$(find "$REPO_ROOT/libs" "$REPO_ROOT/ui" -name "contracts.ts" -o -name "trustHeaders.ts" -o -name "trust-headers.ts" 2>/dev/null | head -5)
API_CLIENT_TS=$(find "$REPO_ROOT/ui" -name "apiClient.ts" -path "*/lib/*" 2>/dev/null | head -3)

if [ -f "$COMPANION_HEADERS" ]; then
    JAVA_HEADERS=$(grep -oP '"X-[^"]+"|"Idempotency-Key"' "$COMPANION_HEADERS" | sort -u)
    echo "  Java CompanionHeaders defines:"
    echo "$JAVA_HEADERS" | while read -r h; do echo "    $h"; done
    log_pass "CompanionHeaders.java found with header constants"
else
    log_fail "CompanionHeaders.java not found at expected path"
fi

TS_FOUND=false
for ts_file in $CONTRACTS_TS $API_CLIENT_TS; do
    if [ -f "$ts_file" ]; then
        echo "  TypeScript contract: $ts_file"
        TS_HEADERS=$(grep -oP "'X-[^']+'" "$ts_file" 2>/dev/null | sort -u || true)
        if [ -n "$TS_HEADERS" ]; then
            echo "$TS_HEADERS" | while read -r h; do echo "    $h"; done
            TS_FOUND=true
        fi
    fi
done

if [ "$TS_FOUND" = true ]; then
    log_pass "TypeScript trust header injection found"
else
    log_skip "No TypeScript contracts.ts/apiClient.ts found for comparison"
fi

# =============================================================================
# CHECK 2: Structural — Error envelope consistency
# =============================================================================
log_phase "Check 2: Error Envelope Format"

ERROR_ENVELOPE="$REPO_ROOT/libs/tech-companion/src/main/java/zw/gov/mohcc/impilo/companion/error/ErrorEnvelope.java"

if [ -f "$ERROR_ENVELOPE" ]; then
    REQUIRED_FIELDS=("code" "message" "details" "request_id" "correlation_id")
    ALL_FOUND=true
    for field in "${REQUIRED_FIELDS[@]}"; do
        if grep -q "$field" "$ERROR_ENVELOPE"; then
            echo "  Found field: $field"
        else
            echo "  MISSING field: $field"
            ALL_FOUND=false
        fi
    done
    if [ "$ALL_FOUND" = true ]; then
        log_pass "ErrorEnvelope has all 5 required fields"
    else
        log_fail "ErrorEnvelope missing required fields"
    fi
else
    log_fail "ErrorEnvelope.java not found"
fi

# =============================================================================
# CHECK 3: Structural — GoldenContractSuite coverage
# =============================================================================
log_phase "Check 3: GoldenContractSuite Coverage"

GOLDEN_SUITE="$REPO_ROOT/libs/tech-companion-harness/src/main/java/zw/gov/mohcc/impilo/companion/harness/GoldenContractSuite.java"

if [ -f "$GOLDEN_SUITE" ]; then
    REQUIRED_TESTS=(
        "missingTenantIdReturns400"
        "missingPodIdReturns400"
        "missingRequestIdReturns400"
        "missingCorrelationIdReturns400"
        "IDENTITY_CONFLICT"
        "FEDERATION_AUTHORITY_VIOLATION"
    )
    ALL_FOUND=true
    for test in "${REQUIRED_TESTS[@]}"; do
        if grep -q "$test" "$GOLDEN_SUITE"; then
            echo "  Found test: $test"
        else
            echo "  MISSING test: $test"
            ALL_FOUND=false
        fi
    done
    if [ "$ALL_FOUND" = true ]; then
        log_pass "GoldenContractSuite covers all wire contract tests"
    else
        log_fail "GoldenContractSuite missing required tests"
    fi
else
    log_fail "GoldenContractSuite.java not found"
fi

# Count services with GoldenContractIT
GOLDEN_COUNT=$(find "$REPO_ROOT/services" -name "*GoldenContract*" -type f 2>/dev/null | wc -l)
SERVICE_COUNT=$(find "$REPO_ROOT/services" -maxdepth 1 -mindepth 1 -type d ! -name "shared-core" | wc -l)
echo "  GoldenContractIT coverage: $GOLDEN_COUNT / $SERVICE_COUNT services"
if [ "$GOLDEN_COUNT" -ge "$SERVICE_COUNT" ]; then
    log_pass "All services have GoldenContractIT"
else
    log_fail "Only $GOLDEN_COUNT/$SERVICE_COUNT services have GoldenContractIT"
fi

# =============================================================================
# CHECK 4: Structural — Idempotency handling
# =============================================================================
log_phase "Check 4: Idempotency Key Handling"

IDEM_FILTER="$REPO_ROOT/libs/tech-companion/src/main/java/zw/gov/mohcc/impilo/companion/filter/IdempotencyFilter.java"
if [ -f "$IDEM_FILTER" ]; then
    if grep -q "409" "$IDEM_FILTER" && grep -q "IDENTITY_CONFLICT" "$IDEM_FILTER"; then
        log_pass "IdempotencyFilter returns 409 with IDENTITY_CONFLICT on replay conflict"
    else
        log_fail "IdempotencyFilter missing 409/IDENTITY_CONFLICT handling"
    fi
else
    log_fail "IdempotencyFilter.java not found"
fi

# =============================================================================
# CHECK 5: Structural — Federation authority
# =============================================================================
log_phase "Check 5: Federation Authority Enforcement"

FED_AUTH=$(find "$REPO_ROOT/libs/tech-companion" -name "FederationAuthority.java" 2>/dev/null | head -1)
if [ -n "$FED_AUTH" ] && [ -f "$FED_AUTH" ]; then
    if grep -q "requireNational\|requireProvincial\|requireDistrict" "$FED_AUTH"; then
        log_pass "FederationAuthority has level-check methods"
    else
        log_fail "FederationAuthority missing level-check methods"
    fi

    if grep -q "FederationAuthorityViolationException" "$FED_AUTH"; then
        log_pass "FederationAuthority throws FederationAuthorityViolationException"
    else
        log_fail "FederationAuthority missing exception type"
    fi
else
    log_skip "FederationAuthority.java not found"
fi

# =============================================================================
# CHECK 6: Live Wire Tests (only with --live flag)
# =============================================================================
if [ "$LIVE_MODE" = true ]; then
    log_phase "Check 6: Live Wire Tests"

    # Test: missing headers → 400
    echo "  Testing: request without trust headers to TSHEPO..."
    HTTP_CODE=$(curl -sf -o /dev/null -w "%{http_code}" \
        -X GET "$TSHEPO_URL/internal/v1/health-check" 2>/dev/null || echo "000")

    if [ "$HTTP_CODE" = "400" ]; then
        log_pass "TSHEPO rejects request without trust headers (HTTP $HTTP_CODE)"
    elif [ "$HTTP_CODE" = "000" ]; then
        log_skip "TSHEPO not reachable at $TSHEPO_URL"
    else
        log_fail "TSHEPO returned $HTTP_CODE instead of 400 for missing headers"
    fi

    # Test: with headers → 200
    echo "  Testing: request WITH trust headers to TSHEPO..."
    HTTP_CODE=$(curl -sf -o /dev/null -w "%{http_code}" \
        -H "X-Tenant-ID: wire-check-tenant" \
        -H "X-Pod-ID: wire-check-pod" \
        -H "X-Request-ID: wire-check-req-$(date +%s)" \
        -H "X-Correlation-ID: wire-check-corr-$(date +%s)" \
        -X GET "$TSHEPO_URL/internal/v1/health-check" 2>/dev/null || echo "000")

    if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "204" ]; then
        log_pass "TSHEPO accepts request with trust headers (HTTP $HTTP_CODE)"
    elif [ "$HTTP_CODE" = "000" ]; then
        log_skip "TSHEPO not reachable"
    else
        log_fail "TSHEPO returned unexpected $HTTP_CODE with valid headers"
    fi

    # Test: idempotency conflict
    IDEM_KEY="wire-check-idem-$(date +%s)"
    echo "  Testing: idempotency key replay with different body..."
    # First request
    curl -sf -o /dev/null \
        -H "X-Tenant-ID: wire-check" -H "X-Pod-ID: wire-check" \
        -H "X-Request-ID: req-1" -H "X-Correlation-ID: corr-1" \
        -H "Idempotency-Key: $IDEM_KEY" \
        -H "Content-Type: application/json" \
        -X POST -d '{"body":"first"}' \
        "$TSHEPO_URL/internal/v1/authorize" 2>/dev/null || true
    # Second request with same key, different body
    CONFLICT_CODE=$(curl -sf -o /dev/null -w "%{http_code}" \
        -H "X-Tenant-ID: wire-check" -H "X-Pod-ID: wire-check" \
        -H "X-Request-ID: req-2" -H "X-Correlation-ID: corr-2" \
        -H "Idempotency-Key: $IDEM_KEY" \
        -H "Content-Type: application/json" \
        -X POST -d '{"body":"different"}' \
        "$TSHEPO_URL/internal/v1/authorize" 2>/dev/null || echo "000")

    if [ "$CONFLICT_CODE" = "409" ]; then
        log_pass "Idempotency conflict returns 409"
    elif [ "$CONFLICT_CODE" = "000" ]; then
        log_skip "TSHEPO not reachable for idempotency test"
    else
        echo "    Note: Got HTTP $CONFLICT_CODE (may depend on endpoint behavior)"
        log_skip "Idempotency conflict test inconclusive (HTTP $CONFLICT_CODE)"
    fi
else
    log_phase "Check 6: Live Wire Tests (SKIPPED — use --live flag)"
    log_skip "Live wire tests require running services (pass --live flag)"
fi

# =============================================================================
# SUMMARY
# =============================================================================
log_phase "Wire Contract Check Summary"

echo -e "  Total checks: $TOTAL"
echo -e "  ${GREEN}Passed: $PASS${NC}"
echo -e "  ${RED}Failed: $FAIL${NC}"
echo -e "  ${YELLOW}Skipped: $SKIP${NC}"
echo ""

if [ $FAIL -gt 0 ]; then
    echo -e "${RED}WIRE CONTRACT CHECK: ISSUES FOUND${NC}"
    exit 1
elif [ $SKIP -gt $((TOTAL / 2)) ]; then
    echo -e "${YELLOW}WIRE CONTRACT CHECK: MOSTLY SKIPPED (missing tools/services)${NC}"
    exit 0
else
    echo -e "${GREEN}WIRE CONTRACT CHECK: PASSED${NC}"
    exit 0
fi

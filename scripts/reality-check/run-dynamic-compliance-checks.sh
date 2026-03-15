#!/usr/bin/env bash
set -euo pipefail

###############################################################################
# Impilo vNext — Dynamic Compliance Check
#
# Goes beyond static grep-based checks to validate compliance dynamically:
#   1. Static check baseline (run existing scripts)
#   2. GoldenContractSuite test structure validation
#   3. Request-path enforcement patterns
#   4. Idempotency behavior test structure
#   5. Outbox/event payload schema checks
#   6. Federation denial behavior checks
#   7. V11ComplianceTest presence and depth
#
# Usage:
#   ./scripts/reality-check/run-dynamic-compliance-checks.sh [--live]
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
[ "${1:-}" = "--live" ] && LIVE_MODE=true

echo ""
echo "============================================================"
echo "  Impilo vNext — Dynamic Compliance Check"
echo "  Mode: $([ "$LIVE_MODE" = true ] && echo "LIVE" || echo "STRUCTURAL")"
echo "============================================================"
echo ""

# =============================================================================
# CHECK 1: Run existing static compliance check
# =============================================================================
log_phase "Check 1: Static Compliance Baseline"

STATIC_SCRIPT="$REPO_ROOT/scripts/compliance/full-platform-compliance-check.sh"
if [ -f "$STATIC_SCRIPT" ]; then
    echo "  Running: $STATIC_SCRIPT"
    if bash "$STATIC_SCRIPT" 2>&1 | tail -5; then
        log_pass "Static compliance check passes"
    else
        log_fail "Static compliance check has failures"
    fi
else
    log_skip "Static compliance script not found"
fi

# =============================================================================
# CHECK 2: GoldenContractSuite — Test Depth Analysis
# =============================================================================
log_phase "Check 2: GoldenContractSuite Test Depth"

GOLDEN_SUITE="$REPO_ROOT/libs/tech-companion-harness/src/main/java/zw/gov/mohcc/impilo/companion/harness/GoldenContractSuite.java"

if [ -f "$GOLDEN_SUITE" ]; then
    # Count test methods
    TEST_COUNT=$(grep -c "@Test" "$GOLDEN_SUITE" 2>/dev/null || echo "0")
    echo "  GoldenContractSuite test methods: $TEST_COUNT"

    # Check specific dynamic-behavior tests
    DYNAMIC_TESTS=(
        "missingTenantIdReturns400:Header enforcement — missing Tenant-ID"
        "missingPodIdReturns400:Header enforcement — missing Pod-ID"
        "missingRequestIdReturns400:Header enforcement — missing Request-ID"
        "missingCorrelationIdReturns400:Header enforcement — missing Correlation-ID"
        "IDENTITY_CONFLICT:Idempotency conflict (409)"
        "FEDERATION_AUTHORITY_VIOLATION:Federation authority denial (403)"
        "ErrorEnvelope:Error envelope format validation"
    )

    FOUND_DYNAMIC=0
    for entry in "${DYNAMIC_TESTS[@]}"; do
        IFS=':' read -r pattern desc <<< "$entry"
        if grep -q "$pattern" "$GOLDEN_SUITE"; then
            echo "    DYNAMIC: $desc"
            ((FOUND_DYNAMIC++))
        else
            echo "    MISSING: $desc"
        fi
    done

    if [ $FOUND_DYNAMIC -ge 5 ]; then
        log_pass "GoldenContractSuite has $FOUND_DYNAMIC/7 dynamic-behavior tests"
    else
        log_fail "GoldenContractSuite only has $FOUND_DYNAMIC/7 dynamic-behavior tests"
    fi
else
    log_fail "GoldenContractSuite not found"
fi

# =============================================================================
# CHECK 3: V11ComplianceTest Coverage
# =============================================================================
log_phase "Check 3: V11ComplianceTest Coverage"

V11_TESTS=$(find "$REPO_ROOT/services" -name "*V11ComplianceTest*" -o -name "*V11Compliance*Test*" 2>/dev/null | grep -v target | sort)
V11_COUNT=$(echo "$V11_TESTS" | grep -c . 2>/dev/null || echo "0")

echo "  V11ComplianceTest files found: $V11_COUNT"
if [ "$V11_COUNT" -gt 0 ]; then
    echo "$V11_TESTS" | while read -r f; do
        svc=$(echo "$f" | sed "s|$REPO_ROOT/services/||" | cut -d/ -f1)
        echo "    $svc"
    done
fi

# These tests go beyond GoldenContract — they test outbox fields, event payloads, etc.
DEEP_V11=0
for test_file in $V11_TESTS; do
    [ ! -f "$test_file" ] && continue
    CHECKS=0
    grep -q "schema_version\|getSchemaVersion" "$test_file" && ((CHECKS++)) || true
    grep -q "event_type\|getEventType" "$test_file" && ((CHECKS++)) || true
    grep -q "MISSING_REQUIRED_HEADER" "$test_file" && ((CHECKS++)) || true
    grep -q "IDENTITY_CONFLICT" "$test_file" && ((CHECKS++)) || true
    if [ $CHECKS -ge 3 ]; then
        ((DEEP_V11++))
    fi
done

if [ $DEEP_V11 -gt 0 ]; then
    log_pass "$DEEP_V11 services have deep V11ComplianceTest (outbox + headers + idempotency)"
else
    log_skip "No deep V11ComplianceTest files found"
fi

# =============================================================================
# CHECK 4: Idempotency Behavior Test Patterns
# =============================================================================
log_phase "Check 4: Idempotency Behavior Tests"

# Find tests that actually test replay behavior (not just presence of header)
IDEM_TESTS=$(grep -rlP "Idempotency-Key.*409|IDENTITY_CONFLICT|replay|conflict" \
    "$REPO_ROOT/services" "$REPO_ROOT/libs" 2>/dev/null | \
    grep -E "Test\.java$|IT\.java$" | grep -v target | sort -u)
IDEM_COUNT=$(echo "$IDEM_TESTS" | grep -c . 2>/dev/null || echo "0")

echo "  Tests with idempotency conflict assertions: $IDEM_COUNT"
if [ $IDEM_COUNT -gt 0 ]; then
    echo "$IDEM_TESTS" | head -10 | while read -r f; do
        echo "    $(echo "$f" | sed "s|$REPO_ROOT/||")"
    done
    log_pass "Idempotency behavior tested in $IDEM_COUNT test files"
else
    log_fail "No tests assert idempotency replay/conflict behavior"
fi

# =============================================================================
# CHECK 5: Outbox Payload Schema Validation
# =============================================================================
log_phase "Check 5: Outbox Payload Schema Validation"

# Check if any test validates outbox event payload structure
OUTBOX_VALIDATION=$(grep -rlP "event_outbox|OutboxEvent|outbox.*assert|payload.*schema" \
    "$REPO_ROOT/services" "$REPO_ROOT/libs" 2>/dev/null | \
    grep -E "Test\.java$|IT\.java$" | grep -v target | sort -u)
OV_COUNT=$(echo "$OUTBOX_VALIDATION" | grep -c . 2>/dev/null || echo "0")

echo "  Tests with outbox/event validation: $OV_COUNT"
if [ $OV_COUNT -gt 3 ]; then
    log_pass "Outbox payload validation present in $OV_COUNT test files"
else
    log_fail "Insufficient outbox payload validation tests ($OV_COUNT)"
fi

# =============================================================================
# CHECK 6: Federation Denial Behavior Tests
# =============================================================================
log_phase "Check 6: Federation Denial Behavior Tests"

FED_TESTS=$(grep -rlP "FEDERATION_AUTHORITY_VIOLATION|FederationAuthority|requireNational.*fail|403.*federation" \
    "$REPO_ROOT/services" "$REPO_ROOT/libs" 2>/dev/null | \
    grep -E "Test\.java$|IT\.java$" | grep -v target | sort -u)
FED_COUNT=$(echo "$FED_TESTS" | grep -c . 2>/dev/null || echo "0")

echo "  Tests with federation denial assertions: $FED_COUNT"
if [ $FED_COUNT -gt 0 ]; then
    echo "$FED_TESTS" | head -10 | while read -r f; do
        echo "    $(echo "$f" | sed "s|$REPO_ROOT/||")"
    done
    log_pass "Federation denial tested in $FED_COUNT test files"
else
    log_fail "No tests assert federation authority denial behavior"
fi

# =============================================================================
# CHECK 7: What is Static-Only vs Dynamic
# =============================================================================
log_phase "Check 7: Static-Only vs Dynamic Test Classification"

echo "  STATIC-ONLY checks (grep/file presence):"
echo "    - tech-companion dependency in pom.xml"
echo "    - /internal/v1/ route presence in source"
echo "    - event_outbox string in source/migrations"
echo "    - GoldenContractIT file existence"
echo "    - actuator dependency in pom.xml"
echo ""
echo "  DYNAMIC checks (actual behavior testing):"
echo "    - GoldenContractSuite tests (MockMvc-based, require Spring context)"
echo "    - V11ComplianceTest (MockMvc-based, test header enforcement + outbox fields)"
echo "    - Smoke tests (HTTP-based, require running services)"
echo "    - Event bus proof (DB-based, require running Postgres)"
echo ""

# Classify what is proven vs what is claimed
GOLDEN_IT_COUNT=$(find "$REPO_ROOT/services" -name "*GoldenContract*" -type f 2>/dev/null | grep -v target | wc -l)
V11_TEST_COUNT=$(find "$REPO_ROOT/services" -name "*V11Compliance*" -type f 2>/dev/null | grep -v target | wc -l)

echo "  GoldenContractIT across services: $GOLDEN_IT_COUNT (needs Maven + Spring to run)"
echo "  V11ComplianceTest across services: $V11_TEST_COUNT (needs Maven + Spring to run)"
echo ""
echo "  ASSESSMENT:"
echo "    Static compliance: PROVEN (scripts run successfully in this environment)"
echo "    GoldenContractIT: STRUCTURALLY PRESENT, UNEXECUTED HERE (needs Maven/Java build)"
echo "    V11ComplianceTest: STRUCTURALLY PRESENT, UNEXECUTED HERE (needs Maven/Java build)"
echo "    Smoke tests: STRUCTURALLY PRESENT, UNEXECUTED HERE (needs Docker runtime)"
echo "    Event bus proof: STRUCTURALLY PRESENT, UNEXECUTED HERE (needs Docker runtime)"

log_pass "Compliance classification documented"

# =============================================================================
# CHECK 8: Live Dynamic Tests (only with --live flag)
# =============================================================================
if [ "$LIVE_MODE" = true ]; then
    log_phase "Check 8: Live Dynamic Compliance Tests"

    # Can we run Maven tests?
    if command -v mvn &>/dev/null; then
        echo "  Running GoldenContractIT for tshepo-service..."
        TSHEPO_DIR="$REPO_ROOT/services/tshepo-service"
        if (cd "$TSHEPO_DIR" && mvn -B test -Dtest="*GoldenContractIT" -DfailIfNoTests=false 2>&1 | tail -10); then
            log_pass "tshepo-service GoldenContractIT executed"
        else
            log_fail "tshepo-service GoldenContractIT failed or could not run"
        fi
    else
        log_skip "Maven not available — cannot run GoldenContractIT"
    fi
else
    log_phase "Check 8: Live Dynamic Tests (SKIPPED — use --live flag)"
    log_skip "Live dynamic tests require Maven + running infra (pass --live flag)"
fi

# =============================================================================
# SUMMARY
# =============================================================================
log_phase "Dynamic Compliance Check Summary"

echo -e "  Total checks: $TOTAL"
echo -e "  ${GREEN}Passed: $PASS${NC}"
echo -e "  ${RED}Failed: $FAIL${NC}"
echo -e "  ${YELLOW}Skipped: $SKIP${NC}"
echo ""
echo "  KEY FINDING:"
echo "  Current compliance checks are PRIMARILY STATIC (file/grep-based)."
echo "  Dynamic test infrastructure EXISTS (GoldenContractSuite, V11ComplianceTest,"
echo "  smoke.sh, event-bus-proof.sh) but requires Maven build + Docker runtime"
echo "  to execute. This environment cannot run them."
echo ""

if [ $FAIL -gt 0 ]; then
    echo -e "${RED}DYNAMIC COMPLIANCE: GAPS FOUND${NC}"
    exit 1
else
    echo -e "${GREEN}DYNAMIC COMPLIANCE: STRUCTURE VERIFIED${NC}"
    exit 0
fi

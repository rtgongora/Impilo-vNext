#!/usr/bin/env bash
set -euo pipefail

###############################################################################
# Impilo vNext — Production Robustness Check
#
# Classifies each service by production-readiness:
#   ROBUST    — migrations, outbox, healthcheck, validation, tests, Dockerfile, README
#   ADEQUATE  — most markers present, minor gaps
#   MINIMAL   — only CRUD wrappers, missing significant markers
#   FRAGILE   — missing core infrastructure (no migrations, no tests, no health)
#
# Usage:
#   ./scripts/reality-check/run-production-robustness-checks.sh
###############################################################################

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SERVICES_DIR="$REPO_ROOT/services"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo ""
echo "============================================================"
echo "  Impilo vNext — Production Robustness Check"
echo "============================================================"
echo ""

ROBUST=0
ADEQUATE=0
MINIMAL=0
FRAGILE=0

printf "%-38s %-5s %-5s %-5s %-5s %-5s %-5s %-5s %-5s %-10s\n" \
    "SERVICE" "MIGR" "OUTBX" "HLTH" "VALID" "TESTS" "DOCKR" "READM" "SCORE" "CLASS"
printf "%-38s %-5s %-5s %-5s %-5s %-5s %-5s %-5s %-5s %-10s\n" \
    "-------" "----" "-----" "----" "-----" "-----" "-----" "-----" "-----" "-----"

for svc_dir in "$SERVICES_DIR"/*/; do
    svc_name=$(basename "$svc_dir")
    [ "$svc_name" = "shared-core" ] && continue
    [ ! -f "$svc_dir/pom.xml" ] && continue
    [ ! -d "$svc_dir/src/main/java" ] && continue

    SCORE=0

    # 1. Flyway migrations
    MIGR="N"
    if [ -d "$svc_dir/src/main/resources/db/migration" ]; then
        MIGR_COUNT=$(find "$svc_dir/src/main/resources/db/migration" -name "V*.sql" 2>/dev/null | wc -l)
        if [ "$MIGR_COUNT" -gt 0 ]; then
            MIGR="Y"
            SCORE=$((SCORE + 1))
        fi
    fi

    # 2. Outbox table
    OUTBX="N"
    if grep -rq "event_outbox\|EventOutbox" "$svc_dir/src/" 2>/dev/null; then
        OUTBX="Y"
        SCORE=$((SCORE + 1))
    fi

    # 3. Health endpoint (actuator)
    HLTH="N"
    if grep -q "spring-boot-starter-actuator" "$svc_dir/pom.xml" 2>/dev/null; then
        HLTH="Y"
        SCORE=$((SCORE + 1))
    fi

    # 4. Validation annotations
    VALID="N"
    if grep -rq "@Valid\|@NotNull\|@NotBlank\|@Size\|@Pattern" "$svc_dir/src/main/java" 2>/dev/null; then
        VALID="Y"
        SCORE=$((SCORE + 1))
    fi

    # 5. Integration tests (beyond GoldenContractIT)
    TESTS="N"
    TEST_COUNT=$(find "$svc_dir/src/test" -name "*Test.java" -o -name "*IT.java" 2>/dev/null | wc -l)
    if [ "$TEST_COUNT" -gt 1 ]; then
        TESTS="Y"
        SCORE=$((SCORE + 1))
    fi

    # 6. Dockerfile
    DOCKR="N"
    if [ -f "$svc_dir/Dockerfile" ]; then
        DOCKR="Y"
        SCORE=$((SCORE + 1))
    fi

    # 7. README
    READM="N"
    if [ -f "$svc_dir/README.md" ]; then
        READM="Y"
        SCORE=$((SCORE + 1))
    fi

    # Classification
    CLASS="FRAGILE"
    COLOR="$RED"
    if [ $SCORE -ge 7 ]; then
        CLASS="ROBUST"
        COLOR="$GREEN"
        ROBUST=$((ROBUST + 1))
    elif [ $SCORE -ge 5 ]; then
        CLASS="ADEQUATE"
        COLOR="$GREEN"
        ADEQUATE=$((ADEQUATE + 1))
    elif [ $SCORE -ge 3 ]; then
        CLASS="MINIMAL"
        COLOR="$YELLOW"
        MINIMAL=$((MINIMAL + 1))
    else
        FRAGILE=$((FRAGILE + 1))
    fi

    printf "%-38s %-5s %-5s %-5s %-5s %-5s %-5s %-5s %-5s ${COLOR}%-10s${NC}\n" \
        "$svc_name" "$MIGR" "$OUTBX" "$HLTH" "$VALID" "$TESTS" "$DOCKR" "$READM" "$SCORE/7" "$CLASS"
done

TOTAL=$((ROBUST + ADEQUATE + MINIMAL + FRAGILE))

echo ""
echo "============================================================"
echo "  Classification Summary"
echo "============================================================"
echo -e "  ${GREEN}ROBUST:   $ROBUST${NC}"
echo -e "  ${GREEN}ADEQUATE: $ADEQUATE${NC}"
echo -e "  ${YELLOW}MINIMAL:  $MINIMAL${NC}"
echo -e "  ${RED}FRAGILE:  $FRAGILE${NC}"
echo "  Total:    $TOTAL"
echo ""

if [ $FRAGILE -gt 0 ]; then
    echo -e "${RED}PRODUCTION ROBUSTNESS: FRAGILE SERVICES FOUND${NC}"
    exit 1
elif [ $MINIMAL -gt 0 ]; then
    echo -e "${YELLOW}PRODUCTION ROBUSTNESS: MINIMAL SERVICES FOUND — hardening needed${NC}"
    exit 0
else
    echo -e "${GREEN}PRODUCTION ROBUSTNESS: ALL SERVICES ADEQUATE OR BETTER${NC}"
    exit 0
fi

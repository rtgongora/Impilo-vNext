#!/usr/bin/env bash
set -euo pipefail

###############################################################################
# Impilo vNext — Run All Reality Checks
#
# Orchestrates all 7 risk-class validation scripts.
#
# Usage:
#   ./scripts/reality-check/run-all.sh [--live]
#
# Flags:
#   --live   Also run live wire/runtime tests (requires Docker services running)
###############################################################################

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LIVE_FLAG="${1:-}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

echo ""
echo -e "${BOLD}${CYAN}╔══════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}${CYAN}║  Impilo vNext — Full Platform Reality Check                    ║${NC}"
echo -e "${BOLD}${CYAN}║  7 Risk Classes × Structural + Dynamic Validation              ║${NC}"
echo -e "${BOLD}${CYAN}╚══════════════════════════════════════════════════════════════════╝${NC}"
echo ""

CHECKS=(
    "A|Fleet Build|build-fleet.sh|"
    "B|Wire Contract|run-wire-checks.sh|$LIVE_FLAG"
    "C|Eventing Interoperability|run-eventing-checks.sh|$LIVE_FLAG"
    "D|Mobile Runnability|run-mobile-runnability-checks.sh|"
    "E|Compose / Runtime Wiring|run-compose-checks.sh|$LIVE_FLAG"
    "F|Dynamic Compliance|run-dynamic-compliance-checks.sh|$LIVE_FLAG"
    "G|Production Robustness|run-production-robustness-checks.sh|"
)

TOTAL_CHECKS=0
PASSED_CHECKS=0
FAILED_CHECKS=0

for entry in "${CHECKS[@]}"; do
    IFS='|' read -r letter name script flag <<< "$entry"
    ((TOTAL_CHECKS++))

    echo -e "\n${BOLD}${CYAN}── Risk Class $letter: $name ──${NC}\n"

    SCRIPT_PATH="$SCRIPT_DIR/$script"
    if [ -f "$SCRIPT_PATH" ]; then
        if bash "$SCRIPT_PATH" $flag 2>&1; then
            echo -e "\n  ${GREEN}Risk Class $letter: PASSED${NC}"
            ((PASSED_CHECKS++))
        else
            echo -e "\n  ${RED}Risk Class $letter: ISSUES FOUND${NC}"
            ((FAILED_CHECKS++))
        fi
    else
        echo -e "  ${RED}Script not found: $SCRIPT_PATH${NC}"
        ((FAILED_CHECKS++))
    fi
done

echo ""
echo -e "${BOLD}${CYAN}╔══════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}${CYAN}║  REALITY CHECK SUMMARY                                         ║${NC}"
echo -e "${BOLD}${CYAN}╚══════════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "  Total risk classes: $TOTAL_CHECKS"
echo -e "  ${GREEN}Passed: $PASSED_CHECKS${NC}"
echo -e "  ${RED}Issues found: $FAILED_CHECKS${NC}"
echo ""

if [ $FAILED_CHECKS -eq 0 ]; then
    echo -e "${GREEN}${BOLD}ALL RISK CLASSES PASSED${NC}"
    exit 0
else
    echo -e "${YELLOW}${BOLD}$FAILED_CHECKS RISK CLASS(ES) HAVE KNOWN ISSUES — see findings docs${NC}"
    exit 1
fi

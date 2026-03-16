#!/bin/bash
# =============================================================================
# Impilo vNext — Run All Implementation Closure Checks
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "============================================"
echo " Impilo vNext — Full Closure Verification"
echo " $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "============================================"
echo ""

TOTAL=0
PASSED=0
FAILED=0

run_check() {
    local name="$1"
    local script="$2"
    TOTAL=$((TOTAL + 1))
    echo ""
    echo ">>>>>>>>>> $name <<<<<<<<<<<"
    if bash "$script"; then
        PASSED=$((PASSED + 1))
        echo ">>> $name: PASSED"
    else
        FAILED=$((FAILED + 1))
        echo ">>> $name: FAILED"
    fi
    echo ""
}

run_check "Incomplete Components" "$SCRIPT_DIR/inspect-incomplete-components.sh"
run_check "No Stubs"             "$SCRIPT_DIR/check-no-stubs.sh"
run_check "App Targets"          "$SCRIPT_DIR/check-app-targets.sh"
run_check "Service Minimums"     "$SCRIPT_DIR/check-service-runtime-minimums.sh"

echo "============================================"
echo " SUMMARY"
echo "============================================"
echo "Total checks:  $TOTAL"
echo "Passed:        $PASSED"
echo "Failed:        $FAILED"
echo ""

if [ "$FAILED" -gt 0 ]; then
    echo "STATUS: PARTIAL — $FAILED check(s) need attention"
    exit 1
else
    echo "STATUS: ALL CLEAR"
    exit 0
fi

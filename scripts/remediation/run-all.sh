#!/usr/bin/env bash
# run-all.sh — Run all remediation verification scripts
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TOTAL=0
PASSED=0
FAILED=0

run_script() {
  local script="$1"
  local name="$(basename "$script" .sh)"
  TOTAL=$((TOTAL + 1))

  echo ""
  echo "================================================================"
  echo "Running: $name"
  echo "================================================================"

  if bash "$script"; then
    PASSED=$((PASSED + 1))
    echo ">>> $name: PASSED"
  else
    FAILED=$((FAILED + 1))
    echo ">>> $name: FAILED"
  fi
}

run_script "$SCRIPT_DIR/scan-fake-writes.sh"
run_script "$SCRIPT_DIR/scan-dead-end-flows.sh"
run_script "$SCRIPT_DIR/scan-thin-route-implementations.sh"
run_script "$SCRIPT_DIR/scan-costing-engine.sh"
run_script "$SCRIPT_DIR/verify-remediations.sh"

echo ""
echo "================================================================"
echo "REMEDIATION VERIFICATION SUMMARY"
echo "================================================================"
echo "Total scripts: $TOTAL"
echo "Passed: $PASSED"
echo "Failed: $FAILED"
echo ""

if [ $FAILED -eq 0 ]; then
  echo "ALL CHECKS PASSED"
  exit 0
else
  echo "SOME CHECKS FAILED — review output above"
  exit 1
fi

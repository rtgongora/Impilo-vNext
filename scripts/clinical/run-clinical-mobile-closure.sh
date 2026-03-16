#!/usr/bin/env bash
# ============================================================================
# run-clinical-mobile-closure.sh — Master orchestration script
#
# Runs all verification scripts in sequence and produces a combined report.
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TOTAL_PASS=0
TOTAL_FAIL=0
RESULTS=()

run_check() {
  local name="$1"
  local script="$2"
  echo ""
  echo "╔══════════════════════════════════════════╗"
  echo "  Running: $name"
  echo "╚══════════════════════════════════════════╝"
  echo ""

  local output
  local rc=0
  output=$("$SCRIPT_DIR/$script" 2>&1) || rc=$?

  echo "$output"
  echo ""

  local pass fail
  pass=$(echo "$output" | grep -oP 'PASS=\K[0-9]+' | tail -1 || echo "0")
  fail=$(echo "$output" | grep -oP 'FAIL=\K[0-9]+' | tail -1 || echo "0")
  # Handle COMPLETE/INCOMPLETE format from steel threads
  if [ "$pass" = "0" ] && [ "$fail" = "0" ]; then
    pass=$(echo "$output" | grep -oP 'COMPLETE=\K[0-9]+' | tail -1 || echo "0")
    fail=$(echo "$output" | grep -oP 'INCOMPLETE=\K[0-9]+' | tail -1 || echo "0")
  fi

  TOTAL_PASS=$((TOTAL_PASS + pass))
  TOTAL_FAIL=$((TOTAL_FAIL + fail))

  if [ "$rc" -eq 0 ]; then
    RESULTS+=("  ✅ $name — PASS=$pass FAIL=$fail")
  else
    RESULTS+=("  ⚠️  $name — PASS=$pass FAIL=$fail")
  fi
}

echo "╔══════════════════════════════════════════════════╗"
echo "║  Clinical & Mobile Closure — Combined Report     ║"
echo "╚══════════════════════════════════════════════════╝"

run_check "EHR Workflow Inspection" "inspect-ehr-workflows.sh"
run_check "EHR Steel Thread Verification" "run-ehr-steel-threads.sh"
run_check "Provider App Parity" "verify-provider-parity.sh"
run_check "Citizen App Parity" "verify-citizen-parity.sh"

echo ""
echo "╔══════════════════════════════════════════════════╗"
echo "║          COMBINED CLOSURE REPORT                 ║"
echo "╚══════════════════════════════════════════════════╝"
echo ""
for r in "${RESULTS[@]}"; do
  echo "$r"
done
echo ""
echo "  TOTAL: PASS=$TOTAL_PASS  FAIL=$TOTAL_FAIL"
echo ""

if [ "$TOTAL_FAIL" -eq 0 ]; then
  echo "  ════════════════════════════════════════"
  echo "  STATUS: ALL CHECKS PASSED"
  echo "  ════════════════════════════════════════"
  exit 0
else
  echo "  ════════════════════════════════════════"
  echo "  STATUS: $TOTAL_FAIL CHECK(S) FAILING"
  echo "  ════════════════════════════════════════"
  exit 1
fi

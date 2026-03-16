#!/usr/bin/env bash
# run-all.sh — Runs all Lovable fidelity audit scripts
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESULTS=()
PASS_COUNT=0
FAIL_COUNT=0

run_audit() {
  local script="$1"
  local name="$2"
  echo ""
  echo "================================================================"
  echo "  Running: $name"
  echo "================================================================"
  echo ""

  if bash "$SCRIPT_DIR/$script"; then
    RESULTS+=("  [PASS] $name")
    PASS_COUNT=$((PASS_COUNT + 1))
  else
    RESULTS+=("  [FAIL] $name")
    FAIL_COUNT=$((FAIL_COUNT + 1))
  fi
}

echo "=== Lovable Fidelity — Full Audit Suite ==="
echo "  Date: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "  Branch: $(git branch --show-current 2>/dev/null || echo 'unknown')"
echo "  Commit: $(git log -1 --oneline 2>/dev/null || echo 'unknown')"

run_audit "discover-lovable-sources.sh" "Source Discovery"
run_audit "audit-page-fidelity.sh" "Page Fidelity"
run_audit "audit-flow-fidelity.sh" "Flow Fidelity"
run_audit "audit-component-fidelity.sh" "Component Fidelity"
run_audit "audit-app-parity.sh" "App Parity"

echo ""
echo "================================================================"
echo "  FINAL RESULTS"
echo "================================================================"
echo ""
for result in "${RESULTS[@]}"; do
  echo "$result"
done
echo ""
TOTAL=$((PASS_COUNT + FAIL_COUNT))
echo "  Overall: $PASS_COUNT/$TOTAL passed"
echo ""

if [ "$FAIL_COUNT" -gt 0 ]; then
  echo "  OVERALL STATUS: INCOMPLETE — $FAIL_COUNT audit(s) failed"
  exit 1
else
  echo "  OVERALL STATUS: ALL AUDITS PASSED"
  exit 0
fi

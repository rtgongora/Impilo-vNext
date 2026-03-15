#!/usr/bin/env bash
# run-all.sh — Run all completeness checks and produce a summary report.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TOTAL_PASS=0
TOTAL_FAIL=0
RESULTS=()

echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║      Impilo vNext — Full Completeness Audit Suite            ║"
echo "║      Run: $(date -u +%Y-%m-%dT%H:%M:%SZ)                          ║"
echo "╚═══════════════════════════════════════════════════════════════╝"

run_check() {
  local name="$1"
  local script="$2"

  echo ""
  echo "┌─────────────────────────────────────────────────────────────┐"
  echo "│  Running: $name"
  echo "└─────────────────────────────────────────────────────────────┘"

  if bash "$SCRIPT_DIR/$script" 2>&1; then
    RESULTS+=("✅ $name")
    TOTAL_PASS=$((TOTAL_PASS + 1))
  else
    RESULTS+=("❌ $name")
    TOTAL_FAIL=$((TOTAL_FAIL + 1))
  fi
}

run_check "Component Inventory"       "inspect-components.sh"
run_check "Service Minimums"          "check-service-minimums.sh"
run_check "App Runnability"           "check-app-runnability.sh"
run_check "Doc & Acceptance Coverage" "check-doc-and-acceptance-coverage.sh"

echo ""
echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║                    AUDIT SUITE SUMMARY                       ║"
echo "╠═══════════════════════════════════════════════════════════════╣"
for result in "${RESULTS[@]}"; do
  printf "║  %-58s║\n" "$result"
done
echo "╠═══════════════════════════════════════════════════════════════╣"
printf "║  Passed: %-3s  Failed: %-3s                                  ║\n" "$TOTAL_PASS" "$TOTAL_FAIL"
if [ "$TOTAL_FAIL" -eq 0 ]; then
  echo "║  Overall: ✅ ALL CHECKS PASSED                               ║"
else
  echo "║  Overall: ⚠  SOME CHECKS FAILED                              ║"
fi
echo "╚═══════════════════════════════════════════════════════════════╝"

[ "$TOTAL_FAIL" -gt 0 ] && exit 1
exit 0

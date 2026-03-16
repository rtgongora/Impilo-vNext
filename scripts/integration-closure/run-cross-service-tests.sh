#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TEST_DIR="$PROJECT_ROOT/test/integration"

echo "============================================="
echo "  Impilo vNext — Cross-Service Integration Tests"
echo "============================================="
echo ""

TOTAL_PASS=0
TOTAL_FAIL=0
RESULTS=()

for test_script in "$TEST_DIR"/steel-thread-*.sh; do
  if [[ -f "$test_script" ]]; then
    name=$(basename "$test_script" .sh)
    echo "--- Running: $name ---"
    if bash "$test_script"; then
      RESULTS+=("PASS: $name")
      TOTAL_PASS=$((TOTAL_PASS + 1))
    else
      RESULTS+=("FAIL: $name")
      TOTAL_FAIL=$((TOTAL_FAIL + 1))
    fi
    echo ""
  fi
done

echo "============================================="
echo "  SUMMARY"
echo "============================================="
for r in "${RESULTS[@]}"; do echo "  $r"; done
echo ""
echo "  Total: $((TOTAL_PASS + TOTAL_FAIL)) | Passed: $TOTAL_PASS | Failed: $TOTAL_FAIL"
echo "============================================="

[[ $TOTAL_FAIL -eq 0 ]] && exit 0 || exit 1

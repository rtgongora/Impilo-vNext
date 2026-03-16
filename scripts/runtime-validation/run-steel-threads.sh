#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
TEST_DIR="$ROOT_DIR/test/integration"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'; BOLD='\033[1m'; NC='\033[0m'

# Source test helpers
if [[ -f "$TEST_DIR/_common.sh" ]]; then
  source "$TEST_DIR/_common.sh"
else
  echo -e "${RED}[ERROR] test/integration/_common.sh not found${NC}"
  exit 1
fi

THREAD_PASS=0
THREAD_FAIL=0

run_thread() {
  local label="$1"
  local script="$2"
  local rc=0

  echo ""
  echo -e "${BOLD}--- Steel Thread: $label ---${NC}"

  if [[ -f "$script" ]]; then
    bash "$script" || rc=$?
  else
    echo -e "${YELLOW}[SKIP]${NC} Script not found: $script"
    rc=2
  fi

  if [[ "$rc" -eq 0 ]]; then
    echo -e "${GREEN}[PASS]${NC} $label"
    THREAD_PASS=$((THREAD_PASS + 1))
  elif [[ "$rc" -eq 2 ]]; then
    echo -e "${YELLOW}[SKIP]${NC} $label (no script)"
    THREAD_FAIL=$((THREAD_FAIL + 1))
  else
    echo -e "${RED}[FAIL]${NC} $label (exit code $rc)"
    THREAD_FAIL=$((THREAD_FAIL + 1))
  fi
}

echo -e "${BOLD}=== Steel Thread Tests ===${NC}"

# Thread A: Provider flow
run_thread "Provider Flow" "$TEST_DIR/steel-thread-a-provider.sh"

# Thread B: Citizen flow
run_thread "Citizen Flow" "$TEST_DIR/steel-thread-b-citizen.sh"

# Thread C: Support escalation
run_thread "Support Escalation" "$TEST_DIR/steel-thread-c-support.sh"

# Thread D: Eventing (messaging)
run_thread "Eventing" "$TEST_DIR/steel-thread-e-eventing.sh"

# Thread E: Federation
run_thread "Federation" "$TEST_DIR/steel-thread-f-federation.sh"

# Summary
echo ""
echo "========================================="
echo -e "Steel Threads: ${GREEN}${THREAD_PASS} passed${NC}, ${RED}${THREAD_FAIL} failed${NC} out of $((THREAD_PASS + THREAD_FAIL))"
echo "========================================="

if [[ "$THREAD_FAIL" -gt 0 ]]; then
  exit 1
fi
exit 0

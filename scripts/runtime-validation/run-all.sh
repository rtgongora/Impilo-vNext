#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'; BOLD='\033[1m'; NC='\033[0m'

OVERALL_RC=0
STEP_PASS=0
STEP_FAIL=0

run_step() {
  local label="$1"
  local cmd="$2"
  local rc=0

  echo ""
  echo -e "${BOLD}######################################################${NC}"
  echo -e "${BOLD}# $label${NC}"
  echo -e "${BOLD}######################################################${NC}"

  eval "$cmd" || rc=$?

  if [[ "$rc" -eq 0 ]]; then
    echo -e "${GREEN}[PASS]${NC} $label"
    STEP_PASS=$((STEP_PASS + 1))
  else
    echo -e "${RED}[FAIL]${NC} $label (exit code $rc)"
    STEP_FAIL=$((STEP_FAIL + 1))
    OVERALL_RC=1
  fi

  return 0  # Continue on failure
}

echo -e "${BOLD}=== Impilo Runtime Validation — Full Suite ===${NC}"
echo "Started at: $(date -u '+%Y-%m-%d %H:%M:%S UTC')"

# Step 1: Build all
run_step "Step 1: Build All" "bash '$SCRIPT_DIR/build-all.sh'"

# Step 2: Boot runtime
run_step "Step 2: Boot Runtime" "bash '$SCRIPT_DIR/boot-runtime.sh'"

# Step 3: Auth bootstrap
BOOTSTRAP_SCRIPT="$ROOT_DIR/scripts/integration-closure/bootstrap-auth.sh"
if [[ -f "$BOOTSTRAP_SCRIPT" ]]; then
  run_step "Step 3: Auth Bootstrap" "bash '$BOOTSTRAP_SCRIPT'"
else
  echo ""
  echo -e "${YELLOW}[SKIP]${NC} Step 3: Auth Bootstrap — script not found: $BOOTSTRAP_SCRIPT"
  STEP_FAIL=$((STEP_FAIL + 1))
  OVERALL_RC=1
fi

# Step 4: Steel thread tests
run_step "Step 4: Steel Thread Tests" "bash '$SCRIPT_DIR/run-steel-threads.sh'"

# Step 5: Eventing proof
run_step "Step 5: Eventing Proof" "bash '$SCRIPT_DIR/run-eventing-proof.sh'"

# Step 6: App runtime checks
run_step "Step 6: App Runtime Checks" "bash '$SCRIPT_DIR/run-app-runtime-checks.sh'"

# Step 7: Wire compliance
run_step "Step 7: Wire Compliance" "bash '$SCRIPT_DIR/run-wire-compliance.sh'"

# Step 8: Collect artifacts
run_step "Step 8: Collect Artifacts" "bash '$SCRIPT_DIR/collect-artifacts.sh'"

# Final summary
echo ""
echo "========================================="
echo -e "${BOLD}Full Suite Summary${NC}"
echo "========================================="
echo -e "Steps: ${GREEN}${STEP_PASS} passed${NC}, ${RED}${STEP_FAIL} failed${NC} out of $((STEP_PASS + STEP_FAIL))"
echo "Finished at: $(date -u '+%Y-%m-%d %H:%M:%S UTC')"
echo "========================================="

if [[ "$OVERALL_RC" -ne 0 ]]; then
  echo -e "${RED}One or more steps failed. See output above for details.${NC}"
  exit 1
fi

echo -e "${GREEN}All steps passed.${NC}"
exit 0

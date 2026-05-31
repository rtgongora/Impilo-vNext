#!/usr/bin/env bash
# Master blocking gate runner for preview pipeline (VM or CI). Does NOT deploy preview.
set -euo pipefail
REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
cd "$REPO_PATH"
source "$REPO_PATH/scripts/test/_gate-common.sh"

BLOCKING=(
  "security:scripts/test/run-security-checks.sh"
  "static:scripts/test/run-static-checks.sh"
  "change-safety:scripts/guard/run-change-safety-gates.sh"
  "frontend:scripts/test/run-frontend-checks.sh"
  "backend:scripts/test/run-backend-checks.sh"
  "api-contracts:scripts/test/run-api-contract-checks.sh"
  "integration:scripts/test/run-integration-checks.sh"
)

ADVISORY=(
  "mobile:scripts/test/run-mobile-checks.sh"
  "web-e2e:scripts/test/run-web-e2e.sh"
)

FAIL=0
FAILED_GATES=()
PASSED_GATES=()
WARN_GATES=()

echo "Impilo preview gates — $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "REPO: $REPO_PATH"
echo "BRANCH: $(git branch --show-current 2>/dev/null || echo unknown)"
echo "COMMIT: $(git rev-parse --short HEAD 2>/dev/null || echo unknown)"
echo "LOG_DIR: $GATE_LOG_DIR"
echo ""
echo "NOTE: Preview deployment is NOT part of this script. Deploy only after user authorization."
echo ""

run_gate() {
  local label="$1" script="$2" blocking="$3"
  if bash "$script"; then
    PASSED_GATES+=("$label")
  else
    if [[ "$blocking" == "1" ]]; then
      FAILED_GATES+=("$label")
      FAIL=1
    else
      WARN_GATES+=("$label")
    fi
  fi
}

for entry in "${BLOCKING[@]}"; do
  label="${entry%%:*}"
  script="${entry#*:}"
  run_gate "$label" "$script" 1
done

for entry in "${ADVISORY[@]}"; do
  label="${entry%%:*}"
  script="${entry#*:}"
  run_gate "$label" "$script" 0 || true
done

echo ""
echo "========== PREVIEW GATES SUMMARY =========="
echo "Passed (${#PASSED_GATES[@]}): ${PASSED_GATES[*]:-none}"
echo "Failed blocking (${#FAILED_GATES[@]}): ${FAILED_GATES[*]:-none}"
echo "Advisory warnings (${#WARN_GATES[@]}): ${WARN_GATES[*]:-none}"
echo "Logs: $GATE_LOG_DIR"
if [[ "$FAIL" -ne 0 ]]; then
  echo "VERDICT: BLOCKED — fix failures before user-authorized preview deploy"
  exit 1
fi
echo "VERDICT: BLOCKING GATES PASSED — user may authorize manual preview deploy"
exit 0

#!/usr/bin/env bash
# Canonical VM/Cursor quality pipeline — same phases used by GitHub Actions (via sub-scripts).
# Does NOT deploy preview. Writes reports/pipeline/latest-summary.{md,json}.
set -uo pipefail

REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
cd "$REPO_PATH"
source "$REPO_PATH/scripts/pipeline/_pipeline-common.sh"

export REPO_PATH GATE_LOG_DIR="$PIPELINE_LOG_DIR"
export GUARD_SUMMARY_FILE="/tmp/impilo-change-summary.txt"

echo "Impilo local quality pipeline"
echo "Started: $PIPELINE_STARTED_AT"
echo "Reports: $PIPELINE_REPORT_DIR"
echo ""

# 1. Workspace
pipeline_run_phase workspace "Workspace verification" 1 \
  bash scripts/pipeline/verify-workspace.sh

# 2. Tools
if [[ "${PIPELINE_SKIP_TOOLCHECK:-0}" != "1" ]]; then
  pipeline_run_phase tools "Dependency/tool verification" 1 \
    bash scripts/pipeline/verify-tools.sh || true
fi

# 3. Security
pipeline_run_phase security "Secret/security checks" 1 \
  bash scripts/test/run-security-checks.sh

# 4. Static
pipeline_run_phase static "Static checks" 1 \
  bash scripts/test/run-static-checks.sh

# 5. Frontend
if [[ "${PIPELINE_SKIP_FRONTEND:-0}" != "1" ]]; then
  pipeline_run_phase frontend "Frontend checks" 1 \
    bash scripts/test/run-frontend-checks.sh
fi

# 6. Backend
if [[ "${PIPELINE_SKIP_BACKEND:-0}" != "1" ]]; then
  pipeline_run_phase backend "Backend checks" 1 \
    bash scripts/test/run-backend-checks.sh
fi

# 7. Core transaction completion evidence (blocks metric fraud)
pipeline_run_phase core-tx-evidence "Core transaction completion evidence" 1 \
  bash scripts/guard/check-core-transaction-completion-evidence.sh

# 8. Backend-frontend parity
pipeline_run_phase parity-web "Backend-to-frontend parity" 1 \
  bash scripts/guard/check-backend-frontend-parity.sh

# 9. Mobile parity (blocking for new gaps)
pipeline_run_phase parity-mobile "Mobile parity" 1 \
  bash scripts/guard/check-mobile-parity.sh

# 10. API contracts
pipeline_run_phase api-contracts "API contract checks" 1 \
  bash scripts/test/run-api-contract-checks.sh

# 11. Integration
if [[ "${PIPELINE_SKIP_INTEGRATION:-0}" != "1" ]]; then
  pipeline_run_phase integration "Integration baseline" 1 \
    bash scripts/test/run-integration-checks.sh
fi

# 12. Regression
if [[ "${PIPELINE_SKIP_REGRESSION:-0}" != "1" ]]; then
  export PREVIEW_BASE_URL="${PREVIEW_BASE_URL:-http://127.0.0.1}"
  pipeline_run_phase regression "Regression checks" 1 \
    bash tests/regression/preview-http-regression.sh
  pipeline_run_phase regression-parity "Frontend parity route smoke" 1 \
    bash tests/regression/frontend-backend-parity-smoke.sh
fi

# 12b. Session Experience multi-persona E2E (advisory; needs a live preview).
# Skipped by default — set PIPELINE_RUN_SESSION_E2E=1 (and SESSION_E2E_URL) to run it.
if [[ "${PIPELINE_RUN_SESSION_E2E:-0}" == "1" ]]; then
  session_e2e_blocking=0
  [[ "${SESSION_E2E_STRICT:-0}" == "1" ]] && session_e2e_blocking=1
  pipeline_run_phase session-experience-e2e "Session Experience multi-persona E2E" "$session_e2e_blocking" \
    bash scripts/test/verify-session-experience-e2e.sh --url "${SESSION_E2E_URL:-http://41.57.127.235}" || true
fi

# 13. Full-boot readiness (advisory unless PIPELINE_FULL_BOOT_BLOCKING=1)
full_boot_blocking=0
[[ "${PIPELINE_FULL_BOOT_BLOCKING:-0}" == "1" ]] && full_boot_blocking=1
pipeline_run_phase full-boot-discover "Full-boot artifact generation" 0 \
  node scripts/full-boot/generate-full-boot-artifacts.mjs || true
pipeline_run_phase full-boot-targets "Full-boot build/image target discovery" 0 \
  bash scripts/build/discover-build-targets.sh || true
pipeline_run_phase full-boot-doctrine "Doctrine compliance" "$full_boot_blocking" \
  bash scripts/guard/check-doctrine-compliance.sh || true
pipeline_run_phase full-boot-runtime "Full-boot runtime completeness" 0 \
  bash scripts/guard/check-full-boot-runtime-completeness.sh || true
pipeline_run_phase full-boot-waves "Full-boot wave coverage" 0 \
  bash scripts/guard/check-full-boot-waves.sh || true
pipeline_run_phase full-boot-inventory "Registry inventory contract" 0 \
  bash scripts/guard/check-registry-inventory-contract.sh || true

# 14. Change-safety
pipeline_run_phase change-safety "Change-safety gates" 1 \
  bash scripts/guard/run-change-safety-gates.sh

# 15. Mobile build/typecheck (advisory — deep parity in parity-mobile phase)
pipeline_run_phase mobile "Mobile build checks" 0 \
  bash scripts/test/run-mobile-checks.sh || true

# 16. Web E2E (advisory unless PIPELINE_E2E_BLOCKING=1)
if [[ "${PIPELINE_SKIP_E2E:-1}" != "1" ]]; then
  e2e_blocking=0
  [[ "${PIPELINE_E2E_BLOCKING:-0}" == "1" ]] && e2e_blocking=1
  pipeline_run_phase web-e2e "Web E2E" "$e2e_blocking" \
    bash scripts/test/run-web-e2e.sh || true
fi

# Summary
echo ""
echo "========== LOCAL QUALITY PIPELINE SUMMARY =========="
VERDICT="PASS"
if [[ "$PIPELINE_FAIL" -ne 0 ]]; then
  VERDICT="FAIL"
elif [[ ${#PIPELINE_ADVISORY[@]} -gt 0 ]]; then
  VERDICT="PASS WITH ADVISORY WARNINGS"
fi

echo "Verdict: $VERDICT"
echo "Passed (${#PIPELINE_PASSED[@]}): ${PIPELINE_PASSED[*]:-none}"
echo "Failed (${#PIPELINE_FAILED[@]}): ${PIPELINE_FAILED[*]:-none}"
echo "Advisory (${#PIPELINE_ADVISORY[@]}): ${PIPELINE_ADVISORY[*]:-none}"

CHANGED="$(git diff --name-only HEAD~1...HEAD 2>/dev/null | wc -l)"
echo "Files changed vs HEAD~1: $CHANGED"
echo ""
echo "Deploy: manual only — bash scripts/deploy/manual-authorized-preview-deploy.sh"
echo "Feedback: bash scripts/pipeline/cursor-local-feedback.sh"

pipeline_write_reports "$VERDICT"

if [[ "$PIPELINE_FAIL" -ne 0 ]]; then
  exit 1
fi
exit 0

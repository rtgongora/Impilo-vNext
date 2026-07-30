#!/usr/bin/env bash
# Canonical VM/Cursor quality pipeline — same phases used by GitHub Actions (via sub-scripts).
# Does NOT deploy preview. Writes reports/pipeline/latest-summary.{md,json}.
set -uo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"
source "$REPO_PATH/scripts/pipeline/_pipeline-common.sh"

export REPO_PATH GATE_LOG_DIR="$PIPELINE_LOG_DIR"
export GUARD_SUMMARY_FILE="/tmp/impilo-change-summary.txt"

# Blocking gate defaults (override with *_BLOCKING=0 or PREVIEW_SMOKE_SKIP=1 to relax locally)
export PRODUCT_TRUTH_GATE_BLOCKING="${PRODUCT_TRUTH_GATE_BLOCKING:-1}"
export COHESION_GATE_BLOCKING="${COHESION_GATE_BLOCKING:-1}"
export PHASE6_GATE_BLOCKING="${PHASE6_GATE_BLOCKING:-1}"
export PREVIEW_SMOKE_BLOCKING="${PREVIEW_SMOKE_BLOCKING:-1}"
export PREVIEW_URL="${PREVIEW_URL:-https://impilo.mohcc.gov.zw}"

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

# 5+6. Frontend + Backend — the two dominant phases (~4 min npm, ~5 min maven).
# They touch disjoint trees (ui/ vs services/) and distinct log files, so they
# run concurrently by default; PIPELINE_CONCURRENT_CORE=0 restores sequential.
if [[ "${PIPELINE_CONCURRENT_CORE:-1}" == "1" \
      && "${PIPELINE_SKIP_FRONTEND:-0}" != "1" \
      && "${PIPELINE_SKIP_BACKEND:-0}" != "1" ]]; then
  echo ""
  echo "========== PHASE: Frontend + Backend checks (concurrent) =========="
  bash scripts/test/run-frontend-checks.sh >"$PIPELINE_LOG_DIR/frontend.log" 2>&1 &
  _fe_pid=$!
  bash scripts/test/run-backend-checks.sh >"$PIPELINE_LOG_DIR/backend.log" 2>&1 &
  _be_pid=$!
  _fe_rc=0; _be_rc=0
  wait "$_fe_pid" || _fe_rc=$?
  wait "$_be_pid" || _be_rc=$?
  # Summary bookkeeping must happen in the parent shell — background jobs
  # cannot mutate the pipeline's pass/fail arrays.
  if [[ "$_fe_rc" -eq 0 ]]; then
    pipeline_phase_pass "Frontend checks"; echo "PASS  Frontend checks"
  else
    echo "FAIL  Frontend checks (see $PIPELINE_LOG_DIR/frontend.log)"
    tail -n 30 "$PIPELINE_LOG_DIR/frontend.log" 2>/dev/null || true
    pipeline_phase_fail "Frontend checks"
  fi
  if [[ "$_be_rc" -eq 0 ]]; then
    pipeline_phase_pass "Backend checks"; echo "PASS  Backend checks"
  else
    echo "FAIL  Backend checks (see $PIPELINE_LOG_DIR/backend.log)"
    tail -n 30 "$PIPELINE_LOG_DIR/backend.log" 2>/dev/null || true
    pipeline_phase_fail "Backend checks"
  fi
else
  if [[ "${PIPELINE_SKIP_FRONTEND:-0}" != "1" ]]; then
    pipeline_run_phase frontend "Frontend checks" 1 \
      bash scripts/test/run-frontend-checks.sh
  fi
  if [[ "${PIPELINE_SKIP_BACKEND:-0}" != "1" ]]; then
    pipeline_run_phase backend "Backend checks" 1 \
      bash scripts/test/run-backend-checks.sh
  fi
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

# 9b. Product Truth audit gate (blocking by default — PRODUCT_TRUTH_VIOLATION_THRESHOLD=0)
product_truth_blocking=1
[[ "${PRODUCT_TRUTH_GATE_BLOCKING:-1}" != "1" ]] && product_truth_blocking=0
pipeline_run_phase product-truth "Product Truth audit gate" "$product_truth_blocking" \
  bash scripts/guard/check-product-truth.sh

# 9c. Phase 6 full-stack service completion
phase6_blocking=1
[[ "${PHASE6_GATE_BLOCKING:-1}" != "1" ]] && phase6_blocking=0
pipeline_run_phase phase6-completion "Phase 6 service completion" "$phase6_blocking" \
  bash scripts/guard/check-phase6-service-completion.sh

# 9d. Cross-service cohesion (blocking by default)
cohesion_blocking=1
[[ "${COHESION_GATE_BLOCKING:-1}" != "1" ]] && cohesion_blocking=0
pipeline_run_phase cohesion "Cross-service cohesion" "$cohesion_blocking" \
  bash scripts/guard/check-cross-service-cohesion.sh

# 9d-2. ext_authz upstream header allowlist (blocking): a header the PDP sets and the
# Helm allowlist omits is dropped silently, and works fine in the gRPC-based dev configs.
pipeline_run_phase ext-authz-allowlist "ext_authz upstream header allowlist" 1 \
  bash scripts/guard/check-ext-authz-header-allowlist.sh

# 9d-3. A5 reference classification (blocking): a new free-text programme_id/department_id
# column must be classified, not left to be discovered later as an unresolvable value.
pipeline_run_phase reference-id-classification "A5 reference id classification" 1 \
  bash scripts/guard/check-reference-id-classification.sh

# 9d-4. Policy path pins (blocking): a `path_contains` pin ending in `/` matches the
# collection root and nothing under it, so the rule reports a boundary it does not enforce.
pipeline_run_phase policy-path-pins "policy path_contains pin semantics" 1 \
  bash scripts/guard/check-policy-path-pins.sh

# 9e. Preview sandbox runtime smoke + persistence E2E (blocking when preview reachable)
preview_blocking=1
[[ "${PREVIEW_SMOKE_BLOCKING:-1}" != "1" ]] && preview_blocking=0
if [[ "${PREVIEW_SMOKE_SKIP:-0}" == "1" ]]; then
  pipeline_run_phase preview-smoke "Preview sandbox smoke (skipped)" 0 \
    bash -c 'echo "SKIP preview smoke — PREVIEW_SMOKE_SKIP=1"'
else
  pipeline_run_phase preview-smoke "Preview sandbox runtime smoke" "$preview_blocking" \
    bash scripts/test/preview-sandbox-runtime-smoke.sh
  pipeline_run_phase preview-persistence-e2e "Preview persistence E2E" "$preview_blocking" \
    bash scripts/test/run-preview-sandbox-persistence-e2e.sh
fi

# 9f. Runtime truth heuristics (advisory by default)
runtime_truth_blocking=0
[[ "${RUNTIME_TRUTH_GATE_BLOCKING:-0}" == "1" ]] && runtime_truth_blocking=1
pipeline_run_phase runtime-truth-heuristics "Runtime truth heuristics" "$runtime_truth_blocking" \
  bash scripts/guard/check-runtime-truth-heuristics.sh

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
    bash scripts/test/verify-session-experience-e2e.sh --url "${SESSION_E2E_URL:-https://impilo.mohcc.gov.zw}" || true
fi

# 13. Full-boot readiness (advisory unless PIPELINE_FULL_BOOT_BLOCKING=1)
full_boot_blocking=0
[[ "${PIPELINE_FULL_BOOT_BLOCKING:-0}" == "1" ]] && full_boot_blocking=1
pipeline_run_phase full-boot-discover "Full-boot artifact generation" 0 \
  bash scripts/full-boot/generate-artifacts.sh || true
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

# 15. Mobile build/typecheck — blocking on real type errors. The script itself keeps genuine
# tooling absence advisory (no pnpm, failed workspace install, APK/iOS builds), so this fails on
# broken mobile code, not on a thin VM. It was non-blocking AND called with `|| true`, so both
# layers had to change: fixing either one alone would have left the failure swallowed.
pipeline_run_phase mobile "Mobile build checks" 1 \
  bash scripts/test/run-mobile-checks.sh

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

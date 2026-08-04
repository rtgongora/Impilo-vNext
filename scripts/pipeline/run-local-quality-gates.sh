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
    bash scripts/pipeline/verify-tools.sh
fi

# 3. Security
pipeline_run_phase security "Secret/security checks" 1 \
  bash scripts/test/run-security-checks.sh

# 4. Static
pipeline_run_phase static "Static checks" 1 \
  bash scripts/test/run-static-checks.sh

# 4b. Repository integrity (blocking, always applicable, seconds to run).
# Leftover conflict markers compile in exactly the formats where they do most
# damage, and typescript.ignoreBuildErrors turns a red build green without
# touching product code.
pipeline_run_phase repo-integrity "Repository integrity" 1 \
  bash scripts/guard/check-repo-integrity.sh

# 4c. Governance pack (blocking, PATH-SELECTED). The verifier enforces seven
# invariants — required files present, CLAUDE.md imports the rules, no frozen
# legacy language outside archive/, the pointer names the active version, the
# versioned architecture is complete rather than a stub, v1.3.1 is historical
# only, and exactly one complete active architecture copy exists. It was wired
# to nothing until now. It also runs from an unrelated cwd, because it resolves
# its own repo root and a path-dependent verifier is a verifier that lies.
GOV_PATHS='^(CLAUDE\.md|docs/architecture/|docs/experience/packs/|docs/standards/|scripts/architecture/)'
if [[ "${PIPELINE_FORCE_GOVERNANCE:-0}" == "1" ]] \
   || git diff --name-only HEAD~1...HEAD 2>/dev/null | grep -qE "$GOV_PATHS"; then
  pipeline_run_phase governance "Governance pack (repo root)" 1 \
    bash scripts/architecture/verify-governance-pack.sh
  pipeline_run_phase governance-cwd "Governance pack (unrelated cwd)" 1 \
    bash -c 'cd /tmp && bash "$OLDPWD/scripts/architecture/verify-governance-pack.sh"'
else
  pipeline_gate_not_applicable governance "Governance pack (repo root)" \
    "no governance paths changed since HEAD~1"
  pipeline_gate_not_applicable governance-cwd "Governance pack (unrelated cwd)" \
    "no governance paths changed since HEAD~1"
fi

# 5+6. Frontend + Backend — the two dominant phases (~4 min npm, ~5 min maven).
# They touch disjoint trees (ui/ vs services/) and distinct log files, so they
# run concurrently by default; PIPELINE_CONCURRENT_CORE=0 restores sequential.
# The concurrent branch hand-rolls what pipeline_run_phase does, because that helper
# runs its command synchronously. That is fine — except it also skipped the ONE thing
# the helper does first: pipeline_should_skip. So PIPELINE_ONLY/PIPELINE_SKIP silently
# did not apply here, and `PIPELINE_ONLY=static` ran the full ~9-minute frontend+backend
# suite anyway. Consult the same predicate, and hand the filtered case to the sequential
# branch below, which already honours it via pipeline_run_phase. One source of filtering
# truth, not two.
_fe_filtered=0; _be_filtered=0
pipeline_should_skip frontend && _fe_filtered=1
pipeline_should_skip backend  && _be_filtered=1

if [[ "${PIPELINE_CONCURRENT_CORE:-1}" == "1" \
      && "${PIPELINE_SKIP_FRONTEND:-0}" != "1" && "$_fe_filtered" == "0" \
      && "${PIPELINE_SKIP_BACKEND:-0}" != "1" && "$_be_filtered" == "0" ]]; then
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

# 9d-5. Keycloak realm import (BLOCKING). Realm JSON Keycloak cannot parse does not
# degrade the platform — Keycloak fails to BOOT, taking all web and mobile auth with it,
# and `--import-realm` skips realms that already exist, so a running estate looks fine
# until the next restart. This gate had never run anywhere: it existed only in a GitHub
# workflow whose runner has been billing-locked since 2026-04-08, and in that window a
# `_comment` key and a 269-character description sat unimportable in the preview realm
# for eight days. Fail-closed by construction — missing docker, missing image, missing
# realm, an undeclared realm, zero realms, or an unwritable log directory are all
# failures, never skips. Governed set: deploy/keycloak/governed-realms.json.
pipeline_run_phase keycloak-realm-import "Keycloak realm import (governed realms)" 1 \
  bash scripts/guard/check-keycloak-realm-import.sh

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

# 9f. Runtime truth heuristics — BLOCKING (was advisory).
# Measured 2026-08-04: passes on main, and mutates nothing. Named "heuristics" for a
# reason — it infers decorative handlers, fixture dashboards and mock clients
# statically, so a future false positive is plausible. RUNTIME_TRUTH_GATE_BLOCKING=0
# demotes it for the run that hits one; reaching for that should come with a fix
# rather than become the default again.
runtime_truth_blocking=1
[[ "${RUNTIME_TRUTH_GATE_BLOCKING:-1}" == "0" ]] && runtime_truth_blocking=0
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
    bash scripts/test/verify-session-experience-e2e.sh --url "${SESSION_E2E_URL:-https://impilo.mohcc.gov.zw}"
fi

# 13. Full-boot readiness — BLOCKING (was advisory). All six measured passing on main,
# 2026-08-04. An advisory failure here was never harmless: require-fresh-gates.sh accepts
# "PASS WITH ADVISORY WARNINGS", so a failing doctrine-compliance or registry-inventory
# check still authorised a deploy. PIPELINE_FULL_BOOT_BLOCKING=0 demotes the set.
#
# KNOWN DEFECT this change deliberately does not mask: five of these six MUTATE the working
# tree as they run - generate-artifacts (10 files), discover-build-targets (3),
# check-full-boot-runtime-completeness (4), check-full-boot-waves (10) and
# check-registry-inventory-contract (10). A check that rewrites what it inspects cannot
# report drift honestly, and since preview_preflight_common hard-fails on a dirty tree,
# running the gates is itself what blocks the following deploy. Repairing that means making
# them check-only and is a separate change: blocking neither causes nor cures it.
full_boot_blocking=1
[[ "${PIPELINE_FULL_BOOT_BLOCKING:-1}" == "0" ]] && full_boot_blocking=0
pipeline_run_phase full-boot-discover "Full-boot artifact generation" "$full_boot_blocking" \
  bash scripts/full-boot/generate-artifacts.sh
pipeline_run_phase full-boot-targets "Full-boot build/image target discovery" "$full_boot_blocking" \
  bash scripts/build/discover-build-targets.sh
pipeline_run_phase full-boot-doctrine "Doctrine compliance" "$full_boot_blocking" \
  bash scripts/guard/check-doctrine-compliance.sh
pipeline_run_phase full-boot-runtime "Full-boot runtime completeness" "$full_boot_blocking" \
  bash scripts/guard/check-full-boot-runtime-completeness.sh
pipeline_run_phase full-boot-waves "Full-boot wave coverage" "$full_boot_blocking" \
  bash scripts/guard/check-full-boot-waves.sh
pipeline_run_phase full-boot-inventory "Registry inventory contract" "$full_boot_blocking" \
  bash scripts/guard/check-registry-inventory-contract.sh

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
    bash scripts/test/run-web-e2e.sh
fi

# Summary
echo ""
echo "========== LOCAL QUALITY PIPELINE SUMMARY =========="

# A mandatory gate that produced no result at all is a failure, not a pass. Without
# this, a gate deleted, renamed or short-circuited by an early `return` simply
# vanishes from the summary and the run still reports PASS — which is precisely how
# the realm guard stayed unwired while the pipeline reported green.
pipeline_assert_mandatory_gates_ran

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

#!/usr/bin/env bash
#
# Guard: prove that the expected integration tests ACTUALLY EXECUTED (not silently
# skipped) after a Failsafe run. Parses services/*/target/failsafe-reports/*.xml and,
# for each expected IT class, asserts tests>0 and failures==0 and errors==0.
#
# A CI integration job MUST NOT pass with all 16 integration tests skipped — call this
# after `mvn verify -P<profile>` in the infra-provided environment to enforce that.
#
# Usage:
#   scripts/test/assert-integration-tests-ran.sh <IT-simple-name> [<IT-simple-name> ...]
#   scripts/test/assert-integration-tests-ran.sh --category containers|postgres|runtime-proof
#
# Exit: 0 = every expected IT ran green; non-zero = a class was missing, all-skipped, or failed.
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# Canonical inventory by category (keep in sync with the it-* Maven profiles).
CONTAINERS_ITS=(SearchPgvectorAnnIT ClinicalKnowledgePathwayApiIT GoldenContractIT ImagingExperienceWireMockIT MobileProviderTier2ResponseShapeIT)
POSTGRES_ITS=(CostaGoldenContractIT ShareSlipGoldenContractIT TshepoAuditGoldenContractIT MvumoCrossServiceFlowIT)
RUNTIME_PROOF_ITS=(PermitEnforcementRuntimeProofIT AssuranceWorkflowRuntimeProofIT GdhcnReadinessRuntimeProofIT StepUpVerificationIT TrustAuthorityRegistryRuntimeProofIT SigningRuntimeProofIT TshepoConsentDevInstanceIT)

EXPECTED=()
if [[ "${1:-}" == "--category" ]]; then
  case "${2:-}" in
    containers)     EXPECTED=("${CONTAINERS_ITS[@]}") ;;
    postgres)       EXPECTED=("${POSTGRES_ITS[@]}") ;;
    runtime-proof)  EXPECTED=("${RUNTIME_PROOF_ITS[@]}") ;;
    *) echo "unknown category: ${2:-}" >&2; exit 2 ;;
  esac
else
  EXPECTED=("$@")
fi
[[ ${#EXPECTED[@]} -gt 0 ]] || { echo "no expected IT classes given" >&2; exit 2; }

# Sum tests/failures/errors/skipped across one or more report files.
attr() { sed -n "s/.*<testsuite[^>]* $1=\"\([0-9]*\)\".*/\1/p" "$2" | head -1; }

fail=0
for cls in "${EXPECTED[@]}"; do
  # A golden-contract IT extends GoldenContractSuite and emits @Nested reports named
  # GoldenContractSuite$*.xml (its own class report shows 0 tests). Locate the module
  # that owns the IT and aggregate the right reports.
  src="$(find "$REPO_ROOT"/services/*/src/test -name "${cls}.java" 2>/dev/null | head -1 || true)"
  module_reports=""
  if [[ -n "$src" ]]; then
    module_dir="${src%%/src/test/*}"
    module_reports="$module_dir/target/failsafe-reports"
  fi
  reports=()
  own="$(find "$REPO_ROOT"/services/*/target/failsafe-reports \( -name "*.${cls}.xml" -o -name "TEST-*${cls}.xml" \) 2>/dev/null | head -1 || true)"
  own_tests=0
  [[ -n "$own" ]] && own_tests=$(attr tests "$own")
  if [[ -n "$own" && "${own_tests:-0}" -gt 0 ]]; then
    reports+=("$own")                     # ordinary IT with its own report
  elif [[ -d "$module_reports" ]]; then
    # golden-contract IT: its own report has 0 tests; the real tests are the
    # GoldenContractSuite @Nested reports in the owning module.
    while IFS= read -r r; do reports+=("$r"); done < <(find "$module_reports" -name "*GoldenContractSuite\$*.xml" 2>/dev/null)
  fi
  if [[ ${#reports[@]} -eq 0 ]]; then
    echo "MISSING  $cls — no failsafe report (did the profile run this module?)"; fail=1; continue
  fi
  tests=0; failures=0; errors=0; skipped=0
  for r in "${reports[@]}"; do
    rt=$(attr tests "$r"); rf=$(attr failures "$r"); re=$(attr errors "$r"); rs=$(attr skipped "$r")
    tests=$(( tests + ${rt:-0} )); failures=$(( failures + ${rf:-0} ))
    errors=$(( errors + ${re:-0} )); skipped=$(( skipped + ${rs:-0} ))
  done
  ran=$(( tests - skipped ))
  if (( ran <= 0 )); then
    echo "SKIPPED  $cls — tests=$tests skipped=$skipped (infrastructure absent; this job must provide it)"; fail=1
  elif (( failures > 0 || errors > 0 )); then
    echo "FAILED   $cls — tests=$tests failures=$failures errors=$errors"; fail=1
  else
    echo "RAN      $cls — tests=$tests (0 failures)"
  fi
done
if (( fail )); then
  echo "GUARD FAILED: not every expected integration test executed green." >&2
  exit 1
fi
echo "GUARD OK: all ${#EXPECTED[@]} expected integration tests executed green."

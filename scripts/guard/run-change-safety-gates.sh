#!/usr/bin/env bash
#
# Change-safety gates — run this BEFORE you push (docs/runbooks/shared-tree-concurrency.md §5a)
# and stop on ANY hit, including one that is not yours.
#
# What counts as "your change" is decided by resolve_base_ref() in
# _guard-common.sh. In short: on a MERGE commit the base is what the merge
# brought in, so the commits merged from canonical are NOT attributed to you;
# on a plain commit it is HEAD~1; GUARD_BASE_REF overrides everything and
# GUARD_UPSTREAM_REF (or `git config guard.upstreamRef`) names the branch your
# lane is measured against. The full rule is documented in _guard-common.sh.
#
set -euo pipefail
if [[ -z "${REPO_PATH:-}" ]]; then
  REPO_PATH="$(git -C "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)" rev-parse --show-toplevel 2>/dev/null \
    || echo /opt/impilo/repos/Impilo-vNext)"
fi
export REPO_PATH   # every child guard reviews the same checkout as this entry point
cd "$REPO_PATH"
source "$REPO_PATH/scripts/guard/_guard-common.sh"

CHECKS=(
  tests/base-ref-resolution-test.sh   # the attribution rule itself, before anything uses it
  check-deprecated-surfaces.sh
  check-retired-sidecars-full-boot.sh
  check-dangerous-deletions.sh
  check-duplicate-services.sh
  check-service-inventory.sh
  check-feature-inventory.sh
  check-route-inventory.sh
  check-api-contracts.sh
  check-backend-frontend-parity.sh
  check-mobile-parity.sh
  check-madi-surfacing.sh
  check-care-continuum-doctrine.sh
  check-imnci-capture-coverage.sh
  check-dak-traceability.sh
  check-rmnp-capture-coverage.sh
  check-source-text-integrity.sh
  check-migration-version-collisions.sh
  check-butano-data-durability.sh
)

FAIL=0
echo "=== Change-safety gates ==="
echo "REPO: $REPO_PATH"
BASE="$(GUARD_EXPLAIN_BASE=1 resolve_base_ref)"
export GUARD_BASE_REF="$BASE"   # resolve once; every child guard reviews the same range
echo "BASE: $BASE"
echo "Under review: $(git rev-list --count "$BASE"..HEAD 2>/dev/null || echo '?') commit(s), $(git diff --name-only "$BASE" HEAD 2>/dev/null | wc -l | tr -d ' ') file(s)"

for c in "${CHECKS[@]}"; do
  echo ""
  if bash "scripts/guard/$c"; then
    :
  else
    FAIL=1
  fi
done

bash scripts/guard/generate-change-summary.sh || true

if [[ "$FAIL" -ne 0 ]]; then
  echo ""
  echo "CHANGE-SAFETY: BLOCKED"
  exit 1
fi
echo ""
echo "CHANGE-SAFETY: PASSED"
exit 0

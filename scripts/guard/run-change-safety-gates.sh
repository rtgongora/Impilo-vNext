#!/usr/bin/env bash
set -euo pipefail
_GUARD_CALLER_PWD="$PWD"   # captured before the cd; see guard_assert_repo_path
export _GUARD_CALLER_PWD
REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"
source "$REPO_PATH/scripts/guard/_guard-common.sh"

# Before any gate runs: prove we are inspecting the tree the caller is standing in. A gate that
# passes cleanly about somebody else's checkout is worse than no gate — it is the one result nobody
# goes back and questions.
guard_assert_repo_path || exit 1

CHECKS=(
  check-deprecated-surfaces.sh
  check-retired-sidecars-full-boot.sh
  check-dangerous-deletions.sh
  check-duplicate-services.sh
  check-service-inventory.sh
  check-feature-inventory.sh
  check-route-inventory.sh
  check-api-contracts.sh
  check-typed-hook-passthrough.sh
  check-cross-tree-node-modules.sh
  check-backend-frontend-parity.sh
  check-mobile-parity.sh
  check-madi-surfacing.sh
  check-care-continuum-doctrine.sh
  check-imnci-capture-coverage.sh
  check-dak-traceability.sh
  check-rmnp-capture-coverage.sh
  check-top-no-record-level-emit.sh
  check-source-text-integrity.sh
  check-migration-version-collisions.sh
  check-butano-data-durability.sh
)

FAIL=0
echo "=== Change-safety gates ==="
echo "BASE: $(resolve_base_ref)"
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

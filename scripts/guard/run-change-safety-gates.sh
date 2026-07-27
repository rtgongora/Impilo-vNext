#!/usr/bin/env bash
set -euo pipefail
REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
cd "$REPO_PATH"
source "$REPO_PATH/scripts/guard/_guard-common.sh"

CHECKS=(
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

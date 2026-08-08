#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_gate-common.sh"
cd "$REPO_PATH"
FAIL=0

gate_run "openapi-files-present" bash -c '
  test -d contracts/openapi
  count=$(find contracts/openapi -name "*.openapi.yaml" -o -name "*.yaml" | wc -l)
  test "$count" -gt 0
' || FAIL=1

gate_run "service-registry-validation-advisory" bash -c '
  pip install -q pyyaml 2>/dev/null || python3 -m pip install -q pyyaml
  SERVICE_REGISTRY_VALIDATION_MODE=advisory python3 scripts/architecture/validate-service-registry.py
' || gate_warn "service-registry validation advisory failed"

gate_run "bff-health-version-route-exists" bash -c '
  grep -rq "/health/version" services/experience-bff/src/main/java
' || FAIL=1

gate_run "openapi-social-contract" bash -c 'test -f contracts/openapi/social.openapi.yaml' || FAIL=1

gate_run "openapi-yaml-validity" bash scripts/guard/check-openapi-yaml-validity.sh || FAIL=1

gate_run "trust-decision-contracts" bash scripts/guard/check-trust-decision-contracts.sh || FAIL=1

gate_run "contract-implementation-matrix" bash -c '
  cd scripts/completeness
  npm install --silent 2>/dev/null || npm install --silent
  npm run contract-matrix --silent
  test -f ../../reports/product/contract-implementation-matrix.json
' || FAIL=1

# Was "advisory" in two independent ways, neither of which worked as intended:
#   - the threshold was ASSIGNED but never EXPORTED inside `bash -c`, so the child
#     guard never saw 999999 and fell back to its own default of 0 — every run
#     reported "threshold=0" and failed;
#   - `|| gate_warn` then downgraded that failure to a warning, so the phase passed
#     regardless of the number.
# Permanently red and permanently ignored. The guard now ratchets against a recorded
# baseline (reports/product/contract-implementation-baseline.json), so at-or-below
# baseline is a genuine pass and a regression is a genuine failure. No export is
# needed: absent an explicit override the guard reads that baseline itself.
gate_run "contract-implementation" bash scripts/guard/check-contract-implementation.sh || FAIL=1

exit "$FAIL"

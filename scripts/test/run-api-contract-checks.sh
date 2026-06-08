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

gate_run "contract-implementation-matrix" bash -c '
  cd scripts/completeness
  npm install --silent 2>/dev/null || npm install --silent
  npm run contract-matrix --silent
  test -f ../../reports/product/contract-implementation-matrix.json
' || FAIL=1

gate_run "contract-implementation-gate-advisory" bash -c '
  CONTRACT_IMPLEMENTATION_VIOLATION_THRESHOLD="${CONTRACT_IMPLEMENTATION_VIOLATION_THRESHOLD:-999999}"
  bash scripts/guard/check-contract-implementation.sh
' || gate_warn "contract implementation violations present — see CONTRACT_IMPLEMENTATION_MATRIX.md"

exit "$FAIL"

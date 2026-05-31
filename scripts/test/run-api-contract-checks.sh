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
  rg -q "/health/version" services/experience-bff/src/main/java
' || FAIL=1

gate_run "openapi-social-contract" bash -c 'test -f contracts/openapi/social.openapi.yaml' || FAIL=1

exit "$FAIL"
